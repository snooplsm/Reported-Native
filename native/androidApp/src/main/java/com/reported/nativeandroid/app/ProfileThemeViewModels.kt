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


class ProfileViewModel : ViewModel(), UdfStore<ProfileUiState, ProfileAction> {
    private val _state = MutableStateFlow(ProfileUiState())
    override val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    override fun onAction(action: ProfileAction) {
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

class ThemeViewModel : ViewModel(), UdfStore<ThemeUiState, ThemeAction> {
    private val _state = MutableStateFlow(ThemeUiState())
    override val state: StateFlow<ThemeUiState> = _state.asStateFlow()

    override fun onAction(action: ThemeAction) {
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
