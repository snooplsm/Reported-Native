package com.reported.nativeandroid.app

import com.reported.shared.model.AppThemeMode
import com.reported.shared.model.ComplaintCategory
import com.reported.shared.model.ReportSummary
import com.reported.shared.model.UserSession

enum class SubmissionStage {
    PICK_MEDIA,
    VERIFY
}

data class SubmissionMedia(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val sourceUri: String? = null
)

data class PlateCandidate(
    val plate: String,
    val confidence: Float,
    val rawPlateText: String? = null,
    val wasPlateCorrected: Boolean = false,
    val state: String? = null,
    val stateConfidence: Float? = null,
    val plateType: String? = null,
    val plateTypeLabel: String? = null,
    val focalPointX: Float? = null,
    val focalPointY: Float? = null,
    val boundsLeft: Float? = null,
    val boundsTop: Float? = null,
    val boundsRight: Float? = null,
    val boundsBottom: Float? = null,
    val rotationDegrees: Float = 0f,
    val cornerPoints: List<Float> = emptyList(),
    val sourceImageWidth: Int? = null,
    val sourceImageHeight: Int? = null,
    val thumbnailUri: String? = null,
    val videoFramePreviewUri: String? = null,
    val videoFrameTimeMs: Long? = null
)

data class AddressSuggestion(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val region: String? = null
)

data class SessionUiState(
    val loading: Boolean = true,
    val session: UserSession? = null,
    val isGuest: Boolean = false
)

data class SocialAuthProfile(
    val provider: String,
    val providerUserId: String,
    val idToken: String,
    val email: String,
    val firstName: String = "",
    val lastName: String = ""
)

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

data class RegisterUiState(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    val testify: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

data class ReportsUiState(
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val mode: ReportsMode? = null,
    val reports: List<ReportSummary> = emptyList(),
    val hasMore: Boolean = false,
    val nextSkip: Int = 0,
    val reportDetails: Map<String, ReportSummary> = emptyMap(),
    val detailLoadingIds: Set<String> = emptySet(),
    val deletingReportKeys: Set<String> = emptySet(),
    val licenseQuery: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val error: String? = null
)

enum class ReportsMode {
    SEARCH,
    LIST
}

data class ProfileUiState(
    val loading: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val testify: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val error: String? = null,
    val editing: Boolean = false
)

data class ThemeUiState(
    val mode: AppThemeMode = AppThemeMode.SYSTEM
)

data class ComposerValidationErrors(
    val media: String? = null,
    val complaint: String? = null,
    val plate: String? = null,
    val plateRegion: String? = null,
    val address: String? = null,
    val occurredAt: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(media, complaint, plate, plateRegion, address, occurredAt).any { it != null }
}

data class PlateCorrectionPrompt(
    val rawPlate: String,
    val suggestedPlate: String,
    val state: String,
    val label: String
)

data class ComposerUiState(
    val stage: SubmissionStage = SubmissionStage.PICK_MEDIA,
    val selectedComplaintId: String? = null,
    val complaintSheetOpen: Boolean = false,
    val primaryMedia: SubmissionMedia? = null,
    val extraMedia: List<SubmissionMedia> = emptyList(),
    val pendingMediaSelection: SubmissionMedia? = null,
    val awaitingVideoProcessingDecision: Boolean = false,
    val pendingVideoProcessingMedia: SubmissionMedia? = null,
    val detectingPlates: Boolean = false,
    val detectionMessage: String? = null,
    val detectionResultMessage: String? = null,
    val detectionProgress: Float = 0f,
    val detectionFramePreviewUri: String? = null,
    val detectionFrameTimeMs: Long = 0L,
    val detectionVideoDurationMs: Long = 0L,
    val detectionFrameCandidates: List<PlateCandidate> = emptyList(),
    val plateCandidates: List<PlateCandidate> = emptyList(),
    val selectedPlateCandidate: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val addressQuery: String = "",
    val addressSuggestions: List<AddressSuggestion> = emptyList(),
    val lookupInFlight: Boolean = false,
    val plate: String = "",
    val plateRegion: String = "NY",
    val address: String = "",
    val description: String = "",
    val notes: String = "",
    val occurredAtIso: String = "",
    val photoOccurredAtIso: String? = null,
    val complaintCategories: List<ComplaintCategory> = emptyList(),
    val showComplaintImages: Boolean = true,
    val selectedComplaintIds: List<String> = emptyList(),
    val draftLoaded: Boolean = false,
    val submitting: Boolean = false,
    val submitProgress: Float? = null,
    val submitMessage: String? = null,
    val error: String? = null,
    val plateCorrectionPrompt: PlateCorrectionPrompt? = null,
    val keptPlateCorrectionRaw: String? = null,
    val validationErrors: ComposerValidationErrors = ComposerValidationErrors(),
    val infoMessage: String = "Upload a photo or video first, then verify the plate, complaint, time, and NYC address before submitting."
)

sealed interface ComposerEvent {
    data class ReportSubmitted(val objectId: String) : ComposerEvent
}

sealed interface ComposerAction {
    data object LoadDraft : ComposerAction
    data object SubmitPressed : ComposerAction
    data object DiscardDraftConfirmed : ComposerAction
    data object ClearComposerError : ComposerAction
    data object PlateCorrectionAccepted : ComposerAction
    data object PlateCorrectionDismissed : ComposerAction
    data object PlateCorrectionKept : ComposerAction

    data class ComplaintTileChosen(val complaintId: String) : ComposerAction
    data class SelectedComplaintChanged(val complaintId: String) : ComposerAction
    data class UploadMediaChosen(val media: SubmissionMedia) : ComposerAction
    data class PrimaryMediaChosenForCurrentReport(val media: SubmissionMedia) : ComposerAction
    data class PendingComplaintConfirmed(val complaintId: String) : ComposerAction
    data class PrimaryMediaChosen(val media: SubmissionMedia, val complaintId: String) : ComposerAction
    data class ExtraMediaAdded(val media: SubmissionMedia) : ComposerAction
    data class MediaRemoved(val media: SubmissionMedia) : ComposerAction
    data class VideoProcessingDecision(val process: Boolean) : ComposerAction
    data object VideoProcessingCancelled : ComposerAction
    data object DetectionResultDismissed : ComposerAction

    data class DetectionProgressChanged(
        val message: String,
        val progress: Float,
        val framePreviewUri: String? = null,
        val frameTimeMs: Long = 0L,
        val videoDurationMs: Long = 0L,
        val frameCandidates: List<PlateCandidate> = emptyList(),
        val allCandidates: List<PlateCandidate> = emptyList()
    ) : ComposerAction
    data class DetectionFinished(
        val candidates: List<PlateCandidate> = emptyList(),
        val inferredPlate: String? = null,
        val inferredState: String? = null
    ) : ComposerAction
    data class PlateCandidateChosen(val candidate: PlateCandidate) : ComposerAction

    data class AddressQueryChanged(val value: String) : ComposerAction
    data class AddressSuggestionsChanged(
        val suggestions: List<AddressSuggestion>,
        val loading: Boolean = false
    ) : ComposerAction
    data class AddressLookupLoadingChanged(val loading: Boolean) : ComposerAction
    data class AddressChosen(val suggestion: AddressSuggestion) : ComposerAction

    data class MetadataApplied(
        val occurredAtIso: String? = null,
        val photoOccurredAtIso: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val inferredState: String? = null,
        val inferredAddress: String? = null
    ) : ComposerAction

    data class FieldsChanged(
        val plate: String? = null,
        val plateRegion: String? = null,
        val address: String? = null,
        val description: String? = null,
        val notes: String? = null,
        val occurredAtIso: String? = null
    ) : ComposerAction
}
