package com.reported.shared.session

import com.reported.shared.base.AppEnvironment
import com.reported.shared.model.AppThemeMode

class AppThemePreferencesRepository {
    private val settings = platformSettings()
    private val key = "reported.theme.mode"

    suspend fun read(environment: AppEnvironment): AppThemeMode {
        val stored = settings.getStringOrNull(key)
        return stored
            ?.let { raw -> AppThemeMode.entries.firstOrNull { it.name == raw } }
            ?: defaultFor(environment)
    }

    suspend fun write(mode: AppThemeMode) {
        settings.putString(key, mode.name)
    }

    private fun defaultFor(environment: AppEnvironment): AppThemeMode =
        if (environment == AppEnvironment.Development) AppThemeMode.LIGHT else AppThemeMode.SYSTEM
}
