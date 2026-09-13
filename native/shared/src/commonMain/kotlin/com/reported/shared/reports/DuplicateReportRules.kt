package com.reported.shared.reports

import com.reported.shared.model.ReportSummary
import com.reported.shared.model.SubmitReportCommand
import kotlin.time.Instant
import kotlin.math.abs

private const val duplicateIncidentWindowMillis = 60 * 60 * 1000L

internal fun isDuplicateSubmission(
    existingReport: ReportSummary,
    command: SubmitReportCommand,
    normalizedCommandPlateKey: String = normalizedPlateKey(command.plate, command.plateRegion)
): Boolean {
    if (normalizedPlateKey(existingReport.plate, existingReport.plateRegion) != normalizedCommandPlateKey) {
        return false
    }
    return isSameDuplicateIncidentWindow(existingReport.incidentAt, command.timeOfIncidentIso)
}

internal fun isDuplicateSubmittedReport(
    submittedReport: SubmittedReportRecord,
    command: SubmitReportCommand,
    normalizedCommandPlateKey: String = normalizedPlateKey(command.plate, command.plateRegion)
): Boolean {
    if (submittedReport.plateKey != normalizedCommandPlateKey) {
        return false
    }
    return isSameDuplicateIncidentWindow(submittedReport.incidentAtIso, command.timeOfIncidentIso)
}

internal fun isSameDuplicateIncidentWindow(leftIso: String?, rightIso: String?): Boolean {
    val left = parseDuplicateInstant(leftIso) ?: return false
    val right = parseDuplicateInstant(rightIso) ?: return false
    return abs(left.toEpochMilliseconds() - right.toEpochMilliseconds()) <= duplicateIncidentWindowMillis
}

internal fun normalizedDuplicateIncidentIso(value: String?): String? =
    parseDuplicateInstant(value)?.toString()

private fun parseDuplicateInstant(value: String?): Instant? =
    value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
