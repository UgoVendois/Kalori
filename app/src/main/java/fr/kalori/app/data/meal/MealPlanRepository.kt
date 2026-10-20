package fr.kalori.app.data.meal

import androidx.room.withTransaction
import fr.kalori.app.core.database.AppDatabase
import fr.kalori.app.core.database.entity.IngredientEntity
import fr.kalori.app.core.database.entity.MealPlanEntryEntity
import fr.kalori.app.core.database.entity.MealPlanEntryWithRecipe
import fr.kalori.app.core.database.entity.RecipeIngredientEntity
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
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.MealStatus
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.model.SeasonMask
import fr.kalori.app.domain.model.RecipeOrigin
import fr.kalori.app.domain.model.RestrictionKind
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

/** Plat d'un jour, prêt à afficher. */
data class PlannedDay(
    val date: LocalDate,
    val recipeId: String,
    val name: String,
    val mainProtein: MainProtein,
    val portionFactor: Double,
    val nutrition: Nutrition,
    val status: MealStatus,
    val outOfSeason: Boolean,
    val aiGenerated: Boolean,
)

/** Détail d'un plat : ingrédients à la portion ajustée, allergènes, étapes. */
data class MealDetail(
    val day: PlannedDay,
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

/**
 * Plan de repas : les 7 prochains jours (aujourd'hui inclus), l'historique et la liste de courses.
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

    private fun upcomingDates(): List<LocalDate> = (0L until DAYS).map { today().plusDays(it) }

    fun observeUpcoming(): Flow<List<PlannedDay>> {
        val dates = upcomingDates()
        return mealPlanDao.observeRangeWithRecipe(dates.first(), dates.last()).map { list -> list.map { it.toPlannedDay() } }
    }

    fun observeHistory(): Flow<List<PlannedDay>> =
        mealPlanDao.observeHistoryWithRecipe(today()).map { list -> list.map { it.toPlannedDay() } }

    fun observeDetail(date: LocalDate): Flow<MealDetail?> =
        mealPlanDao.observeEntryWithRecipe(date).flatMapLatest { entry ->
            if (entry == null) return@flatMapLatest flowOf(null)
            combine(catalogDao.observeRecipe(entry.recipe.id), catalogDao.observeIngredients()) { recipe, ingredients ->
                recipe ?: return@combine null
                val byId = ingredients.associateBy { it.id }
                MealDetail(
                    day = entry.toPlannedDay(),
                    ingredients = recipe.ingredients.mapNotNull { line ->
                        byId[line.ingredientId]?.let { it to line.grams * entry.entry.portionFactor }
                    },
                    allergensMask = recipe.recipe.allergensMask,
                    steps = recipe.recipe.steps,
                )
            }
        }

    /** Complète les jours à venir qui n'ont pas encore de plat. Sans profil, ne fait rien. */
    suspend fun ensureUpcomingPlanned() = mutex.withLock {
        withContext(io) {
            val missing = upcomingDates().filter { mealPlanDao.get(it) == null }
            if (missing.isNotEmpty()) planDates(missing)
        }
    }

    /**
     * Recalcule les plats à venir encore « prévus » (après un changement de goûts, de restrictions
     * ou d'objectif). Les plats déjà marqués mangés ou sautés sont conservés.
     */
    suspend fun regenerateUpcoming() = mutex.withLock {
        withContext(io) {
            val dates = upcomingDates()
            val existing = mealPlanDao.getRange(dates.first(), dates.last()).associateBy { it.date }
            planDates(dates.filter { existing[it]?.status != MealStatus.EATEN && existing[it]?.status != MealStatus.SKIPPED })
        }
    }

    suspend fun replace(date: LocalDate): ReplaceResult = mutex.withLock {
        withContext(io) {
            val current = mealPlanDao.get(date) ?: return@withContext ReplaceResult.NOT_PLANNED
            val inputs = loadInputs() ?: return@withContext ReplaceResult.NOT_PLANNED
            val context = loadContext(inputs.candidates)
            val rejected = current.rejectedRecipeIds.toSet() + current.recipeId
            val meal = MealPlanner.replace(date, inputs.candidates, inputs.prefs, inputs.target, context, rejected)
                // Toutes les alternatives ont déjà été refusées : on repart de zéro, sauf le plat actuel.
                ?: MealPlanner.replace(date, inputs.candidates, inputs.prefs, inputs.target, context, setOf(current.recipeId))
                ?: return@withContext ReplaceResult.NO_ALTERNATIVE
            val newRejected = if (meal.recipeId in rejected) listOf(current.recipeId) else rejected.toList()
            mealPlanDao.upsert(meal.toEntity(newRejected))
            ReplaceResult.REPLACED
        }
    }

    suspend fun setStatus(date: LocalDate, status: MealStatus) = withContext(io) {
        mealPlanDao.get(date)?.let { mealPlanDao.upsert(it.copy(status = status)) }
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
                    recipes[entry.recipeId]?.ingredients.orEmpty().map { line: RecipeIngredientEntity ->
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
        val target: NutritionTargets,
    )

    private suspend fun loadInputs(): Inputs? {
        val target = profileRepository.getMealTarget() ?: return null
        seedImporter.ready.first { it }
        val candidates = catalogDao.getActiveRecipes().map { it.toCandidate() }
        val prefs = restrictionDao.getAll().toFoodPreferences()
        return Inputs(candidates, prefs, target)
    }

    /** Repas des 14 jours avant et après la fenêtre, pour la règle de non-répétition. */
    private suspend fun loadContext(candidates: List<MealCandidate>): List<MealContext> {
        val proteins = candidates.associate { it.id to it.mainProtein }
        val entries = mealPlanDao.getRange(today().minusDays(CONTEXT_DAYS), today().plusDays(DAYS + CONTEXT_DAYS))
        return entries.mapNotNull { e -> proteins[e.recipeId]?.let { MealContext(e.date, e.recipeId, it) } }
    }

    private suspend fun planDates(dates: List<LocalDate>) {
        if (dates.isEmpty()) return
        val inputs = loadInputs() ?: return
        val context = loadContext(inputs.candidates).filter { it.date !in dates }
        val planned = MealPlanner.plan(dates, inputs.candidates, inputs.prefs, inputs.target, context)
        database.withTransaction { mealPlanDao.upsertAll(planned.map { it.toEntity(emptyList()) }) }
        SafeLogger.d(TAG) { "planned ${planned.size}/${dates.size} days" }
    }

    private fun PlannedMeal.toEntity(rejected: List<String>) = MealPlanEntryEntity(
        date = date,
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

    private fun MealPlanEntryWithRecipe.toPlannedDay() = PlannedDay(
        date = entry.date,
        recipeId = recipe.id,
        name = recipe.name,
        mainProtein = recipe.mainProtein,
        portionFactor = entry.portionFactor,
        nutrition = Nutrition(entry.kcal, entry.protein, entry.fat, entry.carbs, entry.fiber),
        status = entry.status,
        outOfSeason = !SeasonMask.contains(recipe.seasonMask, entry.date.monthValue),
        aiGenerated = recipe.origin == RecipeOrigin.AI,
    )

    private companion object {
        const val TAG = "MealPlanRepository"
        const val DAYS = 7L
        const val CONTEXT_DAYS = 14L
    }
}

/** Goûts et restrictions : chaque modification recalcule les plats à venir. */
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
