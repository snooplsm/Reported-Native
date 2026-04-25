package com.reported.nativeandroid.media

import android.content.Context
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.shared.model.DraftMedia
import com.reported.shared.model.DraftPlateCandidate
import com.reported.shared.model.ReportDraft
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class DetectedInfraction(
    val id: String,
    val media: SubmissionMedia,
    val plate: String,
    val plateRegion: String,
    val plateConfidence: Float,
    val stateConfidence: Float,
    val complaintId: String,
    val occurredAtIso: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val candidates: List<PlateCandidate>
) {
    fun toDraft(): ReportDraft = ReportDraft(
        plate = plate,
        plateRegion = plateRegion,
        address = address,
        complaintIds = listOf(complaintId),
        occurredAtIso = occurredAtIso,
        selectedComplaintId = complaintId,
        stage = "VERIFY",
        primaryMedia = DraftMedia(
            uri = media.uri,
            displayName = media.displayName,
            mimeType = media.mimeType,
            isVideo = media.isVideo
        ),
        latitude = latitude,
        longitude = longitude,
        plateCandidates = candidates.map { it.toDraft() },
        selectedPlateCandidate = plate
    )
}

object DetectedInfractionStore {
    private const val PREFS = "reported.detected.infractions"
    private const val KEY_PREFIX = "candidate."

    fun save(context: Context, candidate: DetectedInfraction) {
        prefs(context).edit()
            .putString(KEY_PREFIX + candidate.id, candidate.toJson().toString())
            .apply()
    }

    fun load(context: Context, id: String): DetectedInfraction? {
        val raw = prefs(context).getString(KEY_PREFIX + id, null) ?: return null
        return runCatching { JSONObject(raw).toDetectedInfraction() }.getOrNull()
    }

    fun remove(context: Context, id: String) {
        prefs(context).edit().remove(KEY_PREFIX + id).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

private fun PlateCandidate.toDraft() = DraftPlateCandidate(
    plate = plate,
    confidence = confidence,
    state = state,
    stateConfidence = stateConfidence,
    plateType = plateType,
    plateTypeLabel = plateTypeLabel,
    focalPointX = focalPointX,
    focalPointY = focalPointY,
    boundsLeft = boundsLeft,
    boundsTop = boundsTop,
    boundsRight = boundsRight,
    boundsBottom = boundsBottom,
    rotationDegrees = rotationDegrees,
    cornerPoints = cornerPoints,
    thumbnailUri = thumbnailUri,
    videoFramePreviewUri = videoFramePreviewUri,
    videoFrameTimeMs = videoFrameTimeMs
)

private fun DetectedInfraction.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("media", JSONObject()
        .put("uri", media.uri)
        .put("displayName", media.displayName)
        .put("mimeType", media.mimeType)
        .put("isVideo", media.isVideo)
    )
    .put("plate", plate)
    .put("plateRegion", plateRegion)
    .put("plateConfidence", plateConfidence.toDouble())
    .put("stateConfidence", stateConfidence.toDouble())
    .put("complaintId", complaintId)
    .put("occurredAtIso", occurredAtIso)
    .put("address", address)
    .put("latitude", latitude)
    .put("longitude", longitude)
    .put("candidates", JSONArray().apply {
        candidates.forEach { candidate ->
            put(JSONObject()
                .put("plate", candidate.plate)
                .put("confidence", candidate.confidence.toDouble())
                .put("state", candidate.state)
                .put("stateConfidence", candidate.stateConfidence)
                .put("plateType", candidate.plateType)
                .put("plateTypeLabel", candidate.plateTypeLabel)
                .put("focalPointX", candidate.focalPointX)
                .put("focalPointY", candidate.focalPointY)
                .put("boundsLeft", candidate.boundsLeft)
                .put("boundsTop", candidate.boundsTop)
                .put("boundsRight", candidate.boundsRight)
                .put("boundsBottom", candidate.boundsBottom)
                .put("rotationDegrees", candidate.rotationDegrees.toDouble())
                .put("cornerPoints", JSONArray(candidate.cornerPoints))
                .put("thumbnailUri", candidate.thumbnailUri)
                .put("videoFramePreviewUri", candidate.videoFramePreviewUri)
                .put("videoFrameTimeMs", candidate.videoFrameTimeMs)
            )
        }
    })

private fun JSONObject.toDetectedInfraction(): DetectedInfraction {
    val mediaJson = getJSONObject("media")
    return DetectedInfraction(
        id = getString("id"),
        media = SubmissionMedia(
            uri = mediaJson.getString("uri"),
            displayName = mediaJson.optString("displayName"),
            mimeType = mediaJson.optString("mimeType"),
            isVideo = mediaJson.optBoolean("isVideo")
        ),
        plate = getString("plate"),
        plateRegion = getString("plateRegion"),
        plateConfidence = optDouble("plateConfidence").toFloat(),
        stateConfidence = optDouble("stateConfidence").toFloat(),
        complaintId = getString("complaintId"),
        occurredAtIso = optString("occurredAtIso").ifBlank { Instant.now().toString() },
        address = optString("address"),
        latitude = optNullableDouble("latitude"),
        longitude = optNullableDouble("longitude"),
        candidates = optJSONArray("candidates").toPlateCandidates()
    )
}

private fun JSONArray?.toPlateCandidates(): List<PlateCandidate> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(PlateCandidate(
                plate = item.optString("plate"),
                confidence = item.optDouble("confidence").toFloat(),
                state = item.optNullableString("state"),
                stateConfidence = item.optNullableDouble("stateConfidence")?.toFloat(),
                plateType = item.optNullableString("plateType"),
                plateTypeLabel = item.optNullableString("plateTypeLabel"),
                focalPointX = item.optNullableDouble("focalPointX")?.toFloat(),
                focalPointY = item.optNullableDouble("focalPointY")?.toFloat(),
                boundsLeft = item.optNullableDouble("boundsLeft")?.toFloat(),
                boundsTop = item.optNullableDouble("boundsTop")?.toFloat(),
                boundsRight = item.optNullableDouble("boundsRight")?.toFloat(),
                boundsBottom = item.optNullableDouble("boundsBottom")?.toFloat(),
                rotationDegrees = item.optDouble("rotationDegrees", 0.0).toFloat(),
                cornerPoints = item.optJSONArray("cornerPoints").toFloatList(),
                thumbnailUri = item.optNullableString("thumbnailUri"),
                videoFramePreviewUri = item.optNullableString("videoFramePreviewUri"),
                videoFrameTimeMs = item.optNullableLong("videoFrameTimeMs")
            ))
        }
    }
}

private fun JSONArray?.toFloatList(): List<Float> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            add(optDouble(index).toFloat())
        }
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name)

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (isNull(name) || !has(name)) null else optDouble(name)

private fun JSONObject.optNullableLong(name: String): Long? =
    if (isNull(name) || !has(name)) null else optLong(name)
