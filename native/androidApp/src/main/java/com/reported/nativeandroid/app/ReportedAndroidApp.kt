package com.reported.nativeandroid.app

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reported.nativeandroid.analytics.ReportedAnalytics
import com.reported.nativeandroid.navigation.AuthDestination
import com.reported.nativeandroid.navigation.TabDestination
import com.reported.nativeandroid.R
import com.reported.nativeandroid.auth.SocialAuthDeepLinks
import com.reported.nativeandroid.remoteconfig.ReportedRemoteConfig
import com.reported.shared.model.AppThemeMode
import com.reported.nativeandroid.auth.LoginScreen
import com.reported.nativeandroid.auth.RegisterScreen
import com.reported.nativeandroid.auth.SplashScreen
import com.reported.nativeandroid.batch.BatchScreen
import com.reported.nativeandroid.live.LiveScreen
import com.reported.nativeandroid.profile.ProfileScreen
import com.reported.nativeandroid.profile.SettingsScreen
import com.reported.nativeandroid.screens.ReportComposerScreen
import com.reported.nativeandroid.reports.ReportsScreen
import com.reported.nativeandroid.theme.ReportedTheme
import kotlinx.coroutines.launch

private enum class AuthOverlayDestination(val route: String) {
    Login(AuthDestination.Login.route),
    Register(AuthDestination.Register.route)
}

private enum class DrawerTarget {
    Closed,
    Open
}

private const val SYSTEM_NOTICE_PREFS = "reported.system_notice"
private const val DISMISSED_SYSTEM_NOTICE_KEY = "dismissed_notice"
private const val BUY_ME_A_COFFEE_URL = "https://www.buymeacoffee.com/reported"

@Composable
fun ReportedAndroidApp(sessionViewModel: SessionViewModel = viewModel()) {
    val navController = rememberNavController()
    val sessionState by sessionViewModel.state.collectAsState()
    val themeViewModel: ThemeViewModel = viewModel()
    val themeState by themeViewModel.state.collectAsState()
    val sharedMediaRequest by SharedMediaIntents.requests.collectAsState()
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var authOverlay by remember { mutableStateOf<AuthOverlayDestination?>(null) }
    var afterAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val reportSubmittedMessage = stringResource(R.string.report_submitted)
    val viewActionLabel = stringResource(R.string.action_view)

    LaunchedEffect(Unit) {
        sessionViewModel.onAction(SessionAction.Load)
        themeViewModel.onAction(ThemeAction.Load)
    }

    LaunchedEffect(Unit) {
        SocialAuthDeepLinks.profiles.collect { profile ->
            sessionViewModel.onAction(SessionAction.SocialSignInCompleted(profile))
        }
    }

    val darkTheme = when (themeState.mode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }

    ReportedTheme(darkTheme = darkTheme) {
        if (showLogoutConfirmation) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmation = false },
                title = { Text(stringResource(R.string.logout_title)) },
                text = { Text(stringResource(R.string.logout_confirmation)) },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutConfirmation = false
                        sessionViewModel.onAction(SessionAction.Logout)
                    }) { Text(stringResource(R.string.logout)) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmation = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
        val isAuthorized = sessionState.session?.isAuthorized == true
        val inMainShell = isAuthorized || sessionState.isGuest

        LaunchedEffect(sharedMediaRequest?.id, inMainShell, sessionState.loading) {
            if (sharedMediaRequest != null && !inMainShell && !sessionState.loading) {
                sessionViewModel.onAction(SessionAction.ContinueAsGuest)
            }
        }

        if (sessionState.loading) {
            StartupLoadingScreen()
        } else if (inMainShell) {
            val remoteConfigSnapshot by ReportedRemoteConfig.snapshot.collectAsState()
            val systemNotice = remoteConfigSnapshot.systemNotice.trim()
            var dismissedSystemNotice by remember { mutableStateOf(readDismissedSystemNotice(context)) }
            val visibleSystemNotice = systemNotice.takeIf {
                it.isNotBlank() && it != dismissedSystemNotice
            }
            val items = buildList {
                add(TabDestination.Report)
                add(TabDestination.AutoReport)
                if (remoteConfigSnapshot.enableLive) add(TabDestination.Live)
                add(TabDestination.Reports)
                add(TabDestination.Profile)
                add(TabDestination.Settings)
            }
            val mainNavController = rememberNavController()
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            var pendingOpenReportObjectId by remember { mutableStateOf<String?>(null) }
            val mainBackStackEntry by mainNavController.currentBackStackEntryAsState()
            val selectedTab = items.firstOrNull { it.route == mainBackStackEntry?.destination?.route } ?: TabDestination.Report
            fun openSubmittedReport(objectId: String) {
                pendingOpenReportObjectId = objectId
                mainNavController.navigate(TabDestination.Reports.route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(mainNavController.graph.startDestinationId) {
                        saveState = true
                    }
                }
            }
            LaunchedEffect(sharedMediaRequest?.id) {
                if (sharedMediaRequest != null) {
                    mainNavController.navigate(TabDestination.Report.route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(mainNavController.graph.startDestinationId) {
                            saveState = true
                        }
                    }
                }
            }
            LaunchedEffect(selectedTab.route) {
                ReportedAnalytics.logScreenView(selectedTab.label)
            }
            LaunchedEffect(remoteConfigSnapshot.enableLive, mainBackStackEntry?.destination?.route) {
                if (!remoteConfigSnapshot.enableLive && mainBackStackEntry?.destination?.route == TabDestination.Live.route) {
                    mainNavController.navigate(TabDestination.Report.route) {
                        launchSingleTop = true
                        popUpTo(mainNavController.graph.startDestinationId) {
                            saveState = true
                        }
                    }
                }
            }
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.End
                ),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                SliderNavShell(
                    avatarUrl = sessionState.session?.avatarUrl,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = innerPadding,
                    leftContent = { closeDrawer ->
                        LeftGliderNavRail(
                            items = items,
                            selectedTab = selectedTab,
                            onLogout = if (isAuthorized) { {
                                closeDrawer()
                                showLogoutConfirmation = true
                            } } else null,
                            closeDrawer = closeDrawer,
                            onSelected = { item, closeDrawer ->
                                if (
                                    isAuthorized ||
                                    item == TabDestination.Report ||
                                    item == TabDestination.AutoReport ||
                                    item == TabDestination.Live ||
                                    item == TabDestination.Settings
                                ) {
                                    if (item == TabDestination.Settings) {
                                        ReportedAnalytics.logSettingsTapped("navigation")
                                    }
                                    mainNavController.navigate(item.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(mainNavController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                    }
                                    closeDrawer()
                                } else {
                                    authOverlay = AuthOverlayDestination.Login
                                    closeDrawer()
                                }
                            }
                        )
                    },
                    centerContent = { onOpenMenu ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            visibleSystemNotice?.let { notice ->
                                SystemNoticeBanner(
                                    message = notice,
                                    onDismiss = {
                                        dismissedSystemNotice = notice
                                        saveDismissedSystemNotice(context, notice)
                                    }
                                )
                            }
                            NavHost(
                                navController = mainNavController,
                                startDestination = TabDestination.Report.route,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                    composable(TabDestination.Report.route) {
                                        ReportComposerScreen(
                                            isAuthorized = isAuthorized,
                                            onRequireLogin = { afterAuth ->
                                                afterAuthAction = afterAuth
                                                authOverlay = AuthOverlayDestination.Login
                                            },
                                            onOpenMenu = onOpenMenu,
                                            sharedMediaRequest = sharedMediaRequest,
                                            onSharedMediaConsumed = SharedMediaIntents::consume,
                                            onReportSubmitted = { objectId ->
                                                scope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = reportSubmittedMessage,
                                                        actionLabel = viewActionLabel,
                                                        withDismissAction = true,
                                                        duration = SnackbarDuration.Long
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        openSubmittedReport(objectId)
                                                    }
                                                }
                                            }
                                        )
                                    }
                            composable(TabDestination.Reports.route) {
                                ReportsScreen(
                                    isAuthorized = isAuthorized,
                                    onRequireLogin = { authOverlay = AuthOverlayDestination.Login },
                                    onOpenMenu = onOpenMenu,
                                    openReportObjectId = pendingOpenReportObjectId,
                                    onOpenedReport = { pendingOpenReportObjectId = null }
                                )
                            }
                            composable(TabDestination.AutoReport.route) {
                                BatchScreen(
                                    isAuthorized = isAuthorized,
                                    onRequireLogin = { authOverlay = AuthOverlayDestination.Login },
                                    onOpenMenu = onOpenMenu,
                                    title = stringResource(R.string.nav_auto_report),
                                    autoReportMode = true
                                )
                            }
                            composable(TabDestination.Live.route) {
                                LiveScreen(onOpenMenu = onOpenMenu)
                            }
                            composable(TabDestination.Profile.route) {
                                ProfileScreen(
                                    isAuthorized = isAuthorized,
                                    onRequireLogin = { authOverlay = AuthOverlayDestination.Login },
                                    onLogout = { showLogoutConfirmation = true },
                                    onOpenMenu = onOpenMenu
                                )
                            }
                            composable(TabDestination.Settings.route) {
                                SettingsScreen(
                                    onThemeModeSelected = { themeViewModel.onAction(ThemeAction.ModeChanged(it)) },
                                    onOpenMenu = onOpenMenu
                                )
                            }
                            }
                        }

                        authOverlay?.let { destination ->
                            val overlayNavController = rememberNavController()
                            Dialog(
                                onDismissRequest = {
                                    authOverlay = null
                                    afterAuthAction = null
                                },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    tonalElevation = 6.dp
                                ) {
                                    NavHost(
                                        navController = overlayNavController,
                                        startDestination = destination.route,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        composable(AuthDestination.Login.route) {
                                            LoginScreen(
                                                onSuccess = {
                                                    val action = afterAuthAction
                                                    authOverlay = null
                                                    afterAuthAction = null
                                                    sessionViewModel.onAction(SessionAction.Authenticated)
                                                    action?.invoke()
                                                },
                                                onRegister = { overlayNavController.navigate(AuthDestination.Register.route) },
                                                onBack = {
                                                    authOverlay = null
                                                    afterAuthAction = null
                                                },
                                                onDismiss = {
                                                    authOverlay = null
                                                    afterAuthAction = null
                                                },
                                                modal = true
                                            )
                                        }
                                        composable(AuthDestination.Register.route) {
                                            RegisterScreen(
                                                onSuccess = {
                                                    val action = afterAuthAction
                                                    authOverlay = null
                                                    afterAuthAction = null
                                                    sessionViewModel.onAction(SessionAction.Authenticated)
                                                    action?.invoke()
                                                },
                                                onLogin = { overlayNavController.navigate(AuthDestination.Login.route) },
                                                onBack = { overlayNavController.popBackStack() },
                                                onDismiss = {
                                                    authOverlay = null
                                                    afterAuthAction = null
                                                },
                                                modal = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        } else {
            NavHost(
                navController = navController,
                startDestination = AuthDestination.Splash.route,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                composable(AuthDestination.Splash.route) {
                    SplashScreen(
                        onLogin = { navController.navigate(AuthDestination.Login.route) },
                        onRegister = { navController.navigate(AuthDestination.Register.route) },
                        onSkip = { sessionViewModel.onAction(SessionAction.ContinueAsGuest) }
                    )
                }
                composable(AuthDestination.Login.route) {
                    LoginScreen(
                        onSuccess = { sessionViewModel.onAction(SessionAction.Authenticated) },
                        onRegister = { navController.navigate(AuthDestination.Register.route) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AuthDestination.Register.route) {
                    RegisterScreen(
                        onSuccess = { sessionViewModel.onAction(SessionAction.Authenticated) },
                        onLogin = { navController.navigate(AuthDestination.Login.route) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartupLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SystemNoticeBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = message,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 5.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.system_notice_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun readDismissedSystemNotice(context: Context): String =
    context.applicationContext
        .getSharedPreferences(SYSTEM_NOTICE_PREFS, Context.MODE_PRIVATE)
        .getString(DISMISSED_SYSTEM_NOTICE_KEY, "")
        .orEmpty()

private fun saveDismissedSystemNotice(context: Context, notice: String) {
    context.applicationContext
        .getSharedPreferences(SYSTEM_NOTICE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(DISMISSED_SYSTEM_NOTICE_KEY, notice)
        .apply()
}

@Composable
private fun SliderNavShell(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    drawerWidth: androidx.compose.ui.unit.Dp = 116.dp,
    contentPadding: PaddingValues = PaddingValues(),
    leftContent: @Composable ((() -> Unit)) -> Unit,
    centerContent: @Composable ((() -> Unit)) -> Unit
) {
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { drawerWidth.toPx() }
    var currentOffset by remember { mutableFloatStateOf(0f) }
    var drawerTarget by remember { mutableStateOf(DrawerTarget.Closed) }
    val draggableState = rememberDraggableState { delta ->
        currentOffset = (currentOffset + delta).coerceIn(0f, drawerWidthPx)
    }

    fun settle(offset: Float, velocity: Float) {
        val shouldOpen = offset > drawerWidthPx * 0.4f || velocity > 120f
        drawerTarget = if (shouldOpen) DrawerTarget.Open else DrawerTarget.Closed
        currentOffset = if (shouldOpen) drawerWidthPx else 0f
    }

    fun toggleDrawer() {
        drawerTarget = if (drawerTarget == DrawerTarget.Open) DrawerTarget.Closed else DrawerTarget.Open
        currentOffset = if (drawerTarget == DrawerTarget.Open) drawerWidthPx else 0f
    }

    fun closeDrawer() {
        drawerTarget = DrawerTarget.Closed
        currentOffset = 0f
    }

    Box(modifier = modifier.clipToBounds()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(drawerWidth)
                .graphicsLayer {
                    translationX = currentOffset - drawerWidthPx
                }
        ) {
            leftContent(::closeDrawer)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding)
                .graphicsLayer {
                    translationX = currentOffset
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = draggableState,
                    onDragStopped = { velocity -> settle(currentOffset, velocity) }
                )
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalMenuAvatar provides avatarUrl) {
                centerContent(::toggleDrawer)
            }
        }
    }
}

@Composable
private fun LeftGliderNavRail(
    onLogout: (() -> Unit)?,
    items: List<TabDestination>,
    selectedTab: TabDestination,
    closeDrawer: () -> Unit,
    onSelected: (TabDestination, () -> Unit) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var showCoffeeInfo by remember { mutableStateOf(false) }
    if (showCoffeeInfo) {
        AlertDialog(
            onDismissRequest = { showCoffeeInfo = false },
            title = { Text(stringResource(R.string.coffee_title)) },
            text = { Text(stringResource(R.string.coffee_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCoffeeInfo = false
                        ReportedAnalytics.logBuyMeCoffeeOpen(surface = "left_nav", source = "info_dialog")
                        uriHandler.openUri(BUY_ME_A_COFFEE_URL)
                        closeDrawer()
                    }
                ) {
                    Text(stringResource(R.string.coffee_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCoffeeInfo = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(116.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEach { item ->
                val selected = item.route == selectedTab.route
                val localizedLabel = item.localizedLabel()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(
                            if (selected) {
                                androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onSelected(item, closeDrawer) }
                        .padding(vertical = 14.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val image = when (item) {
                        TabDestination.Report -> Icons.Outlined.AddCircle
                        TabDestination.AutoReport -> Icons.Outlined.AutoAwesome
                        TabDestination.Live -> Icons.Outlined.Videocam
                        TabDestination.Reports -> Icons.AutoMirrored.Outlined.List
                        TabDestination.Profile -> Icons.Outlined.AccountCircle
                        TabDestination.Settings -> Icons.Outlined.Settings
                    }
                    Icon(
                        imageVector = image,
                        contentDescription = localizedLabel,
                        tint = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = localizedLabel,
                        color = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (onLogout != null) {
                TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.logout))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            ReportedAnalytics.logBuyMeCoffeeTapped(surface = "left_nav")
                            ReportedAnalytics.logBuyMeCoffeeOpen(surface = "left_nav", source = "primary")
                            uriHandler.openUri(BUY_ME_A_COFFEE_URL)
                            closeDrawer()
                        }
                        .padding(vertical = 13.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bmc_logo_no_background),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.coffee_nav_label),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                IconButton(
                    modifier = Modifier.align(Alignment.TopEnd),
                    onClick = {
                        ReportedAnalytics.logBuyMeCoffeeInfoTapped(surface = "left_nav")
                        showCoffeeInfo = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.coffee_accessibility),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

internal val LocalMenuAvatar = androidx.compose.runtime.compositionLocalOf<String?> { null }

@Composable
fun ShellMenuIcon(tint: androidx.compose.ui.graphics.Color = androidx.compose.material3.LocalContentColor.current) {
    val painter = coil.compose.rememberAsyncImagePainter(LocalMenuAvatar.current)
    if (painter.state is coil.compose.AsyncImagePainter.State.Success) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = stringResource(R.string.menu_open),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape)
        )
    } else {
        Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.menu_open), tint = tint)
    }
}

@Composable
private fun TabDestination.localizedLabel(): String = when (this) {
    TabDestination.Report -> stringResource(R.string.nav_new_report)
    TabDestination.AutoReport -> stringResource(R.string.nav_auto_report)
    TabDestination.Live -> stringResource(R.string.nav_live)
    TabDestination.Reports -> stringResource(R.string.nav_my_reports)
    TabDestination.Profile -> stringResource(R.string.nav_profile)
    TabDestination.Settings -> stringResource(R.string.nav_settings)
}
