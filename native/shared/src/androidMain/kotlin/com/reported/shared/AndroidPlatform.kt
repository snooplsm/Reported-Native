package com.reported.shared

import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun platformEngineFactory(): HttpClientEngineFactory<*> = OkHttp
