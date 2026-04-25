package com.reported.nativeandroid.screens

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.shared.model.PlatePatternClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val DETECTOR_ASSET = "models/yolo-v9-t-640-license-plates-end2end.onnx"
private const val OCR_ASSET = "models/global_mobile_vit_v2_ocr.onnx"
private const val PLATE_STATE_ASSET = "models/reported-plate-class-best.onnx"
private const val COMPLAINT_ASSET = "models/reported-v13-optimized.onnx"
private const val PLATE_SEGMENTATION_ASSET = "models/reported-plate-seg.onnx"
private const val VEHICLE_SEGMENTATION_ASSET = "models/yolov8n-seg.onnx"
private const val VEHICLE_SEGMENTATION_NMS_ASSET = "models/nms-yolov8.onnx"
private const val DETECTOR_SIZE = 640
private const val VEHICLE_SEGMENTATION_SIZE = 640
private const val VEHICLE_SEGMENTATION_CLASSES = 80
private const val VEHICLE_SEGMENTATION_ROW_SIZE = 116
private const val VEHICLE_SEGMENTATION_TOP_K = 100f
private const val VEHICLE_SEGMENTATION_IOU_THRESHOLD = 0.4f
private const val VEHICLE_SEGMENTATION_SCORE_THRESHOLD = 0.2f
private const val VEHICLE_SEGMENTATION_MIN_SCORE = 0.5f
private const val VEHICLE_SEGMENTATION_MIN_SIDE = 60f
private const val COCO_PERSON_LABEL = 0
private const val COCO_BICYCLE_LABEL = 1
private const val COMPLAINT_BLOCKED_BIKE_LANE = "blocked_bike_lane"
private const val COMPLAINT_BLOCKED_CROSSWALK = "blocked_crosswalk"
private const val COMPLAINT_CLASS_BLOCKED_BIKE_LANE = 0
private const val COMPLAINT_CLASS_BLOCKED_CROSSWALK = 1
private const val COMPLAINT_DETECTION_THRESHOLD = 0.9f
private const val PLATE_STATE_SIZE = 160
private const val PLATE_SEGMENTATION_SIZE = 160
private const val OCR_WIDTH = 140
private const val OCR_HEIGHT = 70
private const val DETECTION_THRESHOLD = 0.2f
private const val SEGMENTATION_MASK_THRESHOLD = 0.5f
private const val MAX_CANDIDATES = 8
private const val LETTERBOX_COLOR = 114
private const val OCR_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_"
private const val DESKEW_TRIGGER_DEGREES = 1.5f
private const val MAX_DESKEW_DEGREES = 20
private const val EDGE_THRESHOLD = 32
private const val PLATE_AXIS_PADDING = 0.45f
private const val PLATE_CROSS_AXIS_PADDING = 0.65f
private const val ROTATED_MATCH_DISTANCE_MULTIPLIER = 2.5f
private const val VIDEO_FRAME_STRIDE = 3
private const val VIDEO_DETECTION_BATCH_SIZE = 3
private const val VIDEO_ROTATION_PROBE_STRIDE = 9
private const val VIDEO_DRY_PROCESSED_FRAME_COUNT = 3
private val PLATE_STATE_LABELS = listOf("CT", "00", "NJ", "NY", "NY_PD", "NY_TLC", "PA")
private val VIDEO_ROTATION_PROBE_DEGREES = floatArrayOf(-90f, 90f, 180f)
private val VEHICLE_SEGMENTATION_LABELS = setOf(2, 5, 7) // COCO car, bus, truck.

private data class LetterboxedBitmap(
    val bitmap: Bitmap,
    val scale: Float,
    val padX: Float,
    val padY: Float
)

private data class VehicleSegmentationBitmap(
    val bitmap: Bitmap,
    val scale: Float
)

private data class Detection(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val score: Float
)

private data class SegmentedObject(
    val label: Int,
    val detection: Detection
)

private data class VideoFrameSample(
    val frameIndex: Int,
    val frameTimeUs: Long,
    val bitmap: Bitmap
)

private data class PlateCropWork(
    val bitmap: Bitmap,
    val sourceDetection: Detection,
    val crop: PlateCrop
)

private data class PlateCrop(
    val bitmap: Bitmap,
    val thumbnailBitmap: Bitmap,
    val detection: Detection,
    val rotationDegrees: Float,
    val cornerPoints: List<Float> = emptyList()
)

private data class RotatedBitmap(
    val bitmap: Bitmap,
    val sourceToRotated: Matrix
)

private data class RotatedVariantDetection(
    val candidates: List<PlateCandidate>,
    val degrees: Float
)

private data class RotatedPlateMatch(
    val rotatedImage: RotatedBitmap,
    val detection: Detection,
    val appliedRotationDegrees: Float,
    val matchDistanceSquared: Float
)

private data class AlprSessions(
    val detector: OrtSession,
    val ocr: OrtSession,
    val plateState: OrtSession,
    val complaint: OrtSession?,
    val plateSegmentation: OrtSession?,
    val vehicleSegmentation: OrtSession?,
    val vehicleSegmentationNms: OrtSession?
)

private data class PlateStateClassification(
    val state: String?,
    val confidence: Float,
    val label: String
)

internal data class VideoFrameScanProgress(
    val processedFrames: Int,
    val totalFrames: Int,
    val frameTimeUs: Long,
    val durationMs: Long,
    val candidatesFound: Int,
    val framePreviewUri: String? = null,
    val frameCandidates: List<PlateCandidate> = emptyList(),
    val allCandidates: List<PlateCandidate> = emptyList()
)

internal object NativeAlprEngine {
    private val ortEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val sessionMutex = Mutex()
    private var detectorSession: OrtSession? = null
    private var ocrSession: OrtSession? = null
    private var plateStateSession: OrtSession? = null
    private var complaintSession: OrtSession? = null
    private var plateSegmentationSession: OrtSession? = null
    private var vehicleSegmentationSession: OrtSession? = null
    private var vehicleSegmentationNmsSession: OrtSession? = null

    suspend fun detectLicensePlates(context: Context, media: SubmissionMedia): List<PlateCandidate> =
        withContext(Dispatchers.IO) {
            if (media.isVideo) {
                return@withContext emptyList()
            }

            val bitmap = decodeBitmap(context, Uri.parse(media.uri)) ?: return@withContext emptyList()
            val sessions = getSessions(context.applicationContext)
            detectLicensePlatesInBitmap(context, bitmap, sessions)
        }

    suspend fun inferComplaintId(context: Context, media: SubmissionMedia): String? =
        withContext(Dispatchers.IO) {
            if (media.isVideo) return@withContext null
            val bitmap = decodeBitmap(context, Uri.parse(media.uri)) ?: return@withContext null
            val sessions = getSessions(context.applicationContext)
            detectComplaint(bitmap, sessions.complaint)
        }

    suspend fun detectLicensePlatesInVideo(
        context: Context,
        media: SubmissionMedia,
        onProgress: suspend (VideoFrameScanProgress) -> Unit
    ): List<PlateCandidate> = withContext(Dispatchers.IO) {
        if (!media.isVideo) {
            return@withContext detectLicensePlates(context, media)
        }

        val sessions = getSessions(context.applicationContext)
        val retriever = MediaMetadataRetriever()
        val bestCandidatesByPlate = linkedMapOf<String, PlateCandidate>()
        try {
            val uri = Uri.parse(media.uri)
            setRetrieverDataSource(context, retriever, uri)
            clearVideoFramePreviews(context)
            val durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.times(1_000L)
                ?: 0L
            val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
                ?.takeIf { it > 0f }
                ?: 30f
            val metadataRotationDegrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toFloatOrNull()
                ?.normalizedVideoRotationDegrees()
                ?: 0f
            val frameIntervalUs = (1_000_000f / frameRate).roundToLong().coerceAtLeast(1L)
            val totalFrames = frameCount ?: ceil(durationUs / frameIntervalUs.toDouble())
                .toInt()
                .coerceAtLeast(1)
            if (totalFrames <= 0) {
                onProgress(VideoFrameScanProgress(0, 0, 0L, 0L, 0))
                return@withContext emptyList()
            }

            val recentProcessedFramesHadPlate = ArrayDeque<Boolean>(VIDEO_DRY_PROCESSED_FRAME_COUNT)
            var lastSuccessfulFallbackRotationDegrees: Float? = null
            var index = 0
            while (index < totalFrames) {
                currentCoroutineContext().ensureActive()
                val samples = mutableListOf<VideoFrameSample>()
                while (index < totalFrames && samples.size < VIDEO_DETECTION_BATCH_SIZE) {
                    val frameTimeUs = if (durationUs > 0L && frameCount != null) {
                        ((index.toDouble() / totalFrames.toDouble()) * durationUs.toDouble()).roundToLong()
                    } else {
                        (index * frameIntervalUs).coerceAtMost(durationUs.takeIf { it > 0L } ?: Long.MAX_VALUE)
                    }
                    retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let { frame ->
                        samples += VideoFrameSample(index, frameTimeUs, frame)
                    }
                    index += VIDEO_FRAME_STRIDE
                }
                if (samples.isEmpty()) continue

                val batchedCandidates = detectLicensePlatesInBitmapsBatched(
                    context = context,
                    bitmaps = samples.map { it.bitmap },
                    sessions = sessions
                )
                samples.forEachIndexed { sampleIndex, sample ->
                    currentCoroutineContext().ensureActive()
                    var frameCandidates = batchedCandidates.getOrNull(sampleIndex).orEmpty()
                    if (frameCandidates.isEmpty() && shouldProbeRotations(sample.frameIndex, recentProcessedFramesHadPlate)) {
                        val rotatedDetection = detectLicensePlatesInRotatedVariants(
                            context = context,
                            bitmap = sample.bitmap,
                            sessions = sessions,
                            probeDegrees = orderedVideoRotationProbeDegrees(
                                metadataRotationDegrees = metadataRotationDegrees,
                                lastSuccessfulRotationDegrees = lastSuccessfulFallbackRotationDegrees
                            )
                        )
                        if (rotatedDetection != null) {
                            frameCandidates = rotatedDetection.candidates
                            lastSuccessfulFallbackRotationDegrees = rotatedDetection.degrees
                        }
                    }
                    val framePreviewUri = saveVideoFramePreview(
                        context = context,
                        frameIndex = sample.frameIndex,
                        bitmap = sample.bitmap,
                        candidates = frameCandidates
                    )
                    frameCandidates = frameCandidates.map { candidate ->
                        candidate.copy(
                            videoFramePreviewUri = framePreviewUri,
                            videoFrameTimeMs = sample.frameTimeUs / 1_000L
                        )
                    }
                    for (candidate in frameCandidates) {
                        val current = bestCandidatesByPlate[candidate.plate]
                        if (current == null || candidate.confidence > current.confidence) {
                            bestCandidatesByPlate[candidate.plate] = candidate
                        }
                    }
                    if (!sample.bitmap.isRecycled) {
                        sample.bitmap.recycle()
                    }
                    recentProcessedFramesHadPlate.addLast(frameCandidates.isNotEmpty())
                    while (recentProcessedFramesHadPlate.size > VIDEO_DRY_PROCESSED_FRAME_COUNT) {
                        recentProcessedFramesHadPlate.removeFirst()
                    }
                    val allCandidates = bestCandidatesByPlate.values.sortedByDescending { it.confidence }.take(MAX_CANDIDATES)
                    onProgress(
                        VideoFrameScanProgress(
                            processedFrames = sample.frameIndex + 1,
                            totalFrames = totalFrames,
                            frameTimeUs = sample.frameTimeUs,
                            durationMs = durationUs / 1_000L,
                            candidatesFound = bestCandidatesByPlate.size,
                            framePreviewUri = framePreviewUri,
                            frameCandidates = frameCandidates,
                            allCandidates = allCandidates
                        )
                    )
                }
            }

            bestCandidatesByPlate.values.sortedByDescending { it.confidence }.take(MAX_CANDIDATES)
        } catch (_: Throwable) {
            emptyList()
        } finally {
            retriever.release()
        }
    }

    private fun detectLicensePlatesInBitmap(context: Context, bitmap: Bitmap, sessions: AlprSessions): List<PlateCandidate> {
        val detections = detect(bitmap, sessions.detector)
        if (detections.isEmpty()) {
            return emptyList()
        }

        val works = detections.take(MAX_CANDIDATES).mapNotNull { detection ->
            val plateCrop = refinedPlateCrop(bitmap, detection, sessions) ?: return@mapNotNull null
            PlateCropWork(bitmap = bitmap, sourceDetection = detection, crop = plateCrop)
        }
        val ocrTexts = runOcrBatched(works.map { it.crop.bitmap }, sessions.ocr)
        val plateStates = classifyPlateStatesBatched(works.map { it.crop.bitmap }, sessions.plateState)
        return works.mapIndexedNotNull { index, work ->
            buildPlateCandidate(
                context = context,
                bitmap = work.bitmap,
                detection = work.sourceDetection,
                plateCrop = work.crop,
                plateText = ocrTexts.getOrNull(index).orEmpty(),
                plateState = plateStates.getOrNull(index)
            )
        }.distinctBy { it.plate }.sortedByDescending { it.confidence }
    }

    private fun detectLicensePlatesInBitmapsBatched(
        context: Context,
        bitmaps: List<Bitmap>,
        sessions: AlprSessions
    ): List<List<PlateCandidate>> {
        if (bitmaps.isEmpty()) return emptyList()
        val detectionsByBitmap = detectBatched(bitmaps, sessions.detector)
        val worksByBitmap = bitmaps.mapIndexed { bitmapIndex, bitmap ->
            val detections = detectionsByBitmap.getOrNull(bitmapIndex).orEmpty()
            detections.take(MAX_CANDIDATES).mapNotNull { detection ->
                val plateCrop = refinedPlateCrop(bitmap, detection, sessions) ?: return@mapNotNull null
                PlateCropWork(bitmap = bitmap, sourceDetection = detection, crop = plateCrop)
            }
        }
        val allWorks = worksByBitmap.flatten()
        val ocrTexts = runOcrBatched(allWorks.map { it.crop.bitmap }, sessions.ocr)
        val plateStates = classifyPlateStatesBatched(allWorks.map { it.crop.bitmap }, sessions.plateState)
        var ocrIndex = 0
        var classifierIndex = 0
        return worksByBitmap.map { works ->
            works.mapNotNull { work ->
                val plateText = ocrTexts.getOrNull(ocrIndex++).orEmpty()
                val plateState = plateStates.getOrNull(classifierIndex++)
                buildPlateCandidate(
                    context = context,
                    bitmap = work.bitmap,
                    detection = work.sourceDetection,
                    plateCrop = work.crop,
                    plateText = plateText,
                    plateState = plateState
                )
            }.distinctBy { it.plate }.sortedByDescending { it.confidence }
        }
    }

    private fun buildPlateCandidate(
        context: Context,
        bitmap: Bitmap,
        detection: Detection,
        plateCrop: PlateCrop,
        plateText: String,
        plateState: PlateStateClassification?
    ): PlateCandidate? {
        val normalized = normalizePlateText(plateText)
        if (normalized.isBlank()) return null
        val patternMatch = PlatePatternClassifier.classify(normalized)
        val detectedState = patternMatch?.state ?: if (normalized.startsWith('T') && normalized.endsWith('C')) {
            "NY"
        } else {
            plateState?.state
        }
        val stateConfidence = patternMatch?.confidence ?: plateState?.confidence
        val refinedDetection = plateCrop.detection
        return PlateCandidate(
            plate = normalized,
            confidence = min(1f, max(detection.score, refinedDetection.score)),
            state = detectedState,
            stateConfidence = stateConfidence,
            plateType = patternMatch?.type?.name,
            plateTypeLabel = patternMatch?.label,
            focalPointX = ((refinedDetection.x1 + refinedDetection.x2) / 2f / bitmap.width).coerceIn(0f, 1f),
            focalPointY = ((refinedDetection.y1 + refinedDetection.y2) / 2f / bitmap.height).coerceIn(0f, 1f),
            boundsLeft = (refinedDetection.x1 / bitmap.width).coerceIn(0f, 1f),
            boundsTop = (refinedDetection.y1 / bitmap.height).coerceIn(0f, 1f),
            boundsRight = (refinedDetection.x2 / bitmap.width).coerceIn(0f, 1f),
            boundsBottom = (refinedDetection.y2 / bitmap.height).coerceIn(0f, 1f),
            rotationDegrees = plateCrop.rotationDegrees,
            cornerPoints = plateCrop.cornerPoints.toNormalizedCornerPoints(
                width = bitmap.width,
                height = bitmap.height
            ),
            thumbnailUri = savePlateThumbnail(context, normalized, plateCrop.thumbnailBitmap)
        )
    }

    private fun shouldProbeRotations(frameIndex: Int, recentProcessedFramesHadPlate: ArrayDeque<Boolean>): Boolean =
        frameIndex > 0 &&
            frameIndex % VIDEO_ROTATION_PROBE_STRIDE == 0 &&
            recentProcessedFramesHadPlate.size >= VIDEO_DRY_PROCESSED_FRAME_COUNT &&
            recentProcessedFramesHadPlate.none { it }

    private fun detectLicensePlatesInRotatedVariants(
        context: Context,
        bitmap: Bitmap,
        sessions: AlprSessions,
        probeDegrees: List<Float>
    ): RotatedVariantDetection? {
        for (degrees in probeDegrees) {
            val rotated = rotateBitmapWithTransform(bitmap, degrees)
            val candidates = detectLicensePlatesInBitmap(context, rotated.bitmap, sessions)
            if (candidates.isNotEmpty()) {
                val mappedCandidates = candidates.mapNotNull { candidate ->
                    candidate.mapFromRotatedToSource(rotated, bitmap.width, bitmap.height)
                }.sortedByDescending { it.confidence }
                if (mappedCandidates.isNotEmpty()) {
                    return RotatedVariantDetection(mappedCandidates, degrees)
                }
            }
        }
        return null
    }

    private fun orderedVideoRotationProbeDegrees(
        metadataRotationDegrees: Float,
        lastSuccessfulRotationDegrees: Float?
    ): List<Float> {
        val ordered = mutableListOf<Float>()
        fun add(degrees: Float?) {
            val normalized = degrees?.normalizedVideoRotationDegrees() ?: return
            if (abs(normalized) < 0.5f) return
            if (ordered.none { abs(it - normalized) < 0.5f }) {
                ordered += normalized
            }
        }

        add(lastSuccessfulRotationDegrees)
        add(metadataRotationDegrees)
        add(-metadataRotationDegrees)
        VIDEO_ROTATION_PROBE_DEGREES.forEach(::add)
        return ordered
    }

    private fun PlateCandidate.mapFromRotatedToSource(
        rotatedImage: RotatedBitmap,
        sourceWidth: Int,
        sourceHeight: Int
    ): PlateCandidate? {
        val inverse = Matrix()
        if (!rotatedImage.sourceToRotated.invert(inverse)) return null
        val rotatedWidth = rotatedImage.bitmap.width.toFloat()
        val rotatedHeight = rotatedImage.bitmap.height.toFloat()
        val points = if (cornerPoints.size >= 8) {
            FloatArray(8) { index ->
                if (index % 2 == 0) cornerPoints[index] * rotatedWidth else cornerPoints[index] * rotatedHeight
            }
        } else {
            floatArrayOf(
                (boundsLeft ?: return null) * rotatedWidth,
                (boundsTop ?: return null) * rotatedHeight,
                (boundsRight ?: return null) * rotatedWidth,
                (boundsTop ?: return null) * rotatedHeight,
                (boundsRight ?: return null) * rotatedWidth,
                (boundsBottom ?: return null) * rotatedHeight,
                (boundsLeft ?: return null) * rotatedWidth,
                (boundsBottom ?: return null) * rotatedHeight
            )
        }
        inverse.mapPoints(points)
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (index in 0 until 8 step 2) {
            left = min(left, points[index])
            top = min(top, points[index + 1])
            right = max(right, points[index])
            bottom = max(bottom, points[index + 1])
        }
        if (right <= left || bottom <= top) return null
        return copy(
            focalPointX = (((left + right) / 2f) / sourceWidth).coerceIn(0f, 1f),
            focalPointY = (((top + bottom) / 2f) / sourceHeight).coerceIn(0f, 1f),
            boundsLeft = (left / sourceWidth).coerceIn(0f, 1f),
            boundsTop = (top / sourceHeight).coerceIn(0f, 1f),
            boundsRight = (right / sourceWidth).coerceIn(0f, 1f),
            boundsBottom = (bottom / sourceHeight).coerceIn(0f, 1f),
            cornerPoints = points.toList().toNormalizedCornerPoints(sourceWidth, sourceHeight)
        )
    }

    private fun setRetrieverDataSource(context: Context, retriever: MediaMetadataRetriever, uri: Uri) {
        if (uri.scheme == "file") {
            val path = uri.path ?: return
            retriever.setDataSource(path)
            return
        }
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            retriever.setDataSource(descriptor.fileDescriptor)
        }
    }

    private fun clearVideoFramePreviews(context: Context) {
        File(context.cacheDir, "alpr-video-frames")
            .takeIf { it.exists() }
            ?.listFiles()
            ?.forEach { it.delete() }
    }

    private fun saveVideoFramePreview(
        context: Context,
        frameIndex: Int,
        bitmap: Bitmap,
        candidates: List<PlateCandidate>
    ): String? = runCatching {
        val directory = File(context.cacheDir, "alpr-video-frames").apply { mkdirs() }
        val maxWidth = 720f
        val scale = min(1f, maxWidth / bitmap.width.toFloat())
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, width, height), imagePaint)

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.rgb(0, 200, 83)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textSize = 28f
            color = Color.WHITE
        }
        val labelBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(210, 0, 0, 0)
        }
        candidates.forEach { candidate ->
            val points = candidate.cornerPoints
            if (points.size >= 8) {
                val path = android.graphics.Path().apply {
                    moveTo(points[0] * width, points[1] * height)
                    lineTo(points[2] * width, points[3] * height)
                    lineTo(points[4] * width, points[5] * height)
                    lineTo(points[6] * width, points[7] * height)
                    close()
                }
                canvas.drawPath(path, boxPaint)
            } else {
                val left = (candidate.boundsLeft ?: 0f) * width
                val top = (candidate.boundsTop ?: 0f) * height
                val right = (candidate.boundsRight ?: 0f) * width
                val bottom = (candidate.boundsBottom ?: 0f) * height
                canvas.drawRect(left, top, right, bottom, boxPaint)
            }
            val label = buildString {
                append(candidate.plate)
                candidate.state?.let { append("  ").append(it) }
                append("  ").append((candidate.confidence * 100).roundToInt()).append("%")
            }
            val labelX = ((candidate.boundsLeft ?: 0f) * width).coerceIn(4f, width - 4f)
            val labelY = (((candidate.boundsTop ?: 0f) * height) - 10f).coerceAtLeast(32f)
            val labelWidth = labelPaint.measureText(label) + 16f
            canvas.drawRoundRect(
                labelX,
                labelY - 30f,
                (labelX + labelWidth).coerceAtMost(width.toFloat() - 4f),
                labelY + 8f,
                8f,
                8f,
                labelBackground
            )
            canvas.drawText(label, labelX + 8f, labelY, labelPaint)
        }

        val file = File(directory, "frame-${frameIndex.toString().padStart(6, '0')}.jpg")
        file.outputStream().use { outputStream ->
            output.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
        }
        output.recycle()
        Uri.fromFile(file).toString()
    }.getOrNull()

    private suspend fun getSessions(context: Context): AlprSessions = sessionMutex.withLock {
        val detector = detectorSession ?: ortEnvironment.createSession(
            context.assets.open(DETECTOR_ASSET).readBytes(),
            OrtSession.SessionOptions()
        ).also { detectorSession = it }
        val ocr = ocrSession ?: ortEnvironment.createSession(
            context.assets.open(OCR_ASSET).readBytes(),
            OrtSession.SessionOptions()
        ).also { ocrSession = it }
        val plateState = plateStateSession ?: ortEnvironment.createSession(
            context.assets.open(PLATE_STATE_ASSET).readBytes(),
            OrtSession.SessionOptions()
        ).also { plateStateSession = it }
        val complaint = complaintSession ?: runCatching {
            ortEnvironment.createSession(
                context.assets.open(COMPLAINT_ASSET).readBytes(),
                OrtSession.SessionOptions()
            )
        }.getOrNull()?.also { complaintSession = it }
        val plateSegmentation = plateSegmentationSession ?: runCatching {
            ortEnvironment.createSession(
                context.assets.open(PLATE_SEGMENTATION_ASSET).readBytes(),
                OrtSession.SessionOptions()
            )
        }.getOrNull()?.also { plateSegmentationSession = it }
        AlprSessions(
            detector = detector,
            ocr = ocr,
            plateState = plateState,
            complaint = complaint,
            plateSegmentation = plateSegmentation,
            vehicleSegmentation = null,
            vehicleSegmentationNms = null
        )
    }

    private fun mergeDetections(
        directDetections: List<Detection>,
        vehicleDetections: List<Detection>
    ): List<Detection> {
        if (directDetections.isEmpty()) return vehicleDetections.sortedByDescending { it.score }
        if (vehicleDetections.isEmpty()) return directDetections.sortedByDescending { it.score }
        val merged = mutableListOf<Detection>()
        (directDetections + vehicleDetections).sortedByDescending { it.score }.forEach { detection ->
            val isDuplicate = merged.any { existing -> existing.iouWith(detection) > 0.55f }
            if (!isDuplicate) {
                merged += detection
            }
        }
        return merged.sortedByDescending { it.score }
    }

    private fun detectPlatesViaVehicleSegmentation(bitmap: Bitmap, sessions: AlprSessions): List<Detection> {
        val segmenter = sessions.vehicleSegmentation ?: return emptyList()
        val nms = sessions.vehicleSegmentationNms ?: return emptyList()
        val vehicles = detectVehicles(bitmap, segmenter, nms)
            .sortedWith(compareBy<Detection> { it.centerDistanceSquared(bitmap) }.thenByDescending { it.area })
            .take(4)
        if (vehicles.isEmpty()) return emptyList()
        val detections = mutableListOf<Detection>()
        vehicles.forEach { vehicle ->
            val vehicleBitmap = crop(bitmap, vehicle) ?: return@forEach
            val vehiclePlateDetections = detect(vehicleBitmap, sessions.detector)
            vehiclePlateDetections.forEach { plate ->
                detections += Detection(
                    x1 = (vehicle.x1 + plate.x1).coerceIn(0f, bitmap.width.toFloat()),
                    y1 = (vehicle.y1 + plate.y1).coerceIn(0f, bitmap.height.toFloat()),
                    x2 = (vehicle.x1 + plate.x2).coerceIn(0f, bitmap.width.toFloat()),
                    y2 = (vehicle.y1 + plate.y2).coerceIn(0f, bitmap.height.toFloat()),
                    score = (plate.score * 0.85f + vehicle.score * 0.15f).coerceIn(0f, 1f)
                )
            }
        }
        return detections.sortedByDescending { it.score }
    }

    private fun detectVehicles(bitmap: Bitmap, segmenter: OrtSession, nms: OrtSession): List<Detection> {
        return detectSegmentedObjects(bitmap, segmenter, nms)
            .filter {
                it.label in VEHICLE_SEGMENTATION_LABELS &&
                    it.detection.score >= VEHICLE_SEGMENTATION_MIN_SCORE &&
                    (it.detection.x2 - it.detection.x1) >= VEHICLE_SEGMENTATION_MIN_SIDE &&
                    (it.detection.y2 - it.detection.y1) >= VEHICLE_SEGMENTATION_MIN_SIDE
            }
            .map { it.detection }
    }

    private fun detectSegmentedObjects(bitmap: Bitmap, segmenter: OrtSession, nms: OrtSession): List<SegmentedObject> {
        val segmented = vehicleSegmentationBitmap(bitmap)
        val inputName = segmenter.inputNames.first()
        val inputBuffer = bitmapToFloatBuffer(segmented.bitmap)
        OnnxTensor.createTensor(
            ortEnvironment,
            inputBuffer,
            longArrayOf(1, 3, VEHICLE_SEGMENTATION_SIZE.toLong(), VEHICLE_SEGMENTATION_SIZE.toLong())
        ).use { inputTensor ->
            segmenter.run(mapOf(inputName to inputTensor)).use { segmentResults ->
                val detectionTensor = segmentResults[0] as? OnnxTensor ?: return emptyList()
                val configBuffer = ByteBuffer.allocateDirect(4 * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(VEHICLE_SEGMENTATION_CLASSES.toFloat())
                        put(VEHICLE_SEGMENTATION_TOP_K)
                        put(VEHICLE_SEGMENTATION_IOU_THRESHOLD)
                        put(VEHICLE_SEGMENTATION_SCORE_THRESHOLD)
                        rewind()
                    }
                OnnxTensor.createTensor(ortEnvironment, configBuffer, longArrayOf(4)).use { configTensor ->
                    nms.run(mapOf("detection" to detectionTensor, "config" to configTensor)).use { nmsResults ->
                        val selected = flattenFloats(nmsResults[0].value)
                        if (selected.isEmpty()) return emptyList()
                        val rowSize = inferVehicleSegmentationRowSize(selected.size)
                        if (rowSize < VEHICLE_SEGMENTATION_CLASSES + 4) return emptyList()
                        val rowCount = selected.size / rowSize
                        val objects = mutableListOf<SegmentedObject>()
                        for (rowIndex in 0 until rowCount) {
                            val offset = rowIndex * rowSize
                            val centerX = selected[offset]
                            val centerY = selected[offset + 1]
                            val width = selected[offset + 2]
                            val height = selected[offset + 3]
                            var bestLabel = -1
                            var bestScore = Float.NEGATIVE_INFINITY
                            for (labelIndex in 0 until VEHICLE_SEGMENTATION_CLASSES) {
                                val score = selected[offset + 4 + labelIndex]
                                if (score > bestScore) {
                                    bestScore = score
                                    bestLabel = labelIndex
                                }
                            }
                            if (bestScore < VEHICLE_SEGMENTATION_SCORE_THRESHOLD) {
                                continue
                            }
                            val x1 = ((centerX - width / 2f) / segmented.scale).coerceIn(0f, bitmap.width.toFloat())
                            val y1 = ((centerY - height / 2f) / segmented.scale).coerceIn(0f, bitmap.height.toFloat())
                            val x2 = ((centerX + width / 2f) / segmented.scale).coerceIn(0f, bitmap.width.toFloat())
                            val y2 = ((centerY + height / 2f) / segmented.scale).coerceIn(0f, bitmap.height.toFloat())
                            if ((x2 - x1) >= 8f && (y2 - y1) >= 8f) {
                                objects += SegmentedObject(
                                    label = bestLabel,
                                    detection = Detection(x1, y1, x2, y2, bestScore)
                                )
                            }
                        }
                        return objects
                    }
                }
            }
        }
    }

    private fun inferVehicleSegmentationRowSize(flatSize: Int): Int =
        when {
            flatSize % VEHICLE_SEGMENTATION_ROW_SIZE == 0 -> VEHICLE_SEGMENTATION_ROW_SIZE
            flatSize % (VEHICLE_SEGMENTATION_CLASSES + 4) == 0 -> VEHICLE_SEGMENTATION_CLASSES + 4
            else -> VEHICLE_SEGMENTATION_ROW_SIZE
        }

    private fun detect(bitmap: Bitmap, session: OrtSession): List<Detection> {
        val inputName = session.inputNames.first()
        val letterboxed = letterbox(bitmap, DETECTOR_SIZE)
        val inputBuffer = bitmapToFloatBuffer(letterboxed.bitmap)
        OnnxTensor.createTensor(
            ortEnvironment,
            inputBuffer,
            longArrayOf(1, 3, DETECTOR_SIZE.toLong(), DETECTOR_SIZE.toLong())
        ).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { results ->
                val raw = (results[0].value as? Array<FloatArray>) ?: return emptyList()
                return raw.mapNotNull { row ->
                    if (row.size < 7) return@mapNotNull null
                    val score = row[6]
                    if (score < DETECTION_THRESHOLD) return@mapNotNull null
                    val x1 = ((row[1] - letterboxed.padX) / letterboxed.scale).coerceIn(0f, bitmap.width.toFloat())
                    val y1 = ((row[2] - letterboxed.padY) / letterboxed.scale).coerceIn(0f, bitmap.height.toFloat())
                    val x2 = ((row[3] - letterboxed.padX) / letterboxed.scale).coerceIn(0f, bitmap.width.toFloat())
                    val y2 = ((row[4] - letterboxed.padY) / letterboxed.scale).coerceIn(0f, bitmap.height.toFloat())
                    if ((x2 - x1) < 8f || (y2 - y1) < 8f) {
                        null
                    } else {
                        Detection(x1, y1, x2, y2, score)
                    }
                }.sortedByDescending { it.score }
            }
        }
    }

    private fun detectComplaint(bitmap: Bitmap, session: OrtSession?): String? {
        session ?: return null
        val inputName = session.inputNames.first()
        val letterboxed = letterbox(bitmap, DETECTOR_SIZE)
        val inputBuffer = bitmapToFloatBuffer(letterboxed.bitmap)
        OnnxTensor.createTensor(
            ortEnvironment,
            inputBuffer,
            longArrayOf(1, 3, DETECTOR_SIZE.toLong(), DETECTOR_SIZE.toLong())
        ).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { results ->
                val raw = (results[0].value as? Array<FloatArray>) ?: return null
                val best = raw.mapNotNull { row ->
                    if (row.size < 7) return@mapNotNull null
                    val label = row[5].roundToInt()
                    val score = row[6]
                    if (
                        score < COMPLAINT_DETECTION_THRESHOLD ||
                        (label != COMPLAINT_CLASS_BLOCKED_BIKE_LANE && label != COMPLAINT_CLASS_BLOCKED_CROSSWALK)
                    ) {
                        null
                    } else {
                        label to score
                    }
                }.maxByOrNull { it.second } ?: return null
                return when (best.first) {
                    COMPLAINT_CLASS_BLOCKED_BIKE_LANE -> COMPLAINT_BLOCKED_BIKE_LANE
                    COMPLAINT_CLASS_BLOCKED_CROSSWALK -> COMPLAINT_BLOCKED_CROSSWALK
                    else -> null
                }
            }
        }
    }

    private fun detectBatched(bitmaps: List<Bitmap>, session: OrtSession): List<List<Detection>> {
        if (bitmaps.isEmpty()) return emptyList()
        if (bitmaps.size == 1) return listOf(detect(bitmaps.first(), session))
        return runCatching {
            val inputName = session.inputNames.first()
            val letterboxed = bitmaps.map { letterbox(it, DETECTOR_SIZE) }
            val inputBuffer = bitmapsToFloatBuffer(letterboxed.map { it.bitmap })
            OnnxTensor.createTensor(
                ortEnvironment,
                inputBuffer,
                longArrayOf(bitmaps.size.toLong(), 3, DETECTOR_SIZE.toLong(), DETECTOR_SIZE.toLong())
            ).use { inputTensor ->
                session.run(mapOf(inputName to inputTensor)).use { results ->
                    val raw = (results[0].value as? Array<FloatArray>) ?: return@use emptyList<List<Detection>>()
                    val grouped = MutableList(bitmaps.size) { mutableListOf<Detection>() }
                    raw.forEach { row ->
                        if (row.size < 7) return@forEach
                        val batchIndex = row[0].roundToInt()
                        if (batchIndex !in bitmaps.indices) return@forEach
                        val score = row[6]
                        if (score < DETECTION_THRESHOLD) return@forEach
                        val bitmap = bitmaps[batchIndex]
                        val item = letterboxed[batchIndex]
                        val x1 = ((row[1] - item.padX) / item.scale).coerceIn(0f, bitmap.width.toFloat())
                        val y1 = ((row[2] - item.padY) / item.scale).coerceIn(0f, bitmap.height.toFloat())
                        val x2 = ((row[3] - item.padX) / item.scale).coerceIn(0f, bitmap.width.toFloat())
                        val y2 = ((row[4] - item.padY) / item.scale).coerceIn(0f, bitmap.height.toFloat())
                        if ((x2 - x1) >= 8f && (y2 - y1) >= 8f) {
                            grouped[batchIndex] += Detection(x1, y1, x2, y2, score)
                        }
                    }
                    grouped.map { detections -> detections.sortedByDescending { it.score } }
                }
            }
        }.getOrElse {
            bitmaps.map { detect(it, session) }
        }
    }

    private fun runOcr(bitmap: Bitmap, session: OrtSession): String {
        val inputName = session.inputNames.first()
        val processed = prepareOcrBitmap(bitmap)
        val byteBuffer = ByteBuffer.allocateDirect(OCR_WIDTH * OCR_HEIGHT)
        putOcrBitmapBytes(processed, byteBuffer)
        byteBuffer.rewind()
        OnnxTensor.createTensor(
            ortEnvironment,
            byteBuffer,
            longArrayOf(1, OCR_HEIGHT.toLong(), OCR_WIDTH.toLong(), 1),
            OnnxJavaType.UINT8
        ).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { results ->
                val raw = (results[0].value as? Array<FloatArray>)?.firstOrNull() ?: return ""
                return decodeOcrRow(raw)
            }
        }
    }

    private fun runOcrBatched(bitmaps: List<Bitmap>, session: OrtSession): List<String> {
        if (bitmaps.isEmpty()) return emptyList()
        if (bitmaps.size == 1) return listOf(runOcr(bitmaps.first(), session))
        return runCatching {
            val inputName = session.inputNames.first()
            val processed = bitmaps.map(::prepareOcrBitmap)
            val byteBuffer = ByteBuffer.allocateDirect(bitmaps.size * OCR_WIDTH * OCR_HEIGHT)
            processed.forEach { putOcrBitmapBytes(it, byteBuffer) }
            byteBuffer.rewind()
            OnnxTensor.createTensor(
                ortEnvironment,
                byteBuffer,
                longArrayOf(bitmaps.size.toLong(), OCR_HEIGHT.toLong(), OCR_WIDTH.toLong(), 1),
                OnnxJavaType.UINT8
            ).use { inputTensor ->
                session.run(mapOf(inputName to inputTensor)).use { results ->
                    val raw = (results[0].value as? Array<FloatArray>) ?: return@use emptyList()
                    if (raw.size < bitmaps.size) return@use emptyList()
                    raw.take(bitmaps.size).map(::decodeOcrRow)
                }
            }.takeIf { it.size == bitmaps.size } ?: bitmaps.map { runOcr(it, session) }
        }.getOrElse {
            bitmaps.map { runOcr(it, session) }
        }
    }

    private fun putOcrBitmapBytes(bitmap: Bitmap, byteBuffer: ByteBuffer) {
        for (y in 0 until OCR_HEIGHT) {
            for (x in 0 until OCR_WIDTH) {
                val pixel = bitmap.getPixel(x, y)
                byteBuffer.put((pixel and 0xFF).toByte())
            }
        }
    }

    private fun decodeOcrRow(raw: FloatArray): String {
        val slotCount = raw.size / OCR_ALPHABET.length
        if (slotCount <= 0) return ""
        val chars = StringBuilder(slotCount)
        for (slotIndex in 0 until slotCount) {
            val offset = slotIndex * OCR_ALPHABET.length
            var bestIndex = 0
            var bestValue = Float.NEGATIVE_INFINITY
            for (alphabetIndex in 0 until OCR_ALPHABET.length) {
                val score = raw[offset + alphabetIndex]
                if (score > bestValue) {
                    bestValue = score
                    bestIndex = alphabetIndex
                }
            }
            chars.append(OCR_ALPHABET[bestIndex])
        }
        return chars.toString()
    }

    private fun classifyPlateState(bitmap: Bitmap, session: OrtSession): PlateStateClassification? {
        val inputName = session.inputNames.first()
        val resized = Bitmap.createScaledBitmap(bitmap, PLATE_STATE_SIZE, PLATE_STATE_SIZE, true)
        val inputBuffer = bitmapToFloatBuffer(resized)
        OnnxTensor.createTensor(
            ortEnvironment,
            inputBuffer,
            longArrayOf(1, 3, PLATE_STATE_SIZE.toLong(), PLATE_STATE_SIZE.toLong())
        ).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { results ->
                val raw = flattenFloats(results[0].value)
                val probabilities = when {
                    raw.size >= PLATE_STATE_LABELS.size + 4 -> raw.copyOfRange(4, 4 + PLATE_STATE_LABELS.size)
                    raw.size >= PLATE_STATE_LABELS.size -> raw.copyOfRange(0, PLATE_STATE_LABELS.size)
                    else -> return null
                }
                val bestIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
                val label = PLATE_STATE_LABELS[bestIndex]
                val confidence = probabilities[bestIndex].coerceIn(0f, 1f)
                return PlateStateClassification(
                    state = label.takeUnless { it == "00" }?.substringBefore("_"),
                    confidence = confidence,
                    label = label
                )
            }
        }
    }

    private fun classifyPlateStatesBatched(
        bitmaps: List<Bitmap>,
        session: OrtSession
    ): List<PlateStateClassification?> {
        if (bitmaps.isEmpty()) return emptyList()
        if (bitmaps.size == 1) return listOf(classifyPlateState(bitmaps.first(), session))
        return runCatching {
            val inputName = session.inputNames.first()
            val resized = bitmaps.map { Bitmap.createScaledBitmap(it, PLATE_STATE_SIZE, PLATE_STATE_SIZE, true) }
            val inputBuffer = bitmapsToFloatBuffer(resized)
            OnnxTensor.createTensor(
                ortEnvironment,
                inputBuffer,
                longArrayOf(bitmaps.size.toLong(), 3, PLATE_STATE_SIZE.toLong(), PLATE_STATE_SIZE.toLong())
            ).use { inputTensor ->
                session.run(mapOf(inputName to inputTensor)).use { results ->
                    val raw = flattenFloats(results[0].value)
                    val rowSize = raw.size / bitmaps.size
                    if (rowSize < PLATE_STATE_LABELS.size) return@use emptyList()
                    (bitmaps.indices).map { index ->
                        val start = index * rowSize
                        val end = min(raw.size, start + rowSize)
                        decodePlateStateClassification(raw.copyOfRange(start, end))
                    }
                }
            }.takeIf { it.size == bitmaps.size } ?: bitmaps.map { classifyPlateState(it, session) }
        }.getOrElse {
            bitmaps.map { classifyPlateState(it, session) }
        }
    }

    private fun decodePlateStateClassification(raw: FloatArray): PlateStateClassification? {
        val probabilities = when {
            raw.size >= PLATE_STATE_LABELS.size + 4 -> raw.copyOfRange(4, 4 + PLATE_STATE_LABELS.size)
            raw.size >= PLATE_STATE_LABELS.size -> raw.copyOfRange(0, PLATE_STATE_LABELS.size)
            else -> return null
        }
        val bestIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
        val label = PLATE_STATE_LABELS[bestIndex]
        val confidence = probabilities[bestIndex].coerceIn(0f, 1f)
        return PlateStateClassification(
            state = label.takeUnless { it == "00" }?.substringBefore("_"),
            confidence = confidence,
            label = label
        )
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? =
        when (uri.scheme) {
            "file" -> uri.path?.let(::File)?.takeIf { it.exists() }?.inputStream()?.use(BitmapFactory::decodeStream)
            else -> context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }

    private fun decodeMediaPreviewBitmap(context: Context, media: SubmissionMedia): Bitmap? {
        val uri = Uri.parse(media.uri)
        if (!media.isVideo) {
            return decodeBitmap(context, uri)
        }
        val retriever = MediaMetadataRetriever()
        return try {
            setRetrieverDataSource(context, retriever, uri)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (_: Throwable) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun crop(bitmap: Bitmap, detection: Detection): Bitmap? {
        val left = max(0, detection.x1.toInt())
        val top = max(0, detection.y1.toInt())
        val right = min(bitmap.width, detection.x2.toInt())
        val bottom = min(bitmap.height, detection.y2.toInt())
        val width = right - left
        val height = bottom - top
        if (width <= 2 || height <= 2) return null
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun segmentPlateCrop(
        source: Bitmap,
        expandedDetection: Detection,
        expandedCrop: Bitmap,
        session: OrtSession?
    ): PlateCrop? {
        session ?: return null
        return runCatching {
            val inputName = session.inputNames.first()
            val resized = Bitmap.createScaledBitmap(
                expandedCrop,
                PLATE_SEGMENTATION_SIZE,
                PLATE_SEGMENTATION_SIZE,
                true
            )
            val inputBuffer = bitmapToFloatBuffer(resized)
            OnnxTensor.createTensor(
                ortEnvironment,
                inputBuffer,
                longArrayOf(1, 3, PLATE_SEGMENTATION_SIZE.toLong(), PLATE_SEGMENTATION_SIZE.toLong())
            ).use { inputTensor ->
                session.run(mapOf(inputName to inputTensor)).use { results ->
                    val raw = flattenFloats(results[0].value)
                    val maskSide = sqrt(raw.size.toFloat()).roundToInt()
                    if (maskSide <= 1 || maskSide * maskSide > raw.size) {
                        return@runCatching null
                    }
                    val cropPoints = maskToOrientedBox(raw, maskSide, expandedCrop.width, expandedCrop.height)
                        ?: return@runCatching null
                    val sourcePoints = cropPoints.toMutableList()
                    for (index in 0 until sourcePoints.size step 2) {
                        sourcePoints[index] = (sourcePoints[index] + expandedDetection.x1).coerceIn(0f, source.width.toFloat())
                        sourcePoints[index + 1] = (sourcePoints[index + 1] + expandedDetection.y1).coerceIn(0f, source.height.toFloat())
                    }
                    val detection = sourcePoints.toBoundingDetection(expandedDetection.score, expandedDetection)
                    PlateCrop(
                        bitmap = crop(source, detection) ?: expandedCrop,
                        thumbnailBitmap = crop(source, detection) ?: expandedCrop,
                        detection = detection,
                        rotationDegrees = estimateAngleFromPoints(sourcePoints),
                        cornerPoints = sourcePoints
                    )
                }
            }
        }.getOrNull()
    }

    private fun maskToOrientedBox(
        raw: FloatArray,
        side: Int,
        cropWidth: Int,
        cropHeight: Int
    ): List<Float>? {
        val points = ArrayList<Pair<Float, Float>>()
        val logitOutput = raw.any { it < 0f || it > 1f }
        for (y in 0 until side) {
            for (x in 0 until side) {
                val value = raw[y * side + x]
                val probability = if (logitOutput) {
                    (1f / (1f + exp(-value)))
                } else {
                    value
                }
                if (probability >= SEGMENTATION_MASK_THRESHOLD) {
                    points += Pair(
                        (x + 0.5f) / side * cropWidth,
                        (y + 0.5f) / side * cropHeight
                    )
                }
            }
        }
        if (points.size < 8) return null

        val meanX = points.sumOf { it.first.toDouble() }.toFloat() / points.size
        val meanY = points.sumOf { it.second.toDouble() }.toFloat() / points.size
        var xx = 0f
        var yy = 0f
        var xy = 0f
        points.forEach { (x, y) ->
            val dx = x - meanX
            val dy = y - meanY
            xx += dx * dx
            yy += dy * dy
            xy += dx * dy
        }
        val axisAngle = 0.5f * atan2(2f * xy, xx - yy)
        val axisX = cos(axisAngle)
        val axisY = sin(axisAngle)
        val crossX = -axisY
        val crossY = axisX
        var minAxis = Float.POSITIVE_INFINITY
        var maxAxis = Float.NEGATIVE_INFINITY
        var minCross = Float.POSITIVE_INFINITY
        var maxCross = Float.NEGATIVE_INFINITY
        points.forEach { (x, y) ->
            val dx = x - meanX
            val dy = y - meanY
            val axis = dx * axisX + dy * axisY
            val cross = dx * crossX + dy * crossY
            minAxis = min(minAxis, axis)
            maxAxis = max(maxAxis, axis)
            minCross = min(minCross, cross)
            maxCross = max(maxCross, cross)
        }
        if ((maxAxis - minAxis) < 4f || (maxCross - minCross) < 4f) return null

        fun corner(axis: Float, cross: Float): FloatArray = floatArrayOf(
            (meanX + axis * axisX + cross * crossX).coerceIn(0f, cropWidth.toFloat()),
            (meanY + axis * axisY + cross * crossY).coerceIn(0f, cropHeight.toFloat())
        )
        val topLeft = corner(minAxis, minCross)
        val topRight = corner(maxAxis, minCross)
        val bottomRight = corner(maxAxis, maxCross)
        val bottomLeft = corner(minAxis, maxCross)
        return listOf(
            topLeft[0], topLeft[1],
            topRight[0], topRight[1],
            bottomRight[0], bottomRight[1],
            bottomLeft[0], bottomLeft[1]
        )
    }

    private fun refinedPlateCrop(bitmap: Bitmap, detection: Detection, sessions: AlprSessions): PlateCrop? {
        val initialPlateCrop = crop(bitmap, detection) ?: return null
        val expandedDetection = expandedPlateDetection(bitmap, detection)
        val expandedCrop = crop(bitmap, expandedDetection) ?: return PlateCrop(
            bitmap = initialPlateCrop,
            thumbnailBitmap = initialPlateCrop,
            detection = detection,
            rotationDegrees = estimateDeskewAngle(initialPlateCrop),
            cornerPoints = detection.cornerPoints()
        )
        val angle = estimateDeskewAngle(initialPlateCrop).let { initialAngle ->
            if (abs(initialAngle) >= DESKEW_TRIGGER_DEGREES) {
                initialAngle
            } else {
                estimateDeskewAngle(expandedCrop)
            }
        }
        if (abs(angle) < DESKEW_TRIGGER_DEGREES) {
            return PlateCrop(
                bitmap = initialPlateCrop,
                thumbnailBitmap = initialPlateCrop,
                detection = detection,
                rotationDegrees = 0f,
                cornerPoints = detection.cornerPoints()
            )
        }

        val rotatedMatch = findBestRotatedPlateMatch(
            bitmap = bitmap,
            detection = detection,
            estimatedAngle = angle,
            detector = sessions.detector
        ) ?: return PlateCrop(
            bitmap = initialPlateCrop,
            thumbnailBitmap = initialPlateCrop,
            detection = detection,
            rotationDegrees = 0f,
            cornerPoints = detection.cornerPoints()
        )

        val plateBitmap = crop(rotatedMatch.rotatedImage.bitmap, rotatedMatch.detection) ?: return null
        val imageCornerPoints = rotatedMatch.detection.toOriginalImageCornerPoints(
            rotatedImage = rotatedMatch.rotatedImage,
            originalDetection = detection
        )
        val displayDetection = detection
        val displayThumbnail = crop(bitmap, displayDetection) ?: initialPlateCrop
        return PlateCrop(
            bitmap = plateBitmap,
            thumbnailBitmap = displayThumbnail,
            detection = displayDetection.copy(score = rotatedMatch.detection.score),
            rotationDegrees = -rotatedMatch.appliedRotationDegrees,
            cornerPoints = imageCornerPoints
        )
    }

    private fun findBestRotatedPlateMatch(
        bitmap: Bitmap,
        detection: Detection,
        estimatedAngle: Float,
        detector: OrtSession
    ): RotatedPlateMatch? {
        val rotations = listOf(-estimatedAngle, estimatedAngle).distinctBy { it.roundToInt() }
        return rotations.mapNotNull { rotationDegrees ->
            val rotatedImage = rotateBitmapWithTransform(bitmap, rotationDegrees)
            val expectedDetection = detection.mapWith(rotatedImage.sourceToRotated)
            val rotatedDetection = detect(rotatedImage.bitmap, detector)
                .bestMatchFor(expectedDetection)
                ?: return@mapNotNull null
            RotatedPlateMatch(
                rotatedImage = rotatedImage,
                detection = rotatedDetection,
                appliedRotationDegrees = rotationDegrees,
                matchDistanceSquared = rotatedDetection.centerDistanceSquaredFrom(expectedDetection)
            )
        }.maxWithOrNull(
            compareBy<RotatedPlateMatch> { it.detection.score }
                .thenByDescending { -it.matchDistanceSquared }
        )
    }

    private fun List<Detection>.bestMatchFor(expected: Detection): Detection? {
        if (isEmpty()) return null
        val expectedCenterX = (expected.x1 + expected.x2) / 2f
        val expectedCenterY = (expected.y1 + expected.y2) / 2f
        val expectedWidth = expected.x2 - expected.x1
        val expectedHeight = expected.y2 - expected.y1
        val maxDistance = max(expectedWidth, expectedHeight) * ROTATED_MATCH_DISTANCE_MULTIPLIER
        return minByOrNull { detection ->
            val centerX = (detection.x1 + detection.x2) / 2f
            val centerY = (detection.y1 + detection.y2) / 2f
            val dx = centerX - expectedCenterX
            val dy = centerY - expectedCenterY
            dx * dx + dy * dy
        }?.takeIf { detection ->
            val centerX = (detection.x1 + detection.x2) / 2f
            val centerY = (detection.y1 + detection.y2) / 2f
            val dx = centerX - expectedCenterX
            val dy = centerY - expectedCenterY
            dx * dx + dy * dy <= maxDistance * maxDistance
        }
    }

    private fun Detection.centerDistanceSquaredFrom(expected: Detection): Float {
        val centerX = (x1 + x2) / 2f
        val centerY = (y1 + y2) / 2f
        val expectedCenterX = (expected.x1 + expected.x2) / 2f
        val expectedCenterY = (expected.y1 + expected.y2) / 2f
        val dx = centerX - expectedCenterX
        val dy = centerY - expectedCenterY
        return dx * dx + dy * dy
    }

    private fun expandedPlateDetection(bitmap: Bitmap, detection: Detection): Detection {
        val width = detection.x2 - detection.x1
        val height = detection.y2 - detection.y1
        return Detection(
            x1 = (detection.x1 - width * PLATE_AXIS_PADDING).coerceIn(0f, bitmap.width.toFloat()),
            y1 = (detection.y1 - height * PLATE_CROSS_AXIS_PADDING).coerceIn(0f, bitmap.height.toFloat()),
            x2 = (detection.x2 + width * PLATE_AXIS_PADDING).coerceIn(0f, bitmap.width.toFloat()),
            y2 = (detection.y2 + height * PLATE_CROSS_AXIS_PADDING).coerceIn(0f, bitmap.height.toFloat()),
            score = detection.score
        )
    }

    private fun Detection.toOriginalImageCornerPoints(
        rotatedImage: RotatedBitmap,
        originalDetection: Detection
    ): List<Float> {
        val points = floatArrayOf(
            x1, y1,
            x2, y1,
            x2, y2,
            x1, y2
        )
        val inverse = Matrix()
        if (!rotatedImage.sourceToRotated.invert(inverse)) {
            return originalDetection.cornerPoints()
        }
        inverse.mapPoints(points)
        return points.toList()
    }

    private fun Detection.mapWith(matrix: Matrix): Detection {
        val points = floatArrayOf(
            x1, y1,
            x2, y1,
            x2, y2,
            x1, y2
        )
        matrix.mapPoints(points)
        return points.toList().toBoundingDetection(score, this)
    }

    private fun Detection.cornerPoints(): List<Float> = listOf(
        x1, y1,
        x2, y1,
        x2, y2,
        x1, y2
    )

    private fun estimateAngleFromPoints(points: List<Float>): Float {
        if (points.size < 4) return 0f
        val dx = points[2] - points[0]
        val dy = points[3] - points[1]
        return (atan2(dy, dx) * 180f / PI.toFloat()).let { degrees ->
            var normalized = degrees
            while (normalized > 45f) normalized -= 90f
            while (normalized < -45f) normalized += 90f
            normalized.coerceIn(-MAX_DESKEW_DEGREES.toFloat(), MAX_DESKEW_DEGREES.toFloat())
        }
    }

    private fun List<Float>.toBoundingDetection(score: Float, fallback: Detection): Detection {
        if (size < 8) return fallback
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (index in 0 until 8 step 2) {
            left = min(left, this[index])
            top = min(top, this[index + 1])
            right = max(right, this[index])
            bottom = max(bottom, this[index + 1])
        }
        return if ((right - left) >= 8f && (bottom - top) >= 8f) {
            Detection(left, top, right, bottom, score)
        } else {
            fallback
        }
    }

    private fun savePlateThumbnail(context: Context, plate: String, bitmap: Bitmap): String? =
        runCatching {
            val directory = File(context.cacheDir, "alpr-thumbnails").apply { mkdirs() }
            val safePlate = plate.ifBlank { "plate" }.filter { it.isLetterOrDigit() }.take(12)
            val file = File(directory, "${safePlate}-${System.nanoTime()}.png")
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            Uri.fromFile(file).toString()
        }.getOrNull()

    private fun letterbox(source: Bitmap, targetSize: Int): LetterboxedBitmap {
        val scale = min(targetSize / source.width.toFloat(), targetSize / source.height.toFloat())
        val scaledWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawRGB(LETTERBOX_COLOR, LETTERBOX_COLOR, LETTERBOX_COLOR)
        val padX = (targetSize - scaledWidth) / 2f
        val padY = (targetSize - scaledHeight) / 2f
        canvas.drawBitmap(resized, padX, padY, null)
        return LetterboxedBitmap(output, scale, padX, padY)
    }

    private fun vehicleSegmentationBitmap(source: Bitmap): VehicleSegmentationBitmap {
        val scale = VEHICLE_SEGMENTATION_SIZE / max(source.width, source.height).toFloat()
        val scaledWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        val output = Bitmap.createBitmap(
            VEHICLE_SEGMENTATION_SIZE,
            VEHICLE_SEGMENTATION_SIZE,
            Bitmap.Config.ARGB_8888
        )
        Canvas(output).apply {
            drawColor(Color.BLACK)
            drawBitmap(resized, 0f, 0f, null)
        }
        return VehicleSegmentationBitmap(output, scale)
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val floats = bitmapsToFloatBuffer(listOf(bitmap))
        floats.rewind()
        return floats
    }

    private fun bitmapsToFloatBuffer(bitmaps: List<Bitmap>): FloatBuffer {
        val batchSize = bitmaps.size.coerceAtLeast(1)
        val first = bitmaps.first()
        val width = first.width
        val height = first.height
        val channels = 3
        val floats = ByteBuffer.allocateDirect(4 * batchSize * width * height * channels)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        bitmaps.forEach { bitmap ->
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            for (channel in 0 until channels) {
                for (pixel in pixels) {
                    val value = when (channel) {
                        0 -> (pixel shr 16) and 0xFF
                        1 -> (pixel shr 8) and 0xFF
                        else -> pixel and 0xFF
                    }
                    floats.put(value / 255f)
                }
            }
        }
        floats.rewind()
        return floats
    }

    private fun prepareOcrBitmap(source: Bitmap): Bitmap {
        val resized = Bitmap.createScaledBitmap(source, OCR_WIDTH, OCR_HEIGHT, true)
        val grayscale = Bitmap.createBitmap(OCR_WIDTH, OCR_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(resized, 0f, 0f, paint)
        return grayscale
    }

    private fun estimateDeskewAngle(source: Bitmap): Float {
        val analysisWidth = 220
        val scale = analysisWidth / source.width.toFloat()
        val analysisHeight = max(48, (source.height * scale).roundToInt())
        val resized = Bitmap.createScaledBitmap(source, analysisWidth, analysisHeight, true)
        val grayscale = IntArray(analysisWidth * analysisHeight)
        val pixels = IntArray(analysisWidth * analysisHeight)
        resized.getPixels(pixels, 0, analysisWidth, 0, 0, analysisWidth, analysisHeight)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            grayscale[index] = (r * 30 + g * 59 + b * 11) / 100
        }

        val allEdgePoints = mutableListOf<Pair<Int, Int>>()
        val horizontalEdgePoints = mutableListOf<Pair<Int, Int>>()
        val orientationVotes = HashMap<Int, Int>()
        for (y in 1 until analysisHeight - 1) {
            for (x in 1 until analysisWidth - 1) {
                val idx = y * analysisWidth + x
                val gx = grayscale[idx + 1] - grayscale[idx - 1]
                val gy = grayscale[idx + analysisWidth] - grayscale[idx - analysisWidth]
                val magnitude = abs(gx) + abs(gy)
                if (magnitude >= EDGE_THRESHOLD) {
                    val point = x to y
                    allEdgePoints += point
                    if (abs(gy) > abs(gx) * 0.65f) {
                        horizontalEdgePoints += point
                        val edgeAngle = normalizePlateAngle(
                            Math.toDegrees(atan2((-gx).toDouble(), gy.toDouble())).toFloat()
                        )
                        val angleBin = edgeAngle.roundToInt().coerceIn(-MAX_DESKEW_DEGREES, MAX_DESKEW_DEGREES)
                        orientationVotes[angleBin] = (orientationVotes[angleBin] ?: 0) + magnitude
                    }
                }
            }
        }
        orientationVotes.maxByOrNull { it.value }?.let { (angle, votes) ->
            if (votes >= EDGE_THRESHOLD * 18) {
                return angle.toFloat()
            }
        }
        val edgePoints = horizontalEdgePoints.takeIf { it.size >= 24 } ?: allEdgePoints
        if (edgePoints.size < 24) return 0f

        var bestAngle = 0
        var bestVotes = 0
        for (angle in -MAX_DESKEW_DEGREES..MAX_DESKEW_DEGREES) {
            val normalRadians = ((angle + 90).toDouble() * PI) / 180.0
            val cosTheta = cos(normalRadians)
            val sinTheta = sin(normalRadians)
            val rhoVotes = HashMap<Int, Int>()
            for ((x, y) in edgePoints) {
                val rho = (x * cosTheta + y * sinTheta).roundToInt()
                val votes = (rhoVotes[rho] ?: 0) + 1
                rhoVotes[rho] = votes
                if (votes > bestVotes) {
                    bestVotes = votes
                    bestAngle = angle
                }
            }
        }
        return bestAngle.toFloat()
    }

    private fun normalizePlateAngle(degrees: Float): Float {
        var normalized = degrees
        while (normalized > 45f) normalized -= 90f
        while (normalized < -45f) normalized += 90f
        return normalized.coerceIn(-MAX_DESKEW_DEGREES.toFloat(), MAX_DESKEW_DEGREES.toFloat())
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun rotateBitmapWithTransform(source: Bitmap, degrees: Float): RotatedBitmap {
        if (degrees == 0f) return RotatedBitmap(source, Matrix())
        val matrix = Matrix().apply { postRotate(degrees) }
        val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        matrix.mapRect(bounds)
        matrix.postTranslate(-bounds.left, -bounds.top)
        val output = Bitmap.createBitmap(
            bounds.width().roundToInt().coerceAtLeast(1),
            bounds.height().roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val paint = Paint().apply { isFilterBitmap = true }
        Canvas(output).drawBitmap(source, matrix, paint)
        return RotatedBitmap(output, matrix)
    }

    private fun Float.normalizedVideoRotationDegrees(): Float {
        val rounded = (this / 90f).roundToInt() * 90
        val normalized = ((rounded % 360) + 360) % 360
        return when (normalized) {
            90 -> 90f
            180 -> 180f
            270 -> -90f
            else -> 0f
        }
    }

    private fun List<Float>.toNormalizedCornerPoints(width: Int, height: Int): List<Float> {
        if (size < 8 || width <= 0 || height <= 0) return emptyList()
        val normalized = ArrayList<Float>(8)
        for (index in 0 until 8 step 2) {
            normalized += (this[index] / width).coerceIn(0f, 1f)
            normalized += (this[index + 1] / height).coerceIn(0f, 1f)
        }
        return normalized
    }

    private fun normalizePlateText(raw: String): String {
        val cleaned = raw.replace("_", "").filter { it.isLetterOrDigit() }.uppercase()
        if (cleaned.length == 7 && cleaned.startsWith('T') && cleaned.endsWith('C')) {
            return cleaned
                .replace('I', '1')
                .replace('L', '1')
                .replace('Z', '2')
                .replace('G', '6')
                .replace('B', '8')
                .replace('A', '4')
                .replace('O', '0')
        }
        return cleaned
    }

    private fun flattenFloats(value: Any?): FloatArray =
        when (value) {
            is FloatArray -> value
            is Array<*> -> {
                val values = ArrayList<Float>()
                fun append(item: Any?) {
                    when (item) {
                        is Float -> values += item
                        is FloatArray -> values.addAll(item.toList())
                        is Array<*> -> item.forEach(::append)
                    }
                }
                value.forEach(::append)
                values.toFloatArray()
            }
            else -> FloatArray(0)
        }
}

private val Detection.area: Float
    get() = max(0f, x2 - x1) * max(0f, y2 - y1)

private fun Detection.centerDistanceSquared(bitmap: Bitmap): Float {
    val centerX = (x1 + x2) / 2f
    val centerY = (y1 + y2) / 2f
    val dx = centerX - bitmap.width / 2f
    val dy = centerY - bitmap.height / 2f
    return dx * dx + dy * dy
}

private fun Detection.iouWith(other: Detection): Float {
    val left = max(x1, other.x1)
    val top = max(y1, other.y1)
    val right = min(x2, other.x2)
    val bottom = min(y2, other.y2)
    val intersection = max(0f, right - left) * max(0f, bottom - top)
    val union = area + other.area - intersection
    return if (union <= 0f) 0f else intersection / union
}

private fun Detection.isNear(other: Detection, bitmap: Bitmap): Boolean {
    if (iouWith(other) > 0.02f) return true
    val centerX = (x1 + x2) / 2f
    val centerY = (y1 + y2) / 2f
    val otherCenterX = (other.x1 + other.x2) / 2f
    val otherCenterY = (other.y1 + other.y2) / 2f
    val dx = (centerX - otherCenterX) / bitmap.width.coerceAtLeast(1)
    val dy = (centerY - otherCenterY) / bitmap.height.coerceAtLeast(1)
    return sqrt(dx * dx + dy * dy) < 0.32f
}

private fun PlateCandidate.centerBiasedScore(): Float {
    val centerX = focalPointX ?: 0.5f
    val centerY = focalPointY ?: 0.5f
    val distanceFromCenter = kotlin.math.sqrt(
        ((centerX - 0.5f) * (centerX - 0.5f) + (centerY - 0.5f) * (centerY - 0.5f)).toDouble()
    ).toFloat()
    val centerScore = (1f - (distanceFromCenter / 0.70710677f)).coerceIn(0f, 1f)
    return confidence * 0.72f + centerScore * 0.28f
}
