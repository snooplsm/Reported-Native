package com.reported.nativeandroid.app

import com.reported.nativeandroid.di.AppGraph
import com.reported.nativeandroid.media.ParseMediaUploader
import com.reported.shared.model.PlatePatternClassifier
import com.reported.shared.model.SubmitReportMediaFile

internal suspend fun uploadMediaForSubmission(
    snapshot: ComposerUiState,
    submittedMedia: List<SubmissionMedia>,
    onProgress: (Float, String) -> Unit
): List<SubmitReportMediaFile> =
    if (snapshot.isPhiladelphiaSubmission()) {
        uploadPhiladelphiaMedia(submittedMedia, onProgress)
    } else {
        uploadParseMedia(submittedMedia, "Uploading media", onProgress)
    }

private suspend fun uploadPhiladelphiaMedia(
    submittedMedia: List<SubmissionMedia>,
    onProgress: (Float, String) -> Unit
): List<SubmitReportMediaFile> {
    require(submittedMedia.none { it.isVideo }) { PhiladelphiaSubmissionVideoMessage }
    require(submittedMedia.size <= PhiladelphiaSubmissionMediaCount) { PhiladelphiaSubmissionMediaMessage }
    return uploadParseMedia(submittedMedia, "Uploading Philadelphia photos", onProgress)
}

private suspend fun uploadParseMedia(
    submittedMedia: List<SubmissionMedia>,
    messagePrefix: String,
    onProgress: (Float, String) -> Unit
): List<SubmitReportMediaFile> =
    ParseMediaUploader.uploadAll(
        AppGraph.applicationContext,
        submittedMedia
    ) { progress ->
        val totalFiles = progress.totalFiles.coerceAtLeast(1)
        val overall = ((progress.currentFileIndex.toFloat() + progress.fraction) / totalFiles.toFloat())
            .coerceIn(0f, 1f)
        onProgress(
            overall * 0.82f,
            "$messagePrefix (${(progress.fraction * 100).toInt()}%)"
        )
    }

internal fun validateComposerSubmission(state: ComposerUiState): ComposerValidationErrors =
    ComposerValidationErrors(
        media = when {
            state.primaryMedia == null -> "Add at least one photo or video."
            state.isPhiladelphiaSubmission() && state.mediaItems().any { it.isVideo } -> PhiladelphiaSubmissionVideoMessage
            state.isPhiladelphiaSubmission() && state.mediaItems().size > PhiladelphiaSubmissionMediaCount -> PhiladelphiaSubmissionMediaMessage
            state.mediaItems().size > MaxSubmissionMediaCount -> MaxSubmissionMediaMessage
            state.mediaItems().count { it.isVideo } > MaxSubmissionVideoCount -> MaxSubmissionVideoMessage
            else -> null
        },
        complaint = if (state.selectedComplaintId.isNullOrBlank()) "Choose a complaint type." else null,
        plate = when {
            state.plate.isBlank() -> "Enter the license plate."
            !PlatePatternClassifier.isValidForSubmission(state.plate) -> "License plate must be ${PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH} characters or fewer."
            else -> null
        },
        plateRegion = if (state.plateRegion.isBlank()) "Choose a state." else null,
        address = if (state.addressQuery.isBlank() && state.address.isBlank()) "Enter or choose an address." else null,
        occurredAt = if (state.occurredAtIso.isBlank()) "Choose when this happened." else null
    )
