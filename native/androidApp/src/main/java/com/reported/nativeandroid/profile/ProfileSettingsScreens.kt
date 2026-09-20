package com.reported.nativeandroid.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reported.nativeandroid.R
import com.reported.nativeandroid.app.ProfileAction
import com.reported.nativeandroid.app.ProfileViewModel
import com.reported.nativeandroid.media.AutoReportThresholds
import com.reported.nativeandroid.screens.ComplaintChipGroup
import com.reported.nativeandroid.screens.LoginRequiredScreen
import com.reported.nativeandroid.screens.MessageCard
import com.reported.nativeandroid.screens.PrimaryButton
import com.reported.nativeandroid.screens.ReportedField
import com.reported.nativeandroid.screens.ScreenSection
import com.reported.shared.model.AppThemeMode
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    isAuthorized: Boolean,
    onRequireLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenMenu: () -> Unit,
    vm: ProfileViewModel = viewModel()
) {
    if (!isAuthorized) {
        LoginRequiredScreen(
            title = stringResource(R.string.profile_sign_in_title),
            message = stringResource(R.string.profile_sign_in_message),
            onLogin = onRequireLogin
        )
        return
    }

    val state by vm.state.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.onAction(ProfileAction.Load) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.nav_profile)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                IconButton(onClick = onOpenMenu) {
                    com.reported.nativeandroid.app.ShellMenuIcon()
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 9.dp, end = 9.dp, top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ScreenSection(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    state.error?.let { MessageCard(it) }
                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ReportedField(
                                stringResource(R.string.field_first_name),
                                state.firstName,
                                { vm.onAction(ProfileAction.FieldsChanged(firstName = it)) },
                                modifier = Modifier.weight(1f),
                                enabled = state.editing
                            )
                            ReportedField(
                                stringResource(R.string.field_last_name),
                                state.lastName,
                                { vm.onAction(ProfileAction.FieldsChanged(lastName = it)) },
                                modifier = Modifier.weight(1f),
                                enabled = state.editing
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ReportedField(
                                stringResource(R.string.field_phone),
                                state.phone,
                                { vm.onAction(ProfileAction.FieldsChanged(phone = it)) },
                                modifier = Modifier.weight(1f),
                                enabled = state.editing
                            )
                            ReportedField(
                                stringResource(R.string.field_email),
                                state.email,
                                { vm.onAction(ProfileAction.FieldsChanged(email = it)) },
                                modifier = Modifier.weight(1f),
                                enabled = state.editing
                            )
                        }
                    } else {
                        ReportedField(stringResource(R.string.field_first_name), state.firstName, { vm.onAction(ProfileAction.FieldsChanged(firstName = it)) }, enabled = state.editing)
                        ReportedField(stringResource(R.string.field_last_name), state.lastName, { vm.onAction(ProfileAction.FieldsChanged(lastName = it)) }, enabled = state.editing)
                        ReportedField(stringResource(R.string.field_phone), state.phone, { vm.onAction(ProfileAction.FieldsChanged(phone = it)) }, enabled = state.editing)
                        ReportedField(stringResource(R.string.field_email), state.email, { vm.onAction(ProfileAction.FieldsChanged(email = it)) }, enabled = state.editing)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = state.editing) { vm.onAction(ProfileAction.FieldsChanged(testify = !state.testify)) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = state.testify,
                            onCheckedChange = { vm.onAction(ProfileAction.FieldsChanged(testify = it)) },
                            enabled = state.editing
                        )
                        Text(stringResource(R.string.testify_by_phone))
                    }
                    if (state.editing) {
                        PrimaryButton(stringResource(R.string.action_save), onClick = { vm.onAction(ProfileAction.SavePressed()) }, enabled = !state.loading)
                    } else {
                        PrimaryButton(stringResource(R.string.profile_edit), onClick = { vm.onAction(ProfileAction.ToggleEditing) })
                    }
                    Text(
                        text = stringResource(R.string.profile_logout),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onLogout)
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onOpenMenu: () -> Unit,
    vm: ProfileViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        settingsViewModel.onAction(SettingsAction.NotificationPermissionResult(granted))
    }
    val mediaScannerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        settingsViewModel.onAction(SettingsAction.MediaScannerPermissionResult(grants))
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.onAction(ProfileAction.Load) }
    androidx.compose.runtime.LaunchedEffect(settingsViewModel) {
        settingsViewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.RequestNotificationPermission ->
                    notificationPermissionLauncher.launch(effect.permission)
                is SettingsEffect.RequestMediaScannerPermissions ->
                    mediaScannerPermissionLauncher.launch(effect.permissions)
                is SettingsEffect.OpenUrl -> uriHandler.openUri(effect.url)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.nav_settings)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                IconButton(onClick = onOpenMenu) {
                    com.reported.nativeandroid.app.ShellMenuIcon()
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 9.dp, end = 9.dp, top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ScreenSection(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_appearance),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    ComplaintChipGroup(
                        selectedIds = listOf(state.themeMode.name),
                        options = listOf(
                            AppThemeMode.SYSTEM.name to stringResource(R.string.theme_system),
                            AppThemeMode.LIGHT.name to stringResource(R.string.theme_light),
                            AppThemeMode.DARK.name to stringResource(R.string.theme_dark)
                        ),
                        onToggle = {
                            val mode = AppThemeMode.valueOf(it)
                            vm.onAction(ProfileAction.ThemeModeChanged(mode))
                            onThemeModeSelected(mode)
                        }
                    )

                    Text(
                        stringResource(R.string.reported_ai),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val statusText = when {
                                settingsState.reportedAiDownloading -> stringResource(R.string.reported_ai_installing)
                                settingsState.reportedAiInstalled && settingsState.reportedAiModelSize != null ->
                                    stringResource(R.string.reported_ai_installed_size, settingsState.reportedAiModelSize!!)
                                settingsState.reportedAiInstalled -> stringResource(R.string.reported_ai_installed)
                                else -> stringResource(R.string.reported_ai_not_installed)
                            }
                            Text(statusText, style = MaterialTheme.typography.titleMedium)
                            Text(
                                settingsState.reportedAiAccelerationMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (settingsState.reportedAiDownloading) {
                                settingsState.reportedAiDownloadProgress?.let { progress ->
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            settingsState.reportedAiMessage?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (settingsState.reportedAiInstalled) {
                                OutlinedButton(
                                    onClick = { settingsViewModel.onAction(SettingsAction.DeleteReportedAi) },
                                    enabled = !settingsState.reportedAiDownloading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.reported_ai_delete))
                                }
                            } else {
                                PrimaryButton(
                                    text = stringResource(R.string.reported_ai_install_size, settingsState.reportedAiDownloadSizeLabel),
                                    onClick = { settingsViewModel.onAction(SettingsAction.InstallReportedAi) },
                                    enabled = !settingsState.reportedAiDownloading
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.nav_auto_report),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(stringResource(R.string.settings_confidence_thresholds), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    R.string.settings_threshold_summary,
                                    AutoReportThresholds.percent(settingsState.autoReportPlateThreshold),
                                    AutoReportThresholds.percent(settingsState.autoReportStateThreshold),
                                    AutoReportThresholds.percent(settingsState.autoReportComplaintThreshold)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.settings_trained_with_reported),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { settingsViewModel.onAction(SettingsAction.RoboflowProjectPressed) }) {
                                Text(stringResource(R.string.settings_view_roboflow))
                            }
                            SettingsThresholdSlider(
                                title = stringResource(R.string.field_plate),
                                value = settingsState.autoReportPlateThreshold,
                                onValueChange = { settingsViewModel.onAction(SettingsAction.PlateThresholdChanged(it)) }
                            )
                            SettingsThresholdSlider(
                                title = stringResource(R.string.field_state),
                                value = settingsState.autoReportStateThreshold,
                                onValueChange = { settingsViewModel.onAction(SettingsAction.StateThresholdChanged(it)) }
                            )
                            SettingsThresholdSlider(
                                title = stringResource(R.string.field_infraction),
                                value = settingsState.autoReportComplaintThreshold,
                                onValueChange = { settingsViewModel.onAction(SettingsAction.ComplaintThresholdChanged(it)) }
                            )
                            TextButton(
                                onClick = { settingsViewModel.onAction(SettingsAction.ResetThresholds) }
                            ) {
                                Text(stringResource(R.string.settings_reset_thresholds))
                            }
                        }
                    }

                    if (settingsState.backgroundScanningSupported) {
                        Text(
                            stringResource(R.string.settings_scanner),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isLandscape) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_notifications),
                                    description = stringResource(R.string.settings_notifications_short),
                                    checked = settingsState.notificationsEnabled,
                                    onCheckedChange = { settingsViewModel.onAction(SettingsAction.NotificationsChanged(it)) },
                                    modifier = Modifier.weight(1f)
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_offline_processing),
                                    description = stringResource(R.string.settings_offline_processing_short),
                                    checked = settingsState.offlineProcessingEnabled,
                                    onCheckedChange = { settingsViewModel.onAction(SettingsAction.OfflineProcessingChanged(it)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_notifications),
                                description = stringResource(R.string.settings_notifications_long),
                                checked = settingsState.notificationsEnabled,
                                onCheckedChange = { settingsViewModel.onAction(SettingsAction.NotificationsChanged(it)) }
                            )
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_offline_processing_title),
                                description = stringResource(R.string.settings_offline_processing_long),
                                checked = settingsState.offlineProcessingEnabled,
                                onCheckedChange = { settingsViewModel.onAction(SettingsAction.OfflineProcessingChanged(it)) }
                            )
                        }
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_media_scanner),
                            description = stringResource(R.string.settings_media_scanner_description),
                            checked = settingsState.mediaScannerEnabled,
                            onCheckedChange = { settingsViewModel.onAction(SettingsAction.MediaScannerChanged(it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsThresholdSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val valueRange = AutoReportThresholds.MIN_CONFIDENCE..AutoReportThresholds.MAX_CONFIDENCE
    val sliderState = remember(valueRange) {
        SliderState(value = value, steps = 48, trackRange = valueRange)
    }
    sliderState.value = value
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                AutoReportThresholds.percent(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            state = sliderState,
            onValueChange = { next ->
                val rounded = (next * 100f).roundToInt() / 100f
                onValueChange(rounded.coerceIn(AutoReportThresholds.MIN_CONFIDENCE, AutoReportThresholds.MAX_CONFIDENCE))
            }
        )
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
