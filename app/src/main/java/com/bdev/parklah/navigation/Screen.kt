package com.bdev.parklah.navigation

sealed class Screen(val route: String) {
    data object Home    : Screen("home")
    data object Saved   : Screen("saved")
    data object Recent  : Screen("recent")
    data object Profile : Screen("profile")
}
