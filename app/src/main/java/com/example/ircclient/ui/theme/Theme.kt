package com.example.ircclient.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EInkLight = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF000000),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F3F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFF7F3F7),
    onSurface = Color(0xFF000000),
)

private val DarkColors = darkColorScheme()
private val LightColors = lightColorScheme()

@Composable
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else EInkLight,
        content = content
    )
}
