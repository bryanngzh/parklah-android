package com.bdev.parklah.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NightdriveColorScheme = darkColorScheme(
    primary            = NightPrimary,
    onPrimary          = NightOnPrimary,
    primaryContainer   = NightAccentSoft,
    onPrimaryContainer = NightPrimary,
    secondary          = NightGood,
    onSecondary        = NightOnPrimary,
    secondaryContainer = NightGoodSoft,
    onSecondaryContainer = NightGood,
    error              = NightWarn,
    onError            = NightBg,
    errorContainer     = NightWarnSoft,
    onErrorContainer   = NightWarn,
    background         = NightBg,
    onBackground       = NightInk,
    surface            = NightSurface,
    onSurface          = NightInk,
    surfaceVariant     = NightSurfaceAlt,
    onSurfaceVariant   = NightInkDim,
    outline            = NightInkFaint,
    outlineVariant     = NightBorder,
    scrim              = NightBg,
)

@Composable
fun ParklahTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightdriveColorScheme,
        typography = Typography,
        content = content,
    )
}
