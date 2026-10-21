package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.SnackMode
import fr.kalori.app.domain.nutrition.NutritionTargets

/**
 * Répartition de l'objectif journalier entre les repas de la journée.
 *
 * - L'utilisateur choisit ses repas principaux (matin, midi, soir) : par exemple sans petit-déjeuner.
 * - La collation est ajoutée « si besoin » quand l'objectif est élevé ou qu'il y a peu de repas,
 *   pour éviter des plats démesurés ; elle peut aussi être toujours ou jamais proposée.
 * - Chaque créneau reçoit une part proportionnelle à son poids (Matin 25, Midi 35, Soir 30, Collation 10),
 *   renormalisée sur les créneaux actifs. Sans petit-déjeuner, la collation pèse davantage (20).
 */
object MealSchedule {

    const val SNACK_AUTO_MIN_KCAL = 2200
    private const val SNACK_WEIGHT_WITHOUT_BREAKFAST = 20

    fun slots(enabledMains: Set<MealSlot>, snackMode: SnackMode, dailyKcal: Int): List<MealSlot> {
        val mains = MealSlot.mains.filter { it in enabledMains }.ifEmpty { listOf(MealSlot.NOON) }
        val withSnack = when (snackMode) {
            SnackMode.ALWAYS -> true
            SnackMode.NEVER -> false
            SnackMode.AUTO -> dailyKcal >= SNACK_AUTO_MIN_KCAL || mains.size <= 2
        }
        return if (withSnack) mains + MealSlot.SNACK else mains
    }

    fun targets(daily: NutritionTargets, slots: List<MealSlot>): Map<MealSlot, NutritionTargets> {
        val weights = slots.associateWith { slot ->
            if (slot == MealSlot.SNACK && MealSlot.MORNING !in slots) SNACK_WEIGHT_WITHOUT_BREAKFAST else slot.weight
        }
        val total = weights.values.sum().toDouble()
        return slots.associateWith { daily.scaled(weights.getValue(it) / total) }
    }

    fun targets(daily: NutritionTargets, enabledMains: Set<MealSlot>, snackMode: SnackMode): Map<MealSlot, NutritionTargets> =
        targets(daily, slots(enabledMains, snackMode, daily.kcal))
}
