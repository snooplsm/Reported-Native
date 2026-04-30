package com.reported.shared.session

import com.reported.shared.model.UserSession
import kotlinx.serialization.json.Json

class SettingsSessionStore(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SessionStore {
    private val settings = platformSettings()
    private val key = "reported.session"

    override suspend fun read(): UserSession? {
        val raw = settings.getStringOrNull(key) ?: return null
        return runCatching { json.decodeFromString<UserSession>(raw) }.getOrNull()
    }

    override suspend fun write(session: UserSession) {
        settings.putString(key, json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun clear() {
        settings.remove(key)
    }
}

