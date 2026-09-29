package com.app.speechsummary.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val LightColors = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EFEB), onPrimaryContainer = Ink,
    secondary = Muted, onSecondary = Color.White,
    background = Paper, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFEAF0ED), onSurfaceVariant = Muted,
    outline = Border
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78D6CB), onPrimary = DarkPaper,
    primaryContainer = Color(0xFF164C49), onPrimaryContainer = Color(0xFFD8EFEB),
    secondary = Color(0xFFA7C9C4),
    background = DarkPaper, onBackground = Color(0xFFE6F0EC),
    surface = DarkSurface, onSurface = Color(0xFFE6F0EC),
    surfaceVariant = Color(0xFF29403D), onSurfaceVariant = Color(0xFFBED0CA),
    outline = Color(0xFF50635F)
)

@Composable
fun SpeechSummaryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as? Activity)?.window
        window?.statusBarColor = colors.background.toArgb()
        window?.navigationBarColor = colors.background.toArgb()
        @Suppress("DEPRECATION")
        window?.decorView?.systemUiVisibility = if (darkTheme) 0 else
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}
