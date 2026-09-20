package com.reported.nativeandroid.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reported.nativeandroid.R
import com.reported.nativeandroid.ai.ReportedAiModelStore
import com.reported.nativeandroid.app.UdfStore
import com.reported.nativeandroid.di.AppGraph
import com.reported.nativeandroid.di.MessageResolver
import com.reported.nativeandroid.media.AutoReportThresholds
import com.reported.nativeandroid.media.MediaScannerScheduler
import com.reported.nativeandroid.media.MediaScannerSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val backgroundScanningSupported: Boolean = false,
    val mediaScannerEnabled: Boolean = false,
    val offlineProcessingEnabled: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val autoReportPlateThreshold: Float = AutoReportThresholds.DEFAULT_PLATE_CONFIDENCE,
    val autoReportStateThreshold: Float = AutoReportThresholds.DEFAULT_STATE_CONFIDENCE,
    val autoReportComplaintThreshold: Float = AutoReportThresholds.DEFAULT_COMPLAINT_CONFIDENCE,
    val reportedAiInstalled: Boolean = false,
    val reportedAiModelSize: String? = null,
    val reportedAiAccelerationMessage: String = "",
    val reportedAiDownloadSizeLabel: String = ReportedAiModelStore.compactDownloadSizeLabel,
    val reportedAiDownloading: Boolean = false,
    val reportedAiDownloadProgress: Float? = null,
    val reportedAiMessage: String? = null
)

sealed interface SettingsAction {
    data object Load : SettingsAction
    data class NotificationsChanged(val enabled: Boolean) : SettingsAction
    data class NotificationPermissionResult(val granted: Boolean) : SettingsAction
    data class OfflineProcessingChanged(val enabled: Boolean) : SettingsAction
    data class MediaScannerChanged(val enabled: Boolean) : SettingsAction
    data class MediaScannerPermissionResult(val grants: Map<String, Boolean>) : SettingsAction
    data class PlateThresholdChanged(val value: Float) : SettingsAction
    data class StateThresholdChanged(val value: Float) : SettingsAction
    data class ComplaintThresholdChanged(val value: Float) : SettingsAction
    data object ResetThresholds : SettingsAction
    data object InstallReportedAi : SettingsAction
    data object DeleteReportedAi : SettingsAction
    data object RoboflowProjectPressed : SettingsAction
}

sealed interface SettingsEffect {
    data class RequestNotificationPermission(val permission: String) : SettingsEffect
    data class RequestMediaScannerPermissions(val permissions: Array<String>) : SettingsEffect {
        override fun equals(other: Any?): Boolean =
            other is RequestMediaScannerPermissions && permissions.contentEquals(other.permissions)

        override fun hashCode(): Int = permissions.contentHashCode()
    }
    data class OpenUrl(val url: String) : SettingsEffect
}

class SettingsViewModel internal constructor(
    application: Application,
    private val messages: MessageResolver
) :
    AndroidViewModel(application),
    UdfStore<SettingsUiState, SettingsAction> {

    constructor(application: Application) : this(application, AppGraph.messages)

    private val appContext = application.applicationContext
    private val _state = MutableStateFlow(SettingsUiState())
    override val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        onAction(SettingsAction.Load)
    }

    override fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.Load -> load()
            is SettingsAction.NotificationsChanged -> setNotificationsEnabled(action.enabled)
            is SettingsAction.NotificationPermissionResult -> onNotificationPermissionResult(action.granted)
            is SettingsAction.OfflineProcessingChanged -> setOfflineProcessingEnabled(action.enabled)
            is SettingsAction.MediaScannerChanged -> setMediaScannerEnabled(action.enabled)
            is SettingsAction.MediaScannerPermissionResult -> onMediaScannerPermissionResult(action.grants)
            is SettingsAction.PlateThresholdChanged -> setPlateThreshold(action.value)
            is SettingsAction.StateThresholdChanged -> setStateThreshold(action.value)
            is SettingsAction.ComplaintThresholdChanged -> setComplaintThreshold(action.value)
            SettingsAction.ResetThresholds -> resetThresholds()
            SettingsAction.InstallReportedAi -> installReportedAi()
            SettingsAction.DeleteReportedAi -> deleteReportedAi()
            SettingsAction.RoboflowProjectPressed -> emit(
                SettingsEffect.OpenUrl(REPORTED_ROBOFLOW_PROJECT_URL)
            )
        }
    }

    private fun load() {
        _state.value = settingsState(
            reportedAiDownloading = _state.value.reportedAiDownloading,
            reportedAiDownloadProgress = _state.value.reportedAiDownloadProgress,
            reportedAiMessage = _state.value.reportedAiMessage
        )
    }

    private fun setNotificationsEnabled(enabled: Boolean) {
        if (enabled) {
            val permission = MediaScannerSettings.notificationPermission()
            if (permission != null && !MediaScannerSettings.hasNotificationPermission(appContext)) {
                MediaScannerSettings.setNotificationsEnabled(appContext, true)
                emit(SettingsEffect.RequestNotificationPermission(permission))
                return
            }
        }
        MediaScannerSettings.setNotificationsEnabled(appContext, enabled)
        _state.update { it.copy(notificationsEnabled = enabled) }
    }

    private fun onNotificationPermissionResult(granted: Boolean) {
        val enabled = granted || MediaScannerSettings.hasNotificationPermission(appContext)
        MediaScannerSettings.setNotificationsEnabled(appContext, enabled)
        MediaScannerSettings.markNotificationPermissionAsked(appContext)
        _state.update { it.copy(notificationsEnabled = enabled) }
    }

    private fun setOfflineProcessingEnabled(enabled: Boolean) {
        MediaScannerSettings.setOfflineProcessingEnabled(appContext, enabled)
        val resolved = MediaScannerSettings.isOfflineProcessingEnabled(appContext)
        _state.update { it.copy(offlineProcessingEnabled = resolved) }
        if (resolved && _state.value.mediaScannerEnabled) {
            MediaScannerScheduler.scanNow(appContext)
        }
    }

    private fun setMediaScannerEnabled(enabled: Boolean) {
        if (!enabled) {
            MediaScannerSettings.setEnabled(appContext, false)
            _state.update { it.copy(mediaScannerEnabled = false) }
            return
        }
        if (!MediaScannerSettings.supportsBackgroundLibraryScanning()) {
            MediaScannerSettings.setEnabled(appContext, false)
            _state.update { it.copy(backgroundScanningSupported = false, mediaScannerEnabled = false) }
            return
        }
        if (!MediaScannerSettings.hasRequiredPermissions(appContext)) {
            emit(SettingsEffect.RequestMediaScannerPermissions(MediaScannerSettings.requiredPermissions()))
            return
        }
        enableMediaScanner()
    }

    private fun onMediaScannerPermissionResult(grants: Map<String, Boolean>) {
        if (!MediaScannerSettings.supportsBackgroundLibraryScanning()) {
            MediaScannerSettings.setEnabled(appContext, false)
            _state.update { it.copy(backgroundScanningSupported = false, mediaScannerEnabled = false) }
            return
        }
        val granted = MediaScannerSettings.requiredPermissions().all { permission ->
            grants[permission] == true || MediaScannerSettings.hasRequiredPermissions(appContext)
        }
        if (granted) enableMediaScanner() else {
            MediaScannerSettings.setEnabled(appContext, false)
            _state.update { it.copy(mediaScannerEnabled = false) }
        }
    }

    private fun enableMediaScanner() {
        MediaScannerSettings.setEnabled(appContext, true)
        val enabled = MediaScannerSettings.isEnabled(appContext)
        _state.update { it.copy(mediaScannerEnabled = enabled) }
        if (enabled && _state.value.offlineProcessingEnabled) {
            MediaScannerScheduler.scanNow(appContext)
        }
    }

    private fun setPlateThreshold(value: Float) {
        AutoReportThresholds.setPlateConfidence(appContext, value)
        AutoReportThresholds.setPostInferencePlateConfidence(appContext, value)
        _state.update { it.copy(autoReportPlateThreshold = AutoReportThresholds.plateConfidence(appContext)) }
    }

    private fun setStateThreshold(value: Float) {
        AutoReportThresholds.setStateConfidence(appContext, value)
        _state.update { it.copy(autoReportStateThreshold = AutoReportThresholds.stateConfidence(appContext)) }
    }

    private fun setComplaintThreshold(value: Float) {
        AutoReportThresholds.setComplaintConfidence(appContext, value)
        _state.update { it.copy(autoReportComplaintThreshold = AutoReportThresholds.complaintConfidence(appContext)) }
    }

    private fun resetThresholds() {
        AutoReportThresholds.reset(appContext)
        _state.update {
            it.copy(
                autoReportPlateThreshold = AutoReportThresholds.plateConfidence(appContext),
                autoReportStateThreshold = AutoReportThresholds.stateConfidence(appContext),
                autoReportComplaintThreshold = AutoReportThresholds.complaintConfidence(appContext)
            )
        }
    }

    private fun installReportedAi() {
        if (_state.value.reportedAiDownloading) return
        if (ReportedAiModelStore.isModelInstalled(appContext)) {
            load()
            return
        }
        _state.update {
            it.copy(
                reportedAiDownloading = true,
                reportedAiDownloadProgress = null,
                reportedAiMessage = null
            )
        }
        viewModelScope.launch {
            ReportedAiModelStore.downloadModel(appContext, messages) { downloadedBytes, totalBytes ->
                _state.update {
                    it.copy(
                        reportedAiDownloadProgress = if (totalBytes > 0L) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                    )
                }
            }.fold(
                onSuccess = {
                    _state.value = settingsState(
                        reportedAiDownloadProgress = 1f,
                        reportedAiMessage = messages.resolve(R.string.reported_ai_installed_message)
                    )
                },
                onFailure = { error ->
                    _state.value = settingsState(
                        reportedAiMessage = error.message ?: messages.resolve(R.string.reported_ai_install_error)
                    )
                }
            )
        }
    }

    private fun deleteReportedAi() {
        if (_state.value.reportedAiDownloading) return
        _state.update { it.copy(reportedAiMessage = null) }
        viewModelScope.launch {
            val deleted = ReportedAiModelStore.deleteModel(appContext)
            _state.value = settingsState(
                reportedAiMessage = if (deleted > 0) {
                    messages.resolve(R.string.reported_ai_deleted_message)
                } else {
                    messages.resolve(R.string.reported_ai_not_installed_message)
                }
            )
        }
    }

    private fun settingsState(
        reportedAiDownloading: Boolean = false,
        reportedAiDownloadProgress: Float? = null,
        reportedAiMessage: String? = null
    ) = SettingsUiState(
        backgroundScanningSupported = MediaScannerSettings.supportsBackgroundLibraryScanning(),
        mediaScannerEnabled = MediaScannerSettings.isEnabled(appContext),
        offlineProcessingEnabled = MediaScannerSettings.isOfflineProcessingEnabled(appContext),
        notificationsEnabled = MediaScannerSettings.isNotificationsEnabled(appContext) &&
            MediaScannerSettings.hasNotificationPermission(appContext),
        autoReportPlateThreshold = AutoReportThresholds.plateConfidence(appContext),
        autoReportStateThreshold = AutoReportThresholds.stateConfidence(appContext),
        autoReportComplaintThreshold = AutoReportThresholds.complaintConfidence(appContext),
        reportedAiInstalled = ReportedAiModelStore.isModelInstalled(appContext),
        reportedAiModelSize = ReportedAiModelStore.installedModelSizeLabel(appContext),
        reportedAiAccelerationMessage = ReportedAiModelStore.accelerationMessage(appContext, messages),
        reportedAiDownloadSizeLabel = ReportedAiModelStore.compactDownloadSizeLabel,
        reportedAiDownloading = reportedAiDownloading,
        reportedAiDownloadProgress = reportedAiDownloadProgress,
        reportedAiMessage = reportedAiMessage
    )

    private fun emit(effect: SettingsEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val REPORTED_ROBOFLOW_PROJECT_URL = "https://app.roboflow.com/reported/reported/13"
    }
}
