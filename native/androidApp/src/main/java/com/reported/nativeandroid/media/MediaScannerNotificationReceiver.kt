package com.reported.nativeandroid.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class MediaScannerNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val candidateId = intent.getStringExtra(DetectedInfractionNotifications.EXTRA_CANDIDATE_ID) ?: return
        when (intent.action) {
            DetectedInfractionNotifications.ACTION_YES -> {
                val request = OneTimeWorkRequestBuilder<SubmitDetectedInfractionWorker>()
                    .setInputData(
                        Data.Builder()
                            .putString(DetectedInfractionNotifications.EXTRA_CANDIDATE_ID, candidateId)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueue(request)
            }
            DetectedInfractionNotifications.ACTION_NO -> {
                DetectedInfractionStore.remove(context, candidateId)
                DetectedInfractionNotifications.cancel(context, candidateId)
            }
        }
    }
}
