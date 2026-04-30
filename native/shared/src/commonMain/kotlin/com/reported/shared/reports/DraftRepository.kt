package com.reported.shared.reports

import com.reported.shared.model.ReportDraft
import com.reported.shared.session.platformSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DraftRepository(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val settings = platformSettings()
    private val key = "reported.report.draft"

    suspend fun load(): ReportDraft? {
        val raw = settings.getStringOrNull(key) ?: return null
        return runCatching { json.decodeFromString<ReportDraft>(raw) }.getOrNull()
    }

    suspend fun save(draft: ReportDraft) {
        settings.putString(key, json.encodeToString(draft))
    }

    suspend fun clear() {
        settings.remove(key)
    }
}

