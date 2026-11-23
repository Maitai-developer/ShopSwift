package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = DeepRoyalBlue,
    onPrimary = PureWhite,
    primaryContainer = IceBlueSurface,
    onPrimaryContainer = DeepRoyalBlue,
    secondary = SapphireBlue,
    onSecondary = PureWhite,
    secondaryContainer = SkyBlueLight,
    onSecondaryContainer = DeepRoyalBlueDark,
    tertiary = CobaltAccent,
    onTertiary = PureWhite,
    background = FrostWhite,
    onBackground = SlateInk,
    surface = PureWhite,
    onSurface = SlateInk,
    surfaceVariant = IceBlueSurface,
    onSurfaceVariant = SlateSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle,
    error = CrimsonError,
    onError = PureWhite,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonError
)

private val DarkColorScheme = darkColorScheme(
    primary = LuminousSapphire,
    onPrimary = MidnightNavyBg,
    primaryContainer = DeepRoyalBlue,
    onPrimaryContainer = CrispIceText,
    secondary = SapphireBlue,
    onSecondary = PureWhite,
    secondaryContainer = MidnightNavyCard,
    onSecondaryContainer = SkyBlueLight,
    tertiary = LuminousSapphire,
    onTertiary = MidnightNavyBg,
    background = MidnightNavyBg,
    onBackground = CrispIceText,
    surface = MidnightNavySurface,
    onSurface = CrispIceText,
    surfaceVariant = MidnightNavyCard,
    onSurfaceVariant = MutedIceText,
    outline = ColorTokens.DarkOutline,
    outlineVariant = ColorTokens.DarkOutline,
    error = CrimsonError,
    onError = PureWhite,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonError
)

private object ColorTokens {
    val DarkOutline = androidx.compose.ui.graphics.Color(0xFF1E3A6E)
}

val ShopSwiftShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ShopSwiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShopSwiftTypography,
        shapes = ShopSwiftShapes,
        content = content
    )
}
