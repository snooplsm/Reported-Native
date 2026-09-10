package com.reported.nativeandroid.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import android.util.Log
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.analytics.ReportedAnalytics
import com.reported.nativeandroid.batch.BatchSubmitStore
import com.reported.nativeandroid.di.AppGraph
import com.reported.nativeandroid.media.LocalSubmissionMediaCleaner
import com.reported.nativeandroid.media.ParseMediaUploader
import com.reported.nativeandroid.remoteconfig.ReportedRemoteConfig
import com.reported.shared.model.AppThemeMode
import com.reported.shared.model.Catalogs
import com.reported.shared.model.CityReportingRules
import com.reported.shared.model.DraftMedia
import com.reported.shared.model.DraftPlateCandidate
import com.reported.shared.model.PhiladelphiaMobilityAccessCatalogs
import com.reported.shared.model.PhiladelphiaMobilityAccessDetails
import com.reported.shared.model.PlatePatternClassifier
import com.reported.shared.model.ReportDraft
import com.reported.shared.model.ReportFilter
import com.reported.shared.model.ReportSummary
import com.reported.shared.model.SubmitReportCommand
import com.reported.shared.model.SubmitReportMediaFile
import com.reported.shared.model.VehicleLookupDetails
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class ComposerViewModel : ViewModel(), UdfStore<ComposerUiState, ComposerAction> {
    private val _state = MutableStateFlow(
        ComposerUiState(
            complaintCategories = Catalogs.complaintCategories,
            showComplaintImages = ReportedRemoteConfig.snapshot.value.showComplaintImages
        )
    )
    override val state: StateFlow<ComposerUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<ComposerEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ComposerEvent> = _events.asSharedFlow()
    private val vehicleLookupCoordinator = ComposerVehicleLookupCoordinator(
        scope = viewModelScope,
        currentState = { _state.value },
        updateState = { transform -> _state.update(transform) },
        onPersistDraft = { persistDraft() }
    )

    init {
        viewModelScope.launch {
            ReportedRemoteConfig.snapshot.collect { snapshot ->
                _state.update {
                    it.copy(
                        complaintCategories = snapshot.complaintCategories,
                        showComplaintImages = snapshot.showComplaintImages
                    )
                }
            }
        }
    }

    override fun onAction(action: ComposerAction) {
        when (action) {
            ComposerAction.LoadDraft -> loadDraft()
            is ComposerAction.SubmitRequested -> submitRequested(action.isAuthorized)
            ComposerAction.SubmitPressed -> submit()
            ComposerAction.DiscardDraftConfirmed -> discardDraft()
            ComposerAction.ClearComposerError -> clearComposerError()
            ComposerAction.PlateCorrectionAccepted -> acceptPlateCorrection()
            ComposerAction.PlateCorrectionDismissed -> dismissPlateCorrection()
            ComposerAction.PlateCorrectionKept -> keepPlateCorrection()
            is ComposerAction.ComplaintTileChosen -> onComplaintTileChosen(action.complaintId)
            is ComposerAction.SelectedComplaintChanged -> updateSelectedComplaint(action.complaintId)
            is ComposerAction.UploadMediaChosen -> onUploadMediaChosen(action.media)
            is ComposerAction.PrimaryMediaChosenForCurrentReport -> onPrimaryMediaChosenForCurrentReport(action.media)
            is ComposerAction.PendingComplaintConfirmed -> confirmPendingComplaint(action.complaintId)
            is ComposerAction.PrimaryMediaChosen -> onPrimaryMediaChosen(action.media, action.complaintId)
            is ComposerAction.ExtraMediaAdded -> addExtraMedia(action.media)
            is ComposerAction.MediaRemoved -> removeMedia(action.media)
            is ComposerAction.MediaRejected -> rejectMedia(action.media, action.message)
            is ComposerAction.VideoProcessingDecision -> onVideoProcessingDecision(action.process)
            ComposerAction.VideoProcessingCancelled -> cancelVideoProcessing()
            ComposerAction.DetectionResultDismissed -> _state.update { it.copy(detectionResultMessage = null) }
            is ComposerAction.DetectionProgressChanged -> setDetectionProgress(
                action.message,
                action.progress,
                action.framePreviewUri,
                action.frameTimeMs,
                action.videoDurationMs,
                action.frameCandidates,
                action.allCandidates
            )
            is ComposerAction.DetectionFinished -> finishDetection(
                candidates = action.candidates,
                inferredPlate = action.inferredPlate,
                inferredState = action.inferredState
            )
            is ComposerAction.VehicleDescriptionApplied -> applyVehicleDescription(action.vehicleDescription)
            is ComposerAction.PlateCandidateChosen -> choosePlateCandidate(action.candidate)
            is ComposerAction.AddressQueryChanged -> updateAddressQuery(action.value)
            is ComposerAction.AddressSuggestionsChanged -> setAddressSuggestions(action.suggestions, action.loading)
            is ComposerAction.AddressLookupLoadingChanged -> setAddressLookupLoading(action.loading)
            is ComposerAction.AddressChosen -> chooseAddress(action.suggestion)
            is ComposerAction.MetadataApplied -> applyDetectedMetadata(
                occurredAtIso = action.occurredAtIso,
                photoOccurredAtIso = action.photoOccurredAtIso,
                latitude = action.latitude,
                longitude = action.longitude,
                inferredState = action.inferredState,
                inferredAddress = action.inferredAddress,
                photoAddressSuggestion = action.photoAddressSuggestion
            )
            is ComposerAction.FieldsChanged -> update(
                plate = action.plate ?: _state.value.plate,
                plateRegion = action.plateRegion ?: _state.value.plateRegion,
                address = action.address ?: _state.value.address,
                description = action.description ?: _state.value.description,
                notes = action.notes ?: _state.value.notes,
                occurredAtIso = action.occurredAtIso ?: _state.value.occurredAtIso
            )
            is ComposerAction.PhiladelphiaMobilityAccessChanged -> updatePhiladelphiaMobilityAccess(
                blockNumber = action.blockNumber,
                streetName = action.streetName,
                zipCode = action.zipCode,
                vehicleMake = action.vehicleMake,
                vehicleModel = action.vehicleModel,
                bodyStyle = action.bodyStyle,
                vehicleColor = action.vehicleColor,
                violationObserved = action.violationObserved,
                frequency = action.frequency
            )
        }
    }

    fun loadDraft() {
        if (_state.value.draftLoaded) return
        viewModelScope.launch {
            val draft = AppGraph.shared.loadDraftUseCase.execute()
            _state.update { current ->
                val restoredPrimaryMedia = draft?.primaryMedia?.toSubmissionMedia()
                val restoredExtraMedia = draft?.extraMedia?.map { it.toSubmissionMedia() }.orEmpty()
                val restoredStage = when {
                    draft?.hasComplaintData() == true || restoredPrimaryMedia != null -> SubmissionStage.VERIFY
                    draft?.stage != null -> runCatching { SubmissionStage.valueOf(draft.stage) }.getOrNull() ?: current.stage
                    else -> current.stage
                }
                current.copy(
                    plate = draft?.plate ?: current.plate,
                    plateRegion = draft?.plateRegion?.ifBlank { "NY" } ?: current.plateRegion,
                    address = draft?.address ?: current.address,
                    addressQuery = draft?.address ?: current.addressQuery,
                    description = draft?.description ?: current.description,
                    notes = draft?.notes ?: current.notes,
                    occurredAtIso = draft?.occurredAtIso ?: current.occurredAtIso,
                    selectedComplaintIds = draft?.complaintIds ?: current.selectedComplaintIds,
                    selectedComplaintId = draft?.selectedComplaintId ?: draft?.complaintIds?.firstOrNull() ?: current.selectedComplaintId,
                    stage = restoredStage,
                    primaryMedia = restoredPrimaryMedia,
                    extraMedia = restoredExtraMedia,
                    latitude = draft?.latitude ?: current.latitude,
                    longitude = draft?.longitude ?: current.longitude,
                    plateCandidates = draft?.plateCandidates?.map { it.toPlateCandidate() } ?: current.plateCandidates,
                    selectedPlateCandidate = draft?.selectedPlateCandidate ?: current.selectedPlateCandidate,
                    vehicleDescription = draft?.vehicleImageDescription?.let { imageDescription ->
                        VehicleDescription(
                            imageDescription = imageDescription,
                            color = draft.vehicleColor,
                            make = draft.vehicleMake,
                            model = draft.vehicleModel
                        )
                    } ?: current.vehicleDescription,
                    philadelphiaMobilityAccessDetails = draft?.philadelphiaMobilityAccessDetails
                        ?: current.philadelphiaMobilityAccessDetails,
                    draftLoaded = true,
                    error = null
                )
            }
            vehicleLookupCoordinator.refresh()
        }
    }

    fun onComplaintTileChosen(complaintId: String) {
        _state.update {
            it.copy(
                selectedComplaintId = complaintId,
                selectedComplaintIds = listOf(complaintId),
                complaintSheetOpen = false,
                error = null,
                validationErrors = it.validationErrors.copy(complaint = null)
            )
        }
        persistDraft()
    }

    fun updateSelectedComplaint(complaintId: String) {
        _state.update {
            it.copy(
                selectedComplaintId = complaintId,
                selectedComplaintIds = listOf(complaintId),
                error = null,
                validationErrors = it.validationErrors.copy(complaint = null)
            )
        }
        persistDraft()
    }

    fun onUploadMediaChosen(media: SubmissionMedia) {
        _state.update {
            val mediaError = it.mediaLimitErrorFor(media, replacingPrimary = true)
            if (mediaError != null) {
                return@update it.copy(validationErrors = it.validationErrors.copy(media = mediaError))
            }
            it.copy(
                pendingMediaSelection = media,
                primaryMedia = media,
                stage = SubmissionStage.VERIFY,
                plateCandidates = emptyList(),
                selectedPlateCandidate = null,
                plate = "",
                detectionResultMessage = null,
                photoOccurredAtIso = null,
                photoAddressSuggestion = null,
                complaintSheetOpen = true,
                error = null,
                validationErrors = it.validationErrors.copy(media = null)
            ).withMediaAddedTiming(previous = it)
        }
        persistDraft()
        vehicleLookupCoordinator.refresh()
    }

    fun confirmPendingComplaint(complaintId: String) {
        val pending = _state.value.pendingMediaSelection ?: _state.value.primaryMedia ?: return
        _state.update {
            val mediaError = it.mediaLimitErrorFor(pending, replacingPrimary = true)
            if (mediaError != null) {
                return@update it.copy(
                    pendingMediaSelection = null,
                    complaintSheetOpen = false,
                    validationErrors = it.validationErrors.copy(media = mediaError)
                )
            }
            it.copy(
                selectedComplaintId = complaintId,
                selectedComplaintIds = listOf(complaintId),
                primaryMedia = pending,
                pendingMediaSelection = null,
                complaintSheetOpen = false,
                stage = SubmissionStage.VERIFY,
                error = null,
                validationErrors = it.validationErrors.copy(media = null, complaint = null)
            )
        }
        persistDraft()
    }

    fun onPrimaryMediaChosen(media: SubmissionMedia, complaintId: String) {
        _state.update {
            val mediaError = it.mediaLimitErrorFor(media, replacingPrimary = true)
            if (mediaError != null) {
                if (it.primaryMedia?.uri == media.uri) {
                    return@update it.copy(
                        selectedComplaintId = complaintId,
                        selectedComplaintIds = listOf(complaintId),
                        stage = SubmissionStage.VERIFY,
                        validationErrors = it.validationErrors.copy(complaint = null)
                    )
                }
                return@update it.copy(validationErrors = it.validationErrors.copy(media = mediaError))
            }
            it.copy(
                selectedComplaintId = complaintId,
                selectedComplaintIds = listOf(complaintId),
                primaryMedia = media,
                pendingMediaSelection = null,
                complaintSheetOpen = false,
                awaitingVideoProcessingDecision = media.isVideo,
                pendingVideoProcessingMedia = if (media.isVideo) media else null,
                stage = SubmissionStage.VERIFY,
                error = null,
                photoOccurredAtIso = null,
                photoAddressSuggestion = null,
                validationErrors = it.validationErrors.copy(media = null, complaint = null)
            ).withMediaAddedTiming(previous = it)
        }
        persistDraft()
    }

    private fun onPrimaryMediaChosenForCurrentReport(media: SubmissionMedia) {
        _state.update {
            val mediaError = it.mediaLimitErrorFor(media, replacingPrimary = true)
            if (mediaError != null) {
                if (it.primaryMedia?.uri == media.uri) {
                    return@update it.copy(
                        stage = SubmissionStage.VERIFY,
                        validationErrors = it.validationErrors.copy(media = null)
                    )
                }
                return@update it.copy(validationErrors = it.validationErrors.copy(media = mediaError))
            }
            it.copy(
                primaryMedia = media,
                pendingMediaSelection = null,
                complaintSheetOpen = false,
                awaitingVideoProcessingDecision = media.isVideo,
                pendingVideoProcessingMedia = if (media.isVideo) media else null,
                stage = SubmissionStage.VERIFY,
                error = null,
                photoOccurredAtIso = null,
                photoAddressSuggestion = null,
                validationErrors = it.validationErrors.copy(media = null)
            ).withMediaAddedTiming(previous = it)
        }
        persistDraft()
    }

    fun addExtraMedia(media: SubmissionMedia) {
        _state.update {
            val mediaError = it.mediaLimitErrorFor(media, replacingPrimary = false)
            if (mediaError != null) {
                return@update it.copy(validationErrors = it.validationErrors.copy(media = mediaError))
            }
            it.copy(
                extraMedia = it.extraMedia + media,
                validationErrors = it.validationErrors.copy(media = null)
            ).withMediaAddedTiming(previous = it)
        }
        persistDraft()
    }

    fun removeExtraMedia(media: SubmissionMedia) {
        _state.update {
            val remainingExtras = it.extraMedia.filterNot { candidate -> candidate == media }
            it.copy(
                extraMedia = remainingExtras,
                firstMediaAddedElapsedRealtimeMs = if (it.primaryMedia != null || remainingExtras.isNotEmpty()) {
                    it.firstMediaAddedElapsedRealtimeMs
                } else {
                    null
                }
            )
        }
        persistDraft()
    }

    fun removeMedia(media: SubmissionMedia) {
        _state.update { current ->
            when {
                current.primaryMedia == media -> {
                    val nextPrimary = current.extraMedia.firstOrNull()
                    val remainingExtras = if (nextPrimary != null) current.extraMedia.drop(1) else emptyList()
                    val mediaError = if (nextPrimary != null) null else current.validationErrors.media
                    val hasRemainingMedia = nextPrimary != null || remainingExtras.isNotEmpty()
                    current.copy(
                        primaryMedia = nextPrimary,
                        extraMedia = remainingExtras,
                        firstMediaAddedElapsedRealtimeMs = if (hasRemainingMedia) {
                            current.firstMediaAddedElapsedRealtimeMs
                        } else {
                            null
                        },
                        stage = if (nextPrimary != null || current.hasComplaintData()) {
                            SubmissionStage.VERIFY
                        } else {
                            SubmissionStage.PICK_MEDIA
                        },
                        plateCandidates = emptyList(),
                        selectedPlateCandidate = null,
                        detectingPlates = false,
                        detectionMessage = null,
                        detectionResultMessage = null,
                        detectionProgress = 0f,
                        pendingVideoProcessingMedia = null,
                        awaitingVideoProcessingDecision = false,
                        vehicleDescription = if (nextPrimary != null) current.vehicleDescription else null,
                        plate = if (nextPrimary != null) current.plate else "",
                        plateRegion = if (nextPrimary != null) current.plateRegion else "NY",
                        address = if (nextPrimary != null) current.address else "",
                        addressQuery = if (nextPrimary != null) current.addressQuery else "",
                        occurredAtIso = if (nextPrimary != null) current.occurredAtIso else "",
                        photoOccurredAtIso = if (nextPrimary != null) current.photoOccurredAtIso else null,
                        photoAddressSuggestion = if (nextPrimary != null) current.photoAddressSuggestion else null,
                        latitude = if (nextPrimary != null) current.latitude else null,
                        longitude = if (nextPrimary != null) current.longitude else null,
                        validationErrors = current.validationErrors.copy(media = mediaError)
                    )
                }
                else -> {
                    val remainingExtras = current.extraMedia.filterNot { candidate -> candidate == media }
                    current.copy(
                        extraMedia = remainingExtras,
                        firstMediaAddedElapsedRealtimeMs = if (current.primaryMedia != null || remainingExtras.isNotEmpty()) {
                            current.firstMediaAddedElapsedRealtimeMs
                        } else {
                            null
                        }
                    )
                }
            }
        }
        persistDraft()
        vehicleLookupCoordinator.refresh()
    }

    private fun rejectMedia(media: SubmissionMedia, message: String) {
        removeMedia(media)
        _state.update {
            it.copy(
                awaitingVideoProcessingDecision = false,
                pendingVideoProcessingMedia = null,
                detectingPlates = false,
                detectionMessage = null,
                detectionResultMessage = null,
                detectionProgress = 0f,
                detectionFramePreviewUri = null,
                validationErrors = it.validationErrors.copy(media = message)
            )
        }
        persistDraft()
    }

    fun onVideoProcessingDecision(process: Boolean) {
        _state.update {
            it.copy(
                awaitingVideoProcessingDecision = false,
                pendingVideoProcessingMedia = null,
                detectingPlates = process,
                detectionMessage = if (process) "Detecting plates from video" else null,
                detectionResultMessage = if (process) null else "Video plate detection skipped.",
                detectionProgress = if (process) 0.05f else 0f,
                detectionFramePreviewUri = null,
                detectionFrameTimeMs = 0L,
                detectionVideoDurationMs = 0L,
                detectionFrameCandidates = emptyList(),
                plateCandidates = if (process) it.plateCandidates else it.plateCandidates
            )
        }
    }

    fun cancelVideoProcessing() {
        _state.update {
            it.copy(
                awaitingVideoProcessingDecision = false,
                pendingVideoProcessingMedia = null,
                detectingPlates = false,
                detectionMessage = null,
                detectionResultMessage = null,
                detectionProgress = 0f,
                detectionFramePreviewUri = null,
                detectionFrameTimeMs = 0L,
                detectionVideoDurationMs = 0L,
                detectionFrameCandidates = emptyList()
            )
        }
    }

    fun setDetectionProgress(
        message: String,
        progress: Float,
        framePreviewUri: String? = null,
        frameTimeMs: Long = 0L,
        videoDurationMs: Long = 0L,
        frameCandidates: List<PlateCandidate> = emptyList(),
        allCandidates: List<PlateCandidate> = emptyList()
    ) {
        _state.update {
            it.copy(
                detectingPlates = true,
                detectionMessage = message,
                detectionResultMessage = null,
                detectionProgress = progress.coerceIn(0f, 1f),
                detectionFramePreviewUri = framePreviewUri ?: it.detectionFramePreviewUri,
                detectionFrameTimeMs = frameTimeMs,
                detectionVideoDurationMs = videoDurationMs.takeIf { duration -> duration > 0L } ?: it.detectionVideoDurationMs,
                detectionFrameCandidates = frameCandidates,
                plateCandidates = allCandidates.ifEmpty { it.plateCandidates }
            )
        }
    }

    fun finishDetection(
        candidates: List<PlateCandidate> = emptyList(),
        inferredPlate: String? = null,
        inferredState: String? = null
    ) {
        val detectedState = candidates
            .firstOrNull { it.plate == inferredPlate }
            ?.state
            ?: candidates.firstOrNull()?.state
        val nextState = detectedState ?: inferredState
        _state.update {
            it.copy(
                detectingPlates = false,
                detectionMessage = null,
                detectionResultMessage = null,
                detectionProgress = 0f,
                detectionFramePreviewUri = null,
                detectionFrameTimeMs = 0L,
                detectionVideoDurationMs = 0L,
                detectionFrameCandidates = emptyList(),
                plateCandidates = candidates,
                selectedPlateCandidate = inferredPlate ?: it.selectedPlateCandidate,
                plate = inferredPlate ?: it.plate,
                plateRegion = nextState ?: it.plateRegion,
                validationErrors = it.validationErrors.copy(
                    plate = if (inferredPlate != null) null else it.validationErrors.plate,
                    plateRegion = if (nextState != null) null else it.validationErrors.plateRegion
                )
            )
        }
        persistDraft()
        vehicleLookupCoordinator.refresh()
    }

    fun applyVehicleDescription(vehicleDescription: VehicleDescription) {
        _state.update { current ->
            current.copy(
                vehicleDescription = vehicleDescription,
                philadelphiaMobilityAccessDetails = current.philadelphiaMobilityAccessDetails
                    .prefilledFrom(vehicleDescription),
                notes = if (current.notes.isBlank()) {
                    vehicleDescription.formattedForNotes()
                } else {
                    current.notes
                }
            )
        }
        persistDraft()
    }

    fun choosePlateCandidate(candidate: PlateCandidate) {
        val current = _state.value
        val selectedRank = current.plateCandidates.indexOfFirst { it.plate == candidate.plate }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
        ReportedAnalytics.logAlprResultSelected(
            plateRegion = candidate.state,
            candidateCount = current.plateCandidates.size,
            selectedRank = selectedRank,
            confidence = candidate.confidence.toDouble(),
            wasCorrected = candidate.wasPlateCorrected
        )
        _state.update {
            it.copy(
                selectedPlateCandidate = candidate.plate,
                plate = candidate.plate,
                plateRegion = candidate.state ?: it.plateRegion,
                validationErrors = it.validationErrors.copy(
                    plate = null,
                    plateRegion = if (candidate.state != null) null else it.validationErrors.plateRegion
                )
            )
        }
        persistDraft()
        vehicleLookupCoordinator.refresh()
    }

    fun updateAddressQuery(value: String) {
        _state.update {
            val trimmedValue = value.trim()
            val matchesChosenAddress = value == it.address
            it.copy(
                addressQuery = value,
                address = if (trimmedValue.isEmpty()) "" else it.address,
                latitude = if (matchesChosenAddress) it.latitude else null,
                longitude = if (matchesChosenAddress) it.longitude else null,
                validationErrors = it.validationErrors.copy(address = null)
            )
        }
        persistDraft()
    }

    fun setAddressSuggestions(suggestions: List<AddressSuggestion>, loading: Boolean = false) {
        _state.update {
            it.copy(
                addressSuggestions = suggestions,
                lookupInFlight = loading
            )
        }
    }

    fun setAddressLookupLoading(loading: Boolean) {
        _state.update { it.copy(lookupInFlight = loading) }
    }

    fun chooseAddress(suggestion: AddressSuggestion) {
        val previousLookupKey = _state.value.vehicleLookupKey()
        _state.update {
            it.copy(
                address = suggestion.label,
                addressQuery = suggestion.label,
                latitude = suggestion.latitude,
                longitude = suggestion.longitude,
                plateRegion = suggestion.region ?: it.plateRegion,
                philadelphiaMobilityAccessDetails = it.philadelphiaMobilityAccessDetails.withAddressSuggestion(suggestion),
                addressSuggestions = emptyList(),
                lookupInFlight = false,
                validationErrors = it.validationErrors.copy(
                    address = null,
                    plateRegion = if (suggestion.region != null) null else it.validationErrors.plateRegion
                )
            )
        }
        persistDraft()
        if (_state.value.vehicleLookupKey() != previousLookupKey) {
            vehicleLookupCoordinator.refresh()
        }
    }

    fun applyDetectedMetadata(
        occurredAtIso: String? = null,
        photoOccurredAtIso: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        inferredState: String? = null,
        inferredAddress: String? = null,
        photoAddressSuggestion: AddressSuggestion? = null
    ) {
        _state.update {
            val updated = it.copy(
                occurredAtIso = occurredAtIso ?: it.occurredAtIso,
                photoOccurredAtIso = photoOccurredAtIso ?: it.photoOccurredAtIso,
                latitude = latitude ?: it.latitude,
                longitude = longitude ?: it.longitude,
                photoAddressSuggestion = photoAddressSuggestion ?: it.photoAddressSuggestion,
                plateRegion = inferredState ?: it.plateRegion,
                address = inferredAddress ?: it.address,
                addressQuery = inferredAddress ?: it.addressQuery,
                philadelphiaMobilityAccessDetails = photoAddressSuggestion
                    ?.let { suggestion -> it.philadelphiaMobilityAccessDetails.withAddressSuggestion(suggestion) }
                    ?: it.philadelphiaMobilityAccessDetails,
                validationErrors = it.validationErrors.copy(
                    plateRegion = if (inferredState != null) null else it.validationErrors.plateRegion,
                    address = if (inferredAddress != null) null else it.validationErrors.address,
                    occurredAt = if (occurredAtIso != null) null else it.validationErrors.occurredAt
                )
            )
            updated.withPhiladelphiaMediaValidation()
        }
        persistDraft()
    }

    fun clearComposerError() {
        _state.update { it.copy(error = null) }
    }

    fun update(
        plate: String = _state.value.plate,
        plateRegion: String = _state.value.plateRegion,
        address: String = _state.value.address,
        description: String = _state.value.description,
        notes: String = _state.value.notes,
        occurredAtIso: String = _state.value.occurredAtIso
    ) {
        val normalizedPlate = PlatePatternClassifier.normalizePlateInput(plate)
            .take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH)
        val previousLookupKey = _state.value.vehicleLookupKey()
        _state.update {
            it.copy(
                plate = normalizedPlate,
                plateRegion = plateRegion.uppercase(),
                address = address,
                addressQuery = address,
                description = description,
                notes = notes,
                occurredAtIso = occurredAtIso,
                plateCorrectionPrompt = null,
                keptPlateCorrectionRaw = if (normalizedPlate == it.plate) it.keptPlateCorrectionRaw else null,
                validationErrors = it.validationErrors.copy(
                    plate = if (normalizedPlate.isNotBlank()) null else it.validationErrors.plate,
                    plateRegion = if (plateRegion.isNotBlank()) null else it.validationErrors.plateRegion,
                    address = if (address.isNotBlank()) null else it.validationErrors.address,
                    occurredAt = if (occurredAtIso.isNotBlank()) null else it.validationErrors.occurredAt
                )
            )
        }
        persistDraft()
        if (_state.value.vehicleLookupKey() != previousLookupKey) {
            vehicleLookupCoordinator.refresh()
        }
    }

    private fun updatePhiladelphiaMobilityAccess(
        blockNumber: String? = null,
        streetName: String? = null,
        zipCode: String? = null,
        vehicleMake: String? = null,
        vehicleModel: String? = null,
        bodyStyle: String? = null,
        vehicleColor: String? = null,
        violationObserved: String? = null,
        frequency: String? = null
    ) {
        _state.update { current ->
            val details = current.philadelphiaMobilityAccessDetails
            current.copy(
                philadelphiaMobilityAccessDetails = details.copy(
                    blockNumber = blockNumber ?: details.blockNumber,
                    streetName = streetName ?: details.streetName,
                    zipCode = zipCode ?: details.zipCode,
                    vehicleMake = vehicleMake ?: details.vehicleMake,
                    vehicleModel = vehicleModel ?: details.vehicleModel,
                    bodyStyle = bodyStyle ?: details.bodyStyle,
                    vehicleColor = vehicleColor ?: details.vehicleColor,
                    violationObserved = violationObserved ?: details.violationObserved,
                    frequency = frequency ?: details.frequency
                )
            )
        }
        persistDraft()
    }

    private fun acceptPlateCorrection() {
        val prompt = _state.value.plateCorrectionPrompt ?: return
        _state.update {
            it.copy(
                plate = prompt.suggestedPlate,
                plateRegion = prompt.state,
                plateCorrectionPrompt = null,
                keptPlateCorrectionRaw = null,
                validationErrors = it.validationErrors.copy(plate = null, plateRegion = null)
            )
        }
        persistDraft()
        vehicleLookupCoordinator.refresh()
    }

    private fun keepPlateCorrection() {
        val prompt = _state.value.plateCorrectionPrompt ?: return
        _state.update {
            it.copy(
                plateCorrectionPrompt = null,
                keptPlateCorrectionRaw = prompt.rawPlate,
                validationErrors = it.validationErrors.copy(plate = null, plateRegion = null)
            )
        }
    }

    private fun dismissPlateCorrection() {
        _state.update { current ->
            current.copy(
                plateCorrectionPrompt = null,
                validationErrors = current.validationErrors.copy(
                    plate = "Review the plate format before submitting."
                )
            )
        }
    }

    private fun persistDraft() {
        val snapshot = _state.value
        viewModelScope.launch {
            AppGraph.shared.saveDraftUseCase.execute(
                ReportDraft(
                    plate = snapshot.plate,
                    plateRegion = snapshot.plateRegion,
                    address = snapshot.addressQuery.ifBlank { snapshot.address },
                    description = snapshot.description,
                    notes = snapshot.notes,
                    complaintIds = snapshot.selectedComplaintId?.let(::listOf) ?: snapshot.selectedComplaintIds,
                    occurredAtIso = snapshot.occurredAtIso,
                    selectedComplaintId = snapshot.selectedComplaintId,
                    stage = snapshot.stage.name,
                    primaryMedia = snapshot.primaryMedia?.toDraftMedia(),
                    extraMedia = snapshot.extraMedia.map { it.toDraftMedia() },
                    latitude = snapshot.latitude,
                    longitude = snapshot.longitude,
                    plateCandidates = snapshot.plateCandidates.map { it.toDraftPlateCandidate() },
                    selectedPlateCandidate = snapshot.selectedPlateCandidate,
                    vehicleImageDescription = snapshot.vehicleDescription?.imageDescription,
                    vehicleColor = snapshot.vehicleDescription?.color,
                    vehicleMake = snapshot.vehicleDescription?.make,
                    vehicleModel = snapshot.vehicleDescription?.model,
                    philadelphiaMobilityAccessDetails = snapshot.philadelphiaMobilityAccessDetails
                )
            )
        }
    }

    fun discardDraft() {
        viewModelScope.launch {
            AppGraph.shared.clearDraftUseCase.execute()
            _state.value = ComposerUiState(
                complaintCategories = Catalogs.complaintCategories,
                showComplaintImages = ReportedRemoteConfig.snapshot.value.showComplaintImages,
                draftLoaded = true
            )
        }
    }

    fun submit() {
        Log.d("ReportedSubmit", "Submit requested; validating")
        if (!prepareSubmit()) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    submitting = true,
                    submitProgress = 0f,
                    submitMessage = "Preparing report",
                    error = null
                )
            }
            var lastSubmitCommand: SubmitReportCommand? = null
            runCatching {
                val snapshot = _state.value
                val submittedMedia = listOfNotNull(snapshot.primaryMedia) + snapshot.extraMedia
                val philadelphiaDetails = snapshot.submittablePhiladelphiaMobilityAccessDetails()
                var attemptedCommand = SubmitReportCommand(
                    plate = snapshot.plate,
                    plateRegion = snapshot.plateRegion,
                    description = snapshot.description,
                    notes = snapshot.notes,
                    address = snapshot.addressQuery.ifBlank { snapshot.address },
                    complaintIds = snapshot.selectedComplaintIds,
                    timeOfIncidentIso = snapshot.occurredAtIso.ifBlank { null },
                    latitude = snapshot.latitude,
                    longitude = snapshot.longitude,
                    vehicleImageDescription = snapshot.vehicleDescription?.imageDescription,
                    vehicleColor = philadelphiaDetails?.vehicleColor?.ifBlank { null }
                        ?: snapshot.vehicleDescription?.color,
                    vehicleMake = philadelphiaDetails?.vehicleMake?.ifBlank { null }
                        ?: snapshot.vehicleLookupDetails?.vehicleMake
                        ?: snapshot.vehicleDescription?.make,
                    vehicleModel = philadelphiaDetails?.vehicleModel?.ifBlank { null }
                        ?: snapshot.vehicleLookupDetails?.vehicleModel
                        ?: snapshot.vehicleDescription?.model,
                    vehicleYear = snapshot.vehicleLookupDetails?.vehicleYear,
                    vehicleBodyClass = philadelphiaDetails?.bodyStyle?.ifBlank { null }
                        ?: snapshot.vehicleLookupDetails?.vehicleBody,
                    philadelphiaMobilityAccessDetails = philadelphiaDetails
                )
                lastSubmitCommand = attemptedCommand
                Log.d(
                    "ReportedSubmit",
                    "Validated report; media=${submittedMedia.size} plate=${snapshot.plate}/${snapshot.plateRegion} complaintIds=${snapshot.selectedComplaintIds}"
                )
                _state.update { it.copy(submitMessage = "Uploading media", submitProgress = 0.02f) }
                val mediaFiles = uploadMediaForSubmission(snapshot, submittedMedia) { progress, message ->
                    _state.update {
                        it.copy(submitProgress = progress, submitMessage = message)
                    }
                }
                Log.d("ReportedSubmit", "Media upload complete; parseFiles=${mediaFiles.size}")
                _state.update { it.copy(submitMessage = "Submitting report", submitProgress = 0.88f) }
                attemptedCommand = attemptedCommand.copy(mediaFiles = mediaFiles)
                lastSubmitCommand = attemptedCommand
                val submittedObjectId = AppGraph.shared.submitReportUseCase.execute(attemptedCommand)
                Log.d("ReportedSubmit", "Parse submission complete; cleaning local media")
                _state.update { it.copy(submitMessage = "Cleaning up", submitProgress = 0.96f) }
                BatchSubmitStore.markSubmittedAutoReportMedia(AppGraph.applicationContext, submittedMedia)
                LocalSubmissionMediaCleaner.cleanupAfterSuccessfulSubmit(
                    context = AppGraph.applicationContext,
                    media = submittedMedia,
                    plateCandidates = snapshot.plateCandidates
                )
                SubmitAnalyticsPayload(
                    objectId = submittedObjectId,
                    county = inferCounty(snapshot.address),
                    plateRegion = snapshot.plateRegion,
                    complaintCount = snapshot.selectedComplaintIds.size,
                    mediaCount = submittedMedia.size,
                    hasVideo = submittedMedia.any { it.isVideo },
                    mediaToSubmitMillis = snapshot.mediaToSubmitMillis()
                )
            }.onSuccess { result ->
                ReportedAnalytics.logSubmitReport(
                    county = result.county,
                    plateRegion = result.plateRegion,
                    complaintCount = result.complaintCount,
                    mediaCount = result.mediaCount,
                    hasVideo = result.hasVideo,
                    mediaToSubmitMillis = result.mediaToSubmitMillis
                )
                Log.d("ReportedSubmit", "Submit flow finished successfully")
                AppGraph.shared.clearDraftUseCase.execute()
                _state.value = ComposerUiState(
                    complaintCategories = Catalogs.complaintCategories,
                    showComplaintImages = ReportedRemoteConfig.snapshot.value.showComplaintImages,
                    infoMessage = "Report submitted."
                )
                _events.tryEmit(ComposerEvent.ReportSubmitted(result.objectId))
            }.onFailure { error ->
                Log.e("ReportedSubmit", "Submit flow failed", error)
                val failedSnapshot = _state.value
                val failedMedia = listOfNotNull(failedSnapshot.primaryMedia) + failedSnapshot.extraMedia
                ReportedAnalytics.logSubmitReportFailed(
                    surface = "new_report",
                    stage = if (failedSnapshot.stage == SubmissionStage.VERIFY) "verify" else "pick_media",
                    error = error,
                    plateRegion = failedSnapshot.plateRegion,
                    complaintCount = failedSnapshot.selectedComplaintIds.size,
                    mediaCount = failedMedia.size,
                    hasVideo = failedMedia.any { it.isVideo },
                    session = runCatching { AppGraph.shared.loadSessionUseCase.execute() }.getOrNull(),
                    command = lastSubmitCommand ?: SubmitReportCommand(
                        plate = failedSnapshot.plate,
                        plateRegion = failedSnapshot.plateRegion,
                        description = failedSnapshot.description,
                        notes = failedSnapshot.notes,
                        address = failedSnapshot.addressQuery.ifBlank { failedSnapshot.address },
                        complaintIds = failedSnapshot.selectedComplaintIds,
                        timeOfIncidentIso = failedSnapshot.occurredAtIso.ifBlank { null },
                        latitude = failedSnapshot.latitude,
                        longitude = failedSnapshot.longitude,
                        vehicleImageDescription = failedSnapshot.vehicleDescription?.imageDescription,
                        vehicleColor = failedSnapshot.submittablePhiladelphiaMobilityAccessDetails()
                            ?.vehicleColor
                            ?.ifBlank { null }
                            ?: failedSnapshot.vehicleDescription?.color,
                        vehicleMake = failedSnapshot.submittablePhiladelphiaMobilityAccessDetails()
                            ?.vehicleMake
                            ?.ifBlank { null }
                            ?: failedSnapshot.vehicleLookupDetails?.vehicleMake
                            ?: failedSnapshot.vehicleDescription?.make,
                        vehicleModel = failedSnapshot.submittablePhiladelphiaMobilityAccessDetails()
                            ?.vehicleModel
                            ?.ifBlank { null }
                            ?: failedSnapshot.vehicleLookupDetails?.vehicleModel
                            ?: failedSnapshot.vehicleDescription?.model,
                        vehicleYear = failedSnapshot.vehicleLookupDetails?.vehicleYear,
                        vehicleBodyClass = failedSnapshot.submittablePhiladelphiaMobilityAccessDetails()
                            ?.bodyStyle
                            ?.ifBlank { null }
                            ?: failedSnapshot.vehicleLookupDetails?.vehicleBody,
                        philadelphiaMobilityAccessDetails = failedSnapshot.submittablePhiladelphiaMobilityAccessDetails()
                    )
                )
                _state.update { current ->
                    current.copy(
                        submitting = false,
                        submitProgress = null,
                        submitMessage = null,
                        error = "There was an error submitting your report. Please try again."
                    )
                }
            }
        }
    }

    private fun submitRequested(isAuthorized: Boolean) {
        if (!prepareSubmit()) return
        if (isAuthorized) {
            submit()
        } else {
            _events.tryEmit(ComposerEvent.RequireLogin)
        }
    }

    private fun prepareSubmit(): Boolean {
        val suggestion = PlatePatternClassifier.suggestedCorrection(_state.value.plate)
        if (suggestion != null && _state.value.keptPlateCorrectionRaw != _state.value.plate) {
            _state.update {
                it.copy(
                    submitting = false,
                    error = null,
                    plateCorrectionPrompt = PlateCorrectionPrompt(
                        rawPlate = it.plate,
                        suggestedPlate = suggestion.normalizedPlate,
                        state = suggestion.state,
                        label = suggestion.label
                    ),
                    validationErrors = it.validationErrors.copy(plate = null, plateRegion = null)
                )
            }
            return false
        }
        val validationErrors = validateComposerSubmission(_state.value)
        if (validationErrors.hasErrors) {
            _state.update {
                it.copy(
                    submitting = false,
                    error = null,
                    validationErrors = validationErrors,
                    stage = if (it.hasComplaintData()) SubmissionStage.VERIFY else SubmissionStage.PICK_MEDIA
                )
            }
            return false
        }
        _state.update { it.copy(error = null, validationErrors = ComposerValidationErrors()) }
        return true
    }

}
