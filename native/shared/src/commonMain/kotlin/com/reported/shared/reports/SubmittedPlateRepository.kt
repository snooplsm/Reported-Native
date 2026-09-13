package com.reported.shared.reports

import com.reported.shared.model.ReportSummary
import com.reported.shared.model.SubmitReportCommand
import com.reported.shared.session.platformSettings
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DuplicateReportException(
    val plate: String,
    val plateRegion: String
) : IllegalStateException("A report for $plateRegion $plate has already been submitted.")

class SubmittedPlateRepository {
    private val settings = platformSettings()
    private val recordsKey = "reported.submitted.reports"
    private val legacyPlateKeysKey = "reported.submitted.plate.keys"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun contains(command: SubmitReportCommand): Boolean {
        val normalized = normalizedPlateKey(command.plate, command.plateRegion)
        return readRecords().any { isDuplicateSubmittedReport(it, command, normalized) }
    }

    suspend fun add(command: SubmitReportCommand, objectId: String = "") {
        val record = SubmittedReportRecord(
            objectId = objectId,
            plate = command.plate,
            plateRegion = command.plateRegion,
            plateKey = normalizedPlateKey(command.plate, command.plateRegion),
            incidentAtIso = normalizedDuplicateIncidentIso(command.timeOfIncidentIso),
            address = command.address,
            complaintIds = command.complaintIds,
            mediaCount = command.mediaFiles.size.takeIf { it > 0 } ?: command.mediaUrls.size,
            submittedAtIso = Clock.System.now().toString()
        )
        writeRecords(readRecords().filterNot { it.samePersistedIdentity(record) } + record)
    }

    suspend fun remove(report: ReportSummary) {
        remove(
            objectId = report.objectId,
            plate = report.plate,
            plateRegion = report.plateRegion,
            incidentAtIso = report.incidentAt
        )
    }

    suspend fun remove(command: SubmitReportCommand) {
        remove(
            objectId = "",
            plate = command.plate,
            plateRegion = command.plateRegion,
            incidentAtIso = command.timeOfIncidentIso
        )
    }

    suspend fun remove(plate: String, plateRegion: String) {
        remove(objectId = "", plate = plate, plateRegion = plateRegion, incidentAtIso = null)
    }

    private fun remove(objectId: String, plate: String, plateRegion: String, incidentAtIso: String?) {
        val normalized = normalizedPlateKey(plate, plateRegion)
        val normalizedIncidentAt = normalizedDuplicateIncidentIso(incidentAtIso)
        val records = readRecords().filterNot { record ->
            (objectId.isNotBlank() && record.objectId == objectId) ||
                record.plateKey == normalized &&
                (normalizedIncidentAt == null || record.incidentAtIso == normalizedIncidentAt)
        }
        writeRecords(records)
    }

    private fun readRecords(): List<SubmittedReportRecord> =
        (readStoredRecords() + readLegacyRecords())
            .filter { it.plateKey.isNotBlank() }
            .distinctBy { it.persistedIdentity() }

    private fun readStoredRecords(): List<SubmittedReportRecord> =
        settings.getStringOrNull(recordsKey)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { value -> runCatching { json.decodeFromString<SubmittedReportRecord>(value) }.getOrNull() }
            ?.toList()
            .orEmpty()

    private fun readLegacyRecords(): List<SubmittedReportRecord> =
        settings.getStringOrNull(legacyPlateKeysKey)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.mapNotNull(::parseLegacySubmittedPlateRecord)
            ?.toList()
            .orEmpty()

    private fun writeRecords(records: List<SubmittedReportRecord>) {
        settings.putString(
            recordsKey,
            records
                .distinctBy { it.persistedIdentity() }
                .sortedWith(compareBy({ it.plateKey }, { it.incidentAtIso.orEmpty() }, { it.objectId }))
                .joinToString("\n") { json.encodeToString(it) }
        )
    }

    private fun parseLegacySubmittedPlateRecord(value: String): SubmittedReportRecord? {
        val parts = value.split("|", limit = 2)
        val plateKey = parts.firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val incidentAtIso = parts.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizedDuplicateIncidentIso)
        val keyParts = plateKey.split(":", limit = 2)
        return SubmittedReportRecord(
            plateRegion = keyParts.getOrNull(0).orEmpty(),
            plate = keyParts.getOrNull(1).orEmpty(),
            plateKey = plateKey,
            incidentAtIso = incidentAtIso
        )
    }
}

@Serializable
internal data class SubmittedReportRecord(
    val schemaVersion: Int = 1,
    val objectId: String = "",
    val plate: String = "",
    val plateRegion: String = "",
    val plateKey: String = "",
    val incidentAtIso: String? = null,
    val address: String = "",
    val complaintIds: List<String> = emptyList(),
    val mediaCount: Int = 0,
    val submittedAtIso: String = ""
) {
    fun persistedIdentity(): String =
        objectId.takeIf { it.isNotBlank() } ?: "$plateKey|${incidentAtIso.orEmpty()}"

    fun samePersistedIdentity(other: SubmittedReportRecord): Boolean =
        (objectId.isNotBlank() && objectId == other.objectId) ||
            plateKey == other.plateKey && incidentAtIso == other.incidentAtIso
}

internal fun normalizedPlateKey(plate: String, plateRegion: String): String {
    val normalizedPlate = plate.uppercase().filter { it.isLetterOrDigit() }
    val normalizedRegion = plateRegion.uppercase().filter { it.isLetterOrDigit() }.ifBlank { "UNK" }
    return "$normalizedRegion:$normalizedPlate"
}
