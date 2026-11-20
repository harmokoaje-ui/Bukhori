package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ColorOutlineSubtle = androidx.compose.ui.graphics.Color(0xFF252C38)

private val OptixProDarkColorScheme = darkColorScheme(
    primary = CinemaAmber,
    onPrimary = ObsidianBlack,
    primaryContainer = CinemaAmberContainer,
    onPrimaryContainer = CinemaAmberBright,
    secondary = OpticalCyan,
    onSecondary = ObsidianBlack,
    secondaryContainer = OpticalCyanContainer,
    onSecondaryContainer = PureOpticWhite,
    tertiary = NightEmerald,
    onTertiary = ObsidianBlack,
    tertiaryContainer = NightEmeraldContainer,
    onTertiaryContainer = PureOpticWhite,
    error = RecordCrimson,
    onError = PureOpticWhite,
    errorContainer = RecordCrimsonContainer,
    onErrorContainer = PureOpticWhite,
    background = ObsidianBlack,
    onBackground = PureOpticWhite,
    surface = OpticalSurface,
    onSurface = PureOpticWhite,
    surfaceVariant = AnodizedDeck,
    onSurfaceVariant = SlateSecondary,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = OpticalSurface,
    surfaceContainer = AnodizedDeck,
    surfaceContainerHigh = MachinedControl,
    outline = TitaniumBorder,
    outlineVariant = ColorOutlineSubtle
)

@Composable
fun OptixProTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OptixProDarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
