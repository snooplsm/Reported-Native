package com.reported.nativeandroid.navigation

sealed class AuthDestination(val route: String) {
    data object Splash : AuthDestination("splash")
    data object Login : AuthDestination("login")
    data object Register : AuthDestination("register")
}

sealed class TabDestination(val route: String, val label: String) {
    data object Report : TabDestination("report", "New Report")
    data object Batch : TabDestination("batch", "Batch")
    data object Live : TabDestination("live", "Live")
    data object Reports : TabDestination("reports", "My Reports")
    data object Profile : TabDestination("profile", "Profile")
    data object Settings : TabDestination("settings", "Settings")
}
