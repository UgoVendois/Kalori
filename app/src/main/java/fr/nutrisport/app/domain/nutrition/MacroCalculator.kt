package fr.nutrisport.app.domain.nutrition

import fr.nutrisport.app.domain.model.GoalType
import kotlin.math.roundToInt

/** Objectifs journaliers en kcal et en grammes. */
data class NutritionTargets(
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val fiberG: Int,
    /** Avertissements « hors repères Anses » à afficher (aucun ne bloque). */
    val notices: Set<MacroNotice>,
) {
    /** Cible du plat du jour : une part [share] de l'objectif journalier (bornée à 30–50 %). */
    fun forMeal(share: Double): NutritionTargets {
        val s = share.coerceIn(MacroCalculator.MIN_MEAL_SHARE, MacroCalculator.MAX_MEAL_SHARE)
        return copy(
            kcal = (kcal * s).roundToInt(),
            proteinG = (proteinG * s).roundToInt(),
            fatG = (fatG * s).roundToInt(),
            carbsG = (carbsG * s).roundToInt(),
            fiberG = (fiberG * s).roundToInt(),
        )
    }
}

enum class MacroNotice {
    /** Protéines > 20 % de l'énergie : au-dessus du repère Anses population générale (courant chez les sportifs). */
    PROTEIN_ABOVE_ANSES_RANGE,
    /** L'objectif protéique en g/kg a dû être réduit pour garder au moins 40 % de glucides. */
    PROTEIN_REDUCED_FOR_CARBS,
}

/**
 * Répartition des macronutriments (sources : docs/SOURCES.md §5).
 *
 * 1. Lipides : 35 % de l'énergie (bas de la fourchette Anses 35–40 %).
 * 2. Protéines : 1,2 g/kg (maintien) ou 1,6 g/kg (perte ou prise), plafonnées à 2 g/kg,
 *    puis réduites si nécessaire pour laisser au moins 40 % de glucides, sans descendre sous 10 %.
 * 3. Glucides : le reste.
 * 4. Fibres : 30 g/j.
 */
object MacroCalculator {

    const val FAT_ENERGY_SHARE = 0.35
    const val MIN_CARBS_ENERGY_SHARE = 0.40
    const val MIN_PROTEIN_ENERGY_SHARE = 0.10
    const val MAX_PROTEIN_ENERGY_SHARE_ANSES = 0.20
    const val MAX_PROTEIN_G_PER_KG = 2.0
    const val FIBER_G = 30
    const val DEFAULT_MEAL_SHARE = 0.40
    const val MIN_MEAL_SHARE = 0.30
    const val MAX_MEAL_SHARE = 0.50

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBS = 4.0
    private const val KCAL_PER_G_FAT = 9.0

    fun proteinPerKg(goal: GoalType): Double = when (goal) {
        GoalType.MAINTAIN -> 1.2
        GoalType.LOSE, GoalType.GAIN -> 1.6
    }

    fun targets(kcal: Int, weightKg: Double, goal: GoalType): NutritionTargets {
        require(kcal > 0 && weightKg > 0)
        val notices = mutableSetOf<MacroNotice>()
        val fatKcal = kcal * FAT_ENERGY_SHARE

        val wantedProteinKcal = minOf(proteinPerKg(goal), MAX_PROTEIN_G_PER_KG) * weightKg * KCAL_PER_G_PROTEIN
        val maxProteinKcal = kcal - fatKcal - kcal * MIN_CARBS_ENERGY_SHARE
        var proteinKcal = wantedProteinKcal
        if (proteinKcal > maxProteinKcal) {
            proteinKcal = maxProteinKcal
            notices += MacroNotice.PROTEIN_REDUCED_FOR_CARBS
        }
        proteinKcal = proteinKcal.coerceAtLeast(kcal * MIN_PROTEIN_ENERGY_SHARE)
        if (proteinKcal > kcal * MAX_PROTEIN_ENERGY_SHARE_ANSES + 1e-6) {
            notices += MacroNotice.PROTEIN_ABOVE_ANSES_RANGE
        }
        val carbsKcal = kcal - fatKcal - proteinKcal

        return NutritionTargets(
            kcal = kcal,
            proteinG = (proteinKcal / KCAL_PER_G_PROTEIN).roundToInt(),
            fatG = (fatKcal / KCAL_PER_G_FAT).roundToInt(),
            carbsG = (carbsKcal / KCAL_PER_G_CARBS).roundToInt(),
            fiberG = FIBER_G,
            notices = notices,
        )
    }
}
