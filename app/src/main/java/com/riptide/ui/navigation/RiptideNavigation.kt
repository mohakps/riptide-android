package com.riptide.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Session : Screen("session")
    object Settings : Screen("settings")
}
