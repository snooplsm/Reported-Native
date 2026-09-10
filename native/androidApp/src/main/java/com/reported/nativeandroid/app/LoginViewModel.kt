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


class LoginViewModel : ViewModel(), UdfStore<LoginUiState, LoginAction> {
    private val _state = MutableStateFlow(LoginUiState())
    override val state: StateFlow<LoginUiState> = _state.asStateFlow()

    override fun onAction(action: LoginAction) {
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
