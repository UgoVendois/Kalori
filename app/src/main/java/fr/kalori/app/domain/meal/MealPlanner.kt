package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.model.SeasonMask
import fr.kalori.app.domain.model.toMask
import fr.kalori.app.domain.nutrition.NutritionTargets
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** Recette candidate, nutriments pour une portion de base. */
data class MealCandidate(
    val id: String,
    val mainProtein: MainProtein,
    val nutrition: Nutrition,
    val allergensMask: Int,
    val dietMask: Int,
    val seasonMask: Int,
    val ingredientIds: Set<String>,
)

/** Goûts et restrictions déclarés par l'utilisateur. */
data class FoodPreferences(
    val allergens: Set<Allergen> = emptySet(),
    val diets: Set<Diet> = emptySet(),
    val liked: Set<String> = emptySet(),
    val disliked: Set<String> = emptySet(),
)

data class PlannedMeal(
    val date: LocalDate,
    val recipeId: String,
    val portionFactor: Double,
    /** Nutriments de la portion ajustée. */
    val nutrition: Nutrition,
    /** true si aucune recette de saison compatible n'était disponible ce jour-là. */
    val outOfSeason: Boolean,
)

/** Repas déjà planifié ou passé, utilisé pour varier les plats. */
data class MealContext(val date: LocalDate, val recipeId: String, val mainProtein: MainProtein)

/**
 * Choix des plats du jour.
 *
 * Contraintes STRICTES, jamais assouplies : allergènes et traces déclarés, régimes, aliments détestés.
 * Contraintes assouplies seulement si le catalogue compatible est trop petit, dans cet ordre :
 * pas de répétition sur 14 jours → 7 jours → produits hors saison autorisés → 3 jours → aucune.
 * Ensuite : score (calories et protéines proches de la cible, aliments aimés, protéine principale
 * différente des jours voisins) et tirage pondéré déterministe parmi les 5 meilleurs.
 */
object MealPlanner {

    const val MIN_PORTION = 0.6
    const val MAX_PORTION = 1.6
    private const val TOP_N = 5

    private data class Level(val repeatWindowDays: Int, val seasonStrict: Boolean)

    private val levels = listOf(
        Level(14, true), Level(7, true), Level(7, false), Level(3, false), Level(0, false),
    )

    /** Recettes respectant les contraintes strictes. */
    fun eligible(candidates: List<MealCandidate>, prefs: FoodPreferences): List<MealCandidate> {
        val allergenMask = prefs.allergens.toMask()
        return candidates.filter { c ->
            c.allergensMask and allergenMask == 0 &&
                DietRules.isCompatible(prefs.diets, c.dietMask) &&
                c.ingredientIds.none { it in prefs.disliked }
        }
    }

    /**
     * Planifie un plat pour chaque date de [dates], dans l'ordre. [context] : repas déjà connus
     * (passés ou conservés) pris en compte pour la variété. Une date sans recette possible est omise.
     */
    fun plan(
        dates: List<LocalDate>,
        candidates: List<MealCandidate>,
        prefs: FoodPreferences,
        target: NutritionTargets,
        context: List<MealContext>,
    ): List<PlannedMeal> {
        val pool = eligible(candidates, prefs)
        val byId = candidates.associateBy { it.id }
        val known = context.toMutableList()
        val result = mutableListOf<PlannedMeal>()
        for (date in dates.sorted()) {
            val meal = pick(date, pool, prefs, target, known.filter { it.date != date }, excluded = emptySet())
                ?: continue
            result += meal
            known.removeAll { it.date == date }
            known += MealContext(date, meal.recipeId, byId.getValue(meal.recipeId).mainProtein)
        }
        return result
    }

    /** Remplace le plat d'une date : [excluded] contient le plat actuel et ceux déjà refusés ce jour-là. */
    fun replace(
        date: LocalDate,
        candidates: List<MealCandidate>,
        prefs: FoodPreferences,
        target: NutritionTargets,
        context: List<MealContext>,
        excluded: Set<String>,
    ): PlannedMeal? = pick(date, eligible(candidates, prefs), prefs, target, context.filter { it.date != date }, excluded)

    private fun pick(
        date: LocalDate,
        pool: List<MealCandidate>,
        prefs: FoodPreferences,
        target: NutritionTargets,
        context: List<MealContext>,
        excluded: Set<String>,
    ): PlannedMeal? {
        val available = pool.filter { it.id !in excluded }
        if (available.isEmpty()) return null
        val month = date.monthValue
        for (level in levels) {
            val options = available.filter { c ->
                (!level.seasonStrict || SeasonMask.contains(c.seasonMask, month)) &&
                    context.none { it.recipeId == c.id && daysBetween(it.date, date) < level.repeatWindowDays }
            }
            if (options.isEmpty()) continue
            val ranked = options
                .map { it to score(it, date, prefs, target, context) }
                .sortedWith(compareByDescending<Pair<MealCandidate, Double>> { it.second }.thenBy { it.first.id })
                .take(TOP_N)
            val chosen = weightedPick(ranked.map { it.first }, Random(seedFor(date, excluded)))
            val portion = portionFactor(chosen, target)
            return PlannedMeal(
                date = date,
                recipeId = chosen.id,
                portionFactor = portion,
                nutrition = chosen.nutrition * portion,
                outOfSeason = !SeasonMask.contains(chosen.seasonMask, month),
            )
        }
        return null
    }

    fun portionFactor(candidate: MealCandidate, target: NutritionTargets): Double =
        (target.kcal / candidate.nutrition.kcal).coerceIn(MIN_PORTION, MAX_PORTION)

    private fun score(
        c: MealCandidate,
        date: LocalDate,
        prefs: FoodPreferences,
        target: NutritionTargets,
        context: List<MealContext>,
    ): Double {
        val n = c.nutrition * portionFactor(c, target)
        val kcalError = abs(n.kcal - target.kcal) / target.kcal
        val proteinShortfall = max(0.0, target.proteinG - n.protein) / max(1, target.proteinG)
        var score = 10.0 - 20.0 * kcalError - 6.0 * proteinShortfall
        score += 2.0 * minOf(3, c.ingredientIds.count { it in prefs.liked })
        for (other in context) {
            if (other.mainProtein != c.mainProtein) continue
            when (daysBetween(other.date, date)) {
                1L -> score -= 4.0
                2L -> score -= 1.5
            }
        }
        if (SeasonMask.contains(c.seasonMask, date.monthValue)) score += 1.0
        return score
    }

    /** Le meilleur a le plus de chances d'être tiré, sans être systématique (variété). */
    private fun <T> weightedPick(items: List<T>, random: Random): T {
        val weights = items.indices.map { items.size - it }
        var roll = random.nextInt(weights.sum())
        for ((index, w) in weights.withIndex()) {
            if (roll < w) return items[index]
            roll -= w
        }
        return items.last()
    }

    private fun seedFor(date: LocalDate, excluded: Set<String>): Long =
        date.toEpochDay() * 1_000_003L + excluded.sorted().hashCode()

    private fun daysBetween(a: LocalDate, b: LocalDate): Long = abs(ChronoUnit.DAYS.between(a, b))
}
