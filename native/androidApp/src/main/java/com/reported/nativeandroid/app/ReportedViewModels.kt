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

class SessionViewModel : ViewModel() {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    fun onAction(action: SessionAction) {
        when (action) {
            SessionAction.Load -> load()
            SessionAction.Authenticated -> onAuthenticated()
            SessionAction.ContinueAsGuest -> continueAsGuest()
            SessionAction.Logout -> logout()
            is SessionAction.SocialSignInCompleted -> completeSocialSignIn(action.profile)
        }
    }

    fun load() {
        viewModelScope.launch {
            val session = AppGraph.shared.loadSessionUseCase.execute()
            val guestMode = AppGraph.shared.loadGuestModeUseCase.execute()
            ReportedAnalytics.setUser(session)
            _state.value = SessionUiState(
                loading = false,
                session = session,
                isGuest = session == null && guestMode
            )
        }
    }

    fun onAuthenticated() {
        viewModelScope.launch {
            AppGraph.shared.saveGuestModeUseCase.execute(false)
            load()
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            AppGraph.shared.saveGuestModeUseCase.execute(true)
            ReportedAnalytics.setUser(null)
            _state.value = SessionUiState(loading = false, session = null, isGuest = true)
        }
    }

    fun logout() {
        viewModelScope.launch {
            AppGraph.shared.logoutUseCase.execute()
            AppGraph.shared.saveGuestModeUseCase.execute(false)
            ReportedAnalytics.logLogout()
            _state.value = SessionUiState(loading = false, session = null, isGuest = false)
        }
    }

    fun completeSocialSignIn(profile: SocialAuthProfile) {
        viewModelScope.launch {
            runCatching {
                AppGraph.shared.socialLoginUseCase.execute(
                    provider = profile.provider,
                    providerUserId = profile.providerUserId,
                    idToken = profile.idToken,
                    email = profile.email,
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    phone = "",
                    testify = false
                )
            }.onSuccess { session ->
                AppGraph.shared.saveGuestModeUseCase.execute(false)
                ReportedAnalytics.logLogin(profile.provider, session)
                load()
            }.onFailure { error ->
                _state.update { current -> current.copy(loading = false, session = null, isGuest = current.isGuest) }
            }
        }
    }
}

class LoginViewModel : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.EmailChanged -> onEmailChanged(action.value)
            is LoginAction.PasswordChanged -> onPasswordChanged(action.value)
            is LoginAction.LoginPressed -> login(action.onSuccess)
            LoginAction.ForgotPasswordPressed -> forgotPassword()
            LoginAction.PasswordResetMessageDismissed -> dismissPasswordResetMessage()
            is LoginAction.SocialSignInFailed -> onSocialSignInFailed(action.provider, action.message)
            LoginAction.SocialSignInCancelled -> onSocialSignInCancelled()
            is LoginAction.SocialSignInCompleted -> completeSocialSignIn(action.profile, action.onSuccess)
        }
    }

    fun onEmailChanged(value: String) = _state.update { it.copy(email = value) }
    fun onPasswordChanged(value: String) = _state.update { it.copy(password = value) }

    fun login(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                AppGraph.shared.loginUseCase.execute(_state.value.email, _state.value.password)
            }.onSuccess { session ->
                _state.update { current -> current.copy(loading = false, error = null) }
                ReportedAnalytics.logLogin("password", session)
                onSuccess()
            }.onFailure { error ->
                Log.e("ReportedAuth", "Login failed", error)
                _state.update {
                    it.copy(
                        loading = false,
                        error = "There was an error signing in. Please try again."
                    )
                }
            }
        }
    }

    fun forgotPassword() {
        val email = _state.value.email
        if (email.isBlank()) {
            _state.update { it.copy(error = "Enter your email address first.") }
            return
        }
        viewModelScope.launch {
            runCatching { AppGraph.shared.forgotPasswordUseCase.execute(email) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            error = null,
                            passwordResetMessage = "We sent password reset instructions to $email."
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(error = "Couldn't send reset email") }
                }
        }
    }

    fun dismissPasswordResetMessage() {
        _state.update { it.copy(passwordResetMessage = null) }
    }

    fun continueWithGoogle() {
        _state.update {
            it.copy(error = "Google sign-in needs the native provider token wiring before it can continue.")
        }
    }

    fun continueWithApple() {
        _state.update {
            it.copy(error = "Apple sign-in needs the native provider token wiring before it can continue.")
        }
    }

    fun onSocialSignInFailed(provider: String, message: String?) {
        _state.update {
            it.copy(
                loading = false,
                error = "There was an error signing in. Please try again."
            )
        }
    }

    fun onSocialSignInCancelled() {
        _state.update { it.copy(loading = false, error = null) }
    }

    fun completeSocialSignIn(profile: SocialAuthProfile, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                AppGraph.shared.socialLoginUseCase.execute(
                    provider = profile.provider,
                    providerUserId = profile.providerUserId,
                    idToken = profile.idToken,
                    email = profile.email,
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    phone = "",
                    testify = false
                )
            }.onSuccess { session ->
                _state.update { current -> current.copy(loading = false, error = null) }
                ReportedAnalytics.logLogin(profile.provider, session)
                onSuccess()
            }.onFailure { error ->
                Log.e("ReportedAuth", "Social login failed", error)
                _state.update {
                    it.copy(
                        loading = false,
                        error = "There was an error signing in. Please try again."
                    )
                }
            }
        }
    }
}

class RegisterViewModel : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onAction(action: RegisterAction) {
        when (action) {
            is RegisterAction.FieldsChanged -> update(
                firstName = action.firstName ?: _state.value.firstName,
                lastName = action.lastName ?: _state.value.lastName,
                phone = action.phone ?: _state.value.phone,
                email = action.email ?: _state.value.email,
                password = action.password ?: _state.value.password,
                testify = action.testify ?: _state.value.testify
            )
            is RegisterAction.RegisterPressed -> register(action.onSuccess)
            is RegisterAction.SocialSignInFailed -> onSocialSignInFailed(action.provider, action.message)
            RegisterAction.SocialSignInCancelled -> onSocialSignInCancelled()
            is RegisterAction.SocialSignInCompleted -> completeSocialSignIn(action.profile, action.onSuccess)
        }
    }

    fun update(
        firstName: String = _state.value.firstName,
        lastName: String = _state.value.lastName,
        phone: String = _state.value.phone,
        email: String = _state.value.email,
        password: String = _state.value.password,
        testify: Boolean = _state.value.testify
    ) {
        _state.value = _state.value.copy(
            firstName = firstName,
            lastName = lastName,
            phone = phone,
            email = email,
            password = password,
            testify = testify
        )
    }

    fun register(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                AppGraph.shared.registerUseCase.execute(
                    firstName = _state.value.firstName,
                    lastName = _state.value.lastName,
                    phone = _state.value.phone,
                    testify = _state.value.testify,
                    email = _state.value.email,
                    password = _state.value.password
                )
            }.onSuccess { session ->
                _state.update { current -> current.copy(loading = false) }
                ReportedAnalytics.logLogin("password", session)
                onSuccess()
            }.onFailure { error ->
                Log.e("ReportedAuth", "Registration failed", error)
                _state.update {
                    it.copy(
                        loading = false,
                        error = "There was an error creating your account. Please try again."
                    )
                }
            }
        }
    }

    fun continueWithGoogle() {
        _state.update {
            it.copy(error = "Google sign-in needs the native provider token wiring before it can continue.")
        }
    }

    fun continueWithApple() {
        _state.update {
            it.copy(error = "Apple sign-in needs the native provider token wiring before it can continue.")
        }
    }

    fun onSocialSignInFailed(provider: String, message: String?) {
        _state.update {
            it.copy(
                loading = false,
                error = "There was an error signing in. Please try again."
            )
        }
    }

    fun onSocialSignInCancelled() {
        _state.update { it.copy(loading = false, error = null) }
    }

    fun completeSocialSignIn(profile: SocialAuthProfile, onSuccess: () -> Unit) {
        val fallbackFirstName = _state.value.firstName
        val fallbackLastName = _state.value.lastName
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                AppGraph.shared.socialLoginUseCase.execute(
                    provider = profile.provider,
                    providerUserId = profile.providerUserId,
                    idToken = profile.idToken,
                    email = profile.email,
                    firstName = profile.firstName.ifBlank { fallbackFirstName },
                    lastName = profile.lastName.ifBlank { fallbackLastName },
                    phone = _state.value.phone,
                    testify = _state.value.testify
                )
            }.onSuccess { session ->
                _state.update { current -> current.copy(loading = false, error = null) }
                ReportedAnalytics.logLogin(profile.provider, session)
                onSuccess()
            }.onFailure { error ->
                Log.e("ReportedAuth", "Social registration failed", error)
                _state.update {
                    it.copy(
                        loading = false,
                        error = "There was an error signing in. Please try again."
                    )
                }
            }
        }
    }
}

class ReportsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()
    private var activeFilter: ReportFilter? = null

    fun onAction(action: ReportsAction) {
        when (action) {
            ReportsAction.ListChosen -> chooseList()
            ReportsAction.SearchChosen -> chooseSearch()
            ReportsAction.SearchPressed -> search()
            ReportsAction.NextPageRequested -> loadNextPage()
            is ReportsAction.DetailRequested -> loadDetail(action.reportObjectId)
            is ReportsAction.ReportOpened -> openReport(action.reportObjectId)
            is ReportsAction.ReportDeleted -> deleteReport(action.report)
            is ReportsAction.SearchFieldsChanged -> updateSearch(
                license = action.license,
                startDate = action.startDate,
                endDate = action.endDate
            )
        }
    }

    fun chooseList() {
        activeFilter = null
        ReportedAnalytics.logReportsList()
        _state.update {
            it.copy(
                mode = ReportsMode.LIST,
                reports = emptyList(),
                reportDetails = emptyMap(),
                hasMore = false,
                nextSkip = 0,
                error = null
            )
        }
        load(filter = null, append = false)
    }

    fun chooseSearch() {
        activeFilter = null
        _state.update {
            it.copy(
                mode = ReportsMode.SEARCH,
                reports = emptyList(),
                reportDetails = emptyMap(),
                hasMore = false,
                nextSkip = 0,
                error = null
            )
        }
    }

    fun updateSearch(license: String? = null, startDate: String? = null, endDate: String? = null) {
        _state.update {
            it.copy(
                licenseQuery = license ?: it.licenseQuery,
                startDate = startDate ?: it.startDate,
                endDate = endDate ?: it.endDate
            )
        }
    }

    fun search() {
        val current = _state.value
        val filter = ReportFilter(
            license = current.licenseQuery.trim(),
            startDateIso = current.startDate.trim(),
            endDateIso = current.endDate.trim()
        )
        ReportedAnalytics.logReportsSearch(
            hasLicense = filter.license.isNotBlank(),
            hasStartDate = filter.startDateIso.isNotBlank(),
            hasEndDate = filter.endDateIso.isNotBlank()
        )
        activeFilter = filter
        load(filter = filter, append = false)
    }

    fun loadNextPage() {
        val current = _state.value
        if (!current.hasMore || current.loading || current.loadingMore) return
        load(filter = activeFilter, append = true)
    }

    private fun load(filter: ReportFilter?, append: Boolean) {
        viewModelScope.launch {
            val skip = if (append) _state.value.nextSkip else 0
            _state.update {
                it.copy(
                    loading = !append,
                    loadingMore = append,
                    error = null
                )
            }
            runCatching {
                AppGraph.shared.fetchReportsUseCase.execute(filter = filter, skip = skip, forCurrentUser = true)
            }.onSuccess { page ->
                _state.update {
                    val mergedReports = if (append) {
                        (it.reports + page.reports).distinctBy { report -> report.objectId.ifBlank { report.id.toString() } }
                    } else {
                        page.reports
                    }
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        reports = mergedReports,
                        hasMore = page.hasMore,
                        nextSkip = mergedReports.size,
                        reportDetails = if (append) it.reportDetails else emptyMap(),
                        detailLoadingIds = if (append) it.detailLoadingIds else emptySet()
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = error.message ?: "Unable to load reports"
                    )
                }
            }
        }
    }

    fun loadDetail(reportObjectId: String) {
        if (reportObjectId.isBlank()) return
        val current = _state.value
        if (current.reportDetails.containsKey(reportObjectId) || reportObjectId in current.detailLoadingIds) return
        viewModelScope.launch {
            _state.update { it.copy(detailLoadingIds = it.detailLoadingIds + reportObjectId, error = null) }
            runCatching {
                AppGraph.shared.fetchReportDetailUseCase.execute(reportObjectId)
            }.onSuccess { detail ->
                _state.update {
                    it.copy(
                        reportDetails = it.reportDetails + (reportObjectId to detail),
                        detailLoadingIds = it.detailLoadingIds - reportObjectId
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        detailLoadingIds = it.detailLoadingIds - reportObjectId,
                        error = error.message ?: "Unable to load report detail"
                    )
                }
            }
        }
    }

    fun openReport(reportObjectId: String) {
        if (reportObjectId.isBlank()) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    mode = ReportsMode.LIST,
                    loading = true,
                    loadingMore = false,
                    error = null,
                    detailLoadingIds = it.detailLoadingIds + reportObjectId
                )
            }
            runCatching {
                AppGraph.shared.fetchReportDetailUseCase.execute(reportObjectId)
            }.onSuccess { detail ->
                _state.update { current ->
                    val mergedReports = (listOf(detail) + current.reports)
                        .distinctBy { report -> report.reportKey() }
                    current.copy(
                        loading = false,
                        reports = mergedReports,
                        reportDetails = current.reportDetails + (reportObjectId to detail),
                        detailLoadingIds = current.detailLoadingIds - reportObjectId,
                        nextSkip = mergedReports.size
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        detailLoadingIds = it.detailLoadingIds - reportObjectId,
                        error = error.message ?: "Unable to load report"
                    )
                }
            }
        }
    }

    fun deleteReport(report: ReportSummary) {
        val reportKey = report.reportKey()
        if (!report.canDelete || reportKey in _state.value.deletingReportKeys) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    deletingReportKeys = it.deletingReportKeys + reportKey,
                    error = null
                )
            }
            runCatching {
                AppGraph.shared.deleteReportUseCase.execute(report)
            }.onSuccess {
                _state.update {
                    it.copy(
                        reports = it.reports.filterNot { candidate -> candidate.reportKey() == reportKey },
                        reportDetails = it.reportDetails - report.objectId,
                        detailLoadingIds = it.detailLoadingIds - report.objectId,
                        deletingReportKeys = it.deletingReportKeys - reportKey
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        deletingReportKeys = it.deletingReportKeys - reportKey,
                        error = error.message ?: "Unable to delete report"
                    )
                }
            }
        }
    }
}

class ProfileViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.Load -> load()
            ProfileAction.ToggleEditing -> toggleEditing()
            is ProfileAction.SavePressed -> save(action.onSaved)
            is ProfileAction.ThemeModeChanged -> setThemeMode(action.mode)
            is ProfileAction.FieldsChanged -> update(
                firstName = action.firstName ?: _state.value.firstName,
                lastName = action.lastName ?: _state.value.lastName,
                phone = action.phone ?: _state.value.phone,
                email = action.email ?: _state.value.email,
                testify = action.testify ?: _state.value.testify
            )
        }
    }

    fun load() {
        viewModelScope.launch {
            val session = AppGraph.shared.loadSessionUseCase.execute()
            val themeMode = AppGraph.shared.loadAppThemeModeUseCase.execute()
            if (session != null) {
                _state.value = ProfileUiState(
                    firstName = session.firstName,
                    lastName = session.lastName,
                    phone = session.phone,
                    email = session.email,
                    testify = session.testify,
                    themeMode = themeMode
                )
            } else {
                _state.update { it.copy(themeMode = themeMode) }
            }
        }
    }

    fun update(
        firstName: String = _state.value.firstName,
        lastName: String = _state.value.lastName,
        phone: String = _state.value.phone,
        email: String = _state.value.email,
        testify: Boolean = _state.value.testify
    ) {
        _state.update {
            it.copy(
                firstName = firstName,
                lastName = lastName,
                phone = phone,
                email = email,
                testify = testify
            )
        }
    }

    fun toggleEditing() = _state.update { it.copy(editing = !it.editing, error = null) }

    fun setThemeMode(mode: AppThemeMode) {
        _state.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            AppGraph.shared.saveAppThemeModeUseCase.execute(mode)
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                AppGraph.shared.updateProfileUseCase.execute(
                    email = _state.value.email,
                    phone = _state.value.phone,
                    firstName = _state.value.firstName,
                    lastName = _state.value.lastName,
                    testify = _state.value.testify
                )
            }.onSuccess {
                _state.update { current -> current.copy(loading = false, editing = false) }
                onSaved()
            }.onFailure { error ->
                _state.update { current -> current.copy(loading = false, error = error.message ?: "Couldn't update profile") }
            }
        }
    }
}

class ThemeViewModel : ViewModel() {
    private val _state = MutableStateFlow(ThemeUiState())
    val state: StateFlow<ThemeUiState> = _state.asStateFlow()

    fun onAction(action: ThemeAction) {
        when (action) {
            ThemeAction.Load -> load()
            is ThemeAction.ModeChanged -> update(action.mode)
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ThemeUiState(
                mode = AppGraph.shared.loadAppThemeModeUseCase.execute()
            )
        }
    }

    fun update(mode: AppThemeMode) {
        _state.value = ThemeUiState(mode = mode)
        viewModelScope.launch {
            AppGraph.shared.saveAppThemeModeUseCase.execute(mode)
        }
    }
}

class ComposerViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        ComposerUiState(
            complaintCategories = Catalogs.complaintCategories,
            showComplaintImages = ReportedRemoteConfig.snapshot.value.showComplaintImages
        )
    )
    val state: StateFlow<ComposerUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<ComposerEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ComposerEvent> = _events.asSharedFlow()
    private var vehicleClassificationDebugJob: Job? = null
    private var vehicleDetailsLookupJob: Job? = null

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

    fun onAction(action: ComposerAction) {
        when (action) {
            ComposerAction.LoadDraft -> loadDraft()
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
            refreshVehicleLookups()
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
        refreshVehicleLookups()
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
        refreshVehicleLookups()
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
        refreshVehicleLookups()
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
        refreshVehicleLookups()
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
            refreshVehicleLookups()
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

    private fun refreshVehicleClassificationDebugNote() {
        if (!BuildConfig.DEBUG) return
        val normalizedPlate = PlatePatternClassifier.normalizePlateInput(_state.value.plate)
            .take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH)
        if (
            normalizedPlate.length < 2 ||
            (normalizedPlate.firstOrNull() != 'T' && normalizedPlate.firstOrNull() != 'Y')
        ) {
            applyVehicleClassificationDebugNote(null)
            return
        }

        vehicleClassificationDebugJob?.cancel()
        vehicleClassificationDebugJob = viewModelScope.launch {
            delay(350)
            val debugNote = runCatching {
                AppGraph.shared.previewVehicleEnrichmentDebugNoteUseCase.execute(normalizedPlate)
            }.getOrNull()
            val latestPlate = PlatePatternClassifier.normalizePlateInput(_state.value.plate)
                .take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH)
            if (latestPlate == normalizedPlate) {
                applyVehicleClassificationDebugNote(debugNote)
            }
        }
    }

    private fun refreshVehicleLookups() {
        refreshVehicleDetailsLookup()
        refreshVehicleClassificationDebugNote()
    }

    private fun refreshVehicleDetailsLookup() {
        val previousDetails = _state.value.vehicleLookupDetails
        val lookupPlate = PlatePatternClassifier.normalizePlateInput(_state.value.plate)
            .takeIf { it.length in 2..PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH }
        val lookupState = _state.value.plateRegion.trim().uppercase()
            .takeIf { it.matches(Regex("^[A-Z]{2}$")) }
        vehicleDetailsLookupJob?.cancel()
        if (previousDetails != null) {
            _state.update { current ->
                current.copy(
                    philadelphiaMobilityAccessDetails = current.philadelphiaMobilityAccessDetails
                        .withoutVehicleLookupPrefill(previousDetails)
                )
            }
        }
        if (lookupPlate == null || lookupState == null) {
            _state.update {
                it.copy(
                    vehicleLookupDetails = null,
                    vehicleLookupInFlight = false,
                    vehicleLookupMessage = null
                )
            }
            return
        }

        val lookupKey = "$lookupState:$lookupPlate"
        _state.update {
            it.copy(
                vehicleLookupDetails = null,
                vehicleLookupInFlight = true,
                vehicleLookupMessage = null
            )
        }
        vehicleDetailsLookupJob = viewModelScope.launch {
            delay(500)
            try {
                val details = AppGraph.shared.lookupVehicleDetailsUseCase.execute(lookupPlate, lookupState)
                if (_state.value.vehicleLookupKey() != lookupKey) return@launch
                _state.update { current ->
                    current.copy(
                        vehicleLookupDetails = details,
                        vehicleLookupInFlight = false,
                        vehicleLookupMessage = if (details == null) {
                            "No vehicle details were found for this plate."
                        } else {
                            null
                        },
                        philadelphiaMobilityAccessDetails = details?.let { vehicle ->
                            current.philadelphiaMobilityAccessDetails.copy(
                                vehicleMake = current.philadelphiaMobilityAccessDetails.vehicleMake
                                    .ifBlank { vehicle.vehicleMake.orEmpty() },
                                vehicleModel = current.philadelphiaMobilityAccessDetails.vehicleModel
                                    .ifBlank { vehicle.vehicleModel.orEmpty() },
                                bodyStyle = current.philadelphiaMobilityAccessDetails.bodyStyle
                                    .ifBlank { vehicle.vehicleBody.orEmpty() }
                            )
                        } ?: current.philadelphiaMobilityAccessDetails
                    )
                }
                if (details != null) persistDraft()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_state.value.vehicleLookupKey() != lookupKey) return@launch
                _state.update {
                    it.copy(
                        vehicleLookupDetails = null,
                        vehicleLookupInFlight = false,
                        vehicleLookupMessage = "Vehicle details are unavailable right now. You can still submit the report."
                    )
                }
            }
        }
    }

    private fun applyVehicleClassificationDebugNote(debugNote: String?) {
        if (!BuildConfig.DEBUG) return
        _state.update { current ->
            val nextLines = if (current.notes.isBlank()) {
                mutableListOf()
            } else {
                current.notes
                    .lineSequence()
                    .filterNot { it.startsWith(VehicleClassificationDebugPrefix) }
                    .toMutableList()
            }
            debugNote?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(nextLines::add)
            current.copy(notes = nextLines.joinToString("\n"))
        }
        persistDraft()
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
            refreshVehicleLookups()
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
        refreshVehicleLookups()
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
                val mediaFiles = uploadMediaForSubmission(snapshot, submittedMedia)
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

    private suspend fun uploadMediaForSubmission(
        snapshot: ComposerUiState,
        submittedMedia: List<SubmissionMedia>
    ): List<SubmitReportMediaFile> =
        if (snapshot.isPhiladelphiaSubmission()) {
            uploadPhiladelphiaMedia(submittedMedia)
        } else {
            uploadParseMedia(submittedMedia, "Uploading media")
        }

    private suspend fun uploadPhiladelphiaMedia(
        submittedMedia: List<SubmissionMedia>
    ): List<SubmitReportMediaFile> {
        require(submittedMedia.none { it.isVideo }) { PhiladelphiaSubmissionVideoMessage }
        require(submittedMedia.size <= PhiladelphiaSubmissionMediaCount) { PhiladelphiaSubmissionMediaMessage }
        return uploadParseMedia(submittedMedia, "Uploading Philadelphia photos")
    }

    private suspend fun uploadParseMedia(
        submittedMedia: List<SubmissionMedia>,
        messagePrefix: String
    ): List<SubmitReportMediaFile> =
        ParseMediaUploader.uploadAll(
            AppGraph.applicationContext,
            submittedMedia
        ) { progress ->
            val totalFiles = progress.totalFiles.coerceAtLeast(1)
            val overall = ((progress.currentFileIndex.toFloat() + progress.fraction) / totalFiles.toFloat())
                .coerceIn(0f, 1f)
            _state.update {
                it.copy(
                    submitProgress = overall * 0.82f,
                    submitMessage = "$messagePrefix (${(progress.fraction * 100).toInt()}%)"
                )
            }
        }

    fun prepareSubmit(): Boolean {
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
        val validationErrors = validateSubmission(_state.value)
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

    private fun validateSubmission(state: ComposerUiState): ComposerValidationErrors =
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
}

private fun DraftMedia.toSubmissionMedia() = SubmissionMedia(
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    isVideo = isVideo
)

private fun SubmissionMedia.toDraftMedia() = DraftMedia(
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    isVideo = isVideo
)

private fun DraftPlateCandidate.toPlateCandidate() = PlateCandidate(
    plate = plate,
    confidence = confidence,
    rawPlateText = rawPlateText,
    wasPlateCorrected = wasPlateCorrected,
    state = state,
    stateConfidence = stateConfidence,
    plateType = plateType,
    plateTypeLabel = plateTypeLabel,
    focalPointX = focalPointX,
    focalPointY = focalPointY,
    boundsLeft = boundsLeft,
    boundsTop = boundsTop,
    boundsRight = boundsRight,
    boundsBottom = boundsBottom,
    rotationDegrees = rotationDegrees,
    cornerPoints = cornerPoints,
    sourceImageWidth = sourceImageWidth,
    sourceImageHeight = sourceImageHeight,
    thumbnailUri = thumbnailUri,
    videoFramePreviewUri = videoFramePreviewUri,
    videoFrameTimeMs = videoFrameTimeMs
)

private fun PlateCandidate.toDraftPlateCandidate() = DraftPlateCandidate(
    plate = plate,
    confidence = confidence,
    rawPlateText = rawPlateText,
    wasPlateCorrected = wasPlateCorrected,
    state = state,
    stateConfidence = stateConfidence,
    plateType = plateType,
    plateTypeLabel = plateTypeLabel,
    focalPointX = focalPointX,
    focalPointY = focalPointY,
    boundsLeft = boundsLeft,
    boundsTop = boundsTop,
    boundsRight = boundsRight,
    boundsBottom = boundsBottom,
    rotationDegrees = rotationDegrees,
    cornerPoints = cornerPoints,
    sourceImageWidth = sourceImageWidth,
    sourceImageHeight = sourceImageHeight,
    thumbnailUri = thumbnailUri,
    videoFramePreviewUri = videoFramePreviewUri,
    videoFrameTimeMs = videoFrameTimeMs
)

private fun ComposerUiState.hasMedia(media: SubmissionMedia): Boolean =
    primaryMedia?.uri == media.uri || extraMedia.any { it.uri == media.uri }

private fun ComposerUiState.vehicleLookupKey(): String? {
    val normalizedPlate = PlatePatternClassifier.normalizePlateInput(plate)
        .takeIf { it.length in 2..PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH }
        ?: return null
    val normalizedState = plateRegion.trim().uppercase()
        .takeIf { it.matches(Regex("^[A-Z]{2}$")) }
        ?: return null
    return "$normalizedState:$normalizedPlate"
}

private fun PhiladelphiaMobilityAccessDetails.withoutVehicleLookupPrefill(
    details: VehicleLookupDetails
): PhiladelphiaMobilityAccessDetails = copy(
    vehicleMake = vehicleMake.takeUnless {
        it.equals(details.vehicleMake, ignoreCase = true)
    }.orEmpty(),
    vehicleModel = vehicleModel.takeUnless {
        it.equals(details.vehicleModel, ignoreCase = true)
    }.orEmpty(),
    bodyStyle = bodyStyle.takeUnless {
        it.equals(details.vehicleBody, ignoreCase = true)
    }.orEmpty()
)

private const val MaxSubmissionMediaCount = 3
private const val MaxSubmissionVideoCount = 1
private const val MaxSubmissionMediaMessage = "You can attach up to 3 photos or videos."
private const val MaxSubmissionVideoMessage = "You can attach no more than 1 video."
private const val DuplicateSubmissionMediaMessage = "That photo or video is already attached."
private const val VehicleClassificationDebugPrefix = "[DEBUG] Vehicle classification"

private data class SubmitAnalyticsPayload(
    val objectId: String,
    val county: String,
    val plateRegion: String,
    val complaintCount: Int,
    val mediaCount: Int,
    val hasVideo: Boolean,
    val mediaToSubmitMillis: Long?
)

private fun inferCounty(address: String): String {
    val normalized = address.lowercase(Locale.US)
    return when {
        normalized.contains("manhattan") ||
            normalized.contains("new york, ny") ||
            normalized.contains("new york ny") -> "New York County"
        normalized.contains("brooklyn") ||
            normalized.contains("kings county") -> "Kings County"
        normalized.contains("queens") -> "Queens County"
        normalized.contains("bronx") -> "Bronx County"
        normalized.contains("staten island") ||
            normalized.contains("richmond county") -> "Richmond County"
        normalized.contains("philadelphia") ||
            normalized.contains("philly") -> "Philadelphia County"
        else -> "unknown"
    }
}

private fun ComposerUiState.mediaItems(): List<SubmissionMedia> =
    listOfNotNull(primaryMedia) + extraMedia

private fun ComposerUiState.withMediaAddedTiming(previous: ComposerUiState): ComposerUiState {
    if (
        previous.mediaItems().isEmpty() &&
        previous.firstMediaAddedElapsedRealtimeMs == null &&
        mediaItems().isNotEmpty()
    ) {
        val items = mediaItems()
        ReportedAnalytics.logReportMediaAdded(
            surface = "new_report",
            mediaCount = items.size,
            hasVideo = items.any { it.isVideo }
        )
        return copy(firstMediaAddedElapsedRealtimeMs = SystemClock.elapsedRealtime())
    }
    return this
}

private fun ComposerUiState.mediaToSubmitMillis(): Long? =
    firstMediaAddedElapsedRealtimeMs?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) }

private fun ComposerUiState.mediaLimitErrorFor(
    media: SubmissionMedia,
    replacingPrimary: Boolean
): String? {
    val existingMedia = if (replacingPrimary) extraMedia else mediaItems()
    val isPhiladelphia = isPhiladelphiaSubmission()
    return when {
        existingMedia.any { it.uri == media.uri } -> DuplicateSubmissionMediaMessage
        isPhiladelphia && media.isVideo -> PhiladelphiaSubmissionVideoMessage
        isPhiladelphia && existingMedia.any { it.isVideo } -> PhiladelphiaSubmissionVideoMessage
        isPhiladelphia && existingMedia.size + 1 > PhiladelphiaSubmissionMediaCount -> PhiladelphiaSubmissionMediaMessage
        existingMedia.size + 1 > MaxSubmissionMediaCount -> MaxSubmissionMediaMessage
        media.isVideo && existingMedia.count { it.isVideo } >= MaxSubmissionVideoCount -> MaxSubmissionVideoMessage
        else -> null
    }
}

private fun ComposerUiState.withPhiladelphiaMediaValidation(): ComposerUiState {
    if (!isPhiladelphiaSubmission()) return this
    val mediaError = when {
        mediaItems().any { it.isVideo } -> PhiladelphiaSubmissionVideoMessage
        mediaItems().size > PhiladelphiaSubmissionMediaCount -> PhiladelphiaSubmissionMediaMessage
        else -> validationErrors.media
    }
    return copy(validationErrors = validationErrors.copy(media = mediaError))
}

private fun ReportSummary.reportKey(): String =
    objectId.ifBlank { id.toString() }

private fun ReportDraft.hasComplaintData(): Boolean =
    selectedComplaintId != null ||
        complaintIds.isNotEmpty() ||
        plate.isNotBlank() ||
        address.isNotBlank() ||
        description.isNotBlank() ||
        notes.isNotBlank() ||
        philadelphiaMobilityAccessDetails?.hasAnyValue == true ||
        occurredAtIso.isNotBlank() ||
        primaryMedia != null ||
        extraMedia.isNotEmpty() ||
        latitude != null ||
        longitude != null

private fun ComposerUiState.hasComplaintData(): Boolean =
    selectedComplaintId != null ||
        selectedComplaintIds.isNotEmpty() ||
        plate.isNotBlank() ||
        address.isNotBlank() ||
        addressQuery.isNotBlank() ||
        description.isNotBlank() ||
        notes.isNotBlank() ||
        philadelphiaMobilityAccessDetails.hasAnyValue ||
        occurredAtIso.isNotBlank() ||
        primaryMedia != null ||
        extraMedia.isNotEmpty() ||
        latitude != null ||
        longitude != null

private fun PhiladelphiaMobilityAccessDetails.withAddressSuggestion(
    suggestion: AddressSuggestion
): PhiladelphiaMobilityAccessDetails =
    copy(
        blockNumber = suggestion.blockNumber ?: blockNumber,
        streetName = suggestion.streetName ?: streetName,
        zipCode = suggestion.zipCode ?: zipCode
    )

private fun PhiladelphiaMobilityAccessDetails.prefilledFrom(
    vehicleDescription: VehicleDescription
): PhiladelphiaMobilityAccessDetails =
    copy(
        vehicleMake = vehicleMake.ifBlank {
            PhiladelphiaMobilityAccessCatalogs.canonicalVehicleMake(vehicleDescription.make).orEmpty()
        },
        vehicleModel = vehicleModel.ifBlank { vehicleDescription.model.orEmpty() },
        vehicleColor = vehicleColor.ifBlank {
            vehicleDescription.color
                ?.let { detectedColor ->
                    PhiladelphiaMobilityAccessCatalogs.vehicleColors
                        .firstOrNull { it.equals(detectedColor, ignoreCase = true) }
                }
                .orEmpty()
        }
    )

private fun ComposerUiState.submittablePhiladelphiaMobilityAccessDetails(): PhiladelphiaMobilityAccessDetails? {
    val submitAddress = addressQuery.ifBlank { address }
    if (!CityReportingRules.isPhiladelphiaReport(latitude, longitude, submitAddress)) return null
    return philadelphiaMobilityAccessDetails
        .takeIf { it.hasAnyValue }
        ?: PhiladelphiaMobilityAccessDetails()
}
