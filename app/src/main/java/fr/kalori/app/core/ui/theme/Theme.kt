package fr.kalori.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp
import fr.kalori.app.domain.model.ExerciseCategory
import fr.kalori.app.domain.model.MealSlot

val KaloriShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun KaloriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalKaloriColors provides if (darkTheme) DarkKaloriColors else LightKaloriColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = KaloriTypography,
            shapes = KaloriShapes,
            content = content,
        )
    }
}

/** Accès aux couleurs propres à Kalori : `Kalori.colors`. */
object Kalori {
    val colors: KaloriColors
        @Composable @ReadOnlyComposable get() = LocalKaloriColors.current
}

@Composable
@ReadOnlyComposable
fun MealSlot.accent(): Accent = when (this) {
    MealSlot.MORNING -> Kalori.colors.morning
    MealSlot.NOON -> Kalori.colors.noon
    MealSlot.EVENING -> Kalori.colors.evening
    MealSlot.SNACK -> Kalori.colors.snack
}

@Composable
@ReadOnlyComposable
fun ExerciseCategory.accent(): Accent = when (this) {
    ExerciseCategory.GYM -> Kalori.colors.gym
    ExerciseCategory.STREET -> Kalori.colors.street
}
