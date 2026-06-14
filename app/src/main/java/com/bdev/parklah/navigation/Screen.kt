package com.bdev.parklah.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
}
