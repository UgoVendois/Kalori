package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.CookingLevel
import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.MealType
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.model.SeasonMask
import fr.kalori.app.domain.model.toMask
import fr.kalori.app.domain.nutrition.NutritionTargets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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
    /** Coût estimé d'une portion de base, en euros. */
    val cost: Double = 0.0,
    val prepMinutes: Int = 30,
    /** 1 simple, 2 normal, 3 élaboré. */
    val difficulty: Int = 1,
    /** Jours de conservation une fois préparé. */
    val keepDays: Int = 3,
    /** Se transporte et se réchauffe (restes du soir pour le midi). */
    val lunchbox: Boolean = true,
    /** Se prépare en une fois pour la semaine. */
    val batch: Boolean = false,
)

/** Organisation de la semaine : niveau de cuisine, restes pour le midi, recettes de la semaine. */
data class PlanSettings(
    val cookingLevel: CookingLevel = CookingLevel.ELABORATE,
    /** Le midi reprend le dîner de la veille (cuisiné en double, emporté dans une boîte). */
    val lunchLeftovers: Boolean = false,
    /** Créneaux servis par une même recette toute la semaine (matin, collation). */
    val weeklySlots: Set<MealSlot> = emptySet(),
)

/** Goûts et restrictions déclarés par l'utilisateur. */
data class FoodPreferences(
    val allergens: Set<Allergen> = emptySet(),
    val diets: Set<Diet> = emptySet(),
    val liked: Set<String> = emptySet(),
    val disliked: Set<String> = emptySet(),
)

/** Un repas à planifier : un créneau d'un jour, avec sa cible nutritionnelle. */
data class SlotRequest(
    val date: LocalDate,
    val slot: MealSlot,
    val target: NutritionTargets,
    /** Part du budget hebdomadaire pour ce repas (€), null : pas de budget. */
    val budget: Double? = null,
)

data class PlannedMeal(
    val date: LocalDate,
    val slot: MealSlot,
    val recipeId: String,
    val portionFactor: Double,
    /** Nutriments de la portion ajustée. */
    val nutrition: Nutrition,
    /** Coût estimé de la portion ajustée (€). */
    val cost: Double = 0.0,
    /** true si aucune recette de saison compatible n'était disponible pour ce repas. */
    val outOfSeason: Boolean,
)

/** Repas déjà planifié ou passé, utilisé pour varier les plats. */
data class MealContext(
    val date: LocalDate,
    val slot: MealSlot,
    val recipeId: String,
    val mainProtein: MainProtein,
    /** Repas sauté : pas de restes ni de fournée à reprendre. */
    val skipped: Boolean = false,
)

/**
 * Composition des repas de la journée (matin, midi, soir, collation).
 *
 * Contraintes STRICTES, jamais assouplies : allergènes et traces déclarés, régimes, aliments détestés,
 * et type de recette adapté au créneau (petit-déjeuner, plat, collation).
 * Contraintes assouplies seulement si le catalogue compatible est trop petit, dans cet ordre :
 * fenêtre de non-répétition pleine → réduite → produits hors saison autorisés → très courte → aucune.
 * Fenêtre pleine : 14 jours pour les plats, 4 jours pour les petits-déjeuners et collations.
 * Ensuite : score (calories et protéines proches de la cible, aliments aimés, protéine principale
 * différente du reste de la journée et des jours voisins, coût, temps de préparation) et tirage
 * pondéré déterministe.
 *
 * Organisation ([PlanSettings]) :
 * - restes : le midi reprend le dîner de la veille s'il se transporte ; les dîners sont alors choisis
 *   parmi les plats transportables ;
 * - recette de la semaine : un créneau (matin, collation) garde la même recette du lundi au dimanche,
 *   de préférence une fournée (cookies, granola…) ou un assemblage sans préparation ;
 * - niveau de cuisine et budget du repas.
 * Ces préférences s'assouplissent si rien ne convient : d'abord le niveau, puis l'organisation,
 * puis la répétition (jusqu'à 3 jours d'écart), et enfin le budget plutôt que de servir le même plat
 * tous les jours.
 */
object MealPlanner {

    private const val TOP_N = 5
    /** Au-delà, la recette demande une vraie préparation : pas un simple assemblage. */
    private const val ASSEMBLY_MINUTES = 5
    /** Dépassement toléré du budget d'un repas avant d'écarter une recette. */
    private const val BUDGET_TOLERANCE = 1.25
    /** Dîners qui deviennent les restes du lendemain, et midis qui en sont servis. */
    private val leftoverSlots = setOf(MealSlot.NOON, MealSlot.EVENING)

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
        settings: PlanSettings = PlanSettings(),
    ): List<PlannedMeal> {
        val pool = eligible(candidates, prefs)
        val byId = candidates.associateBy { it.id }
        val known = context.toMutableList()
        val result = mutableListOf<PlannedMeal>()
        for (request in requests.sortedWith(compareBy({ it.date }, { it.slot.ordinal }))) {
            val others = known.filterNot { it.date == request.date && it.slot == request.slot }
            val meal = reused(request, pool, others, settings, excluded = emptySet())
                ?: pick(request, pool, prefs, others, emptySet(), settings)
                ?: continue
            result += meal
            known.removeAll { it.date == request.date && it.slot == request.slot }
            known += MealContext(request.date, request.slot, meal.recipeId, byId.getValue(meal.recipeId).mainProtein)
        }
        return result
    }

    /**
     * Remplace un repas : [excluded] contient le plat actuel et ceux déjà refusés pour ce repas.
     * Les restes et la recette de la semaine ne sont pas repris : on cherche bien un autre plat.
     */
    fun replace(
        request: SlotRequest,
        candidates: List<MealCandidate>,
        prefs: FoodPreferences,
        context: List<MealContext>,
        excluded: Set<String>,
        settings: PlanSettings = PlanSettings(),
    ): PlannedMeal? = pick(
        request,
        eligible(candidates, prefs),
        prefs,
        context.filterNot { it.date == request.date && it.slot == request.slot },
        excluded,
        settings,
    )

    fun portionFactor(candidate: MealCandidate, target: NutritionTargets): Double =
        (target.kcal / candidate.nutrition.kcal).coerceIn(portionBounds(candidate.mealType))

    /** Lundi de la semaine de [date] : une recette « de la semaine » vaut du lundi au dimanche. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Peut servir toute la semaine : fournée qui se garde, ou assemblage sans préparation. */
    fun isWeeklyFriendly(c: MealCandidate): Boolean = c.batch || c.prepMinutes <= ASSEMBLY_MINUTES

    /** Peut être cuisiné le soir et emporté le lendemain midi. */
    fun isLeftoverFriendly(c: MealCandidate): Boolean = c.lunchbox && c.keepDays >= 1

    /** Recette imposée par l'organisation : restes du dîner de la veille, ou recette de la semaine. */
    private fun reused(
        request: SlotRequest,
        pool: List<MealCandidate>,
        context: List<MealContext>,
        settings: PlanSettings,
        excluded: Set<String>,
    ): PlannedMeal? {
        val byId = pool.associateBy { it.id }
        val source = when {
            request.slot in settings.weeklySlots -> context
                .filter { it.slot == request.slot && !it.skipped && weekStart(it.date) == weekStart(request.date) }
                .minByOrNull { it.date }
                ?.let { byId[it.recipeId] }
            request.slot == MealSlot.NOON && settings.lunchLeftovers -> context
                .firstOrNull { it.slot == MealSlot.EVENING && !it.skipped && it.date == request.date.minusDays(1) }
                ?.let { byId[it.recipeId] }
                ?.takeIf(::isLeftoverFriendly)
            else -> null
        } ?: return null
        if (source.id in excluded || source.mealType != request.slot.mealType) return null
        return planned(source, request)
    }

    /** Préférences d'organisation assouplies dans cet ordre si rien ne convient. */
    private enum class Strictness(val level: Boolean, val organization: Boolean, val budget: Boolean) {
        ALL(true, true, true),
        NO_LEVEL(false, true, true),
        BUDGET_ONLY(false, false, true),
        NONE(false, false, false),
    }

    private fun pick(
        request: SlotRequest,
        pool: List<MealCandidate>,
        prefs: FoodPreferences,
        context: List<MealContext>,
        excluded: Set<String>,
        settings: PlanSettings,
    ): PlannedMeal? {
        val mealType = request.slot.mealType
        val available = pool.filter { it.mealType == mealType && it.id !in excluded }
        if (available.isEmpty()) return null
        val month = request.date.monthValue
        val levels = if (mealType == MealType.MAIN) mainLevels else lightLevels
        // Répétition rapprochée (fenêtres courtes) seulement après avoir assoupli l'organisation. Un budget
        // intenable ne doit pas servir le même plat tous les jours : au-delà de la fenêtre courte, on le dépasse
        // (le coût reste pénalisé dans le score et l'écran signale le dépassement).
        val phases = listOf(Strictness.ALL, Strictness.NO_LEVEL, Strictness.BUDGET_ONLY).map { it to levels.take(3) } +
            listOf(Strictness.ALL, Strictness.NO_LEVEL, Strictness.BUDGET_ONLY).map { it to levels.subList(3, 4) } +
            listOf(Strictness.NONE to levels)
        for ((strictness, phaseLevels) in phases) {
            val allowed = available.filter { satisfies(it, request, settings, strictness) }
            if (allowed.isEmpty()) continue
            for (level in phaseLevels) {
                val options = allowed.filter { c ->
                    (!level.seasonStrict || SeasonMask.contains(c.seasonMask, month)) &&
                        context.none { it.recipeId == c.id && daysBetween(it.date, request.date) < level.repeatWindowDays }
                }
                if (options.isEmpty()) continue
                val ranked = options
                    .map { it to score(it, request, prefs, context, settings) }
                    .sortedWith(compareByDescending<Pair<MealCandidate, Double>> { it.second }.thenBy { it.first.id })
                    .take(TOP_N)
                return planned(weightedPick(ranked.map { it.first }, Random(seedFor(request, excluded))), request)
            }
        }
        return null
    }

    private fun satisfies(c: MealCandidate, request: SlotRequest, settings: PlanSettings, strictness: Strictness): Boolean {
        if (strictness.level && c.difficulty > settings.cookingLevel.maxDifficulty) return false
        if (strictness.organization) {
            val weekly = request.slot in settings.weeklySlots
            if (weekly && !isWeeklyFriendly(c)) return false
            if (settings.lunchLeftovers && request.slot in leftoverSlots && !isLeftoverFriendly(c)) return false
        }
        val budget = request.budget
        if (strictness.budget && budget != null && c.cost * portionFactor(c, request.target) > budget * BUDGET_TOLERANCE) return false
        return true
    }

    private fun planned(c: MealCandidate, request: SlotRequest): PlannedMeal {
        val portion = portionFactor(c, request.target)
        return PlannedMeal(
            date = request.date,
            slot = request.slot,
            recipeId = c.id,
            portionFactor = portion,
            nutrition = c.nutrition * portion,
            cost = c.cost * portion,
            outOfSeason = !SeasonMask.contains(c.seasonMask, request.date.monthValue),
        )
    }

    private fun score(
        c: MealCandidate,
        request: SlotRequest,
        prefs: FoodPreferences,
        context: List<MealContext>,
        settings: PlanSettings,
    ): Double {
        val target = request.target
        val portion = portionFactor(c, target)
        val n = c.nutrition * portion
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
        request.budget?.takeIf { it > 0 }?.let { budget ->
            val ratio = c.cost * portion / budget
            // Indifférent tant qu'on reste loin du budget ; de plus en plus pénalisant en s'en approchant.
            score -= 4.0 * max(0.0, ratio - 0.7) + 8.0 * max(0.0, ratio - 1.0)
        }
        when (settings.cookingLevel) {
            CookingLevel.ELABORATE -> score += 0.75 * (c.difficulty - 1)
            CookingLevel.NORMAL -> score -= max(0, c.prepMinutes - 45) / 30.0
            CookingLevel.SIMPLE -> score -= max(0, c.prepMinutes - 20) / 15.0
        }
        // Collation de la semaine : on privilégie une vraie fournée (cookies, barres…).
        if (request.slot in settings.weeklySlots && c.batch) score += 6.0
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
