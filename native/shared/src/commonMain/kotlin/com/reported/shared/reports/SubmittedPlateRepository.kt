package com.reported.shared.reports

import com.reported.shared.model.ReportSummary
import com.reported.shared.model.SubmitReportCommand
import com.reported.shared.session.platformSettings

class DuplicateReportException(
    val plate: String,
    val plateRegion: String
) : IllegalStateException("A report for $plateRegion $plate has already been submitted.")

class SubmittedPlateRepository {
    private val settings = platformSettings()
    private val key = "reported.submitted.plate.keys"

    suspend fun contains(plate: String, plateRegion: String): Boolean =
        normalizedPlateKey(plate, plateRegion) in readKeys()

    suspend fun add(command: SubmitReportCommand) {
        val normalized = normalizedPlateKey(command.plate, command.plateRegion)
        settings.putString(key, (readKeys() + normalized).sorted().joinToString("\n"))
    }

    suspend fun remove(report: ReportSummary) {
        remove(report.plate, report.plateRegion)
    }

    suspend fun remove(plate: String, plateRegion: String) {
        val normalized = normalizedPlateKey(plate, plateRegion)
        settings.putString(key, (readKeys() - normalized).sorted().joinToString("\n"))
    }

    private fun readKeys(): Set<String> =
        settings.getStringOrNull(key)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
}

internal fun normalizedPlateKey(plate: String, plateRegion: String): String {
    val normalizedPlate = plate.uppercase().filter { it.isLetterOrDigit() }
    val normalizedRegion = plateRegion.uppercase().filter { it.isLetterOrDigit() }.ifBlank { "UNK" }
    return "$normalizedRegion:$normalizedPlate"
}
