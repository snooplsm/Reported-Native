package com.reported.nativeandroid.di

import com.reported.nativeandroid.BuildConfig
import com.reported.shared.ReportedShared
import com.reported.shared.base.AppEnvironment
import com.reported.shared.base.ParseConfig
import com.reported.shared.base.ReportedConfig

object AppGraph {
    val shared by lazy {
        ReportedShared(
            ReportedConfig(
                environment = if (BuildConfig.DEBUG) AppEnvironment.Development else AppEnvironment.Production,
                apiBaseUrl = BuildConfig.API_BASE_URL,
                parse = ParseConfig(
                    serverUrl = BuildConfig.PARSE_SERVER_URL,
                    applicationId = BuildConfig.PARSE_APPLICATION_ID,
                    javascriptKey = BuildConfig.PARSE_JAVASCRIPT_KEY
                )
            )
        )
    }
}
