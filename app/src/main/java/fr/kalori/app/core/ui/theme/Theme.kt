package fr.kalori.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Neutral98,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Orange40,
    onSecondary = Neutral98,
    secondaryContainer = Orange90,
    onSecondaryContainer = Orange10,
    error = Red40,
    onError = Neutral98,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral98,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Orange80,
    onSecondary = Orange10,
    secondaryContainer = Orange30,
    onSecondaryContainer = Orange90,
    error = Red80,
    onError = Red10,
    errorContainer = Red40,
    onErrorContainer = Red90,
    background = NeutralDark,
    onBackground = Neutral90,
    surface = NeutralDark,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    outline = NeutralVariant50,
)

@Composable
fun KaloriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KaloriTypography,
        content = content,
    )
}
