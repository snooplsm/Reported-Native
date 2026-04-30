package com.reported.nativeandroid

import android.app.Application
import com.reported.nativeandroid.analytics.ReportedAnalytics
import com.google.firebase.FirebaseApp
import com.reported.nativeandroid.di.AppGraph
import com.reported.nativeandroid.media.DetectedInfractionNotifications
import com.reported.nativeandroid.media.MediaScannerScheduler
import com.reported.nativeandroid.media.MediaScannerSettings
import com.reported.nativeandroid.remoteconfig.ReportedRemoteConfig

class ReportedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(this)
        FirebaseApp.initializeApp(this)
        ReportedRemoteConfig.initialize()
        ReportedAnalytics.initialize(this)
        ReportedAnalytics.logAppOpen()
        DetectedInfractionNotifications.ensureChannel(this)
        if (MediaScannerSettings.isEnabled(this)) {
            MediaScannerScheduler.schedule(this)
        }
    }
}
