package com.reported.nativeandroid.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ComposerSubmissionCompletionTest {
    @Test fun mockedSuccessClearsReportAndStopsSpinnerTwice() = runBlocking {
        var state = ComposerUiState()
        var storedDraft: String? = null
        var clearCount = 0
        val events = mutableListOf<ComposerEvent.ReportSubmitted>()
        repeat(2) { index ->
            val media = SubmissionMedia("file:///test.jpg", "test.jpg", "image/jpeg", false)
            state = state.copy(
                stage = SubmissionStage.VERIFY,
                primaryMedia = media, extraMedia = listOf(media.copy(uri = "file:///extra.jpg")),
                plate = "ABC1234", address = "123 Test Street", addressQuery = "123 Test Street",
                description = "Blocked lane", notes = "Test notes", selectedComplaintIds = listOf("test"),
                latitude = 40.7, longitude = -74.0, occurredAtIso = "2026-10-12T12:00:00Z",
                submitting = true, submitProgress = 0.96f, submitMessage = "Cleaning up"
            )
            storedDraft = state.plate
            val mockedSubmit: suspend () -> String = { "report-$index" }
            completeComposerSubmission(mockedSubmit(), emptyList(), false, clearDraft = {
                storedDraft = null
                clearCount++
            }) { freshState, event ->
                state = freshState
                events += event
            }
            assertNull(storedDraft)
            // Whole-state equality also catches future report fields left behind.
            assertEquals(ComposerUiState(draftLoaded = true, showComplaintImages = false, infoMessage = "Report submitted."), state)
            assertFalse(state.submitting)
            assertNull(state.submitProgress)
            assertNull(state.submitMessage)
            assertTrue(state.draftLoaded)
            assertNull(state.primaryMedia)
            assertTrue(state.extraMedia.isEmpty())
        }
        assertEquals(2, clearCount)
        assertEquals(listOf(ComposerEvent.ReportSubmitted("report-0"), ComposerEvent.ReportSubmitted("report-1")), events)
    }
}
