package com.reported.shared.base

data class ParseConfig(
    val serverUrl: String,
    val applicationId: String,
    val javascriptKey: String
)

data class ReportedConfig(
    val environment: AppEnvironment = AppEnvironment.Production,
    val apiBaseUrl: String = environment.baseUrl,
    val parse: ParseConfig,
    val operatingSystem: String = "native-kmp"
)
