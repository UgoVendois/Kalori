package fr.kalori.app.data.meal

import androidx.room.withTransaction
import fr.kalori.app.core.database.AppDatabase
import fr.kalori.app.core.database.entity.IngredientEntity
import fr.kalori.app.core.database.entity.IngredientPriceEntity
import fr.kalori.app.core.database.entity.MealPlanEntryEntity
import fr.kalori.app.core.database.entity.MealPlanEntryWithRecipe
import fr.kalori.app.core.database.entity.RecipeEntity
import fr.kalori.app.core.database.entity.RecipeIngredientEntity
import fr.kalori.app.core.database.entity.RecipeWithIngredients
import fr.kalori.app.core.database.entity.ShoppingCheckEntity
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.core.di.IoDispatcher
import fr.kalori.app.core.logging.SafeLogger
import fr.kalori.app.data.profile.ProfileRepository
import fr.kalori.app.data.profile.dailyTargets
import fr.kalori.app.data.profile.planSettings
import fr.kalori.app.data.profile.slotTargets
import fr.kalori.app.data.seed.SeedImporter
import fr.kalori.app.domain.meal.FoodPreferences
import fr.kalori.app.domain.meal.MealCandidate
import fr.kalori.app.domain.meal.MealContext
import fr.kalori.app.domain.meal.MealPlanner
import fr.kalori.app.domain.meal.MealPrepPlanner
import fr.kalori.app.domain.meal.PlanSettings
import fr.kalori.app.domain.meal.PlannedMeal
import fr.kalori.app.domain.meal.PrepEntry
import fr.kalori.app.domain.meal.PrepInfo
import fr.kalori.app.domain.meal.PrepKind
import fr.kalori.app.domain.meal.ShoppingLine
import fr.kalori.app.domain.meal.ShoppingListBuilder
import fr.kalori.app.domain.meal.ShoppingSection
import fr.kalori.app.domain.meal.SlotRequest
import fr.kalori.app.domain.model.CookingLevel
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.MealStatus
import fr.kalori.app.domain.model.MealType
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.model.RecipeOrigin
import fr.kalori.app.domain.model.RestrictionKind
import fr.kalori.app.domain.model.SeasonMask
import fr.kalori.app.domain.nutrition.NutritionTargets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/** Un repas planifié (créneau d'un jour), prêt à afficher. */
data class DayMeal(
    val date: LocalDate,
    val slot: MealSlot,
    val recipeId: String,
    val name: String,
    val mainProtein: MainProtein,
    val mealType: MealType,
    val portionFactor: Double,
    val nutrition: Nutrition,
    val status: MealStatus,
    val outOfSeason: Boolean,
    val aiGenerated: Boolean,
    val prepMinutes: Int = 0,
    val difficulty: Int = 1,
    /** Coût estimé d'une portion de base (€), avec les prix corrigés par l'utilisateur. */
    val unitCost: Double = 0.0,
    /** Ce que ce repas demande en cuisine ce jour-là (restes, fournée…). */
    val prep: PrepInfo = PrepInfo(PrepKind.COOK, portionFactor),
) {
    /** Coût estimé de la portion mangée. */
    val cost: Double get() = unitCost * portionFactor
    /** Coût de ce qui est cuisiné ce jour-là (portion du lendemain midi ou fournée comprise). */
    val cookCost: Double get() = unitCost * prep.cookPortions
}

/**
 * Détail d'un repas : ingrédients à la portion ajustée, allergènes, étapes.
 * [cookIngredients] : quantités à cuisiner ce jour-là quand elles diffèrent de la portion
 * (dîner cuisiné en double pour le lendemain midi, fournée de la semaine), sinon null.
 */
data class MealDetail(
    val meal: DayMeal,
    val ingredients: List<Pair<IngredientEntity, Double>>,
    val allergensMask: Int,
    val steps: List<String>,
    val cookIngredients: List<Pair<IngredientEntity, Double>>? = null,
    val keepDays: Int = 0,
    val freezable: Boolean = false,
)

data class ShoppingList(
    val from: LocalDate,
    val to: LocalDate,
    val sections: List<ShoppingSection>,
    val checked: Set<String>,
    /** Coût estimé par article (€). */
    val itemCosts: Map<String, Double> = emptyMap(),
    /** Prix au kilo utilisé par article (estimation ou prix saisi). */
    val pricePerKg: Map<String, Double> = emptyMap(),
    /** Articles dont le prix a été saisi par l'utilisateur. */
    val customPrices: Set<String> = emptySet(),
    val weeklyBudget: Int? = null,
) {
    val total: Double get() = itemCosts.values.sum()
}

enum class ReplaceResult { REPLACED, NO_ALTERNATIVE, NOT_PLANNED }

data class VariantContext(
    val target: NutritionTargets,
    val prefs: FoodPreferences,
    val mealType: MealType,
    val weekDishNames: List<String>,
    /** Budget estimé pour ce repas (€), null : pas de budget. */
    val budget: Double? = null,
    val cookingLevel: CookingLevel = CookingLevel.NORMAL,
    /** Le plat sera emporté le lendemain midi : il doit se transporter et se réchauffer. */
    val mustTravel: Boolean = false,
)

/** Prix au kilo de chaque ingrédient : estimation embarquée, remplacée par le prix saisi s'il existe. */
class PriceBook(ingredients: List<IngredientEntity>, custom: List<IngredientPriceEntity>) {
    private val base = ingredients.associate { it.id to it.priceEurPerKg }
    private val overrides = custom.associate { it.ingredientId to it.eurPerKg }

    val customIds: Set<String> get() = overrides.keys

    fun perKg(ingredientId: String): Double = overrides[ingredientId] ?: base[ingredientId] ?: 0.0

    fun estimate(ingredientId: String): Double = base[ingredientId] ?: 0.0

    /** Coût d'une portion de base d'une recette. */
    fun cost(lines: List<RecipeIngredientEntity>): Double = lines.sumOf { it.grams / 1000.0 * perKg(it.ingredientId) }
}

/**
 * Plan de repas : les 7 prochains jours (aujourd'hui inclus), un repas par créneau actif
 * (matin, midi, soir, collation), l'historique et la liste de courses.
 * Toute la logique de choix est dans [MealPlanner] et [MealPrepPlanner] (domaine pur, testé).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MealPlanRepository @Inject constructor(
    private val database: AppDatabase,
    private val profileRepository: ProfileRepository,
    private val seedImporter: SeedImporter,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mealPlanDao = database.mealPlanDao()
    private val catalogDao = database.catalogDao()
    private val restrictionDao = database.restrictionDao()
    private val mutex = Mutex()

    fun today(): LocalDate = LocalDate.now(clock)

    fun upcomingDates(): List<LocalDate> = (0L until DAYS).map { today().plusDays(it) }

    fun observePriceBook(): Flow<PriceBook> =
        combine(catalogDao.observeIngredients(), catalogDao.observePrices()) { ingredients, prices -> PriceBook(ingredients, prices) }

    fun observeUpcoming(): Flow<List<DayMeal>> {
        val dates = upcomingDates()
        return observeMeals(dates.first(), dates.last())
    }

    fun observeHistory(): Flow<List<DayMeal>> =
        mealPlanDao.observeHistoryWithRecipe(today()).map { list -> list.map { it.toDayMeal() } }

    fun observeDetail(date: LocalDate, slot: MealSlot): Flow<MealDetail?> =
        observeMeals(date, date).flatMapLatest { meals ->
            val meal = meals.firstOrNull { it.slot == slot } ?: return@flatMapLatest flowOf(null)
            combine(catalogDao.observeRecipe(meal.recipeId), catalogDao.observeIngredients()) { recipe, ingredients ->
                recipe ?: return@combine null
                val byId = ingredients.associateBy { it.id }
                fun scaled(factor: Double) = recipe.ingredients.mapNotNull { line ->
                    byId[line.ingredientId]?.let { it to line.grams * factor }
                }
                val cook = meal.prep.cookPortions
                MealDetail(
                    meal = meal,
                    ingredients = scaled(meal.portionFactor),
                    allergensMask = recipe.recipe.allergensMask,
                    steps = recipe.recipe.steps,
                    cookIngredients = if (cook > 0 && kotlin.math.abs(cook - meal.portionFactor) > 0.01) scaled(cook) else null,
                    keepDays = recipe.recipe.keepDays,
                    freezable = recipe.recipe.freezable,
                )
            }
        }

    /**
     * Repas de [from] à [to], avec coût et organisation de la cuisine. On charge aussi la veille
     * (restes du midi) et le début de la semaine (jour de fournée) pour relier les repas entre eux.
     */
    private fun observeMeals(from: LocalDate, to: LocalDate): Flow<List<DayMeal>> {
        val loadFrom = minOf(MealPlanner.weekStart(from), from.minusDays(1))
        return combine(
            mealPlanDao.observeRangeWithRecipe(loadFrom, to.plusDays(1)),
            observePriceBook(),
            profileRepository.observeProfile(),
        ) { entries, prices, profile -> Triple(entries, prices, profile) }
            .map { (entries, prices, profile) ->
                val recipes = catalogDao.getRecipes(entries.map { it.recipe.id }.distinct()).associateBy { it.recipe.id }
                val settings = profile?.planSettings() ?: PlanSettings()
                val prep = MealPrepPlanner.analyze(
                    entries.map { PrepEntry(it.entry.date, it.entry.slot, it.recipe.id, it.entry.portionFactor, it.entry.status == MealStatus.SKIPPED) },
                    settings,
                    batchRecipes = recipes.values.filter { it.recipe.batch }.map { it.recipe.id }.toSet(),
                )
                entries
                    .filter { !it.entry.date.isBefore(from) && !it.entry.date.isAfter(to) }
                    .map { e ->
                        e.toDayMeal(
                            unitCost = recipes[e.recipe.id]?.let { prices.cost(it.ingredients) } ?: 0.0,
                            prep = prep[e.entry.date to e.entry.slot],
                        )
                    }
                    .sorted()
            }
    }

    /** Complète les repas manquants des jours à venir et retire ceux des créneaux désactivés. */
    suspend fun ensureUpcomingPlanned() = mutex.withLock {
        withContext(io) {
            val targets = profileRepository.getSlotTargets() ?: return@withContext
            mealPlanDao.deletePlannedSlotsNotIn(today(), targets.keys)
            val existing = existingKeys()
            planRequests { date, slot -> (date to slot) !in existing }
        }
    }

    /**
     * Recompose les repas à venir encore « prévus » (après un changement de goûts, de restrictions,
     * d'objectif, de budget ou d'organisation). Les repas déjà marqués mangés ou sautés sont conservés.
     */
    suspend fun regenerateUpcoming() = mutex.withLock {
        withContext(io) {
            val targets = profileRepository.getSlotTargets() ?: return@withContext
            mealPlanDao.deletePlannedSlotsNotIn(today(), targets.keys)
            val dates = upcomingDates()
            val locked = mealPlanDao.getRange(dates.first(), dates.last())
                .filter { it.status != MealStatus.PLANNED }
                .map { it.date to it.slot }
                .toSet()
            planRequests { date, slot -> (date to slot) !in locked }
        }
    }

    /**
     * Remplace un repas. Les repas liés suivent : un dîner et les restes du lendemain midi changent
     * ensemble, une recette de la semaine change pour tous les jours restants de la semaine.
     */
    suspend fun replace(date: LocalDate, slot: MealSlot): ReplaceResult = mutex.withLock {
        withContext(io) {
            val inputs = loadInputs() ?: return@withContext ReplaceResult.NOT_PLANNED
            val settings = inputs.settings
            val current = mealPlanDao.get(date, slot) ?: return@withContext ReplaceResult.NOT_PLANNED

            // Restes : remplacer le midi revient à remplacer le dîner de la veille, s'il est encore à venir.
            if (settings.lunchLeftovers && slot == MealSlot.NOON) {
                val dinner = mealPlanDao.get(date.minusDays(1), MealSlot.EVENING)
                if (dinner != null && dinner.recipeId == current.recipeId && dinner.status == MealStatus.PLANNED &&
                    !dinner.date.isBefore(today())
                ) {
                    return@withContext replaceGroup(inputs, dinner, listOf(dinner, current))
                }
            }
            val group = when {
                slot in settings.weeklySlots -> mealPlanDao.getRange(maxOf(today(), date), MealPlanner.weekStart(date).plusDays(6))
                    .filter { it.slot == slot && it.recipeId == current.recipeId && it.status == MealStatus.PLANNED }
                settings.lunchLeftovers && slot == MealSlot.EVENING -> listOfNotNull(
                    current,
                    mealPlanDao.get(date.plusDays(1), MealSlot.NOON)
                        ?.takeIf { it.recipeId == current.recipeId && it.status == MealStatus.PLANNED },
                )
                else -> listOf(current)
            }.ifEmpty { listOf(current) }
            replaceGroup(inputs, current, group)
        }
    }

    /** Choisit un autre plat pour [lead] et l'applique à tout le [group] (portion ajustée à chaque créneau). */
    private suspend fun replaceGroup(inputs: Inputs, lead: MealPlanEntryEntity, group: List<MealPlanEntryEntity>): ReplaceResult {
        val target = inputs.targets[lead.slot] ?: return ReplaceResult.NOT_PLANNED
        val request = inputs.request(lead.date, lead.slot, target)
        val keys = group.map { it.date to it.slot }.toSet()
        val context = loadContext(inputs.candidates).filter { (it.date to it.slot) !in keys }
        val rejected = lead.rejectedRecipeIds.toSet() + lead.recipeId
        val meal = MealPlanner.replace(request, inputs.candidates, inputs.prefs, context, rejected, inputs.settings)
            // Toutes les alternatives ont déjà été refusées : on repart de zéro, sauf le plat actuel.
            ?: MealPlanner.replace(request, inputs.candidates, inputs.prefs, context, setOf(lead.recipeId), inputs.settings)
            ?: return ReplaceResult.NO_ALTERNATIVE
        val newRejected = if (meal.recipeId in rejected) listOf(lead.recipeId) else rejected.toList()
        val candidate = inputs.candidates.first { it.id == meal.recipeId }
        database.withTransaction {
            for (entry in group) {
                val slotTarget = inputs.targets[entry.slot] ?: continue
                val portion = MealPlanner.portionFactor(candidate, slotTarget)
                val planned = meal.copy(
                    date = entry.date,
                    slot = entry.slot,
                    portionFactor = portion,
                    nutrition = candidate.nutrition * portion,
                    cost = candidate.cost * portion,
                )
                mealPlanDao.upsert(planned.toEntity(if (entry == lead) newRejected else entry.rejectedRecipeIds))
            }
        }
        return ReplaceResult.REPLACED
    }

    suspend fun setStatus(date: LocalDate, slot: MealSlot, status: MealStatus) = withContext(io) {
        mealPlanDao.get(date, slot)?.let { mealPlanDao.upsert(it.copy(status = status)) }
    }

    /** Contexte d'un repas pour générer une variante par IA : cible, goûts, type, plats de la semaine. */
    suspend fun variantContext(date: LocalDate, slot: MealSlot): VariantContext? = withContext(io) {
        val inputs = loadInputs() ?: return@withContext null
        val target = inputs.targets[slot] ?: return@withContext null
        val current = mealPlanDao.get(date, slot) ?: return@withContext null
        val weekIds = mealPlanDao.getRange(today(), today().plusDays(DAYS - 1))
            .filter { it.slot.mealType == slot.mealType }
            .map { it.recipeId }.toSet() + current.recipeId
        val names = catalogDao.getRecipes(weekIds).map { it.recipe.name }
        val settings = inputs.settings
        VariantContext(
            target = target,
            prefs = inputs.prefs,
            mealType = slot.mealType,
            weekDishNames = names,
            budget = inputs.request(date, slot, target).budget,
            cookingLevel = settings.cookingLevel,
            mustTravel = settings.lunchLeftovers && slot in setOf(MealSlot.NOON, MealSlot.EVENING),
        )
    }

    /** Remplace un repas par une recette (déjà validée), portion ajustée à la cible du créneau. */
    suspend fun applyRecipe(date: LocalDate, slot: MealSlot, recipe: RecipeEntity, lines: List<RecipeIngredientEntity>) =
        mutex.withLock {
            withContext(io) {
                val target = profileRepository.getSlotTargets()?.get(slot) ?: return@withContext
                val current = mealPlanDao.get(date, slot)
                database.withTransaction {
                    catalogDao.replaceRecipes(listOf(recipe), lines)
                    val portion = MealPlanner.portionFactor(RecipeWithIngredients(recipe, lines).toCandidate(), target)
                    val nutrition = recipe.nutrition() * portion
                    mealPlanDao.upsert(
                        MealPlanEntryEntity(
                            date = date,
                            slot = slot,
                            recipeId = recipe.id,
                            portionFactor = portion,
                            kcal = nutrition.kcal,
                            protein = nutrition.protein,
                            fat = nutrition.fat,
                            carbs = nutrition.carbs,
                            fiber = nutrition.fiber,
                            status = MealStatus.PLANNED,
                            rejectedRecipeIds = (current?.rejectedRecipeIds.orEmpty() + listOfNotNull(current?.recipeId)).distinct(),
                        ),
                    )
                }
            }
        }

    /**
     * Ordre de grandeur du budget minimal (€/semaine) : chaque repas servi par la recette compatible
     * la moins chère pour ses calories. Sert d'indication à côté du champ budget.
     */
    suspend fun cheapestWeekEstimate(): Int? = withContext(io) {
        val inputs = loadInputs() ?: return@withContext null
        val pool = MealPlanner.eligible(inputs.candidates, inputs.prefs)
        val perDay = inputs.targets.entries.sumOf { (slot, target) ->
            pool.filter { it.mealType == slot.mealType && it.nutrition.kcal > 0 }
                .minOfOrNull { it.cost / it.nutrition.kcal }
                ?.let { it * target.kcal } ?: 0.0
        }
        (kotlin.math.ceil(perDay * DAYS / 5.0) * 5).toInt().takeIf { it > 0 }
    }

    // ── Prix ──

    /** Enregistre le prix d'un ingrédient (€/kg ou €/L) ; null revient à l'estimation. */
    suspend fun setPrice(ingredientId: String, eurPerKg: Double?) {
        withContext(io) {
            if (eurPerKg == null) {
                catalogDao.deletePrice(ingredientId)
            } else {
                catalogDao.upsertPrices(listOf(IngredientPriceEntity(ingredientId, eurPerKg)))
            }
        }
        // Le budget en dépend : les repas encore prévus sont recomposés avec les nouveaux prix.
        if (profileRepository.getProfile()?.weeklyBudgetEur != null) regenerateUpcoming()
    }

    // ── Liste de courses ──

    /** Lundi de la semaine en cours : clé des cases cochées. */
    private fun weekKey(): LocalDate = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Courses des 7 prochains jours, quantités du jour où l'on cuisine : un dîner cuisiné en double
     * compte aussi la portion du lendemain midi, une fournée compte toute la semaine (et n'est plus
     * rachetée les jours suivants).
     */
    fun observeShoppingList(): Flow<ShoppingList> {
        val dates = upcomingDates()
        val week = weekKey()
        return combine(
            observeMeals(dates.first(), dates.last()),
            catalogDao.observeIngredients(),
            mealPlanDao.observeChecks(week),
            observePriceBook(),
            profileRepository.observeProfile(),
        ) { meals, ingredients, checks, prices, profile ->
            ShoppingInputs(meals, ingredients, checks.filter { it.checked }.map { it.ingredientId }.toSet(), prices, profile)
        }.map { (meals, ingredients, checked, prices, profile) ->
            val recipes = catalogDao.getRecipes(meals.map { it.recipeId }.distinct()).associateBy { it.recipe.id }
            val lines = meals.filter { it.prep.cookPortions > 0 }.flatMap { meal ->
                recipes[meal.recipeId]?.ingredients.orEmpty().map { line ->
                    ShoppingLine(line.ingredientId, line.grams * meal.prep.cookPortions)
                }
            }
            val grams = lines.groupBy { it.ingredientId }.mapValues { (_, l) -> l.sumOf { it.grams } }
            ShoppingList(
                from = dates.first(),
                to = dates.last(),
                sections = ShoppingListBuilder.build(lines, ingredients.associate { it.id to it.toShoppingIngredient() }),
                checked = checked,
                itemCosts = grams.mapValues { (id, g) -> g / 1000.0 * prices.perKg(id) },
                pricePerKg = grams.keys.associateWith { prices.perKg(it) },
                customPrices = prices.customIds,
                weeklyBudget = profile?.weeklyBudgetEur,
            )
        }
    }

    private data class ShoppingInputs(
        val meals: List<DayMeal>,
        val ingredients: List<IngredientEntity>,
        val checked: Set<String>,
        val prices: PriceBook,
        val profile: UserProfileEntity?,
    )

    suspend fun setChecked(ingredientId: String, checked: Boolean) = withContext(io) {
        val week = weekKey()
        mealPlanDao.upsertCheck(ShoppingCheckEntity(week, ingredientId, checked))
        mealPlanDao.deleteChecksBefore(week)
    }

    // ── Interne ──

    private data class Inputs(
        val candidates: List<MealCandidate>,
        val prefs: FoodPreferences,
        val targets: Map<MealSlot, NutritionTargets>,
        val settings: PlanSettings,
        val weeklyBudget: Int?,
        val dailyKcal: Int,
    ) {
        /** Le budget de la semaine est réparti entre les repas au prorata de leurs calories. */
        fun request(date: LocalDate, slot: MealSlot, target: NutritionTargets) = SlotRequest(
            date = date,
            slot = slot,
            target = target,
            budget = weeklyBudget?.let { it / DAYS.toDouble() * target.kcal / dailyKcal.coerceAtLeast(1) },
        )
    }

    private suspend fun loadInputs(): Inputs? {
        val profile = profileRepository.getProfile() ?: return null
        seedImporter.ready.first { it }
        val prices = PriceBook(catalogDao.getIngredients(), catalogDao.getPrices())
        val candidates = catalogDao.getActiveRecipes().map { it.toCandidate(prices.cost(it.ingredients)) }
        val prefs = restrictionDao.getAll().toFoodPreferences()
        val targets = profile.slotTargets()
        return Inputs(candidates, prefs, targets, profile.planSettings(), profile.weeklyBudgetEur, profile.dailyTargets().kcal)
    }

    private suspend fun existingKeys(): Set<Pair<LocalDate, MealSlot>> {
        val dates = upcomingDates()
        return mealPlanDao.getRange(dates.first(), dates.last()).map { it.date to it.slot }.toSet()
    }

    /** Repas des 14 jours avant et après la fenêtre, pour la non-répétition, les restes et les fournées. */
    private suspend fun loadContext(candidates: List<MealCandidate>): List<MealContext> {
        val proteins = candidates.associate { it.id to it.mainProtein }
        val entries = mealPlanDao.getRange(today().minusDays(CONTEXT_DAYS), today().plusDays(DAYS + CONTEXT_DAYS))
        return entries.mapNotNull { e ->
            proteins[e.recipeId]?.let { MealContext(e.date, e.slot, e.recipeId, it, skipped = e.status == MealStatus.SKIPPED) }
        }
    }

    private suspend fun planRequests(include: (LocalDate, MealSlot) -> Boolean) {
        val inputs = loadInputs() ?: return
        val requests = upcomingDates().flatMap { date ->
            inputs.targets.filterKeys { include(date, it) }.map { (slot, target) -> inputs.request(date, slot, target) }
        }
        if (requests.isEmpty()) return
        val keys = requests.map { it.date to it.slot }.toSet()
        val context = loadContext(inputs.candidates).filter { (it.date to it.slot) !in keys }
        val planned = MealPlanner.plan(requests, inputs.candidates, inputs.prefs, context, inputs.settings)
        database.withTransaction { mealPlanDao.upsertAll(planned.map { it.toEntity(emptyList()) }) }
        SafeLogger.d(TAG) { "planned ${planned.size}/${requests.size} meals" }
    }

    private fun PlannedMeal.toEntity(rejected: List<String>) = MealPlanEntryEntity(
        date = date,
        slot = slot,
        recipeId = recipeId,
        portionFactor = portionFactor,
        kcal = nutrition.kcal,
        protein = nutrition.protein,
        fat = nutrition.fat,
        carbs = nutrition.carbs,
        fiber = nutrition.fiber,
        status = MealStatus.PLANNED,
        rejectedRecipeIds = rejected,
    )

    private fun MealPlanEntryWithRecipe.toDayMeal(unitCost: Double = 0.0, prep: PrepInfo? = null) = DayMeal(
        date = entry.date,
        slot = entry.slot,
        recipeId = recipe.id,
        name = recipe.name,
        mainProtein = recipe.mainProtein,
        mealType = recipe.mealType,
        portionFactor = entry.portionFactor,
        nutrition = Nutrition(entry.kcal, entry.protein, entry.fat, entry.carbs, entry.fiber),
        status = entry.status,
        outOfSeason = !SeasonMask.contains(recipe.seasonMask, entry.date.monthValue),
        aiGenerated = recipe.origin == RecipeOrigin.AI,
        prepMinutes = recipe.prepMinutes,
        difficulty = recipe.difficulty,
        unitCost = unitCost,
        prep = prep ?: PrepInfo(PrepKind.COOK, entry.portionFactor),
    )

    private fun List<DayMeal>.sorted() = sortedWith(compareBy({ it.date }, { it.slot.ordinal }))

    private companion object {
        const val TAG = "MealPlanRepository"
        const val DAYS = 7L
        const val CONTEXT_DAYS = 14L
    }
}

/** Goûts et restrictions : chaque modification recalcule les repas à venir. */
@Singleton
class FoodPreferencesRepository @Inject constructor(
    private val database: AppDatabase,
    private val mealPlanRepository: MealPlanRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val restrictionDao = database.restrictionDao()

    fun observe(): Flow<FoodPreferences> = restrictionDao.observeAll().map { it.toFoodPreferences() }

    suspend fun get(): FoodPreferences = withContext(io) { restrictionDao.getAll().toFoodPreferences() }

    fun observeIngredients(): Flow<List<IngredientEntity>> = database.catalogDao().observeIngredients()

    suspend fun save(prefs: FoodPreferences) {
        withContext(io) {
            database.withTransaction {
                restrictionDao.replaceKind(RestrictionKind.ALLERGEN, prefs.allergens.map { it.name })
                restrictionDao.replaceKind(RestrictionKind.DIET, prefs.diets.map { it.name })
                restrictionDao.replaceKind(RestrictionKind.LIKE, prefs.liked - prefs.disliked)
                restrictionDao.replaceKind(RestrictionKind.DISLIKE, prefs.disliked)
            }
        }
        mealPlanRepository.regenerateUpcoming()
    }
}
