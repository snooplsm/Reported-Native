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
internal fun mergeDetections(
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

internal fun detectPlatesViaVehicleSegmentation(bitmap: Bitmap, sessions: AlprSessions): List<Detection> {
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

internal fun detectVehicles(bitmap: Bitmap, segmenter: OrtSession, nms: OrtSession): List<Detection> {
    return detectSegmentedObjects(bitmap, segmenter, nms)
        .filter {
            it.label in VEHICLE_SEGMENTATION_LABELS &&
                it.detection.score >= VEHICLE_SEGMENTATION_MIN_SCORE &&
                (it.detection.x2 - it.detection.x1) >= VEHICLE_SEGMENTATION_MIN_SIDE &&
                (it.detection.y2 - it.detection.y1) >= VEHICLE_SEGMENTATION_MIN_SIDE
        }
        .map { it.detection }
}

internal fun detectSegmentedObjects(bitmap: Bitmap, segmenter: OrtSession, nms: OrtSession): List<SegmentedObject> {
    val segmented = vehicleSegmentationBitmap(bitmap)
    val inputName = segmenter.inputNames.first()
    val inputBuffer = bitmapToFloatBuffer(segmented.bitmap)
    OnnxTensor.createTensor(
        NativeAlprEngine.ortEnvironment,
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
            OnnxTensor.createTensor(NativeAlprEngine.ortEnvironment, configBuffer, longArrayOf(4)).use { configTensor ->
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

internal fun inferVehicleSegmentationRowSize(flatSize: Int): Int =
    when {
        flatSize % VEHICLE_SEGMENTATION_ROW_SIZE == 0 -> VEHICLE_SEGMENTATION_ROW_SIZE
        flatSize % (VEHICLE_SEGMENTATION_CLASSES + 4) == 0 -> VEHICLE_SEGMENTATION_CLASSES + 4
        else -> VEHICLE_SEGMENTATION_ROW_SIZE
    }

internal fun detect(bitmap: Bitmap, session: OrtSession): List<Detection> {
    val inputName = session.inputNames.first()
    val letterboxed = letterbox(bitmap, DETECTOR_SIZE)
    val inputBuffer = bitmapToFloatBuffer(letterboxed.bitmap)
    OnnxTensor.createTensor(
        NativeAlprEngine.ortEnvironment,
        inputBuffer,
        longArrayOf(1, 3, DETECTOR_SIZE.toLong(), DETECTOR_SIZE.toLong())
    ).use { inputTensor ->
        session.run(mapOf(inputName to inputTensor)).use { results ->
            val raw = results[0].value.asFloatRowsOrNull() ?: return emptyList()
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
                NativeAlprEngine.logOrtProfileOnce(session)
            }
        }
    }
}

internal fun detectComplaint(
    bitmap: Bitmap,
    session: OrtSession?,
    confidenceThreshold: Float
): ComplaintInferenceResult? {
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
            NativeAlprEngine.ortEnvironment,
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
                val accepted = best.second >= confidenceThreshold
                Log.d(MEDIA_SCANNER_LOG_TAG, "Complaint inference result: label=${best.first} score=${best.second} complaint=$complaint accepted=$accepted threshold=$confidenceThreshold")
                ComplaintInferenceResult(
                    complaintId = complaint,
                    confidence = best.second,
                    accepted = accepted
                ).also {
                    NativeAlprEngine.logOrtProfileOnce(session)
                }
            }
        }
    }.getOrElse { error ->
        Log.e(MEDIA_SCANNER_LOG_TAG, "Complaint inference failed with exception", error)
        null
    }
}

internal fun rankCandidatesForComplaint(
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

internal fun detectComplaintRegions(
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
            NativeAlprEngine.ortEnvironment,
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
                    if (label != expectedLabel || score < AutoReportThresholds.DEFAULT_COMPLAINT_CONFIDENCE) return@mapNotNull null
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

internal fun detectBatched(bitmaps: List<Bitmap>, session: OrtSession): List<List<Detection>> {
    if (bitmaps.isEmpty()) return emptyList()
    if (bitmaps.size == 1) return listOf(detect(bitmaps.first(), session))
    return runCatching {
        val inputName = session.inputNames.first()
        val letterboxed = bitmaps.map { letterbox(it, DETECTOR_SIZE) }
        val inputBuffer = bitmapsToFloatBuffer(letterboxed.map { it.bitmap })
        OnnxTensor.createTensor(
            NativeAlprEngine.ortEnvironment,
            inputBuffer,
            longArrayOf(bitmaps.size.toLong(), 3, DETECTOR_SIZE.toLong(), DETECTOR_SIZE.toLong())
        ).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { results ->
                val raw = results[0].value.asFloatRowsOrNull() ?: return@use emptyList<List<Detection>>()
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
                    NativeAlprEngine.logOrtProfileOnce(session)
                }
            }
        }
    }.getOrElse {
        bitmaps.map { detect(it, session) }
    }
}

internal fun List<Pair<Int, Float>>.toComplaintLogSummary(): String =
    sortedByDescending { it.second }
        .take(6)
        .joinToString(prefix = "[", postfix = "]") { (label, score) ->
            "${label.toComplaintLabel()}=$score"
        }

internal fun Int.toComplaintLabel(): String =
    when (this) {
        COMPLAINT_CLASS_BLOCKED_BIKE_LANE -> "blocked_bike_lane"
        COMPLAINT_CLASS_BLOCKED_CROSSWALK -> "blocked_crosswalk"
        else -> "label_$this"
        }

internal fun inferComplaintRowSize(flatSize: Int): Int =
    when {
        flatSize % 6 == 0 -> 6
        flatSize % 7 == 0 -> 7
        flatSize % 5 == 0 -> 5
        else -> flatSize
    }

internal fun decodeComplaintScore(raw: FloatArray, offset: Int, rowSize: Int): Pair<Int, Float>? =
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

internal fun Float.normalizedComplaintScore(): Float =
    when {
        !isFinite() -> 0f
        this < 0f -> 0f
        this > 1f -> 1f
        else -> this
    }

internal fun runOcr(bitmap: Bitmap, session: OrtSession): String {
    return runOcrBatched(listOf(bitmap), session).firstOrNull().orEmpty()
}

internal fun runOcrBatched(bitmaps: List<Bitmap>, session: OrtSession): List<String> {
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

internal fun runOcrLegacyChunk(bitmaps: List<Bitmap>, session: OrtSession): List<String> {
    if (bitmaps.isEmpty() || bitmaps.size > OCR_LEGACY_BATCH_SIZE) return emptyList()
    val inputName = session.inputNames.first()
    val processed = bitmaps.map(::prepareOcrBitmap)
    val byteBuffer = ByteBuffer.allocateDirect(OCR_LEGACY_BATCH_SIZE * OCR_WIDTH * OCR_HEIGHT)
    processed.forEach { putOcrBitmapBytes(it, byteBuffer) }
    byteBuffer.rewind()
    OnnxTensor.createTensor(
        NativeAlprEngine.ortEnvironment,
        byteBuffer,
        longArrayOf(OCR_LEGACY_BATCH_SIZE.toLong(), OCR_HEIGHT.toLong(), OCR_WIDTH.toLong(), 1),
        OnnxJavaType.UINT8
    ).use { inputTensor ->
        session.run(mapOf(inputName to inputTensor)).use { results ->
            val raw = results[0].value.asFloatRowsOrNull() ?: return bitmaps.map { "" }
            if (raw.size < bitmaps.size) return bitmaps.map { "" }
            return raw.take(bitmaps.size).map(::decodeOcrRow).also {
                NativeAlprEngine.logOrtProfileOnce(session)
            }
        }
    }
}

internal fun runBackupTextOcrBatched(
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

internal fun shouldTryBackupTextOcr(primaryText: String): Boolean {
    val normalized = normalizePlateText(primaryText)
    if (normalized.isBlank()) return true
    if (normalized.length !in 5..8) return true
    return PlatePatternClassifier.classify(normalized) == null && normalized.length >= 7
}

internal fun selectPlateOcrText(primaryText: String, backupText: String): String {
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

internal fun String.toBackupPlateText(primaryText: String): String {
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

internal fun String.plateLikeWindows(): List<String> {
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

internal fun putOcrBitmapBytes(bitmap: Bitmap, byteBuffer: ByteBuffer) {
    for (y in 0 until OCR_HEIGHT) {
        for (x in 0 until OCR_WIDTH) {
            val pixel = bitmap.getPixel(x, y)
            byteBuffer.put((pixel and 0xFF).toByte())
        }
    }
}

internal fun decodeOcrRow(raw: FloatArray): String {
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

internal fun classifyPlateState(bitmap: Bitmap, session: OrtSession): PlateStateClassification? {
    val inputName = session.inputNames.first()
    val resized = Bitmap.createScaledBitmap(bitmap, PLATE_STATE_SIZE, PLATE_STATE_SIZE, true)
    val inputBuffer = bitmapToFloatBuffer(resized)
    OnnxTensor.createTensor(
        NativeAlprEngine.ortEnvironment,
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
                NativeAlprEngine.logOrtProfileOnce(session)
            }
        }
    }
}

internal fun classifyPlateStatesBatched(
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
            NativeAlprEngine.ortEnvironment,
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
                    NativeAlprEngine.logOrtProfileOnce(session)
                }
            }
        }.takeIf { it.size == bitmaps.size } ?: bitmaps.map { classifyPlateState(it, session) }
    }.getOrElse {
        bitmaps.map { classifyPlateState(it, session) }
    }
}

internal fun decodePlateStateClassification(raw: FloatArray): PlateStateClassification? {
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
