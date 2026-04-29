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
import com.reported.nativeandroid.app.ProfileViewModel
import com.reported.nativeandroid.media.MediaScannerScheduler
import com.reported.nativeandroid.media.MediaScannerSettings
import com.reported.nativeandroid.screens.ComplaintChipGroup
import com.reported.nativeandroid.screens.LoginRequiredScreen
import com.reported.nativeandroid.screens.MessageCard
import com.reported.nativeandroid.screens.PrimaryButton
import com.reported.nativeandroid.screens.ReportedField
import com.reported.nativeandroid.screens.ScreenSection
import com.reported.shared.model.AppThemeMode

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
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

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
                                { vm.update(firstName = it) },
                                modifier = Modifier.weight(1f),
                                enabled = state.editing
                            )
                            ReportedField(
                                "Last Name",
                                state.lastName,
                                { vm.update(lastName = it) },
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
                                { vm.update(phone = it) },
                                modifier = Modifier.weight(1f),
                                enabled = state.editing
                            )
                            ReportedField(
                                "Email",
                                state.email,
                                { vm.update(email = it) },
                                modifier = Modifier.weight(1f),
                                enabled = state.editing
                            )
                        }
                    } else {
                        ReportedField("First Name", state.firstName, { vm.update(firstName = it) }, enabled = state.editing)
                        ReportedField("Last Name", state.lastName, { vm.update(lastName = it) }, enabled = state.editing)
                        ReportedField("Phone", state.phone, { vm.update(phone = it) }, enabled = state.editing)
                        ReportedField("Email", state.email, { vm.update(email = it) }, enabled = state.editing)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = state.editing) { vm.update(testify = !state.testify) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = state.testify,
                            onCheckedChange = { vm.update(testify = it) },
                            enabled = state.editing
                        )
                        Text("I'm willing to testify by phone if needed.")
                    }
                    if (state.editing) {
                        PrimaryButton("Save", onClick = { vm.save {} }, enabled = !state.loading)
                    } else {
                        PrimaryButton("Edit Profile", onClick = vm::toggleEditing)
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
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

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
                            vm.setThemeMode(mode)
                            onThemeModeSelected(mode)
                        }
                    )

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
