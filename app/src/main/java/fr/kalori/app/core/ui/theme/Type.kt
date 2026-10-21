package fr.kalori.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.kalori.app.R

/** Plus Jakarta Sans (police variable, licence OFL) : une graisse par instance. */
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Jakarta = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold),
)

private val base = Typography()

val KaloriTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Jakarta),
    bodyMedium = base.bodyMedium.copy(fontFamily = Jakarta),
    bodySmall = base.bodySmall.copy(fontFamily = Jakarta),
    labelLarge = base.labelLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Medium),
)
