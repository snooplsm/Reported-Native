package com.reported.nativeandroid.profile

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reported.nativeandroid.app.ProfileAction
import com.reported.nativeandroid.app.ProfileViewModel
import com.reported.nativeandroid.ai.ReportedAiModelStore
import com.reported.nativeandroid.media.AutoReportThresholds
import com.reported.nativeandroid.media.MediaScannerScheduler
import com.reported.nativeandroid.media.MediaScannerSettings
import com.reported.nativeandroid.screens.ComplaintChipGroup
import com.reported.nativeandroid.screens.LoginRequiredScreen
import com.reported.nativeandroid.screens.MessageCard
import com.reported.nativeandroid.screens.PrimaryButton
import com.reported.nativeandroid.screens.ReportedField
import com.reported.nativeandroid.screens.ScreenSection
import com.reported.shared.model.AppThemeMode
import kotlinx.coroutines.launch

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
                    Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
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
    vm: ProfileViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val backgroundScanningSupported = MediaScannerSettings.supportsBackgroundLibraryScanning()
    var mediaScannerEnabled by remember { mutableStateOf(MediaScannerSettings.isEnabled(context)) }
    var offlineProcessingEnabled by remember { mutableStateOf(MediaScannerSettings.isOfflineProcessingEnabled(context)) }
    val scope = rememberCoroutineScope()
    var reportedAiInstalled by remember { mutableStateOf(ReportedAiModelStore.isModelInstalled(context)) }
    var reportedAiModelSize by remember { mutableStateOf(ReportedAiModelStore.installedModelSizeLabel(context)) }
    var reportedAiAccelerationMessage by remember { mutableStateOf(ReportedAiModelStore.accelerationMessage(context)) }
    var reportedAiDownloading by remember { mutableStateOf(false) }
    var reportedAiDownloadProgress by remember { mutableStateOf<Float?>(null) }
    var reportedAiMessage by remember { mutableStateOf<String?>(null) }
    var notificationsEnabled by remember {
        mutableStateOf(MediaScannerSettings.isNotificationsEnabled(context) && MediaScannerSettings.hasNotificationPermission(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted || MediaScannerSettings.hasNotificationPermission(context)
        MediaScannerSettings.setNotificationsEnabled(context, notificationsEnabled)
        MediaScannerSettings.markNotificationPermissionAsked(context)
    }
    val mediaScannerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (!backgroundScanningSupported) {
            mediaScannerEnabled = false
            MediaScannerSettings.setEnabled(context, false)
            return@rememberLauncherForActivityResult
        }
        val granted = MediaScannerSettings.requiredPermissions().all { permission ->
            grants[permission] == true ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        mediaScannerEnabled = granted
        MediaScannerSettings.setEnabled(context, granted)
        if (granted && offlineProcessingEnabled) {
            MediaScannerScheduler.scanNow(context)
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.onAction(ProfileAction.Load) }

    fun refreshReportedAiModelState() {
        reportedAiInstalled = ReportedAiModelStore.isModelInstalled(context)
        reportedAiModelSize = ReportedAiModelStore.installedModelSizeLabel(context)
        reportedAiAccelerationMessage = ReportedAiModelStore.accelerationMessage(context)
    }

    fun downloadReportedAiModel() {
        if (reportedAiDownloading) return
        reportedAiDownloading = true
        reportedAiDownloadProgress = null
        reportedAiMessage = null
        scope.launch {
            ReportedAiModelStore.downloadModel(context) { downloadedBytes, totalBytes ->
                reportedAiDownloadProgress = if (totalBytes > 0L) {
                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    null
                }
            }.fold(
                onSuccess = {
                    refreshReportedAiModelState()
                    reportedAiDownloadProgress = 1f
                    reportedAiMessage = "REPORTED AI installed."
                },
                onFailure = { error ->
                    refreshReportedAiModelState()
                    reportedAiMessage = error.message ?: "Could not install REPORTED AI."
                }
            )
            reportedAiDownloading = false
        }
    }

    fun deleteReportedAiModel() {
        if (reportedAiDownloading) return
        reportedAiMessage = null
        scope.launch {
            val deleted = ReportedAiModelStore.deleteModel(context)
            refreshReportedAiModelState()
            reportedAiDownloadProgress = null
            reportedAiMessage = if (deleted > 0) {
                "REPORTED AI deleted."
            } else {
                "No REPORTED AI model was installed."
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
                    Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
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
                                reportedAiDownloading -> "Installing"
                                reportedAiInstalled -> "Installed${reportedAiModelSize?.let { " ($it)" }.orEmpty()}"
                                else -> "Not installed"
                            }
                            Text(statusText, style = MaterialTheme.typography.titleMedium)
                            Text(
                                reportedAiAccelerationMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (reportedAiDownloading) {
                                reportedAiDownloadProgress?.let { progress ->
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            reportedAiMessage?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (reportedAiInstalled) {
                                OutlinedButton(
                                    onClick = ::deleteReportedAiModel,
                                    enabled = !reportedAiDownloading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Delete REPORTED AI")
                                }
                            } else {
                                PrimaryButton(
                                    text = "Install REPORTED AI (${ReportedAiModelStore.compactDownloadSizeLabel})",
                                    onClick = ::downloadReportedAiModel,
                                    enabled = !reportedAiDownloading
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
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Confidence thresholds", style = MaterialTheme.typography.titleMedium)
                            Text(
                                AutoReportThresholds.summary(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (backgroundScanningSupported) {
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
                                    checked = notificationsEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            val permission = MediaScannerSettings.notificationPermission()
                                            if (permission != null && !MediaScannerSettings.hasNotificationPermission(context)) {
                                                MediaScannerSettings.setNotificationsEnabled(context, true)
                                                notificationPermissionLauncher.launch(permission)
                                            } else {
                                                notificationsEnabled = true
                                                MediaScannerSettings.setNotificationsEnabled(context, true)
                                            }
                                        } else {
                                            notificationsEnabled = false
                                            MediaScannerSettings.setNotificationsEnabled(context, false)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SettingsSwitchRow(
                                    title = "Offline processing",
                                    description = "Process new photos locally. Nothing uploads unless you submit.",
                                    checked = offlineProcessingEnabled,
                                    onCheckedChange = { enabled ->
                                        offlineProcessingEnabled = enabled
                                        MediaScannerSettings.setOfflineProcessingEnabled(context, enabled)
                                        if (enabled && mediaScannerEnabled) {
                                            MediaScannerScheduler.scanNow(context)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            SettingsSwitchRow(
                                title = "Notifications",
                                description = "Allow Reported to notify you when a high-confidence infraction is detected.",
                                checked = notificationsEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        val permission = MediaScannerSettings.notificationPermission()
                                        if (permission != null && !MediaScannerSettings.hasNotificationPermission(context)) {
                                            MediaScannerSettings.setNotificationsEnabled(context, true)
                                            notificationPermissionLauncher.launch(permission)
                                        } else {
                                            notificationsEnabled = true
                                            MediaScannerSettings.setNotificationsEnabled(context, true)
                                        }
                                    } else {
                                        notificationsEnabled = false
                                        MediaScannerSettings.setNotificationsEnabled(context, false)
                                    }
                                }
                            )
                            SettingsSwitchRow(
                                title = "Allow offline photo processing to detect violations",
                                description = "Process new photos on this device only. Nothing uploads unless you choose to submit.",
                                checked = offlineProcessingEnabled,
                                onCheckedChange = { enabled ->
                                    offlineProcessingEnabled = enabled
                                    MediaScannerSettings.setOfflineProcessingEnabled(context, enabled)
                                    if (enabled && mediaScannerEnabled) {
                                        MediaScannerScheduler.scanNow(context)
                                    }
                                }
                            )
                        }
                        SettingsSwitchRow(
                            title = "Media scanner",
                            description = "Watch for new photos and queue a local scan when offline processing is allowed.",
                            checked = mediaScannerEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (MediaScannerSettings.hasRequiredPermissions(context)) {
                                        mediaScannerEnabled = true
                                        MediaScannerSettings.setEnabled(context, true)
                                        if (offlineProcessingEnabled) {
                                            MediaScannerScheduler.scanNow(context)
                                        }
                                    } else {
                                        mediaScannerPermissionLauncher.launch(MediaScannerSettings.requiredPermissions())
                                    }
                                } else {
                                    mediaScannerEnabled = false
                                    MediaScannerSettings.setEnabled(context, false)
                                }
                            }
                        )
                    }
                }
            }
        }
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
