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


class RegisterViewModel : ViewModel(), UdfStore<RegisterUiState, RegisterAction> {
    private val _state = MutableStateFlow(RegisterUiState())
    override val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    override fun onAction(action: RegisterAction) {
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
