package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.MealType
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
    val mealType: MealType,
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

/** Un repas à planifier : un créneau d'un jour, avec sa cible nutritionnelle. */
data class SlotRequest(val date: LocalDate, val slot: MealSlot, val target: NutritionTargets)

data class PlannedMeal(
    val date: LocalDate,
    val slot: MealSlot,
    val recipeId: String,
    val portionFactor: Double,
    /** Nutriments de la portion ajustée. */
    val nutrition: Nutrition,
    /** true si aucune recette de saison compatible n'était disponible pour ce repas. */
    val outOfSeason: Boolean,
)

/** Repas déjà planifié ou passé, utilisé pour varier les plats. */
data class MealContext(val date: LocalDate, val slot: MealSlot, val recipeId: String, val mainProtein: MainProtein)

/**
 * Composition des repas de la journée (matin, midi, soir, collation).
 *
 * Contraintes STRICTES, jamais assouplies : allergènes et traces déclarés, régimes, aliments détestés,
 * et type de recette adapté au créneau (petit-déjeuner, plat, collation).
 * Contraintes assouplies seulement si le catalogue compatible est trop petit, dans cet ordre :
 * fenêtre de non-répétition pleine → réduite → produits hors saison autorisés → très courte → aucune.
 * Fenêtre pleine : 14 jours pour les plats, 4 jours pour les petits-déjeuners et collations.
 * Ensuite : score (calories et protéines proches de la cible, aliments aimés, protéine principale
 * différente du reste de la journée et des jours voisins) et tirage pondéré déterministe.
 */
object MealPlanner {

    private const val TOP_N = 5

    private data class Level(val repeatWindowDays: Int, val seasonStrict: Boolean)

    private val mainLevels = listOf(Level(14, true), Level(7, true), Level(7, false), Level(3, false), Level(0, false))
    private val lightLevels = listOf(Level(4, true), Level(2, true), Level(2, false), Level(1, false), Level(0, false))

    /** Bornes du facteur de portion : un petit-déjeuner ou une collation peut être doublé, pas un plat. */
    fun portionBounds(mealType: MealType): ClosedFloatingPointRange<Double> = when (mealType) {
        MealType.MAIN -> 0.6..1.8
        MealType.BREAKFAST, MealType.SNACK -> 0.5..2.0
    }

    /** Recettes respectant les contraintes strictes (tous types confondus). */
    fun eligible(candidates: List<MealCandidate>, prefs: FoodPreferences): List<MealCandidate> {
        val allergenMask = prefs.allergens.toMask()
        return candidates.filter { c ->
            c.allergensMask and allergenMask == 0 &&
                DietRules.isCompatible(prefs.diets, c.dietMask) &&
                c.ingredientIds.none { it in prefs.disliked }
        }
    }

    /**
     * Planifie chaque demande, jour par jour et créneau par créneau. [context] : repas déjà connus
     * (passés ou conservés). Une demande sans recette possible est omise.
     */
    fun plan(
        requests: List<SlotRequest>,
        candidates: List<MealCandidate>,
        prefs: FoodPreferences,
        context: List<MealContext>,
    ): List<PlannedMeal> {
        val pool = eligible(candidates, prefs)
        val byId = candidates.associateBy { it.id }
        val known = context.toMutableList()
        val result = mutableListOf<PlannedMeal>()
        for (request in requests.sortedWith(compareBy({ it.date }, { it.slot.ordinal }))) {
            val others = known.filterNot { it.date == request.date && it.slot == request.slot }
            val meal = pick(request, pool, prefs, others, excluded = emptySet()) ?: continue
            result += meal
            known.removeAll { it.date == request.date && it.slot == request.slot }
            known += MealContext(request.date, request.slot, meal.recipeId, byId.getValue(meal.recipeId).mainProtein)
        }
        return result
    }

    /** Remplace un repas : [excluded] contient le plat actuel et ceux déjà refusés pour ce repas. */
    fun replace(
        request: SlotRequest,
        candidates: List<MealCandidate>,
        prefs: FoodPreferences,
        context: List<MealContext>,
        excluded: Set<String>,
    ): PlannedMeal? = pick(
        request,
        eligible(candidates, prefs),
        prefs,
        context.filterNot { it.date == request.date && it.slot == request.slot },
        excluded,
    )

    fun portionFactor(candidate: MealCandidate, target: NutritionTargets): Double =
        (target.kcal / candidate.nutrition.kcal).coerceIn(portionBounds(candidate.mealType))

    private fun pick(
        request: SlotRequest,
        pool: List<MealCandidate>,
        prefs: FoodPreferences,
        context: List<MealContext>,
        excluded: Set<String>,
    ): PlannedMeal? {
        val mealType = request.slot.mealType
        val available = pool.filter { it.mealType == mealType && it.id !in excluded }
        if (available.isEmpty()) return null
        val month = request.date.monthValue
        val levels = if (mealType == MealType.MAIN) mainLevels else lightLevels
        for (level in levels) {
            val options = available.filter { c ->
                (!level.seasonStrict || SeasonMask.contains(c.seasonMask, month)) &&
                    context.none { it.recipeId == c.id && daysBetween(it.date, request.date) < level.repeatWindowDays }
            }
            if (options.isEmpty()) continue
            val ranked = options
                .map { it to score(it, request, prefs, context) }
                .sortedWith(compareByDescending<Pair<MealCandidate, Double>> { it.second }.thenBy { it.first.id })
                .take(TOP_N)
            val chosen = weightedPick(ranked.map { it.first }, Random(seedFor(request, excluded)))
            val portion = portionFactor(chosen, request.target)
            return PlannedMeal(
                date = request.date,
                slot = request.slot,
                recipeId = chosen.id,
                portionFactor = portion,
                nutrition = chosen.nutrition * portion,
                outOfSeason = !SeasonMask.contains(chosen.seasonMask, month),
            )
        }
        return null
    }

    private fun score(c: MealCandidate, request: SlotRequest, prefs: FoodPreferences, context: List<MealContext>): Double {
        val target = request.target
        val n = c.nutrition * portionFactor(c, target)
        val kcalError = abs(n.kcal - target.kcal) / max(1, target.kcal)
        val proteinShortfall = max(0.0, target.proteinG - n.protein) / max(1, target.proteinG)
        var score = 10.0 - 20.0 * kcalError - 6.0 * proteinShortfall
        score += 2.0 * minOf(3, c.ingredientIds.count { it in prefs.liked })
        if (c.mealType == MealType.MAIN) {
            for (other in context) {
                if (other.mainProtein != c.mainProtein || other.slot.mealType != MealType.MAIN) continue
                when (daysBetween(other.date, request.date)) {
                    0L -> score -= 3.0 // midi et soir du même jour
                    1L -> score -= 4.0
                    2L -> score -= 1.5
                }
            }
        }
        if (SeasonMask.contains(c.seasonMask, request.date.monthValue)) score += 1.0
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

    private fun seedFor(request: SlotRequest, excluded: Set<String>): Long =
        request.date.toEpochDay() * 1_000_003L + request.slot.ordinal * 7_919L + excluded.sorted().hashCode()

    private fun daysBetween(a: LocalDate, b: LocalDate): Long = abs(ChronoUnit.DAYS.between(a, b))
}
