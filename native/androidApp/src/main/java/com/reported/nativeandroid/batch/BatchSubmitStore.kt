package com.reported.nativeandroid.batch

import android.content.Context
import android.net.Uri
import com.reported.nativeandroid.app.SubmissionMedia
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class BatchQueuedReport(
    val id: String,
    val plate: String,
    val plateRegion: String,
    val address: String,
    val description: String = "",
    val notes: String = "",
    val complaintId: String,
    val occurredAtIso: String,
    val latitude: Double?,
    val longitude: Double?,
    val media: List<SubmissionMedia>,
    val submittedContentHashes: List<String> = emptyList()
)

object BatchSubmitStore {
    private const val PREFS = "reported.batch.submit"
    private const val KEY_PREFIX = "report."
    private const val KEY_SUBMITTED_AUTO_REPORT_CONTENT_HASHES = "submitted_auto_report_content_hashes"

    fun save(context: Context, report: BatchQueuedReport) {
        prefs(context).edit()
            .putString(KEY_PREFIX + report.id, report.toJson().toString())
            .apply()
    }

    fun load(context: Context, id: String): BatchQueuedReport? {
        val raw = prefs(context).getString(KEY_PREFIX + id, null) ?: return null
        return runCatching { JSONObject(raw).toBatchQueuedReport() }.getOrNull()
    }

    fun remove(context: Context, id: String) {
        prefs(context).edit().remove(KEY_PREFIX + id).apply()
    }

    fun hasSubmittedAutoReportContentHash(context: Context, hash: String): Boolean =
        prefs(context)
            .getStringSet(KEY_SUBMITTED_AUTO_REPORT_CONTENT_HASHES, emptySet())
            .orEmpty()
            .contains(hash)

    fun markSubmittedAutoReportContentHashes(context: Context, hashes: List<String>) {
        val cleanHashes = hashes.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanHashes.isEmpty()) return
        val stored = prefs(context)
            .getStringSet(KEY_SUBMITTED_AUTO_REPORT_CONTENT_HASHES, emptySet())
            .orEmpty()
            .toMutableSet()
        stored += cleanHashes
        prefs(context).edit()
            .putStringSet(KEY_SUBMITTED_AUTO_REPORT_CONTENT_HASHES, stored.toList().takeLast(2_000).toSet())
            .apply()
    }

    fun markSubmittedAutoReportMedia(context: Context, media: List<SubmissionMedia>) {
        markSubmittedAutoReportContentHashes(context, media.mapNotNull { contentHash(context, it) })
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun contentHash(context: Context, media: SubmissionMedia): String? =
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(Uri.parse(media.uri))?.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            } ?: return null
            digest.digest().joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }.getOrNull()
}

private fun BatchQueuedReport.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("plate", plate)
    .put("plateRegion", plateRegion)
    .put("address", address)
    .put("description", description)
    .put("notes", notes)
    .put("complaintId", complaintId)
    .put("occurredAtIso", occurredAtIso)
    .put("latitude", latitude)
    .put("longitude", longitude)
    .put("submittedContentHashes", JSONArray().apply {
        submittedContentHashes.forEach { put(it) }
    })
    .put("media", JSONArray().apply {
        media.forEach { item ->
            put(
                JSONObject()
                    .put("uri", item.uri)
                    .put("displayName", item.displayName)
                    .put("mimeType", item.mimeType)
                    .put("isVideo", item.isVideo)
                    .put("sourceUri", item.sourceUri)
            )
        }
    })

private fun JSONObject.toBatchQueuedReport(): BatchQueuedReport =
    BatchQueuedReport(
        id = getString("id"),
        plate = getString("plate"),
        plateRegion = getString("plateRegion"),
        address = getString("address"),
        description = optString("description"),
        notes = optString("notes"),
        complaintId = getString("complaintId"),
        occurredAtIso = getString("occurredAtIso"),
        latitude = optNullableDouble("latitude"),
        longitude = optNullableDouble("longitude"),
        media = optJSONArray("media").toSubmissionMedia(),
        submittedContentHashes = optJSONArray("submittedContentHashes").toStringList()
    )

private fun JSONArray?.toSubmissionMedia(): List<SubmissionMedia> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                SubmissionMedia(
                    uri = item.optString("uri"),
                    displayName = item.optString("displayName"),
                    mimeType = item.optString("mimeType"),
                    isVideo = item.optBoolean("isVideo"),
                    sourceUri = item.optNullableString("sourceUri")
                )
            )
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) add(value)
        }
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name) || !has(name)) null else optString(name)

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (isNull(name) || !has(name)) null else optDouble(name)
