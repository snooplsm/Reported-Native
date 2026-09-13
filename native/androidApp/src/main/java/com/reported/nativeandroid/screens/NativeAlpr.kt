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
import android.os.Build
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

@Suppress("UNCHECKED_CAST")
internal fun Any?.asFloatRowsOrNull(): Array<FloatArray>? = this as? Array<FloatArray>

internal const val DETECTOR_ASSET = "models/yolo-v9-t-640-license-plates-end2end.onnx"
internal const val OCR_ASSET = "models/global_mobile_vit_v2_ocr.onnx"
internal const val PLATE_STATE_ASSET = "models/reported-plate-class-best.onnx"
internal const val COMPLAINT_ASSET = "models/reported-v13-optimized.onnx"
internal const val PLATE_SEGMENTATION_ASSET = "models/reported-plate-seg.onnx"
internal const val VEHICLE_SEGMENTATION_ASSET = "models/yolov8n-seg.onnx"
internal const val VEHICLE_SEGMENTATION_NMS_ASSET = "models/nms-yolov8.onnx"
internal const val DETECTOR_SIZE = 640
internal const val COMPLAINT_DETECTOR_SIZE = 512
internal const val VEHICLE_SEGMENTATION_SIZE = 640
internal const val VEHICLE_SEGMENTATION_CLASSES = 80
internal const val VEHICLE_SEGMENTATION_ROW_SIZE = 116
internal const val VEHICLE_SEGMENTATION_TOP_K = 100f
internal const val VEHICLE_SEGMENTATION_IOU_THRESHOLD = 0.4f
internal const val VEHICLE_SEGMENTATION_SCORE_THRESHOLD = 0.2f
internal const val VEHICLE_SEGMENTATION_MIN_SCORE = 0.5f
internal const val VEHICLE_SEGMENTATION_MIN_SIDE = 60f
internal const val COCO_PERSON_LABEL = 0
internal const val COCO_BICYCLE_LABEL = 1
internal const val COMPLAINT_BLOCKED_BIKE_LANE = "blocked_bike_lane"
internal const val COMPLAINT_BLOCKED_CROSSWALK = "blocked_crosswalk"
internal const val COMPLAINT_CLASS_BLOCKED_BIKE_LANE = 0
internal const val COMPLAINT_CLASS_BLOCKED_CROSSWALK = 1
internal const val MEDIA_SCANNER_LOG_TAG = "ReportedMediaScanner"
internal const val PLATE_DETECTION_LOG_TAG = "ReportedPlateDetection"
internal const val PLATE_STATE_SIZE = 160
internal const val PLATE_SEGMENTATION_SIZE = 160
internal const val OCR_WIDTH = 140
internal const val OCR_HEIGHT = 70
internal const val OCR_LEGACY_BATCH_SIZE = 8
internal const val DETECTION_THRESHOLD = 0.2f
internal const val SEGMENTATION_MASK_THRESHOLD = 0.5f
internal const val MAX_CANDIDATES = 8
internal const val LETTERBOX_COLOR = 114
internal const val OCR_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_"
internal const val DESKEW_TRIGGER_DEGREES = 1.5f
internal const val MAX_DESKEW_DEGREES = 20
internal const val EDGE_THRESHOLD = 32
internal const val PLATE_AXIS_PADDING = 0.45f
internal const val PLATE_CROSS_AXIS_PADDING = 0.65f
internal const val PLATE_LIKE_MIN_ASPECT_RATIO = 2.0f
internal const val PLATE_LIKE_MAX_ASPECT_RATIO = 6.5f
internal const val ROTATED_MATCH_DISTANCE_MULTIPLIER = 2.5f
internal const val VIDEO_FRAME_STRIDE = 3
internal const val VIDEO_DETECTION_BATCH_SIZE = 3
internal const val VIDEO_ROTATION_PROBE_STRIDE = 9
internal const val VIDEO_DRY_PROCESSED_FRAME_COUNT = 3
internal val PLATE_STATE_LABELS = listOf("CT", "00", "NJ", "NY", "NY_PD", "NY_TLC", "PA")
internal val VIDEO_ROTATION_PROBE_DEGREES = floatArrayOf(-90f, 90f, 180f)
internal val VEHICLE_SEGMENTATION_LABELS = setOf(2, 5, 7) // COCO car, bus, truck.

internal data class LetterboxedBitmap(
    val bitmap: Bitmap,
    val scale: Float,
    val padX: Float,
    val padY: Float
)

internal data class VehicleSegmentationBitmap(
    val bitmap: Bitmap,
    val scale: Float
)

internal data class Detection(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val score: Float
)

internal data class SegmentedObject(
    val label: Int,
    val detection: Detection
)

internal data class VideoFrameSample(
    val frameIndex: Int,
    val frameTimeUs: Long,
    val bitmap: Bitmap
)

internal data class PlateCropWork(
    val bitmap: Bitmap,
    val sourceDetection: Detection,
    val crop: PlateCrop
)

internal data class PlateCrop(
    val bitmap: Bitmap,
    val thumbnailBitmap: Bitmap,
    val detection: Detection,
    val rotationDegrees: Float,
    val cornerPoints: List<Float> = emptyList()
)

internal data class RotatedBitmap(
    val bitmap: Bitmap,
    val sourceToRotated: Matrix
)

internal data class RotatedVariantDetection(
    val candidates: List<PlateCandidate>,
    val degrees: Float
)

internal data class RotatedPlateMatch(
    val rotatedImage: RotatedBitmap,
    val detection: Detection,
    val appliedRotationDegrees: Float,
    val matchDistanceSquared: Float
)

internal data class AlprSessions(
    val detector: OrtSession,
    val ocr: OrtSession,
    val plateState: OrtSession,
    val complaint: OrtSession?,
    val plateSegmentation: OrtSession?,
    val vehicleSegmentation: OrtSession?,
    val vehicleSegmentationNms: OrtSession?
)

internal data class PlateStateClassification(
    val state: String?,
    val confidence: Float,
    val label: String
)

internal data class ComplaintRegion(
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
    internal val ortEnvironment by lazy { OrtEnvironment.getEnvironment() }
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
            detectComplaint(
                bitmap = bitmap,
                session = sessions.complaint,
                confidenceThreshold = AutoReportThresholds.complaintConfidence(context)
            )
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
            detectComplaint(
                bitmap = bitmap,
                session = sessions.complaint,
                confidenceThreshold = AutoReportThresholds.DEFAULT_COMPLAINT_CONFIDENCE
            )?.takeIf { it.accepted }?.complaintId
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
                boundsTop * rotatedHeight,
                boundsRight * rotatedWidth,
                (boundsBottom ?: return null) * rotatedHeight,
                boundsLeft * rotatedWidth,
                boundsBottom * rotatedHeight
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

    internal fun setRetrieverDataSource(context: Context, retriever: MediaMetadataRetriever, uri: Uri) {
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
        if (isX86Emulator()) {
            val cpuOptions = OrtSession.SessionOptions().also {
                configureDebugOrtProfiling(context, it, label, "cpu-emulator")
            }
            return ortEnvironment.createSession(modelBytes, cpuOptions).also {
                sessionLabels[it] = "$label ORT CPU on x86 emulator"
                Log.i(
                    PLATE_DETECTION_LOG_TAG,
                    "Loaded $label with ORT CPU because NNAPI is unsafe on x86 emulators: $asset"
                )
            }
        }
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

    private fun isX86Emulator(): Boolean {
        val usesX86Abi = Build.SUPPORTED_ABIS.any { abi ->
            abi.equals("x86", ignoreCase = true) || abi.equals("x86_64", ignoreCase = true)
        }
        val usesEmulatorHardware = Build.HARDWARE.equals("ranchu", ignoreCase = true) ||
            Build.HARDWARE.equals("goldfish", ignoreCase = true)
        return usesX86Abi && usesEmulatorHardware
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

    internal fun logOrtProfileOnce(session: OrtSession) {
        session.logOrtProfileOnce()
    }

    private suspend fun getSessionsOrNull(context: Context): AlprSessions? =
        runCatching { getSessions(context) }.getOrElse { error ->
            Log.e(MEDIA_SCANNER_LOG_TAG, "ALPR models unavailable", error)
            null
        }

}
