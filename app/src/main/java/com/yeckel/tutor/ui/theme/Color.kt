package com.yeckel.tutor.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Devices on Android 12+ use dynamic color instead of these palettes.
val LightScheme = lightColorScheme(
    primary = Color(0xFF006B57),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF7EF5DA),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4C635B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DD),
    onSecondaryContainer = Color(0xFF08201A),
    tertiary = Color(0xFF456179),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFDF9),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFDF9),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF404945),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

val DarkScheme = darkColorScheme(
    primary = Color(0xFF5CDBC2),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFF7EF5DA),
    secondary = Color(0xFFB3CCC1),
    onSecondary = Color(0xFF203530),
    secondaryContainer = Color(0xFF374B45),
    onSecondaryContainer = Color(0xFFCEE9DD),
    tertiary = Color(0xFFACC9E0),
    onTertiary = Color(0xFF14334A),
    background = Color(0xFF191C1A),
    onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF191C1A),
    onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF404945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)
