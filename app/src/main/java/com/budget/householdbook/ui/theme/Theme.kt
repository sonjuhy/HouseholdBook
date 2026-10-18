package com.budget.householdbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.budget.householdbook.domain.model.SpaceType

val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun HouseholdBookTheme(
    spaceType: SpaceType = SpaceType.SHARED,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isShared = spaceType == SpaceType.SHARED
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = if (isShared) IosSystemBlueDark else IosSystemGreenDark,
            onPrimary = Color.White,
            primaryContainer = IosSystemGray5Dark,
            onPrimaryContainer = if (isShared) IosSystemBlueDark else IosSystemGreenDark,
            secondary = IosSystemOrangeDark,
            secondaryContainer = IosSystemGray5Dark,
            tertiary = IosSystemBlueDark,
            background = IosSystemGray6Dark,
            onBackground = Color(0xFFFFFFFF),
            surface = IosCardSurfaceDark,
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = IosSystemGray5Dark,
            onSurfaceVariant = Color(0xFFEBEBF5),
            surfaceContainerHigh = IosElevatedModalDark,
            outline = IosSeparatorDark
        )
    } else {
        lightColorScheme(
            primary = if (isShared) IosSystemBlueLight else IosSystemGreenLight,
            onPrimary = Color.White,
            primaryContainer = if (isShared) IosSystemBlueTint else IosSystemGreenTint,
            onPrimaryContainer = if (isShared) IosSystemBlueLight else IosSystemGreenLight,
            secondary = IosSystemOrangeLight,
            secondaryContainer = IosSystemOrangeTint,
            tertiary = IosSystemBlueLight,
            background = IosSystemGray6Light,
            onBackground = Color(0xFF000000),
            surface = IosCardSurfaceLight,
            onSurface = Color(0xFF000000),
            surfaceVariant = IosSystemGray5Light,
            onSurfaceVariant = Color(0xFF3C3C43),
            surfaceContainerHigh = Color(0xFFFFFFFF),
            outline = IosSeparatorLight
        )
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}