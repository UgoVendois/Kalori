package fr.kalori.app.data.meal

import androidx.room.withTransaction
import fr.kalori.app.core.database.AppDatabase
import fr.kalori.app.core.database.entity.IngredientEntity
import fr.kalori.app.core.database.entity.MealPlanEntryEntity
import fr.kalori.app.core.database.entity.MealPlanEntryWithRecipe
import fr.kalori.app.core.database.entity.RecipeEntity
import fr.kalori.app.core.database.entity.RecipeIngredientEntity
import fr.kalori.app.core.database.entity.RecipeWithIngredients
import fr.kalori.app.core.database.entity.ShoppingCheckEntity
import fr.kalori.app.core.di.IoDispatcher
import fr.kalori.app.core.logging.SafeLogger
import fr.kalori.app.data.profile.ProfileRepository
import fr.kalori.app.data.seed.SeedImporter
import fr.kalori.app.domain.meal.FoodPreferences
import fr.kalori.app.domain.meal.MealCandidate
import fr.kalori.app.domain.meal.MealContext
import fr.kalori.app.domain.meal.MealPlanner
import fr.kalori.app.domain.meal.PlannedMeal
import fr.kalori.app.domain.meal.ShoppingLine
import fr.kalori.app.domain.meal.ShoppingListBuilder
import fr.kalori.app.domain.meal.ShoppingSection
import fr.kalori.app.domain.meal.SlotRequest
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
)

/** Détail d'un repas : ingrédients à la portion ajustée, allergènes, étapes. */
data class MealDetail(
    val meal: DayMeal,
    val ingredients: List<Pair<IngredientEntity, Double>>,
    val allergensMask: Int,
    val steps: List<String>,
)

data class ShoppingList(
    val from: LocalDate,
    val to: LocalDate,
    val sections: List<ShoppingSection>,
    val checked: Set<String>,
)

enum class ReplaceResult { REPLACED, NO_ALTERNATIVE, NOT_PLANNED }

data class VariantContext(
    val target: NutritionTargets,
    val prefs: FoodPreferences,
    val mealType: MealType,
    val weekDishNames: List<String>,
)

/**
 * Plan de repas : les 7 prochains jours (aujourd'hui inclus), un repas par créneau actif
 * (matin, midi, soir, collation), l'historique et la liste de courses.
 * Toute la logique de choix est dans [MealPlanner] (domaine pur, testé).
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

    fun observeUpcoming(): Flow<List<DayMeal>> {
        val dates = upcomingDates()
        return mealPlanDao.observeRangeWithRecipe(dates.first(), dates.last()).map { list -> list.map { it.toDayMeal() }.sorted() }
    }

    fun observeHistory(): Flow<List<DayMeal>> =
        mealPlanDao.observeHistoryWithRecipe(today()).map { list -> list.map { it.toDayMeal() } }

    fun observeDetail(date: LocalDate, slot: MealSlot): Flow<MealDetail?> =
        mealPlanDao.observeEntryWithRecipe(date, slot).flatMapLatest { entry ->
            if (entry == null) return@flatMapLatest flowOf(null)
            combine(catalogDao.observeRecipe(entry.recipe.id), catalogDao.observeIngredients()) { recipe, ingredients ->
                recipe ?: return@combine null
                val byId = ingredients.associateBy { it.id }
                MealDetail(
                    meal = entry.toDayMeal(),
                    ingredients = recipe.ingredients.mapNotNull { line ->
                        byId[line.ingredientId]?.let { it to line.grams * entry.entry.portionFactor }
                    },
                    allergensMask = recipe.recipe.allergensMask,
                    steps = recipe.recipe.steps,
                )
            }
        }

    /** Complète les repas manquants des jours à venir et retire ceux des créneaux désactivés. */
    suspend fun ensureUpcomingPlanned() = mutex.withLock {
        withContext(io) {
            val targets = profileRepository.getSlotTargets() ?: return@withContext
            mealPlanDao.deletePlannedSlotsNotIn(today(), targets.keys)
            val existing = existingKeys()
            planRequests(requestsFor(targets) { date, slot -> (date to slot) !in existing })
        }
    }

    /**
     * Recompose les repas à venir encore « prévus » (après un changement de goûts, de restrictions,
     * d'objectif ou de repas de la journée). Les repas déjà marqués mangés ou sautés sont conservés.
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
            planRequests(requestsFor(targets) { date, slot -> (date to slot) !in locked })
        }
    }

    suspend fun replace(date: LocalDate, slot: MealSlot): ReplaceResult = mutex.withLock {
        withContext(io) {
            val current = mealPlanDao.get(date, slot) ?: return@withContext ReplaceResult.NOT_PLANNED
            val inputs = loadInputs() ?: return@withContext ReplaceResult.NOT_PLANNED
            val target = inputs.targets[slot] ?: return@withContext ReplaceResult.NOT_PLANNED
            val request = SlotRequest(date, slot, target)
            val context = loadContext(inputs.candidates)
            val rejected = current.rejectedRecipeIds.toSet() + current.recipeId
            val meal = MealPlanner.replace(request, inputs.candidates, inputs.prefs, context, rejected)
                // Toutes les alternatives ont déjà été refusées : on repart de zéro, sauf le plat actuel.
                ?: MealPlanner.replace(request, inputs.candidates, inputs.prefs, context, setOf(current.recipeId))
                ?: return@withContext ReplaceResult.NO_ALTERNATIVE
            val newRejected = if (meal.recipeId in rejected) listOf(current.recipeId) else rejected.toList()
            mealPlanDao.upsert(meal.toEntity(newRejected))
            ReplaceResult.REPLACED
        }
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
        VariantContext(target, inputs.prefs, slot.mealType, names)
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

    // ── Liste de courses ──

    /** Lundi de la semaine en cours : clé des cases cochées. */
    private fun weekKey(): LocalDate = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun observeShoppingList(): Flow<ShoppingList> {
        val dates = upcomingDates()
        val week = weekKey()
        return combine(
            mealPlanDao.observeRange(dates.first(), dates.last()),
            catalogDao.observeIngredients(),
            mealPlanDao.observeChecks(week),
        ) { entries, ingredients, checks -> Triple(entries, ingredients, checks) }
            .map { (entries, ingredients, checks) ->
                val toBuy = entries.filter { it.status != MealStatus.SKIPPED }
                val recipes = catalogDao.getRecipes(toBuy.map { it.recipeId }.distinct()).associateBy { it.recipe.id }
                val lines = toBuy.flatMap { entry ->
                    recipes[entry.recipeId]?.ingredients.orEmpty().map { line ->
                        ShoppingLine(line.ingredientId, line.grams * entry.portionFactor)
                    }
                }
                ShoppingList(
                    from = dates.first(),
                    to = dates.last(),
                    sections = ShoppingListBuilder.build(lines, ingredients.associate { it.id to it.toShoppingIngredient() }),
                    checked = checks.filter { it.checked }.map { it.ingredientId }.toSet(),
                )
            }
    }

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
    )

    private suspend fun loadInputs(): Inputs? {
        val targets = profileRepository.getSlotTargets() ?: return null
        seedImporter.ready.first { it }
        val candidates = catalogDao.getActiveRecipes().map { it.toCandidate() }
        val prefs = restrictionDao.getAll().toFoodPreferences()
        return Inputs(candidates, prefs, targets)
    }

    private suspend fun existingKeys(): Set<Pair<LocalDate, MealSlot>> {
        val dates = upcomingDates()
        return mealPlanDao.getRange(dates.first(), dates.last()).map { it.date to it.slot }.toSet()
    }

    private fun requestsFor(
        targets: Map<MealSlot, NutritionTargets>,
        include: (LocalDate, MealSlot) -> Boolean,
    ): List<SlotRequest> = upcomingDates().flatMap { date ->
        targets.filterKeys { include(date, it) }.map { (slot, target) -> SlotRequest(date, slot, target) }
    }

    /** Repas des 14 jours avant et après la fenêtre, pour la règle de non-répétition. */
    private suspend fun loadContext(candidates: List<MealCandidate>): List<MealContext> {
        val proteins = candidates.associate { it.id to it.mainProtein }
        val entries = mealPlanDao.getRange(today().minusDays(CONTEXT_DAYS), today().plusDays(DAYS + CONTEXT_DAYS))
        return entries.mapNotNull { e -> proteins[e.recipeId]?.let { MealContext(e.date, e.slot, e.recipeId, it) } }
    }

    private suspend fun planRequests(requests: List<SlotRequest>) {
        if (requests.isEmpty()) return
        val inputs = loadInputs() ?: return
        val keys = requests.map { it.date to it.slot }.toSet()
        val context = loadContext(inputs.candidates).filter { (it.date to it.slot) !in keys }
        val planned = MealPlanner.plan(requests, inputs.candidates, inputs.prefs, context)
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

    private fun MealPlanEntryWithRecipe.toDayMeal() = DayMeal(
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
