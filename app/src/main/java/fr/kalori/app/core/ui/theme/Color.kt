package fr.kalori.app.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Palette Kalori : chaude et appétissante. Orange brûlé (cuisine), vert frais (végétal, réussite),
 * indigo (sport, soir), fonds crème. Chaque mode a ses propres teintes (le sombre n'est pas une inversion).
 * Contrastes vérifiés pour le texte sur les couleurs pleines (≥ 4,5:1).
 */

val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF5C1A00),
    inversePrimary = Color(0xFFFFB693),
    secondary = Color(0xFF2F7D4F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCBF0D7),
    onSecondaryContainer = Color(0xFF07391C),
    tertiary = Color(0xFF4F4FC9),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E1FF),
    onTertiaryContainer = Color(0xFF17166B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF2A1B12),
    surface = Color(0xFFFFF8F3),
    onSurface = Color(0xFF2A1B12),
    surfaceVariant = Color(0xFFF4E3D6),
    onSurfaceVariant = Color(0xFF6B5546),
    surfaceTint = Color(0xFFC2410C),
    inverseSurface = Color(0xFF3D2E24),
    inverseOnSurface = Color(0xFFFFEDE3),
    outline = Color(0xFF9E8677),
    outlineVariant = Color(0xFFE8D6C8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F3),
    surfaceDim = Color(0xFFEBDDD2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF2E9),
    surfaceContainer = Color(0xFFFCEBDF),
    surfaceContainerHigh = Color(0xFFF7E4D6),
    surfaceContainerHighest = Color(0xFFF1DDCE),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB693),
    onPrimary = Color(0xFF561D00),
    primaryContainer = Color(0xFF8A3107),
    onPrimaryContainer = Color(0xFFFFDBC8),
    inversePrimary = Color(0xFFC2410C),
    secondary = Color(0xFF8FD8A8),
    onSecondary = Color(0xFF003920),
    secondaryContainer = Color(0xFF135233),
    onSecondaryContainer = Color(0xFFABF5C3),
    tertiary = Color(0xFFC2C1FF),
    onTertiary = Color(0xFF26248C),
    tertiaryContainer = Color(0xFF3D3CA3),
    onTertiaryContainer = Color(0xFFE2E1FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17110D),
    onBackground = Color(0xFFF2E6DD),
    surface = Color(0xFF17110D),
    onSurface = Color(0xFFF2E6DD),
    surfaceVariant = Color(0xFF52443A),
    onSurfaceVariant = Color(0xFFD8C3B4),
    surfaceTint = Color(0xFFFFB693),
    inverseSurface = Color(0xFFF2E6DD),
    inverseOnSurface = Color(0xFF3A2E26),
    outline = Color(0xFFA08D80),
    outlineVariant = Color(0xFF4A3D34),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3F352F),
    surfaceDim = Color(0xFF17110D),
    surfaceContainerLowest = Color(0xFF110C09),
    surfaceContainerLow = Color(0xFF1F1814),
    surfaceContainer = Color(0xFF241C17),
    surfaceContainerHigh = Color(0xFF2F2621),
    surfaceContainerHighest = Color(0xFF3A312B),
)

/** Couleur d'accent (fond doux + contenu) : créneaux de repas, catégories de séance, macros. */
@Immutable
data class Accent(val container: Color, val content: Color, val solid: Color)

/** Couleurs propres à Kalori, en plus du schéma Material. */
@Immutable
data class KaloriColors(
    val morning: Accent,
    val noon: Accent,
    val evening: Accent,
    val snack: Accent,
    val gym: Accent,
    val street: Accent,
    /** Macronutriments : 3 premières teintes de la palette catégorielle de référence (validées). */
    val protein: Color,
    val fat: Color,
    val carbs: Color,
    val heroGradient: List<Color>,
)

val LightKaloriColors = KaloriColors(
    morning = Accent(Color(0xFFFFEBC2), Color(0xFF5C3B00), Color(0xFFE09A00)),
    noon = Accent(Color(0xFFFFDBC8), Color(0xFF5C1A00), Color(0xFFC2410C)),
    evening = Accent(Color(0xFFE2E1FF), Color(0xFF17166B), Color(0xFF4F4FC9)),
    snack = Accent(Color(0xFFCBF0D7), Color(0xFF07391C), Color(0xFF2F7D4F)),
    gym = Accent(Color(0xFFE2E1FF), Color(0xFF17166B), Color(0xFF4F4FC9)),
    street = Accent(Color(0xFFCBF0D7), Color(0xFF07391C), Color(0xFF2F7D4F)),
    protein = Color(0xFF2A78D6),
    fat = Color(0xFFEB6834),
    carbs = Color(0xFF1BAF7A),
    heroGradient = listOf(Color(0xFFE2581B), Color(0xFFB8360A)),
)

val DarkKaloriColors = KaloriColors(
    morning = Accent(Color(0xFF4A3300), Color(0xFFFFDFA0), Color(0xFFF5B83D)),
    noon = Accent(Color(0xFF6B2606), Color(0xFFFFDBC8), Color(0xFFFF9B6A)),
    evening = Accent(Color(0xFF2E2D84), Color(0xFFE2E1FF), Color(0xFFA9A8FF)),
    snack = Accent(Color(0xFF0F4429), Color(0xFFABF5C3), Color(0xFF7BCB97)),
    gym = Accent(Color(0xFF2E2D84), Color(0xFFE2E1FF), Color(0xFFA9A8FF)),
    street = Accent(Color(0xFF0F4429), Color(0xFFABF5C3), Color(0xFF7BCB97)),
    protein = Color(0xFF3987E5),
    fat = Color(0xFFD95926),
    carbs = Color(0xFF199E70),
    heroGradient = listOf(Color(0xFF8A3107), Color(0xFF5C1E04)),
)

val LocalKaloriColors = staticCompositionLocalOf { LightKaloriColors }
