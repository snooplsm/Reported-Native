package com.reported.nativeandroid.live

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.reported.shared.model.DraftMedia
import com.reported.shared.model.DraftPlateCandidate
import com.reported.shared.model.ReportDraft
import com.reported.shared.model.Catalogs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LiveIncidentRecord(
    val id: Long = 0L,
    val createdAtEpochMs: Long,
    val incidentAtIso: String,
    val complaintId: String,
    val plate: String,
    val plateRegion: String,
    val plateConfidence: Float,
    val stateConfidence: Float?,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val videoUri: String? = null,
    val thumbnailUri: String? = null,
    val submitted: Boolean = false
) {
    val complaintName: String
        get() = Catalogs.complaintCategories.firstOrNull { it.id == complaintId }?.name ?: "Complaint"

    val displayIncidentTime: String
        get() = runCatching {
            Instant.parse(incidentAtIso)
                .atZone(ZoneId.systemDefault())
                .format(LiveIncidentTimeFormatter)
        }.getOrDefault("Now")

    fun toDraft(): ReportDraft = ReportDraft(
        plate = plate,
        plateRegion = plateRegion,
        address = address,
        complaintIds = listOf(complaintId),
        occurredAtIso = incidentAtIso,
        selectedComplaintId = complaintId,
        stage = "VERIFY",
        primaryMedia = videoUri?.let {
            DraftMedia(
                uri = it,
                displayName = "Live incident clip",
                mimeType = "video/mp4",
                isVideo = true
            )
        },
        latitude = latitude,
        longitude = longitude,
        plateCandidates = listOf(
            DraftPlateCandidate(
                plate = plate,
                confidence = plateConfidence,
                state = plateRegion,
                stateConfidence = stateConfidence,
                thumbnailUri = thumbnailUri
            )
        ),
        selectedPlateCandidate = plate
    )
}

private val LiveIncidentTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

class LiveIncidentStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "reported_live_incidents.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE live_incidents (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at_epoch_ms INTEGER NOT NULL,
                incident_at_iso TEXT NOT NULL,
                complaint_id TEXT NOT NULL,
                plate TEXT NOT NULL,
                plate_region TEXT NOT NULL,
                plate_confidence REAL NOT NULL,
                state_confidence REAL,
                address TEXT NOT NULL,
                latitude REAL,
                longitude REAL,
                video_uri TEXT,
                thumbnail_uri TEXT,
                submitted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX live_incidents_created_idx ON live_incidents(created_at_epoch_ms DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS live_incidents")
        onCreate(db)
    }

    fun insert(record: LiveIncidentRecord): Long {
        val values = ContentValues().apply {
            put("created_at_epoch_ms", record.createdAtEpochMs)
            put("incident_at_iso", record.incidentAtIso)
            put("complaint_id", record.complaintId)
            put("plate", record.plate)
            put("plate_region", record.plateRegion)
            put("plate_confidence", record.plateConfidence)
            put("state_confidence", record.stateConfidence)
            put("address", record.address)
            put("latitude", record.latitude)
            put("longitude", record.longitude)
            put("video_uri", record.videoUri)
            put("thumbnail_uri", record.thumbnailUri)
            put("submitted", if (record.submitted) 1 else 0)
        }
        return writableDatabase.insert("live_incidents", null, values)
    }

    fun delete(id: Long): Int =
        writableDatabase.delete("live_incidents", "id = ?", arrayOf(id.toString()))

    fun update(record: LiveIncidentRecord): Int {
        val values = ContentValues().apply {
            put("incident_at_iso", record.incidentAtIso)
            put("complaint_id", record.complaintId)
            put("plate", record.plate)
            put("plate_region", record.plateRegion)
            put("address", record.address)
            put("latitude", record.latitude)
            put("longitude", record.longitude)
        }
        return writableDatabase.update("live_incidents", values, "id = ?", arrayOf(record.id.toString()))
    }

    fun load(id: Long): LiveIncidentRecord? =
        query(selection = "id = ?", selectionArgs = arrayOf(id.toString()), limitOffset = "1").firstOrNull()

    fun all(): List<LiveIncidentRecord> =
        query(selection = null, selectionArgs = null, limitOffset = null)

    fun page(limit: Int, offset: Int): List<LiveIncidentRecord> {
        return query(
            selection = null,
            selectionArgs = null,
            limitOffset = "${limit.coerceAtLeast(1)} OFFSET ${offset.coerceAtLeast(0)}"
        )
    }

    private fun query(
        selection: String?,
        selectionArgs: Array<String>?,
        limitOffset: String?
    ): List<LiveIncidentRecord> {
        val rows = mutableListOf<LiveIncidentRecord>()
        readableDatabase.query(
            "live_incidents",
            null,
            selection,
            selectionArgs,
            null,
            null,
            "created_at_epoch_ms DESC",
            limitOffset
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += LiveIncidentRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    createdAtEpochMs = cursor.getLong(cursor.getColumnIndexOrThrow("created_at_epoch_ms")),
                    incidentAtIso = cursor.getString(cursor.getColumnIndexOrThrow("incident_at_iso")),
                    complaintId = cursor.getString(cursor.getColumnIndexOrThrow("complaint_id")),
                    plate = cursor.getString(cursor.getColumnIndexOrThrow("plate")),
                    plateRegion = cursor.getString(cursor.getColumnIndexOrThrow("plate_region")),
                    plateConfidence = cursor.getFloat(cursor.getColumnIndexOrThrow("plate_confidence")),
                    stateConfidence = cursor.getNullableFloat("state_confidence"),
                    address = cursor.getString(cursor.getColumnIndexOrThrow("address")),
                    latitude = cursor.getNullableDouble("latitude"),
                    longitude = cursor.getNullableDouble("longitude"),
                    videoUri = cursor.getNullableString("video_uri"),
                    thumbnailUri = cursor.getNullableString("thumbnail_uri"),
                    submitted = cursor.getInt(cursor.getColumnIndexOrThrow("submitted")) == 1
                )
            }
        }
        return rows
    }
}

private fun android.database.Cursor.getNullableString(name: String): String? {
    val index = getColumnIndexOrThrow(name)
    return if (isNull(index)) null else getString(index)
}

private fun android.database.Cursor.getNullableFloat(name: String): Float? {
    val index = getColumnIndexOrThrow(name)
    return if (isNull(index)) null else getFloat(index)
}

private fun android.database.Cursor.getNullableDouble(name: String): Double? {
    val index = getColumnIndexOrThrow(name)
    return if (isNull(index)) null else getDouble(index)
}
