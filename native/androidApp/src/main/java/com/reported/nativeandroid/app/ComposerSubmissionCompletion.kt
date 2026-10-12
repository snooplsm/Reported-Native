package com.reported.nativeandroid.app

import com.reported.shared.model.ComplaintCategory

internal suspend fun completeComposerSubmission(
    objectId: String,
    complaintCategories: List<ComplaintCategory>,
    showComplaintImages: Boolean,
    clearDraft: suspend () -> Unit,
    publish: (ComposerUiState, ComposerEvent.ReportSubmitted) -> Unit
) {
    clearDraft()
    publish(
        ComposerUiState(
            complaintCategories = complaintCategories,
            showComplaintImages = showComplaintImages,
            infoMessage = "Report submitted.",
            draftLoaded = true
        ),
        ComposerEvent.ReportSubmitted(objectId)
    )
}
