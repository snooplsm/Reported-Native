package com.reported.nativeandroid.batch

import android.content.Context
import com.reported.nativeandroid.app.SubmissionMedia
import org.json.JSONArray
import org.json.JSONObject

data class BatchQueuedReport(
    val id: String,
    val plate: String,
    val plateRegion: String,
    val address: String,
    val complaintId: String,
    val occurredAtIso: String,
    val latitude: Double?,
    val longitude: Double?,
    val media: List<SubmissionMedia>
)

object BatchSubmitStore {
    private const val PREFS = "reported.batch.submit"
    private const val KEY_PREFIX = "report."

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

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

private fun BatchQueuedReport.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("plate", plate)
    .put("plateRegion", plateRegion)
    .put("address", address)
    .put("complaintId", complaintId)
    .put("occurredAtIso", occurredAtIso)
    .put("latitude", latitude)
    .put("longitude", longitude)
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
        complaintId = getString("complaintId"),
        occurredAtIso = getString("occurredAtIso"),
        latitude = optNullableDouble("latitude"),
        longitude = optNullableDouble("longitude"),
        media = optJSONArray("media").toSubmissionMedia()
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

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name) || !has(name)) null else optString(name)

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (isNull(name) || !has(name)) null else optDouble(name)
