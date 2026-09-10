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


class SessionViewModel : ViewModel(), UdfStore<SessionUiState, SessionAction> {
    private val _state = MutableStateFlow(SessionUiState())
    override val state: StateFlow<SessionUiState> = _state.asStateFlow()

    override fun onAction(action: SessionAction) {
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
