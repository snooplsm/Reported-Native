package com.reported.shared.reports

import com.reported.shared.model.ReportSummary
import com.reported.shared.model.SubmitReportCommand
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportUseCasesTest {
    @Test
    fun duplicateSubmissionRequiresMatchingPlateStateAndIncidentTime() {
        val existing = reportSummary(
            plate = "TEST",
            plateRegion = "NY",
            incidentAt = "2026-06-09T15:00:00Z"
        )
        val command = submitCommand(
            plate = "TEST",
            plateRegion = "NY",
            timeOfIncidentIso = "2026-06-09T15:10:00Z"
        )

        assertTrue(isDuplicateSubmission(existing, command))
    }

    @Test
    fun duplicateSubmissionAllowsSamePlateAtDifferentIncidentTime() {
        val existing = reportSummary(
            plate = "TEST",
            plateRegion = "NY",
            incidentAt = "2026-06-09T15:00:00Z"
        )
        val command = submitCommand(
            plate = "TEST",
            plateRegion = "NY",
            timeOfIncidentIso = "2026-06-10T15:00:00Z"
        )

        assertFalse(isDuplicateSubmission(existing, command))
    }

    @Test
    fun duplicateSubmissionAllowsSamePlateOutsideOneHourWindow() {
        val existing = reportSummary(
            plate = "TEST",
            plateRegion = "NY",
            incidentAt = "2026-06-09T15:00:00Z"
        )
        val command = submitCommand(
            plate = "TEST",
            plateRegion = "NY",
            timeOfIncidentIso = "2026-06-09T16:01:00Z"
        )

        assertFalse(isDuplicateSubmission(existing, command))
    }

    @Test
    fun duplicateSubmissionBlocksSamePlateInsideOneHourWindow() {
        val existing = reportSummary(
            plate = "TEST",
            plateRegion = "NY",
            incidentAt = "2026-06-09T15:00:00Z"
        )
        val command = submitCommand(
            plate = "TEST",
            plateRegion = "NY",
            timeOfIncidentIso = "2026-06-09T15:59:00Z"
        )

        assertTrue(isDuplicateSubmission(existing, command))
    }

    @Test
    fun persistedSubmittedReportUsesSameDuplicateWindow() {
        val submittedReport = SubmittedReportRecord(
            objectId = "abc123",
            plate = "TEST",
            plateRegion = "NY",
            plateKey = "NY:TEST",
            incidentAtIso = "2026-06-09T15:00:00Z",
            address = "10 Hanover Street",
            complaintIds = listOf("Z8vjWz8uYr"),
            mediaCount = 1,
            submittedAtIso = "2026-06-09T15:05:00Z"
        )

        assertTrue(
            isDuplicateSubmittedReport(
                submittedReport,
                submitCommand(
                    plate = "TEST",
                    plateRegion = "NY",
                    timeOfIncidentIso = "2026-06-09T15:59:00Z"
                )
            )
        )
        assertFalse(
            isDuplicateSubmittedReport(
                submittedReport,
                submitCommand(
                    plate = "TEST",
                    plateRegion = "NY",
                    timeOfIncidentIso = "2026-06-09T16:01:00Z"
                )
            )
        )
    }

    @Test
    fun duplicateSubmissionAllowsSamePlateWhenIncidentTimeIsMissing() {
        val existing = reportSummary(
            plate = "TEST",
            plateRegion = "NY",
            incidentAt = ""
        )
        val command = submitCommand(
            plate = "TEST",
            plateRegion = "NY",
            timeOfIncidentIso = "2026-06-09T15:00:00Z"
        )

        assertFalse(isDuplicateSubmission(existing, command))
    }

    @Test
    fun duplicateSubmissionAllowsDifferentStateAtSameIncidentTime() {
        val existing = reportSummary(
            plate = "TEST",
            plateRegion = "NJ",
            incidentAt = "2026-06-09T15:00:00Z"
        )
        val command = submitCommand(
            plate = "TEST",
            plateRegion = "NY",
            timeOfIncidentIso = "2026-06-09T15:00:00Z"
        )

        assertFalse(isDuplicateSubmission(existing, command))
    }

    private fun reportSummary(
        plate: String,
        plateRegion: String,
        incidentAt: String
    ): ReportSummary = ReportSummary(
        id = 0,
        addressId = null,
        incidentAt = incidentAt,
        description = "",
        notes = "",
        plate = plate,
        plateRegion = plateRegion,
        status = "",
        street = ""
    )

    private fun submitCommand(
        plate: String,
        plateRegion: String,
        timeOfIncidentIso: String?
    ): SubmitReportCommand = SubmitReportCommand(
        plate = plate,
        plateRegion = plateRegion,
        description = "",
        notes = "",
        address = "",
        complaintIds = emptyList(),
        timeOfIncidentIso = timeOfIncidentIso
    )
}
