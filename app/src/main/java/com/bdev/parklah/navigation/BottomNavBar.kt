package com.bdev.parklah.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import com.bdev.parklah.ui.theme.GeistFontFamily
import com.bdev.parklah.ui.theme.NightBorder
import com.bdev.parklah.ui.theme.NightInkDim
import com.bdev.parklah.ui.theme.NightPrimary
import com.bdev.parklah.ui.theme.NightSurface

private data class NavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val navItems = listOf(
    NavItem(Screen.Home,    "Map",    Icons.Filled.Map,        Icons.Outlined.Map),
    NavItem(Screen.Saved,   "Saved",  Icons.Filled.Star,       Icons.Outlined.StarOutline),
    NavItem(Screen.Recent,  "Recent", Icons.Filled.AccessTime, Icons.Outlined.AccessTime),
    NavItem(Screen.Profile, "You",    Icons.Filled.Person,     Icons.Outlined.Person),
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        containerColor = NightSurface,
        tonalElevation = 0.dp,
    ) {
        navItems.forEach { item ->
            val selected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.screen.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontFamily = GeistFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NightPrimary,
                    selectedTextColor = NightPrimary,
                    unselectedIconColor = NightInkDim,
                    unselectedTextColor = NightInkDim,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}
