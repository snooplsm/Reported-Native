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
internal val Detection.area: Float
    get() = max(0f, x2 - x1) * max(0f, y2 - y1)

internal fun Detection.centerDistanceSquared(bitmap: Bitmap): Float {
    val centerX = (x1 + x2) / 2f
    val centerY = (y1 + y2) / 2f
    val dx = centerX - bitmap.width / 2f
    val dy = centerY - bitmap.height / 2f
    return dx * dx + dy * dy
}

internal fun Detection.toLogString(): String =
    "box=[${x1.roundToInt()},${y1.roundToInt()},${x2.roundToInt()},${y2.roundToInt()}] score=$score size=${(x2 - x1).roundToInt()}x${(y2 - y1).roundToInt()}"

internal fun Detection.hasPlateLikeAspectRatio(): Boolean {
    val width = x2 - x1
    val height = y2 - y1
    if (width <= 0f || height <= 0f) return false
    val aspectRatio = width / height
    return aspectRatio in PLATE_LIKE_MIN_ASPECT_RATIO..PLATE_LIKE_MAX_ASPECT_RATIO
}

internal fun List<Detection>.toDetectionLogSummary(): String =
    take(12).joinToString(prefix = "[", postfix = "]") { it.toLogString() }

internal fun List<PlateCandidate>.toCandidateLogSummary(): String =
    take(12).joinToString(prefix = "[", postfix = "]") { candidate ->
        "${candidate.plate}/${candidate.state ?: "?"} confidence=${candidate.confidence} stateConfidence=${candidate.stateConfidence} type=${candidate.plateType ?: "-"} bounds=${candidate.boundsLeft},${candidate.boundsTop},${candidate.boundsRight},${candidate.boundsBottom} rotation=${candidate.rotationDegrees}"
    }

internal fun Int.toExifOrientationName(): String =
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

internal fun Detection.iouWith(other: Detection): Float {
    val left = max(x1, other.x1)
    val top = max(y1, other.y1)
    val right = min(x2, other.x2)
    val bottom = min(y2, other.y2)
    val intersection = max(0f, right - left) * max(0f, bottom - top)
    val union = area + other.area - intersection
    return if (union <= 0f) 0f else intersection / union
}

internal fun Detection.isNear(other: Detection, bitmap: Bitmap): Boolean {
    if (iouWith(other) > 0.02f) return true
    val centerX = (x1 + x2) / 2f
    val centerY = (y1 + y2) / 2f
    val otherCenterX = (other.x1 + other.x2) / 2f
    val otherCenterY = (other.y1 + other.y2) / 2f
    val dx = (centerX - otherCenterX) / bitmap.width.coerceAtLeast(1)
    val dy = (centerY - otherCenterY) / bitmap.height.coerceAtLeast(1)
    return sqrt(dx * dx + dy * dy) < 0.32f
}

internal fun PlateCandidate.centerBiasedScore(): Float {
    val centerX = focalPointX ?: 0.5f
    val centerY = focalPointY ?: 0.5f
    val distanceFromCenter = kotlin.math.sqrt(
        ((centerX - 0.5f) * (centerX - 0.5f) + (centerY - 0.5f) * (centerY - 0.5f)).toDouble()
    ).toFloat()
    val centerScore = (1f - (distanceFromCenter / 0.70710677f)).coerceIn(0f, 1f)
    return confidence * 0.72f + centerScore * 0.28f
}

internal fun PlateCandidate.complaintLocationScore(regions: List<Detection>, bitmap: Bitmap): Float {
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

internal fun PlateCandidate.centerInside(detection: Detection, centerX: Float, centerY: Float): Boolean {
    val paddingX = (detection.x2 - detection.x1) * 0.04f
    val paddingY = (detection.y2 - detection.y1) * 0.04f
    return centerX in (detection.x1 - paddingX)..(detection.x2 + paddingX) &&
        centerY in (detection.y1 - paddingY)..(detection.y2 + paddingY)
}

internal fun PlateCandidate.normalizedOverlapWith(detection: Detection, bitmap: Bitmap): Float {
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

internal fun PlateCandidate.centerInside(detection: Detection, bitmap: Bitmap): Boolean {
    val centerX = (focalPointX ?: return false) * bitmap.width
    val centerY = (focalPointY ?: return false) * bitmap.height
    return centerInside(detection, centerX, centerY)
}

internal fun String?.expectedComplaintClass(): Int? {
    val normalized = this?.lowercase().orEmpty()
    return when {
        "bike" in normalized && "lane" in normalized -> COMPLAINT_CLASS_BLOCKED_BIKE_LANE
        "crosswalk" in normalized -> COMPLAINT_CLASS_BLOCKED_CROSSWALK
        else -> null
    }
}
