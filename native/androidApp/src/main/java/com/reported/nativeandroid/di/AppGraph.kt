package com.reported.nativeandroid.di

import android.content.Context
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.analytics.FirebaseVehicleEnrichmentTracker
import com.reported.shared.ReportedShared
import com.reported.shared.base.AppEnvironment
import com.reported.shared.base.ParseConfig
import com.reported.shared.base.ReportedConfig

object AppGraph {
    lateinit var applicationContext: Context
        private set

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    val shared by lazy {
        ReportedShared(
            ReportedConfig(
                environment = if (BuildConfig.DEBUG) AppEnvironment.Development else AppEnvironment.Production,
                parse = ParseConfig(
                    serverUrl = BuildConfig.PARSE_SERVER_URL,
                    applicationId = BuildConfig.PARSE_APPLICATION_ID,
                    javascriptKey = BuildConfig.PARSE_JAVASCRIPT_KEY
                ),
                operatingSystem = "native-android"
            ),
            vehicleEnrichmentTracker = FirebaseVehicleEnrichmentTracker(),
            vehicleEnrichmentPolicy = AndroidVehicleEnrichmentPolicy(applicationContext)
        )
    }
}
