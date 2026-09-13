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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
            title = "Profile",
            message = "Sign in to edit your profile and manage your account.",
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
            title = { Text("Profile") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                                "First Name",
                                state.firstName,
                                { vm.onAction(ProfileAction.FieldsChanged(firstName = it)) },
                                modifier = Modifier.weight(1f),
                                enabled = state.editing
                            )
                            ReportedField(
                                "Last Name",
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
                                "Phone",
                                state.phone,
                                { vm.onAction(ProfileAction.FieldsChanged(phone = it)) },
                                modifier = Modifier.weight(1f),
                                enabled = state.editing
                            )
                            ReportedField(
                                "Email",
                                state.email,
                                { vm.onAction(ProfileAction.FieldsChanged(email = it)) },
                                modifier = Modifier.weight(1f),
                                enabled = state.editing
                            )
                        }
                    } else {
                        ReportedField("First Name", state.firstName, { vm.onAction(ProfileAction.FieldsChanged(firstName = it)) }, enabled = state.editing)
                        ReportedField("Last Name", state.lastName, { vm.onAction(ProfileAction.FieldsChanged(lastName = it)) }, enabled = state.editing)
                        ReportedField("Phone", state.phone, { vm.onAction(ProfileAction.FieldsChanged(phone = it)) }, enabled = state.editing)
                        ReportedField("Email", state.email, { vm.onAction(ProfileAction.FieldsChanged(email = it)) }, enabled = state.editing)
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
                        Text("I'm willing to testify by phone if needed.")
                    }
                    if (state.editing) {
                        PrimaryButton("Save", onClick = { vm.onAction(ProfileAction.SavePressed()) }, enabled = !state.loading)
                    } else {
                        PrimaryButton("Edit Profile", onClick = { vm.onAction(ProfileAction.ToggleEditing) })
                    }
                    Text(
                        text = "Logout",
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
            title = { Text("Settings") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                        "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    ComplaintChipGroup(
                        selectedIds = listOf(state.themeMode.name),
                        options = listOf(
                            AppThemeMode.SYSTEM.name to "System",
                            AppThemeMode.LIGHT.name to "Light",
                            AppThemeMode.DARK.name to "Dark"
                        ),
                        onToggle = {
                            val mode = AppThemeMode.valueOf(it)
                            vm.onAction(ProfileAction.ThemeModeChanged(mode))
                            onThemeModeSelected(mode)
                        }
                    )

                    Text(
                        "Reported AI",
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
                                settingsState.reportedAiDownloading -> "Installing"
                                settingsState.reportedAiInstalled -> "Installed${settingsState.reportedAiModelSize?.let { " ($it)" }.orEmpty()}"
                                else -> "Not installed"
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
                                    Text("Delete REPORTED AI")
                                }
                            } else {
                                PrimaryButton(
                                    text = "Install REPORTED AI (${settingsState.reportedAiDownloadSizeLabel})",
                                    onClick = { settingsViewModel.onAction(SettingsAction.InstallReportedAi) },
                                    enabled = !settingsState.reportedAiDownloading
                                )
                            }
                        }
                    }

                    Text(
                        "Auto-Report",
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
                            Text("Confidence thresholds", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Thresholds: plate ${AutoReportThresholds.percent(settingsState.autoReportPlateThreshold)}+, state ${AutoReportThresholds.percent(settingsState.autoReportStateThreshold)}+, infraction ${AutoReportThresholds.percent(settingsState.autoReportComplaintThreshold)}+.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Trained with Reported data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { settingsViewModel.onAction(SettingsAction.RoboflowProjectPressed) }) {
                                Text("View Roboflow project")
                            }
                            SettingsThresholdSlider(
                                title = "Plate",
                                value = settingsState.autoReportPlateThreshold,
                                onValueChange = { settingsViewModel.onAction(SettingsAction.PlateThresholdChanged(it)) }
                            )
                            SettingsThresholdSlider(
                                title = "State",
                                value = settingsState.autoReportStateThreshold,
                                onValueChange = { settingsViewModel.onAction(SettingsAction.StateThresholdChanged(it)) }
                            )
                            SettingsThresholdSlider(
                                title = "Infraction",
                                value = settingsState.autoReportComplaintThreshold,
                                onValueChange = { settingsViewModel.onAction(SettingsAction.ComplaintThresholdChanged(it)) }
                            )
                            TextButton(
                                onClick = { settingsViewModel.onAction(SettingsAction.ResetThresholds) }
                            ) {
                                Text("Reset thresholds")
                            }
                        }
                    }

                    if (settingsState.backgroundScanningSupported) {
                        Text(
                            "Scanner",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isLandscape) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SettingsSwitchRow(
                                    title = "Notifications",
                                    description = "Notify you when a high-confidence infraction is detected.",
                                    checked = settingsState.notificationsEnabled,
                                    onCheckedChange = { settingsViewModel.onAction(SettingsAction.NotificationsChanged(it)) },
                                    modifier = Modifier.weight(1f)
                                )
                                SettingsSwitchRow(
                                    title = "Offline processing",
                                    description = "Process new photos locally. Nothing uploads unless you submit.",
                                    checked = settingsState.offlineProcessingEnabled,
                                    onCheckedChange = { settingsViewModel.onAction(SettingsAction.OfflineProcessingChanged(it)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            SettingsSwitchRow(
                                title = "Notifications",
                                description = "Allow Reported to notify you when a high-confidence infraction is detected.",
                                checked = settingsState.notificationsEnabled,
                                onCheckedChange = { settingsViewModel.onAction(SettingsAction.NotificationsChanged(it)) }
                            )
                            SettingsSwitchRow(
                                title = "Allow offline photo processing to detect violations",
                                description = "Process new photos on this device only. Nothing uploads unless you choose to submit.",
                                checked = settingsState.offlineProcessingEnabled,
                                onCheckedChange = { settingsViewModel.onAction(SettingsAction.OfflineProcessingChanged(it)) }
                            )
                        }
                        SettingsSwitchRow(
                            title = "Media scanner",
                            description = "Watch for new photos and queue a local scan when offline processing is allowed.",
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
            value = value,
            onValueChange = { next ->
                val rounded = (next * 100f).roundToInt() / 100f
                onValueChange(rounded.coerceIn(AutoReportThresholds.MIN_CONFIDENCE, AutoReportThresholds.MAX_CONFIDENCE))
            },
            valueRange = AutoReportThresholds.MIN_CONFIDENCE..AutoReportThresholds.MAX_CONFIDENCE,
            steps = 48
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
