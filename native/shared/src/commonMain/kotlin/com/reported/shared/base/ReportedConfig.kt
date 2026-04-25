package com.reported.shared.base

data class ParseConfig(
    val serverUrl: String,
    val applicationId: String,
    val javascriptKey: String
)

data class ReportedConfig(
    val environment: AppEnvironment = AppEnvironment.Production,
    val apiBaseUrl: String = environment.baseUrl,
    val parse: ParseConfig = defaultParseConfig()
)

private fun defaultParseConfig(): ParseConfig = ParseConfig(
    serverUrl = "https://parseapi.back4app.com",
    applicationId = "jkAZF8ojV4vOGnhSBjdwiMWBKpWML5tM4SWGKgOV",
    javascriptKey = "LeBKOerWTXGBGRLE0yvg2bXa5RRv4e8PuC6INEFA"
)
