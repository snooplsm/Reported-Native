package com.reported.nativeandroid.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
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

@Composable
fun ReportedAndroidApp(sessionViewModel: SessionViewModel = viewModel()) {
    val navController = rememberNavController()
    val sessionState by sessionViewModel.state.collectAsState()
    val themeViewModel: ThemeViewModel = viewModel()
    val themeState by themeViewModel.state.collectAsState()
    val sharedMediaRequest by SharedMediaIntents.requests.collectAsState()
    var authOverlay by remember { mutableStateOf<AuthOverlayDestination?>(null) }
    var afterAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val systemDark = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        sessionViewModel.load()
        themeViewModel.load()
    }

    LaunchedEffect(Unit) {
        SocialAuthDeepLinks.profiles.collect { profile ->
            sessionViewModel.completeSocialSignIn(profile)
        }
    }

    val darkTheme = when (themeState.mode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }

    ReportedTheme(darkTheme = darkTheme) {
        val isAuthorized = sessionState.session?.isAuthorized == true
        val inMainShell = isAuthorized || sessionState.isGuest

        LaunchedEffect(sharedMediaRequest?.id, inMainShell, sessionState.loading) {
            if (sharedMediaRequest != null && !inMainShell && !sessionState.loading) {
                sessionViewModel.continueAsGuest()
            }
        }

        if (inMainShell) {
            val remoteConfigSnapshot by ReportedRemoteConfig.snapshot.collectAsState()
            val items = buildList {
                add(TabDestination.Report)
                add(TabDestination.Batch)
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
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = innerPadding,
                    leftContent = { closeDrawer ->
                        LeftGliderNavRail(
                            items = items,
                            selectedTab = selectedTab,
                            closeDrawer = closeDrawer,
                            onSelected = { item, closeDrawer ->
                                if (isAuthorized || item == TabDestination.Report || item == TabDestination.Live || item == TabDestination.Settings) {
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
                        NavHost(
                            navController = mainNavController,
                            startDestination = TabDestination.Report.route,
                            modifier = Modifier
                                .fillMaxSize()
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
                                                        message = "Report Submitted",
                                                        actionLabel = "View",
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
                            composable(TabDestination.Batch.route) {
                                BatchScreen(
                                    isAuthorized = isAuthorized,
                                    onRequireLogin = { authOverlay = AuthOverlayDestination.Login },
                                    onOpenMenu = onOpenMenu
                                )
                            }
                            composable(TabDestination.Live.route) {
                                LiveScreen(onOpenMenu = onOpenMenu)
                            }
                            composable(TabDestination.Profile.route) {
                                ProfileScreen(
                                    isAuthorized = isAuthorized,
                                    onRequireLogin = { authOverlay = AuthOverlayDestination.Login },
                                    onLogout = sessionViewModel::logout,
                                    onOpenMenu = onOpenMenu
                                )
                            }
                            composable(TabDestination.Settings.route) {
                                SettingsScreen(
                                    onThemeModeSelected = themeViewModel::update,
                                    onOpenMenu = onOpenMenu
                                )
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
                                                    sessionViewModel.onAuthenticated()
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
                                                    sessionViewModel.onAuthenticated()
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
                        onSkip = sessionViewModel::continueAsGuest
                    )
                }
                composable(AuthDestination.Login.route) {
                    LoginScreen(
                        onSuccess = sessionViewModel::onAuthenticated,
                        onRegister = { navController.navigate(AuthDestination.Register.route) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AuthDestination.Register.route) {
                    RegisterScreen(
                        onSuccess = sessionViewModel::onAuthenticated,
                        onLogin = { navController.navigate(AuthDestination.Login.route) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderNavShell(
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
            centerContent(::toggleDrawer)
        }
    }
}

@Composable
private fun LeftGliderNavRail(
    items: List<TabDestination>,
    selectedTab: TabDestination,
    closeDrawer: () -> Unit,
    onSelected: (TabDestination, () -> Unit) -> Unit
) {
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
                        TabDestination.Batch -> Icons.Outlined.Collections
                        TabDestination.Live -> Icons.Outlined.Videocam
                        TabDestination.Reports -> Icons.AutoMirrored.Outlined.List
                        TabDestination.Profile -> Icons.Outlined.AccountCircle
                        TabDestination.Settings -> Icons.Outlined.Settings
                    }
                    Icon(
                        imageVector = image,
                        contentDescription = item.label,
                        tint = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.label,
                        color = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
