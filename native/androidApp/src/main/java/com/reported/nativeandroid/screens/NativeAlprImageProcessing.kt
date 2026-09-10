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
internal fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
    val decoded = when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.takeIf { it.exists() }?.inputStream()?.use(BitmapFactory::decodeStream)
        else -> context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
    }
    decoded ?: return null
    val orientation = readAlprExifOrientation(context, uri)
    val oriented = decoded.applyExifOrientation(orientation)
    Log.d(
        PLATE_DETECTION_LOG_TAG,
        "Bitmap EXIF orientation applied: uri=$uri, orientation=${orientation.toExifOrientationName()}, input=${decoded.width}x${decoded.height}, output=${oriented.width}x${oriented.height}"
    )
    return oriented
}

internal fun readAlprExifOrientation(context: Context, uri: Uri): Int =
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

internal fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
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

internal fun decodeMediaPreviewBitmap(context: Context, media: SubmissionMedia): Bitmap? {
    val uri = Uri.parse(media.uri)
    if (!media.isVideo) {
        return decodeBitmap(context, uri)
    }
    val retriever = MediaMetadataRetriever()
    return try {
        NativeAlprEngine.setRetrieverDataSource(context, retriever, uri)
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (_: Throwable) {
        null
    } finally {
        retriever.release()
    }
}

internal fun crop(bitmap: Bitmap, detection: Detection): Bitmap? {
    val left = max(0, detection.x1.toInt())
    val top = max(0, detection.y1.toInt())
    val right = min(bitmap.width, detection.x2.toInt())
    val bottom = min(bitmap.height, detection.y2.toInt())
    val width = right - left
    val height = bottom - top
    if (width <= 2 || height <= 2) return null
    return Bitmap.createBitmap(bitmap, left, top, width, height)
}

internal fun segmentPlateCrop(
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
            NativeAlprEngine.ortEnvironment,
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

internal fun maskToOrientedBox(
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

internal fun refinedPlateCrop(bitmap: Bitmap, detection: Detection, sessions: AlprSessions): PlateCrop? {
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
        // Keep the OCR crop refined, but render the stable detector rectangle.
        // The segmentation polygon can overfit bumper text or plate trim and show a fake slant.
        cornerPoints = displayDetection.cornerPoints()
    )
}

internal fun findBestRotatedPlateMatch(
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

internal fun List<Detection>.bestMatchFor(expected: Detection): Detection? {
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

internal fun Detection.centerDistanceSquaredFrom(expected: Detection): Float {
    val centerX = (x1 + x2) / 2f
    val centerY = (y1 + y2) / 2f
    val expectedCenterX = (expected.x1 + expected.x2) / 2f
    val expectedCenterY = (expected.y1 + expected.y2) / 2f
    val dx = centerX - expectedCenterX
    val dy = centerY - expectedCenterY
    return dx * dx + dy * dy
}

internal fun expandedPlateDetection(bitmap: Bitmap, detection: Detection): Detection {
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

internal fun Detection.toOriginalImageCornerPoints(
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

internal fun Detection.mapWith(matrix: Matrix): Detection {
    val points = floatArrayOf(
        x1, y1,
        x2, y1,
        x2, y2,
        x1, y2
    )
    matrix.mapPoints(points)
    return points.toList().toBoundingDetection(score, this)
}

internal fun Detection.cornerPoints(): List<Float> = listOf(
    x1, y1,
    x2, y1,
    x2, y2,
    x1, y2
)

internal fun estimateAngleFromPoints(points: List<Float>): Float {
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

internal fun List<Float>.toBoundingDetection(score: Float, fallback: Detection): Detection {
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

internal fun savePlateThumbnail(context: Context, plate: String, bitmap: Bitmap): String? =
    runCatching {
        val directory = File(context.cacheDir, "alpr-thumbnails").apply { mkdirs() }
        val safePlate = plate.ifBlank { "plate" }.filter { it.isLetterOrDigit() }.take(12)
        val file = File(directory, "${safePlate}-${System.nanoTime()}.png")
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        Uri.fromFile(file).toString()
    }.getOrNull()

internal fun letterbox(source: Bitmap, targetSize: Int): LetterboxedBitmap {
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

internal fun vehicleSegmentationBitmap(source: Bitmap): VehicleSegmentationBitmap {
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

internal fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
    val floats = bitmapsToFloatBuffer(listOf(bitmap))
    floats.rewind()
    return floats
}

internal fun bitmapsToFloatBuffer(bitmaps: List<Bitmap>): FloatBuffer {
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

internal fun prepareOcrBitmap(source: Bitmap): Bitmap {
    val resized = Bitmap.createScaledBitmap(source, OCR_WIDTH, OCR_HEIGHT, true)
    val grayscale = Bitmap.createBitmap(OCR_WIDTH, OCR_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(grayscale)
    val matrix = ColorMatrix().apply { setSaturation(0f) }
    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
    canvas.drawBitmap(resized, 0f, 0f, paint)
    return grayscale
}

internal fun estimateDeskewAngle(source: Bitmap): Float {
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

internal fun normalizePlateAngle(degrees: Float): Float {
    var normalized = degrees
    while (normalized > 45f) normalized -= 90f
    while (normalized < -45f) normalized += 90f
    return normalized.coerceIn(-MAX_DESKEW_DEGREES.toFloat(), MAX_DESKEW_DEGREES.toFloat())
}

internal fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return source
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

internal fun rotateBitmapWithTransform(source: Bitmap, degrees: Float): RotatedBitmap {
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

internal fun Float.normalizedVideoRotationDegrees(): Float {
    val rounded = (this / 90f).roundToInt() * 90
    val normalized = ((rounded % 360) + 360) % 360
    return when (normalized) {
        90 -> 90f
        180 -> 180f
        270 -> -90f
        else -> 0f
    }
}

internal fun List<Float>.toNormalizedCornerPoints(width: Int, height: Int): List<Float> {
    if (size < 8 || width <= 0 || height <= 0) return emptyList()
    val normalized = ArrayList<Float>(8)
    for (index in 0 until 8 step 2) {
        normalized += (this[index] / width).coerceIn(0f, 1f)
        normalized += (this[index + 1] / height).coerceIn(0f, 1f)
    }
    return normalized
}

internal fun normalizePlateText(raw: String): String {
    return raw.replace("_", "").filter { it.isLetterOrDigit() }.uppercase()
}

internal fun flattenFloats(value: Any?): FloatArray =
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
