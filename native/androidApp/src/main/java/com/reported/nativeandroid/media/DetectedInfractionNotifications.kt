package com.reported.nativeandroid.media

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reported.nativeandroid.MainActivity
import com.reported.nativeandroid.R

object DetectedInfractionNotifications {
    const val CHANNEL_ID = "reported.detected.infractions"
    const val ACTION_YES = "com.reported.nativeandroid.media.ACTION_YES"
    const val ACTION_NO = "com.reported.nativeandroid.media.ACTION_NO"
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
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = "Reported infraction detected"
        val text = buildString {
            append("@ ")
            append(candidate.occurredAtIso.toDetectedNotificationTime())
            if (candidate.address.isNotBlank()) {
                append(" @ ")
                append(candidate.address)
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n${candidate.plate} ${candidate.plateRegion}"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(editIntent(context, candidate.id))
            .addAction(0, "Yes", actionIntent(context, ACTION_YES, candidate.id, 1))
            .addAction(0, "No", actionIntent(context, ACTION_NO, candidate.id, 2))
            .addAction(0, "Edit", editIntent(context, candidate.id))
            .build()

        NotificationManagerCompat.from(context).notify(candidate.id.hashCode(), notification)
    }

    fun showNeedsEdit(context: Context, candidateId: String, reason: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Review detected report")
            .setContentText(reason)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(editIntent(context, candidateId))
            .addAction(0, "Edit", editIntent(context, candidateId))
            .build()
        NotificationManagerCompat.from(context).notify(candidateId.hashCode(), notification)
    }

    fun cancel(context: Context, candidateId: String) {
        NotificationManagerCompat.from(context).cancel(candidateId.hashCode())
    }

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
}

private fun String.toDetectedNotificationTime(): String =
    runCatching {
        val instant = java.time.Instant.parse(this)
        java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault(this)
