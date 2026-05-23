package com.bdev.parklah.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bdev.parklah.feature.home.HomeScreen
import com.bdev.parklah.feature.profile.ProfileScreen
import com.bdev.parklah.feature.recent.RecentScreen
import com.bdev.parklah.feature.saved.SavedScreen
import com.bdev.parklah.ui.theme.NightBg

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = NightBg,
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route)    { HomeScreen() }
            composable(Screen.Saved.route)   { SavedScreen() }
            composable(Screen.Recent.route)  { RecentScreen() }
            composable(Screen.Profile.route) { ProfileScreen() }
        }
    }
}
