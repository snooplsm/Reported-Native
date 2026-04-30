package com.reported.nativeandroid.media

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object MediaScannerScheduler {
    private const val PERIODIC_WORK = "reported.media.scanner.periodic"
    private const val ONE_TIME_WORK = "reported.media.scanner.once"
    private const val TAG = "ReportedMediaScanner"

    fun schedule(context: Context) {
        Log.d(TAG, "Scheduling periodic media scanner and immediate scan")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<MediaScannerWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
        scanNow(context)
    }

    fun scanNow(context: Context) {
        Log.d(TAG, "Enqueuing one-time media scan")
        val request = OneTimeWorkRequestBuilder<MediaScannerWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ONE_TIME_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        Log.d(TAG, "Cancelling media scanner work")
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(ONE_TIME_WORK)
    }
}
