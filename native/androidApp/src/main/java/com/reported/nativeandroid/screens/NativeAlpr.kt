package com.reported.nativeandroid.screens

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
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
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.nativeandroid.media.AutoReportThresholds
import com.reported.shared.model.PlatePatternClassifier
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
import java.util.Collections
import java.util.EnumSet
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
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
private const val COMPLAINT_DETECTOR_SIZE = 512
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
private val COMPLAINT_DETECTION_THRESHOLD = AutoReportThresholds.COMPLAINT_CONFIDENCE
private const val MEDIA_SCANNER_LOG_TAG = "ReportedMediaScanner"
private const val PLATE_DETECTION_LOG_TAG = "ReportedPlateDetection"
private const val PLATE_STATE_SIZE = 160
private const val PLATE_SEGMENTATION_SIZE = 160
private const val OCR_WIDTH = 140
private const val OCR_HEIGHT = 70
private const val OCR_LEGACY_BATCH_SIZE = 8
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
private const val PLATE_LIKE_MIN_ASPECT_RATIO = 2.0f
private const val PLATE_LIKE_MAX_ASPECT_RATIO = 6.5f
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

private data class ComplaintRegion(
    val label: Int,
    val detection: Detection
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

internal data class LiveFrameDetectionResult(
    val candidates: List<PlateCandidate>,
    val complaintId: String?
)

internal data class ComplaintInferenceResult(
    val complaintId: String,
    val confidence: Float,
    val accepted: Boolean
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
    private val sessionLabels = Collections.synchronizedMap(WeakHashMap<OrtSession, String>())
    private val profiledSessions = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<OrtSession, Boolean>()))

    suspend fun detectLicensePlates(
        context: Context,
        media: SubmissionMedia,
        expectedComplaintHint: String? = null
    ): List<PlateCandidate> =
        withContext(Dispatchers.IO) {
            Log.d(
                PLATE_DETECTION_LOG_TAG,
                "New report ALPR requested: uri=${media.uri}, sourceUri=${media.sourceUri}, mime=${media.mimeType}, isVideo=${media.isVideo}, hint=$expectedComplaintHint"
            )
            if (media.isVideo) {
                Log.d(PLATE_DETECTION_LOG_TAG, "Skipping image ALPR because selected media is video")
                return@withContext emptyList()
            }

            val bitmap = decodeBitmap(context, Uri.parse(media.uri))
            if (bitmap == null) {
                Log.d(PLATE_DETECTION_LOG_TAG, "Image decode failed for uri=${media.uri}")
                return@withContext emptyList()
            }
            Log.d(PLATE_DETECTION_LOG_TAG, "Decoded bitmap for ALPR: ${bitmap.width}x${bitmap.height}")
            val sessions = getSessionsOrNull(context.applicationContext)
            if (sessions == null) {
                Log.d(PLATE_DETECTION_LOG_TAG, "ALPR session load failed or unavailable")
                return@withContext emptyList()
            }
            detectLicensePlatesInBitmap(context, bitmap, sessions, expectedComplaintHint).also { candidates ->
                Log.d(
                    PLATE_DETECTION_LOG_TAG,
                    "New report ALPR finished: candidates=${candidates.size} ${candidates.toCandidateLogSummary()}"
                )
            }
        }

    suspend fun inferComplaintId(context: Context, media: SubmissionMedia): String? =
        inferComplaint(context, media)?.takeIf { it.accepted }?.complaintId

    suspend fun inferComplaint(context: Context, media: SubmissionMedia): ComplaintInferenceResult? =
        withContext(Dispatchers.IO) {
            if (media.isVideo) return@withContext null
            val bitmap = decodeBitmap(context, Uri.parse(media.uri)) ?: return@withContext null
            val sessions = getSessionsOrNull(context.applicationContext) ?: return@withContext null
            detectComplaint(bitmap, sessions.complaint)
        }

    suspend fun detectLiveFrame(
        context: Context,
        bitmap: Bitmap,
        expectedComplaintHint: String? = null
    ): LiveFrameDetectionResult = withContext(Dispatchers.IO) {
        val sessions = getSessionsOrNull(context.applicationContext)
            ?: return@withContext LiveFrameDetectionResult(emptyList(), null)
        val candidates = detectLicensePlatesInBitmap(
            context = context,
            bitmap = bitmap,
            sessions = sessions,
            expectedComplaintHint = expectedComplaintHint,
            forceBackupTextOcr = false
        )
        val complaintId = if (candidates.isNotEmpty()) {
            detectComplaint(bitmap, sessions.complaint)?.takeIf { it.accepted }?.complaintId
        } else {
            null
        }
        LiveFrameDetectionResult(candidates, complaintId)
    }

    suspend fun detectLicensePlatesInVideo(
        context: Context,
        media: SubmissionMedia,
        startTimeMs: Long = 0L,
        expectedComplaintHint: String? = null,
        onProgress: suspend (VideoFrameScanProgress) -> Unit
    ): List<PlateCandidate> = withContext(Dispatchers.IO) {
        if (!media.isVideo) {
            return@withContext detectLicensePlates(context, media, expectedComplaintHint)
        }

        val sessions = getSessionsOrNull(context.applicationContext) ?: return@withContext emptyList()
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
            val startFrameIndex = if (startTimeMs > 0L && durationUs > 0L) {
                val requestedFrame = if (frameCount != null) {
                    ((startTimeMs * 1_000L).toDouble() / durationUs.toDouble() * totalFrames.toDouble()).roundToInt()
                } else {
                    ((startTimeMs * 1_000L).toDouble() / frameIntervalUs.toDouble()).roundToInt()
                }
                (requestedFrame / VIDEO_FRAME_STRIDE * VIDEO_FRAME_STRIDE).coerceIn(0, totalFrames - 1)
            } else {
                0
            }
            var index = startFrameIndex
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
                    frameCandidates = rankCandidatesForComplaint(
                        candidates = frameCandidates,
                        bitmap = sample.bitmap,
                        sessions = sessions,
                        expectedComplaintHint = expectedComplaintHint
                    )
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

    private fun detectLicensePlatesInBitmap(
        context: Context,
        bitmap: Bitmap,
        sessions: AlprSessions,
        expectedComplaintHint: String? = null,
        forceBackupTextOcr: Boolean = true
    ): List<PlateCandidate> {
        val detections = detect(bitmap, sessions.detector)
        Log.d(
            PLATE_DETECTION_LOG_TAG,
            "Detector returned ${detections.size} raw detection(s) for ${bitmap.width}x${bitmap.height}: ${detections.toDetectionLogSummary()}"
        )
        if (detections.isEmpty()) {
            Log.d(PLATE_DETECTION_LOG_TAG, "ALPR stopped: detector returned no boxes above threshold=$DETECTION_THRESHOLD")
            return emptyList()
        }

        val works = detections.take(MAX_CANDIDATES).mapNotNull { detection ->
            val plateCrop = refinedPlateCrop(bitmap, detection, sessions) ?: run {
                Log.d(PLATE_DETECTION_LOG_TAG, "Dropping detection because crop/refinement failed: ${detection.toLogString()}")
                return@mapNotNull null
            }
            Log.d(
                PLATE_DETECTION_LOG_TAG,
                "Prepared plate crop: source=${detection.toLogString()}, refined=${plateCrop.detection.toLogString()}, rotation=${plateCrop.rotationDegrees}, crop=${plateCrop.bitmap.width}x${plateCrop.bitmap.height}"
            )
            PlateCropWork(bitmap = bitmap, sourceDetection = detection, crop = plateCrop)
        }
        if (works.isEmpty()) {
            Log.d(PLATE_DETECTION_LOG_TAG, "ALPR stopped: all detector boxes failed crop/refinement")
            return emptyList()
        }
        val ocrTexts = runOcrBatched(works.map { it.crop.bitmap }, sessions.ocr)
        val backupOcrTexts = runBackupTextOcrBatched(
            bitmaps = works.map { it.crop.bitmap },
            primaryTexts = ocrTexts,
            force = forceBackupTextOcr
        )
        val plateStates = classifyPlateStatesBatched(works.map { it.crop.bitmap }, sessions.plateState)
        Log.d(
            PLATE_DETECTION_LOG_TAG,
            "OCR/state classifier results: " + works.indices.joinToString(prefix = "[", postfix = "]") { index ->
                val ocr = ocrTexts.getOrNull(index).orEmpty()
                val backupOcr = backupOcrTexts.getOrNull(index).orEmpty()
                val state = plateStates.getOrNull(index)
                "#$index rawOcr='$ocr' backupOcr='$backupOcr' selected='${selectPlateOcrText(ocr, backupOcr)}' state=${state?.label}/${state?.state} stateConfidence=${state?.confidence}"
            }
        )
        val candidates = works.mapIndexedNotNull { index, work ->
            buildPlateCandidate(
                context = context,
                bitmap = work.bitmap,
                detection = work.sourceDetection,
                plateCrop = work.crop,
                plateText = selectPlateOcrText(
                    primaryText = ocrTexts.getOrNull(index).orEmpty(),
                    backupText = backupOcrTexts.getOrNull(index).orEmpty()
                ),
                plateState = plateStates.getOrNull(index)
            )
        }.distinctBy { it.plate }.sortedByDescending { it.confidence }
        Log.d(PLATE_DETECTION_LOG_TAG, "Built ${candidates.size} candidate(s): ${candidates.toCandidateLogSummary()}")
        return rankCandidatesForComplaint(candidates, bitmap, sessions, expectedComplaintHint).also { ranked ->
            if (expectedComplaintHint != null && ranked != candidates) {
                Log.d(PLATE_DETECTION_LOG_TAG, "Complaint-ranked candidates for hint=$expectedComplaintHint: ${ranked.toCandidateLogSummary()}")
            }
        }
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
        val backupOcrTexts = runBackupTextOcrBatched(
            bitmaps = allWorks.map { it.crop.bitmap },
            primaryTexts = ocrTexts,
            force = false
        )
        val plateStates = classifyPlateStatesBatched(allWorks.map { it.crop.bitmap }, sessions.plateState)
        var ocrIndex = 0
        var backupOcrIndex = 0
        var classifierIndex = 0
        return worksByBitmap.map { works ->
            works.mapNotNull { work ->
                val primaryPlateText = ocrTexts.getOrNull(ocrIndex++).orEmpty()
                val backupPlateText = backupOcrTexts.getOrNull(backupOcrIndex++).orEmpty()
                val plateState = plateStates.getOrNull(classifierIndex++)
                buildPlateCandidate(
                    context = context,
                    bitmap = work.bitmap,
                    detection = work.sourceDetection,
                    plateCrop = work.crop,
                    plateText = selectPlateOcrText(primaryPlateText, backupPlateText),
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
        if (normalized.isBlank()) {
            Log.d(
                PLATE_DETECTION_LOG_TAG,
                "Rejecting detection after OCR normalization: rawOcr='$plateText', source=${detection.toLogString()}, refined=${plateCrop.detection.toLogString()}"
            )
            return null
        }
        val patternMatch = PlatePatternClassifier.classify(normalized)
        val correctedPlate = patternMatch?.normalizedPlate ?: normalized
        val wasPlateCorrected = correctedPlate != normalized
        val detectedState = patternMatch?.state ?: if (correctedPlate.startsWith('T') && correctedPlate.endsWith('C')) {
            "NY"
        } else {
            plateState?.state
        }
        val stateConfidence = patternMatch?.confidence ?: plateState?.confidence
        val refinedDetection = plateCrop.detection
        Log.d(
            PLATE_DETECTION_LOG_TAG,
            "Accepting candidate: rawOcr='$plateText', normalized=$normalized, corrected=$correctedPlate, detectorScore=${detection.score}, refinedScore=${refinedDetection.score}, state=${detectedState}, stateConfidence=$stateConfidence, pattern=${patternMatch?.type}, cropRotation=${plateCrop.rotationDegrees}"
        )
        return PlateCandidate(
            plate = correctedPlate,
            confidence = min(1f, max(detection.score, refinedDetection.score)),
            rawPlateText = normalized.takeIf { wasPlateCorrected },
            wasPlateCorrected = wasPlateCorrected,
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
            sourceImageWidth = bitmap.width,
            sourceImageHeight = bitmap.height,
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
            val candidates = detectLicensePlatesInBitmap(
                context = context,
                bitmap = rotated.bitmap,
                sessions = sessions,
                forceBackupTextOcr = false
            )
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
            cornerPoints = points.toList().toNormalizedCornerPoints(sourceWidth, sourceHeight),
            sourceImageWidth = sourceWidth,
            sourceImageHeight = sourceHeight
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
        videoFramePreviewDirectory(context)
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
        val directory = videoFramePreviewDirectory(context).apply { mkdirs() }
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

    private fun videoFramePreviewDirectory(context: Context): File =
        File(File(context.filesDir, "submission-media"), "alpr-video-frames")

    private suspend fun getSessions(context: Context): AlprSessions = sessionMutex.withLock {
        val detector = detectorSession ?: createOrtSessionWithNnapiFallback(
            context = context,
            asset = DETECTOR_ASSET,
            label = "plate detector"
        ).also { detectorSession = it }
        val ocr = ocrSession ?: createOrtSessionWithNnapiFallback(
            context = context,
            asset = OCR_ASSET,
            label = "plate OCR"
        ).also { ocrSession = it }
        val plateState = plateStateSession ?: createOrtSessionWithNnapiFallback(
            context = context,
            asset = PLATE_STATE_ASSET,
            label = "plate state classifier"
        ).also { plateStateSession = it }
        val complaint = complaintSession ?: runCatching {
            createOrtSessionWithNnapiFallback(
                context = context,
                asset = COMPLAINT_ASSET,
                label = "complaint detector"
            )
        }.getOrNull()?.also { complaintSession = it }
        val plateSegmentation = plateSegmentationSession ?: runCatching {
            createOrtSessionWithNnapiFallback(
                context = context,
                asset = PLATE_SEGMENTATION_ASSET,
                label = "plate segmentation"
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

    private fun createOrtSessionWithNnapiFallback(
        context: Context,
        asset: String,
        label: String
    ): OrtSession {
        val modelBytes = context.assets.open(asset).use { it.readBytes() }
        val nnapiOptions = OrtSession.SessionOptions()
        return try {
            configureDebugOrtProfiling(context, nnapiOptions, label, "nnapi")
            nnapiOptions.addNnapi(EnumSet.of(NNAPIFlags.CPU_DISABLED))
            ortEnvironment.createSession(modelBytes, nnapiOptions).also {
                sessionLabels[it] = "$label requested NNAPI"
                Log.i(PLATE_DETECTION_LOG_TAG, "Loaded $label with NNAPI requested: $asset")
            }
        } catch (error: OrtException) {
            Log.w(
                PLATE_DETECTION_LOG_TAG,
                "NNAPI hardware provider failed for $label; falling back to ORT CPU: $asset",
                error
            )
            val cpuOptions = OrtSession.SessionOptions().also {
                configureDebugOrtProfiling(context, it, label, "cpu")
            }
            ortEnvironment.createSession(modelBytes, cpuOptions).also {
                sessionLabels[it] = "$label ORT CPU fallback"
                Log.i(PLATE_DETECTION_LOG_TAG, "Loaded $label with ORT CPU provider: $asset")
            }
        } catch (error: RuntimeException) {
            Log.w(
                PLATE_DETECTION_LOG_TAG,
                "NNAPI hardware provider failed for $label; falling back to ORT CPU: $asset",
                error
            )
            val cpuOptions = OrtSession.SessionOptions().also {
                configureDebugOrtProfiling(context, it, label, "cpu")
            }
            ortEnvironment.createSession(modelBytes, cpuOptions).also {
                sessionLabels[it] = "$label ORT CPU fallback"
                Log.i(PLATE_DETECTION_LOG_TAG, "Loaded $label with ORT CPU provider: $asset")
            }
        }
    }

    private fun configureDebugOrtProfiling(
        context: Context,
        options: OrtSession.SessionOptions,
        label: String,
        providerHint: String
    ) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val safeLabel = label.replace(Regex("[^A-Za-z0-9_-]+"), "-")
            val prefix = File(context.cacheDir, "ort-profile-$safeLabel-$providerHint").absolutePath
            options.enableProfiling(prefix)
        }.onFailure { error ->
            Log.w(PLATE_DETECTION_LOG_TAG, "Unable to enable ORT profiling for $label", error)
        }
    }

    private fun OrtSession.logOrtProfileOnce() {
        if (!BuildConfig.DEBUG || !profiledSessions.add(this)) return
        val label = sessionLabels[this] ?: "unknown session"
        runCatching {
            val profilePath = endProfiling()
            val profileFile = File(profilePath)
            val providerCounts = if (profileFile.exists()) {
                Regex("\"provider\"\\s*:\\s*\"([^\"]+)\"")
                    .findAll(profileFile.readText())
                    .map { it.groupValues[1] }
                    .groupingBy { it }
                    .eachCount()
            } else {
                emptyMap()
            }
            Log.i(
                PLATE_DETECTION_LOG_TAG,
                "ORT profile for $label providers=$providerCounts path=$profilePath"
            )
        }.onFailure { error ->
            Log.w(PLATE_DETECTION_LOG_TAG, "Unable to read ORT profile for $label", error)
        }
    }

    private suspend fun getSessionsOrNull(context: Context): AlprSessions? =
        runCatching { getSessions(context) }.getOrElse { error ->
            Log.e(MEDIA_SCANNER_LOG_TAG, "ALPR models unavailable", error)
            null
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
                }.sortedByDescending { it.score }.also {
                    session.logOrtProfileOnce()
                }
            }
        }
    }

    private fun detectComplaint(bitmap: Bitmap, session: OrtSession?): ComplaintInferenceResult? {
        session ?: run {
            Log.d(MEDIA_SCANNER_LOG_TAG, "Complaint inference skipped: complaint model session is unavailable")
            return null
        }
        return runCatching {
            Log.d(MEDIA_SCANNER_LOG_TAG, "Complaint inference started")
            val inputName = session.inputNames.first()
            val letterboxed = letterbox(bitmap, COMPLAINT_DETECTOR_SIZE)
            val inputBuffer = bitmapToFloatBuffer(letterboxed.bitmap)
            OnnxTensor.createTensor(
                ortEnvironment,
                inputBuffer,
                longArrayOf(1, 3, COMPLAINT_DETECTOR_SIZE.toLong(), COMPLAINT_DETECTOR_SIZE.toLong())
            ).use { inputTensor ->
                session.run(mapOf(inputName to inputTensor)).use { results ->
                    val raw = flattenFloats(results[0].value)
                    if (raw.isEmpty()) {
                        Log.d(MEDIA_SCANNER_LOG_TAG, "Complaint inference failed: unexpected output tensor")
                        return null
                    }
                    val rowSize = inferComplaintRowSize(raw.size)
                    if (rowSize < 6) {
                        Log.d(MEDIA_SCANNER_LOG_TAG, "Complaint inference failed: unexpected flat output size=${raw.size}")
                        return null
                    }
                    val rowCount = raw.size / rowSize
                    Log.d(MEDIA_SCANNER_LOG_TAG, "Complaint model returned $rowCount row(s), rowSize=$rowSize, flatSize=${raw.size}")
                    val scoredRows = (0 until rowCount).mapNotNull { rowIndex ->
                        val offset = rowIndex * rowSize
                        decodeComplaintScore(raw, offset, rowSize)
                    }
                    Log.d(MEDIA_SCANNER_LOG_TAG, "Complaint top scores: ${scoredRows.toComplaintLogSummary()}")
                    val bikeLaneScore = scoredRows
                        .filter { (label, _) -> label == COMPLAINT_CLASS_BLOCKED_BIKE_LANE }
                        .maxOfOrNull { (_, score) -> score }
                    val crosswalkScore = scoredRows
                        .filter { (label, _) -> label == COMPLAINT_CLASS_BLOCKED_CROSSWALK }
                        .maxOfOrNull { (_, score) -> score }
                    val best = listOfNotNull(
                        bikeLaneScore?.let { COMPLAINT_CLASS_BLOCKED_BIKE_LANE to it },
                        crosswalkScore?.let { COMPLAINT_CLASS_BLOCKED_CROSSWALK to it }
                    ).maxByOrNull { it.second } ?: run {
                        Log.d(
                            MEDIA_SCANNER_LOG_TAG,
                            "Complaint inference returned no blocked bike lane/crosswalk label"
                        )
                        return null
                    }
                    val complaint = when (best.first) {
                        COMPLAINT_CLASS_BLOCKED_BIKE_LANE -> COMPLAINT_BLOCKED_BIKE_LANE
                        COMPLAINT_CLASS_BLOCKED_CROSSWALK -> COMPLAINT_BLOCKED_CROSSWALK
                        else -> null
                    } ?: return null
                    val accepted = best.second >= COMPLAINT_DETECTION_THRESHOLD
                    Log.d(MEDIA_SCANNER_LOG_TAG, "Complaint inference result: label=${best.first} score=${best.second} complaint=$complaint accepted=$accepted threshold=$COMPLAINT_DETECTION_THRESHOLD")
                    ComplaintInferenceResult(
                        complaintId = complaint,
                        confidence = best.second,
                        accepted = accepted
                    ).also {
                        session.logOrtProfileOnce()
                    }
                }
            }
        }.getOrElse { error ->
            Log.e(MEDIA_SCANNER_LOG_TAG, "Complaint inference failed with exception", error)
            null
        }
    }

    private fun rankCandidatesForComplaint(
        candidates: List<PlateCandidate>,
        bitmap: Bitmap,
        sessions: AlprSessions,
        expectedComplaintHint: String?
    ): List<PlateCandidate> {
        if (candidates.size <= 1) return candidates
        val expectedLabel = expectedComplaintHint.expectedComplaintClass() ?: return candidates
        val regions = detectComplaintRegions(bitmap, sessions.complaint, expectedLabel)
        if (regions.isEmpty()) return candidates
        return candidates.sortedWith(
            compareByDescending<PlateCandidate> { it.complaintLocationScore(regions.map { region -> region.detection }, bitmap) }
                .thenByDescending { it.confidence }
                .thenByDescending { it.centerBiasedScore() }
        )
    }

    private fun detectComplaintRegions(
        bitmap: Bitmap,
        session: OrtSession?,
        expectedLabel: Int
    ): List<ComplaintRegion> {
        session ?: return emptyList()
        return runCatching {
            val inputName = session.inputNames.first()
            val letterboxed = letterbox(bitmap, COMPLAINT_DETECTOR_SIZE)
            val inputBuffer = bitmapToFloatBuffer(letterboxed.bitmap)
            OnnxTensor.createTensor(
                ortEnvironment,
                inputBuffer,
                longArrayOf(1, 3, COMPLAINT_DETECTOR_SIZE.toLong(), COMPLAINT_DETECTOR_SIZE.toLong())
            ).use { inputTensor ->
                session.run(mapOf(inputName to inputTensor)).use { results ->
                    val raw = flattenFloats(results[0].value)
                    if (raw.isEmpty()) return emptyList()
                    val rowSize = inferComplaintRowSize(raw.size)
                    if (rowSize < 6) return emptyList()
                    val rowCount = raw.size / rowSize
                    (0 until rowCount).mapNotNull { rowIndex ->
                        val offset = rowIndex * rowSize
                        val (label, score) = decodeComplaintScore(raw, offset, rowSize) ?: return@mapNotNull null
                        if (label != expectedLabel || score < COMPLAINT_DETECTION_THRESHOLD) return@mapNotNull null
                        val coordinateOffset = if (rowSize >= 7) 1 else 0
                        val x1 = ((raw[offset + coordinateOffset] - letterboxed.padX) / letterboxed.scale)
                            .coerceIn(0f, bitmap.width.toFloat())
                        val y1 = ((raw[offset + coordinateOffset + 1] - letterboxed.padY) / letterboxed.scale)
                            .coerceIn(0f, bitmap.height.toFloat())
                        val x2 = ((raw[offset + coordinateOffset + 2] - letterboxed.padX) / letterboxed.scale)
                            .coerceIn(0f, bitmap.width.toFloat())
                        val y2 = ((raw[offset + coordinateOffset + 3] - letterboxed.padY) / letterboxed.scale)
                            .coerceIn(0f, bitmap.height.toFloat())
                        if ((x2 - x1) < 8f || (y2 - y1) < 8f) return@mapNotNull null
                        ComplaintRegion(label, Detection(x1, y1, x2, y2, score))
                    }
                }
            }
        }.getOrElse { error ->
            Log.w(MEDIA_SCANNER_LOG_TAG, "Complaint region inference failed", error)
            emptyList()
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
                    grouped.map { detections -> detections.sortedByDescending { it.score } }.also {
                        session.logOrtProfileOnce()
                    }
                }
            }
        }.getOrElse {
            bitmaps.map { detect(it, session) }
        }
    }

    private fun List<Pair<Int, Float>>.toComplaintLogSummary(): String =
        sortedByDescending { it.second }
            .take(6)
            .joinToString(prefix = "[", postfix = "]") { (label, score) ->
                "${label.toComplaintLabel()}=$score"
            }

    private fun Int.toComplaintLabel(): String =
        when (this) {
            COMPLAINT_CLASS_BLOCKED_BIKE_LANE -> "blocked_bike_lane"
            COMPLAINT_CLASS_BLOCKED_CROSSWALK -> "blocked_crosswalk"
            else -> "label_$this"
            }

    private fun inferComplaintRowSize(flatSize: Int): Int =
        when {
            flatSize % 6 == 0 -> 6
            flatSize % 7 == 0 -> 7
            flatSize % 5 == 0 -> 5
            else -> flatSize
        }

    private fun decodeComplaintScore(raw: FloatArray, offset: Int, rowSize: Int): Pair<Int, Float>? =
        when {
            rowSize >= 7 -> raw[offset + 5].roundToInt() to raw[offset + 6].normalizedComplaintScore()
            rowSize == 6 -> {
                val bikeLaneScore = raw[offset + 4].normalizedComplaintScore()
                val crosswalkScore = raw[offset + 5].normalizedComplaintScore()
                if (bikeLaneScore >= crosswalkScore) {
                    COMPLAINT_CLASS_BLOCKED_BIKE_LANE to bikeLaneScore
                } else {
                    COMPLAINT_CLASS_BLOCKED_CROSSWALK to crosswalkScore
                }
            }
            rowSize == 5 -> raw[offset + 4].roundToInt() to raw[offset + 3].normalizedComplaintScore()
            rowSize >= 2 -> {
                val bestLabel = (0 until rowSize).maxByOrNull { raw[offset + it] } ?: return null
                bestLabel to raw[offset + bestLabel].normalizedComplaintScore()
            }
            else -> null
        }

    private fun Float.normalizedComplaintScore(): Float =
        when {
            !isFinite() -> 0f
            this < 0f -> 0f
            this > 1f -> 1f
            else -> this
        }

    private fun runOcr(bitmap: Bitmap, session: OrtSession): String {
        return runOcrBatched(listOf(bitmap), session).firstOrNull().orEmpty()
    }

    private fun runOcrBatched(bitmaps: List<Bitmap>, session: OrtSession): List<String> {
        if (bitmaps.isEmpty()) return emptyList()
        val results = mutableListOf<String>()
        var index = 0
        while (index < bitmaps.size) {
            val end = min(index + OCR_LEGACY_BATCH_SIZE, bitmaps.size)
            results += runOcrLegacyChunk(bitmaps.subList(index, end), session)
            index = end
        }
        return results
    }

    private fun runOcrLegacyChunk(bitmaps: List<Bitmap>, session: OrtSession): List<String> {
        if (bitmaps.isEmpty() || bitmaps.size > OCR_LEGACY_BATCH_SIZE) return emptyList()
        val inputName = session.inputNames.first()
        val processed = bitmaps.map(::prepareOcrBitmap)
        val byteBuffer = ByteBuffer.allocateDirect(OCR_LEGACY_BATCH_SIZE * OCR_WIDTH * OCR_HEIGHT)
        processed.forEach { putOcrBitmapBytes(it, byteBuffer) }
        byteBuffer.rewind()
        OnnxTensor.createTensor(
            ortEnvironment,
            byteBuffer,
            longArrayOf(OCR_LEGACY_BATCH_SIZE.toLong(), OCR_HEIGHT.toLong(), OCR_WIDTH.toLong(), 1),
            OnnxJavaType.UINT8
        ).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { results ->
                val raw = (results[0].value as? Array<FloatArray>) ?: return bitmaps.map { "" }
                if (raw.size < bitmaps.size) return bitmaps.map { "" }
                return raw.take(bitmaps.size).map(::decodeOcrRow).also {
                    session.logOrtProfileOnce()
                }
            }
        }
    }

    private fun runBackupTextOcrBatched(
        bitmaps: List<Bitmap>,
        primaryTexts: List<String>,
        force: Boolean
    ): List<String> {
        if (bitmaps.isEmpty()) return emptyList()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            bitmaps.mapIndexed { index, bitmap ->
                val primaryText = primaryTexts.getOrNull(index).orEmpty()
                if (!force && !shouldTryBackupTextOcr(primaryText)) {
                    return@mapIndexed ""
                }
                runCatching {
                    val input = InputImage.fromBitmap(bitmap, 0)
                    val result = Tasks.await(recognizer.process(input), 2, TimeUnit.SECONDS)
                    result.text.toBackupPlateText(primaryText)
                }.getOrElse { error ->
                    Log.d(PLATE_DETECTION_LOG_TAG, "Backup Google OCR failed for plate crop #$index", error)
                    ""
                }
            }
        } finally {
            recognizer.close()
        }
    }

    private fun shouldTryBackupTextOcr(primaryText: String): Boolean {
        val normalized = normalizePlateText(primaryText)
        if (normalized.isBlank()) return true
        if (normalized.length !in 5..8) return true
        return PlatePatternClassifier.classify(normalized) == null && normalized.length >= 7
    }

    private fun selectPlateOcrText(primaryText: String, backupText: String): String {
        val primary = normalizePlateText(primaryText)
        val backup = normalizePlateText(backupText)
        if (backup.isBlank()) return primaryText
        if (primary.isBlank()) return backup
        val primaryPattern = PlatePatternClassifier.classify(primary)
        val backupPattern = PlatePatternClassifier.classify(backup)
        if (backupPattern != null && primaryPattern == null) return backup
        if (primary.length !in 5..8 && backup.length in 5..8) return backup
        return primaryText
    }

    private fun String.toBackupPlateText(primaryText: String): String {
        val primary = normalizePlateText(primaryText)
        val tokenCandidates = split(Regex("[^A-Za-z0-9]+"))
            .flatMap { token -> token.plateLikeWindows() }
        val compactCandidates = normalizePlateText(this).plateLikeWindows()
        val candidates = (tokenCandidates + compactCandidates).distinct()
        if (candidates.isEmpty()) return normalizePlateText(this).takeIf { it.length in 5..8 }.orEmpty()
        return candidates.maxWithOrNull(
            compareBy<String> { PlatePatternClassifier.classify(it)?.confidence ?: 0f }
                .thenBy { if (it == primary) 1 else 0 }
                .thenBy { if (it.length in 6..8) 1 else 0 }
                .thenBy { it.length }
        ).orEmpty()
    }

    private fun String.plateLikeWindows(): List<String> {
        val normalized = normalizePlateText(this)
        if (normalized.length in 5..8) return listOf(normalized)
        if (normalized.length < 5) return emptyList()
        val windows = mutableListOf<String>()
        for (size in 8 downTo 5) {
            if (normalized.length < size) continue
            for (start in 0..(normalized.length - size)) {
                windows += normalized.substring(start, start + size)
            }
        }
        return windows
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
                ).also {
                    session.logOrtProfileOnce()
                }
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
                    }.also {
                        session.logOrtProfileOnce()
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

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        val decoded = when (uri.scheme) {
            "file" -> uri.path?.let(::File)?.takeIf { it.exists() }?.inputStream()?.use(BitmapFactory::decodeStream)
            else -> context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
        decoded ?: return null
        val orientation = readExifOrientation(context, uri)
        val oriented = decoded.applyExifOrientation(orientation)
        Log.d(
            PLATE_DETECTION_LOG_TAG,
            "Bitmap EXIF orientation applied: uri=$uri, orientation=${orientation.toExifOrientationName()}, input=${decoded.width}x${decoded.height}, output=${oriented.width}x${oriented.height}"
        )
        return oriented
    }

    private fun readExifOrientation(context: Context, uri: Uri): Int =
        runCatching {
            when (uri.scheme) {
                "file" -> uri.path
                    ?.let(::File)
                    ?.takeIf { it.exists() }
                    ?.inputStream()
                    ?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                else -> context.contentResolver.openInputStream(uri)
                    ?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrElse { error ->
            Log.d(PLATE_DETECTION_LOG_TAG, "Unable to read bitmap EXIF orientation for uri=$uri", error)
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return this
        }
        return runCatching {
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        }.getOrElse { error ->
            Log.d(PLATE_DETECTION_LOG_TAG, "Failed to apply EXIF orientation=${orientation.toExifOrientationName()}", error)
            this
        }
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
        if (detection.hasPlateLikeAspectRatio()) {
            Log.d(
                PLATE_DETECTION_LOG_TAG,
                "Skipping deskew for plate-like detector box: ${detection.toLogString()}, crop=${initialPlateCrop.width}x${initialPlateCrop.height}"
            )
            return PlateCrop(
                bitmap = initialPlateCrop,
                thumbnailBitmap = initialPlateCrop,
                detection = detection,
                rotationDegrees = 0f,
                cornerPoints = detection.cornerPoints()
            )
        }
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

        val rotatedExpandedDetection = expandedPlateDetection(rotatedMatch.rotatedImage.bitmap, rotatedMatch.detection)
        val rotatedExpandedCrop = crop(rotatedMatch.rotatedImage.bitmap, rotatedExpandedDetection)
        val segmentedRotatedCrop = rotatedExpandedCrop?.let {
            segmentPlateCrop(
                source = rotatedMatch.rotatedImage.bitmap,
                expandedDetection = rotatedExpandedDetection,
                expandedCrop = it,
                session = sessions.plateSegmentation
            )
        }
        val ocrDetection = segmentedRotatedCrop?.detection ?: rotatedMatch.detection
        val plateBitmap = crop(rotatedMatch.rotatedImage.bitmap, ocrDetection)
            ?: crop(rotatedMatch.rotatedImage.bitmap, rotatedMatch.detection)
            ?: return null
        val imageCornerPoints = ocrDetection.toOriginalImageCornerPoints(
            rotatedImage = rotatedMatch.rotatedImage,
            originalDetection = detection
        )
        val displayDetection = detection
        val displayThumbnail = crop(bitmap, displayDetection) ?: initialPlateCrop
        Log.d(
            PLATE_DETECTION_LOG_TAG,
            "Rotated OCR crop: original=${detection.toLogString()}, rotatedMatch=${rotatedMatch.detection.toLogString()}, ocr=${ocrDetection.toLogString()}, usedSegmentation=${segmentedRotatedCrop != null}, plateBitmap=${plateBitmap.width}x${plateBitmap.height}"
        )
        return PlateCrop(
            bitmap = plateBitmap,
            thumbnailBitmap = displayThumbnail,
            detection = displayDetection.copy(score = ocrDetection.score),
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
        return raw.replace("_", "").filter { it.isLetterOrDigit() }.uppercase()
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

private fun Detection.toLogString(): String =
    "box=[${x1.roundToInt()},${y1.roundToInt()},${x2.roundToInt()},${y2.roundToInt()}] score=$score size=${(x2 - x1).roundToInt()}x${(y2 - y1).roundToInt()}"

private fun Detection.hasPlateLikeAspectRatio(): Boolean {
    val width = x2 - x1
    val height = y2 - y1
    if (width <= 0f || height <= 0f) return false
    val aspectRatio = width / height
    return aspectRatio in PLATE_LIKE_MIN_ASPECT_RATIO..PLATE_LIKE_MAX_ASPECT_RATIO
}

private fun List<Detection>.toDetectionLogSummary(): String =
    take(12).joinToString(prefix = "[", postfix = "]") { it.toLogString() }

private fun List<PlateCandidate>.toCandidateLogSummary(): String =
    take(12).joinToString(prefix = "[", postfix = "]") { candidate ->
        "${candidate.plate}/${candidate.state ?: "?"} confidence=${candidate.confidence} stateConfidence=${candidate.stateConfidence} type=${candidate.plateType ?: "-"} bounds=${candidate.boundsLeft},${candidate.boundsTop},${candidate.boundsRight},${candidate.boundsBottom} rotation=${candidate.rotationDegrees}"
    }

private fun Int.toExifOrientationName(): String =
    when (this) {
        ExifInterface.ORIENTATION_NORMAL -> "NORMAL"
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "FLIP_HORIZONTAL"
        ExifInterface.ORIENTATION_ROTATE_180 -> "ROTATE_180"
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> "FLIP_VERTICAL"
        ExifInterface.ORIENTATION_TRANSPOSE -> "TRANSPOSE"
        ExifInterface.ORIENTATION_ROTATE_90 -> "ROTATE_90"
        ExifInterface.ORIENTATION_TRANSVERSE -> "TRANSVERSE"
        ExifInterface.ORIENTATION_ROTATE_270 -> "ROTATE_270"
        ExifInterface.ORIENTATION_UNDEFINED -> "UNDEFINED"
        else -> "UNKNOWN_$this"
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

private fun PlateCandidate.complaintLocationScore(regions: List<Detection>, bitmap: Bitmap): Float {
    if (regions.isEmpty()) return 0f
    val centerX = (focalPointX ?: return 0f) * bitmap.width
    val centerY = (focalPointY ?: return 0f) * bitmap.height
    return regions.maxOf { region ->
        val inside = centerInside(region, centerX, centerY)
        val regionCenterX = (region.x1 + region.x2) / 2f
        val regionCenterY = (region.y1 + region.y2) / 2f
        val dx = (centerX - regionCenterX) / bitmap.width.coerceAtLeast(1)
        val dy = (centerY - regionCenterY) / bitmap.height.coerceAtLeast(1)
        val proximity = (1f - (sqrt(dx * dx + dy * dy) / 0.70710677f)).coerceIn(0f, 1f)
        val overlap = normalizedOverlapWith(region, bitmap)
        (if (inside) 2f else 0f) + proximity + overlap
    }
}

private fun PlateCandidate.centerInside(detection: Detection, centerX: Float, centerY: Float): Boolean {
    val paddingX = (detection.x2 - detection.x1) * 0.04f
    val paddingY = (detection.y2 - detection.y1) * 0.04f
    return centerX in (detection.x1 - paddingX)..(detection.x2 + paddingX) &&
        centerY in (detection.y1 - paddingY)..(detection.y2 + paddingY)
}

private fun PlateCandidate.normalizedOverlapWith(detection: Detection, bitmap: Bitmap): Float {
    val left = (boundsLeft ?: return 0f) * bitmap.width
    val top = (boundsTop ?: return 0f) * bitmap.height
    val right = (boundsRight ?: return 0f) * bitmap.width
    val bottom = (boundsBottom ?: return 0f) * bitmap.height
    val intersectionLeft = max(left, detection.x1)
    val intersectionTop = max(top, detection.y1)
    val intersectionRight = min(right, detection.x2)
    val intersectionBottom = min(bottom, detection.y2)
    val intersection = max(0f, intersectionRight - intersectionLeft) * max(0f, intersectionBottom - intersectionTop)
    val plateArea = max(1f, (right - left) * (bottom - top))
    return (intersection / plateArea).coerceIn(0f, 1f)
}

private fun PlateCandidate.centerInside(detection: Detection, bitmap: Bitmap): Boolean {
    val centerX = (focalPointX ?: return false) * bitmap.width
    val centerY = (focalPointY ?: return false) * bitmap.height
    return centerInside(detection, centerX, centerY)
}

private fun String?.expectedComplaintClass(): Int? {
    val normalized = this?.lowercase().orEmpty()
    return when {
        "bike" in normalized && "lane" in normalized -> COMPLAINT_CLASS_BLOCKED_BIKE_LANE
        "crosswalk" in normalized -> COMPLAINT_CLASS_BLOCKED_CROSSWALK
        else -> null
    }
}
