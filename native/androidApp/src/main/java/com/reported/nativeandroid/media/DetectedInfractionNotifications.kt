package com.reported.nativeandroid.media

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.MainActivity
import com.reported.nativeandroid.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object DetectedInfractionNotifications {
    const val CHANNEL_ID = "reported.detected.infractions"
    const val ACTION_SUBMIT_NOW = "com.reported.nativeandroid.media.ACTION_SUBMIT_NOW"
    const val ACTION_CANCEL = "com.reported.nativeandroid.media.ACTION_CANCEL"
    const val ACTION_EDIT = "com.reported.nativeandroid.media.ACTION_EDIT"
    const val EXTRA_CANDIDATE_ID = "candidate_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Detected infractions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "License plate and complaint detections from new photos."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showDetected(context: Context, candidate: DetectedInfraction) {
        ensureChannel(context)
        if (!MediaScannerSettings.isNotificationsEnabled(context)) {
            Log.d(TAG, "Not showing detection notification: scanner notifications are disabled")
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Not showing detection notification: POST_NOTIFICATIONS is not granted")
            return
        }

        val complaintLabel = candidate.complaintId.toNotificationComplaintLabel()
        val title = "$complaintLabel detected"
        val incidentTime = candidate.occurredAtIso.toDetectedNotificationTime()
        val text = buildString {
            append("Incident time: ")
            append(incidentTime)
            if (candidate.address.isNotBlank()) {
                append(" • ")
                append(candidate.address)
            }
        }
        val detailText = "$complaintLabel\nIncident time: $incidentTime\n${candidate.address}\n${candidate.plate} ${candidate.plateRegion}"
        val bigPicture = buildInfractionNotificationImage(context, candidate)
        val style = if (bigPicture != null) {
            NotificationCompat.BigPictureStyle()
                .bigPicture(bigPicture)
                .setBigContentTitle(title)
                .setSummaryText("Incident time: $incidentTime • ${candidate.plate} ${candidate.plateRegion}")
        } else {
            NotificationCompat.BigTextStyle().bigText(detailText)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reported)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(editIntent(context, candidate.id))
            .addAction(0, "Submit now", actionIntent(context, ACTION_SUBMIT_NOW, candidate.id, 1))
            .addAction(0, "Edit", editIntent(context, candidate.id))
            .addAction(0, "Cancel", actionIntent(context, ACTION_CANCEL, candidate.id, 2))
            .build()

        NotificationManagerCompat.from(context).notify(candidate.id.hashCode(), notification)
        Log.d(
            TAG,
            "Shown detection notification: id=${candidate.id}, complaint=$complaintLabel, plate=${candidate.plate}, state=${candidate.plateRegion}, bigPicture=${bigPicture != null}"
        )
    }

    fun showNeedsEdit(context: Context, candidateId: String, reason: String) {
        ensureChannel(context)
        if (!MediaScannerSettings.isNotificationsEnabled(context)) {
            Log.d(TAG, "Not showing needs-edit notification: scanner notifications are disabled")
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Not showing needs-edit notification: POST_NOTIFICATIONS is not granted")
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reported)
            .setContentTitle("Review detected report")
            .setContentText(reason)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(editIntent(context, candidateId))
            .addAction(0, "Edit", editIntent(context, candidateId))
            .build()
        NotificationManagerCompat.from(context).notify(candidateId.hashCode(), notification)
        Log.d(TAG, "Shown needs-edit notification: id=$candidateId, reason=$reason")
    }

    fun cancel(context: Context, candidateId: String) {
        NotificationManagerCompat.from(context).cancel(candidateId.hashCode())
        Log.d(TAG, "Cancelled detection notification: id=$candidateId")
    }

    private const val TAG = "ReportedMediaScanner"
    private const val NOTIFICATION_IMAGE_WIDTH = 1024
    private const val NOTIFICATION_IMAGE_HEIGHT = 512
    private const val PLATE_CONTEXT_WIDTH_MULTIPLIER = 7f
    private const val PLATE_CONTEXT_HEIGHT_MULTIPLIER = 6f

    private fun editIntent(context: Context, candidateId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_EDIT)
            .putExtra(EXTRA_CANDIDATE_ID, candidateId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            candidateId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(context: Context, action: String, candidateId: String, salt: Int): PendingIntent {
        val intent = Intent(context, MediaScannerNotificationReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_CANDIDATE_ID, candidateId)
        return PendingIntent.getBroadcast(
            context,
            candidateId.hashCode() + salt,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildInfractionNotificationImage(context: Context, infraction: DetectedInfraction): Bitmap? {
        val selectedPlate = infraction.candidates.firstOrNull { it.plate == infraction.plate }
            ?: infraction.candidates.maxByOrNull { it.confidence }

        val previewBitmap = selectedPlate?.videoFramePreviewUri
            ?.let { decodeBitmap(context, it) }
        val sourceBitmap = previewBitmap ?: decodeBitmap(context, infraction.media.uri)
        if (sourceBitmap == null) {
            Log.d(TAG, "Notification big picture unavailable: could not decode source media")
            return null
        }

        val cropped = runCatching {
            centerCropAroundPlate(
                bitmap = sourceBitmap,
                candidate = selectedPlate,
                targetWidth = NOTIFICATION_IMAGE_WIDTH,
                targetHeight = NOTIFICATION_IMAGE_HEIGHT
            )
        }.getOrElse { error ->
            Log.d(TAG, "Notification big picture crop failed: ${error.message}")
            null
        }
        if (sourceBitmap !== previewBitmap && cropped !== sourceBitmap) {
            sourceBitmap.recycle()
        }
        return cropped
    }

    private fun decodeBitmap(context: Context, uriString: String): Bitmap? =
        runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()

    private fun centerCropAroundPlate(
        bitmap: Bitmap,
        candidate: PlateCandidate?,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
        val bounds = candidate?.pixelBounds(bitmap.width, bitmap.height)
        val centerX = bounds?.let { (it.left + it.right) / 2f } ?: (bitmap.width / 2f)
        val centerY = bounds?.let { (it.top + it.bottom) / 2f } ?: (bitmap.height / 2f)
        val plateWidth = bounds?.let { max(1f, it.right - it.left) } ?: (bitmap.width / 3f)
        val plateHeight = bounds?.let { max(1f, it.bottom - it.top) } ?: (bitmap.height / 3f)

        var cropWidth = max(plateWidth * PLATE_CONTEXT_WIDTH_MULTIPLIER, bitmap.width * 0.42f)
        var cropHeight = max(plateHeight * PLATE_CONTEXT_HEIGHT_MULTIPLIER, cropWidth / targetAspect)
        cropWidth = max(cropWidth, cropHeight * targetAspect)
        cropHeight = cropWidth / targetAspect

        if (cropWidth > bitmap.width) {
            cropWidth = bitmap.width.toFloat()
            cropHeight = cropWidth / targetAspect
        }
        if (cropHeight > bitmap.height) {
            cropHeight = bitmap.height.toFloat()
            cropWidth = cropHeight * targetAspect
        }

        val left = (centerX - cropWidth / 2f).coerceIn(0f, max(0f, bitmap.width - cropWidth))
        val top = (centerY - cropHeight / 2f).coerceIn(0f, max(0f, bitmap.height - cropHeight))
        val source = android.graphics.Rect(
            left.roundToInt(),
            top.roundToInt(),
            min(bitmap.width, (left + cropWidth).roundToInt()),
            min(bitmap.height, (top + cropHeight).roundToInt())
        )
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(output).drawBitmap(
            bitmap,
            source,
            android.graphics.Rect(0, 0, targetWidth, targetHeight),
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        )
        return output
    }

    private data class PixelBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    private fun PlateCandidate.pixelBounds(width: Int, height: Int): PixelBounds? {
        if (cornerPoints.size >= 8) {
            val xs = cornerPoints.filterIndexed { index, _ -> index % 2 == 0 }.map { it * width }
            val ys = cornerPoints.filterIndexed { index, _ -> index % 2 == 1 }.map { it * height }
            return PixelBounds(
                left = xs.minOrNull() ?: return null,
                top = ys.minOrNull() ?: return null,
                right = xs.maxOrNull() ?: return null,
                bottom = ys.maxOrNull() ?: return null
            )
        }
        val left = boundsLeft ?: return null
        val top = boundsTop ?: return null
        val right = boundsRight ?: return null
        val bottom = boundsBottom ?: return null
        return PixelBounds(
            left = left * width,
            top = top * height,
            right = right * width,
            bottom = bottom * height
        )
    }
}

private fun String.toDetectedNotificationTime(): String =
    runCatching {
        val instant = java.time.Instant.parse(this)
        java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault(this)

private fun String.toNotificationComplaintLabel(): String =
    when (this) {
        "blocked_bike_lane" -> "Blocked bike lane"
        "blocked_crosswalk" -> "Blocked crosswalk"
        else -> replace("_", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            .ifBlank { "Infraction" }
    }
