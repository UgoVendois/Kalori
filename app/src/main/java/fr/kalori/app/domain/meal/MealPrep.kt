package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.MealSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Ce qu'un repas demande en cuisine le jour où il est mangé. */
enum class PrepKind {
    /** Cuisiné le jour même. */
    COOK,
    /** Dîner cuisiné en double : une portion pour le lendemain midi. */
    COOK_WITH_LEFTOVERS,
    /** Midi : restes du dîner de la veille, rien à cuisiner. */
    LEFTOVERS,
    /** Jour de fournée : toute la semaine est préparée en une fois. */
    BATCH_DAY,
    /** Pris dans la fournée de la semaine, rien à préparer. */
    FROM_BATCH,
}

/** Repas planifié, vu du point de vue de la préparation. */
data class PrepEntry(
    val date: LocalDate,
    val slot: MealSlot,
    val recipeId: String,
    val portionFactor: Double,
    val skipped: Boolean = false,
)

/**
 * [cookPortions] : portions de base à cuisiner ce jour-là pour ce repas (restes ou fournée compris),
 * 0 si rien à cuisiner. [batchDays] : jours couverts par la fournée (jour de fournée seulement).
 */
data class PrepInfo(val kind: PrepKind, val cookPortions: Double, val batchDays: Int = 0)

/**
 * Déduit l'organisation de la cuisine à partir des repas planifiés : quel dîner est cuisiné en double
 * pour le midi suivant, quel jour se prépare la fournée de la semaine, et donc quelles quantités
 * acheter. Les liens sont recalculés à chaque fois (rien n'est stocké) : remplacer ou sauter un repas
 * met l'organisation à jour d'elle-même.
 */
object MealPrepPlanner {

    /**
     * [entries] doit inclure la veille du premier jour affiché (restes) et le début de la semaine en
     * cours (fournées). [batchRecipes] : recettes qui se préparent en fournée (les autres recettes
     * « de la semaine » sont de simples assemblages faits chaque jour).
     */
    fun analyze(
        entries: List<PrepEntry>,
        settings: PlanSettings,
        batchRecipes: Set<String>,
    ): Map<Pair<LocalDate, MealSlot>, PrepInfo> {
        val byKey = entries.associateBy { it.date to it.slot }
        val result = mutableMapOf<Pair<LocalDate, MealSlot>, PrepInfo>()
        val batchGroups = entries
            .filter { it.slot in settings.weeklySlots && it.recipeId in batchRecipes && !it.skipped }
            .groupBy { Triple(it.slot, MealPlanner.weekStart(it.date), it.recipeId) }
        val batchFirst = batchGroups.values.associate { group ->
            val first = group.minBy { it.date }
            (first.date to first.slot) to daysUntilSunday(first.date)
        }
        val inBatch = batchGroups.values.flatten().map { it.date to it.slot }.toSet()

        for (entry in entries) {
            val key = entry.date to entry.slot
            result[key] = when {
                key in batchFirst -> {
                    val days = batchFirst.getValue(key)
                    PrepInfo(PrepKind.BATCH_DAY, entry.portionFactor * days, batchDays = days)
                }
                key in inBatch -> PrepInfo(PrepKind.FROM_BATCH, 0.0)
                settings.lunchLeftovers && entry.slot == MealSlot.NOON && isLeftover(entry, byKey) ->
                    PrepInfo(PrepKind.LEFTOVERS, 0.0)
                settings.lunchLeftovers && entry.slot == MealSlot.EVENING -> {
                    val next = byKey[entry.date.plusDays(1) to MealSlot.NOON]
                    when {
                        // Lendemain pas encore planifié (fin de la fenêtre) : on prévoit quand même la boîte.
                        next == null -> PrepInfo(PrepKind.COOK_WITH_LEFTOVERS, cooked(entry) + entry.portionFactor)
                        next.recipeId == entry.recipeId && !next.skipped ->
                            PrepInfo(PrepKind.COOK_WITH_LEFTOVERS, cooked(entry) + next.portionFactor)
                        else -> PrepInfo(PrepKind.COOK, cooked(entry))
                    }
                }
                else -> PrepInfo(PrepKind.COOK, cooked(entry))
            }
        }
        return result
    }

    private fun isLeftover(noon: PrepEntry, byKey: Map<Pair<LocalDate, MealSlot>, PrepEntry>): Boolean =
        byKey[noon.date.minusDays(1) to MealSlot.EVENING]?.recipeId == noon.recipeId

    private fun cooked(entry: PrepEntry) = if (entry.skipped) 0.0 else entry.portionFactor

    private fun daysUntilSunday(date: LocalDate): Int =
        ChronoUnit.DAYS.between(date, date.with(DayOfWeek.SUNDAY)).toInt() + 1
}
