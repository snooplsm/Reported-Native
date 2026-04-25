package com.reported.shared.session

class GuestModePreferencesRepository {
    private val settings = platformSettings()
    private val key = "reported.guest.mode"

    suspend fun read(): Boolean = settings.getBoolean(key, false)

    suspend fun write(enabled: Boolean) {
        settings.putBoolean(key, enabled)
    }
}
