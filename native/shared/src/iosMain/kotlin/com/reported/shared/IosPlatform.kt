package com.reported.shared

import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun platformEngineFactory(): HttpClientEngineFactory<*> = Darwin
