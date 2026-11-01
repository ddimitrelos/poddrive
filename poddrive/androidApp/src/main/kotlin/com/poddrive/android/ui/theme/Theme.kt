package com.poddrive.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PodDriveDarkColorScheme = darkColorScheme(
    background = Color(0xFF0F0F12),
    surface = Color(0xFF1C1C24),
    primary = Color(0xFFF59E0B),
    secondary = Color(0xFF34D399),   // HANDING_OFF teal — drive screen Spotify hand-off indicator
    onBackground = Color.White,
    onSurface = Color.White,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    surfaceVariant = Color(0xFF272736),
)

@Composable
fun PodDriveTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PodDriveDarkColorScheme,
        content = content
    )
}
