package fr.kalori.app.screenshots

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.core.ui.theme.KaloriTheme
import fr.kalori.app.data.meal.DayMeal
import fr.kalori.app.data.meal.MealDetail
import fr.kalori.app.data.profile.Organization
import fr.kalori.app.domain.meal.PrepKind
import fr.kalori.app.domain.meal.Budget
import fr.kalori.app.domain.meal.PrepInfo
import fr.kalori.app.domain.model.BudgetPeriod
import fr.kalori.app.domain.model.PriceTier
import fr.kalori.app.data.meal.PriceBook
import fr.kalori.app.data.profile.slotTargets
import fr.kalori.app.data.seed.SeedMapper
import fr.kalori.app.domain.meal.FoodPreferences
import fr.kalori.app.domain.model.ActivityLevel
import fr.kalori.app.domain.model.Aisle
import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.MealStatus
import fr.kalori.app.domain.model.MealType
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.model.Sex
import fr.kalori.app.domain.model.SnackMode
import fr.kalori.app.domain.model.UnitSystem
import fr.kalori.app.feature.meals.FoodPreferencesActions
import fr.kalori.app.feature.meals.FoodPreferencesContent
import fr.kalori.app.feature.meals.FoodPreferencesUiState
import fr.kalori.app.feature.meals.MealDetailContent
import fr.kalori.app.feature.meals.MealsContent
import fr.kalori.app.feature.meals.MealsUiState
import fr.kalori.app.feature.meals.SaveIndicator
import fr.kalori.app.feature.meals.SaveState
import fr.kalori.app.feature.meals.ScheduleState
import fr.kalori.app.feature.settings.SettingsActions
import fr.kalori.app.feature.settings.SettingsContent
import fr.kalori.app.feature.settings.SettingsUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Rendus des écrans (Roborazzi + Robolectric, sans émulateur), en clair et en sombre.
 * Génération : ./gradlew recordRoborazziDebug --tests '*ScreenshotTest*'
 * Images : app/build/outputs/roborazzi/
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h915dp-xxhdpi")
class ScreenshotTest {

    private val bundle = SeedMapper.parse { File("src/main/assets/seed/$it").readText() }
    private val ingredients = bundle.ingredients.map(SeedMapper::ingredient)
    private val facts = ingredients.associate { it.id to SeedMapper.facts(it) }
    private val recipes = bundle.recipes.associate { seed -> seed.id to SeedMapper.recipe(seed, facts, Instant.EPOCH) }
    private val prices = PriceBook(ingredients, emptyList(), PriceTier.DISCOUNT)

    private val today = LocalDate.of(2026, 10, 21)
    private val profile = UserProfileEntity(
        sex = Sex.MALE, birthYear = 1995, birthMonth = 3, heightCm = 180.0, startWeightKg = 70.0, targetWeightKg = 76.0,
        activityLevel = ActivityLevel.ACTIVE, goalType = GoalType.GAIN, paceKgPerWeek = 0.25, kcalTarget = 2720,
        proteinG = 112, fatG = 106, carbsG = 324, fiberG = 30, unitSystem = UnitSystem.METRIC,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )
    private val targets = profile.slotTargets()

    private fun meal(
        date: LocalDate,
        slot: MealSlot,
        recipeId: String,
        status: MealStatus = MealStatus.PLANNED,
        prep: ((Double) -> PrepInfo)? = null,
    ): DayMeal {
        val (recipe, lines) = recipes.getValue(recipeId)
        val portion = (targets.getValue(slot).kcal / recipe.kcal).coerceIn(0.5, 2.0)
        return DayMeal(
            date = date, slot = slot, recipeId = recipe.id, name = recipe.name, mainProtein = recipe.mainProtein,
            mealType = recipe.mealType, portionFactor = portion,
            nutrition = Nutrition(recipe.kcal, recipe.protein, recipe.fat, recipe.carbs, recipe.fiber) * portion,
            status = status, outOfSeason = false, aiGenerated = false,
            prepMinutes = recipe.prepMinutes, difficulty = recipe.difficulty, unitCost = prices.cost(lines),
            prep = prep?.invoke(portion) ?: PrepInfo(PrepKind.COOK, portion),
        )
    }

    private val dayMeals = listOf(
        meal(today, MealSlot.MORNING, "porridge_pomme_cannelle", MealStatus.EATEN),
        meal(today, MealSlot.NOON, "poulet_curry_coco", MealStatus.EATEN),
        meal(today, MealSlot.SNACK, "collation_banane_cacahuete"),
        meal(today, MealSlot.EVENING, "risotto_potiron"),
    )

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            KaloriTheme(darkTheme = dark) { Surface { content() } }
        }
    }

    private val mealsState = MealsUiState(
        loading = false,
        hasProfile = true,
        today = today,
        dates = (0L..6L).map { today.plusDays(it) },
        selectedDate = today,
        meals = mapOf(today to dayMeals),
        slotTargets = targets,
        dailyKcal = profile.kcalTarget,
    )

    @Test fun meals_light() = shoot("meals_light") { MealsScreenPreview(mealsState) }

    @Test fun meals_dark() = shoot("meals_dark", dark = true) { MealsScreenPreview(mealsState) }

    /** Sans petit-déjeuner : l'objectif se répartit entre midi, soir et une collation. */
    @Test fun meals_no_breakfast() = shoot("meals_no_breakfast") {
        val noBreakfast = profile.copy(enabledSlotsMask = 0b110).slotTargets()
        val meals = listOf(
            meal(today, MealSlot.NOON, "poulet_curry_coco"),
            // Comme le planificateur : la collation la plus copieuse, la cible étant élevée sans petit-déjeuner.
            meal(today, MealSlot.SNACK, recipes.values.map { it.first }.filter { it.mealType == MealType.SNACK }.maxBy { it.kcal }.id),
            meal(today, MealSlot.EVENING, "risotto_potiron"),
        ).map { m ->
            val (recipe, _) = recipes.getValue(m.recipeId)
            val portion = (noBreakfast.getValue(m.slot).kcal / recipe.kcal).coerceIn(0.5, 2.0)
            m.copy(portionFactor = portion, nutrition = Nutrition(recipe.kcal, recipe.protein, recipe.fat, recipe.carbs, recipe.fiber) * portion)
        }
        MealsScreenPreview(mealsState.copy(meals = mapOf(today to meals), slotTargets = noBreakfast))
    }

    /** Restes du soir pour le midi, fournée de cookies et budget de la semaine. */
    @Test fun meals_organization() = shoot("meals_organization") {
        val dates = (0L..6L).map { today.plusDays(it) }
        val day = { date: LocalDate ->
            listOf(
                meal(date, MealSlot.MORNING, "granola_maison_yaourt"),
                meal(date, MealSlot.NOON, "chili_sin_carne", prep = { PrepInfo(PrepKind.LEFTOVERS, 0.0) }),
                meal(date, MealSlot.SNACK, "cookies_avoine_chocolat", prep = { PrepInfo(PrepKind.BATCH_DAY, it * 5, batchDays = 5) }),
                meal(date, MealSlot.EVENING, "lentilles_lardons", prep = { PrepInfo(PrepKind.COOK_WITH_LEFTOVERS, it * 2.1) }),
            )
        }
        MealsScreenPreview(mealsState.copy(meals = dates.associateWith(day), budget = Budget(100, BudgetPeriod.MONTH)))
    }

    /** Dîner cuisiné en double pour le lendemain midi : quantités « à cuisiner » affichées d'emblée. */
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    @Test fun meal_detail() = shoot("meal_detail") {
        val (recipe, lines) = recipes.getValue("lentilles_lardons")
        val byId = ingredients.associateBy { it.id }
        val dinner = meal(today, MealSlot.EVENING, "lentilles_lardons", prep = { PrepInfo(PrepKind.COOK_WITH_LEFTOVERS, it * 2.1) })
        val detail = MealDetail(
            meal = dinner,
            ingredients = lines.map { byId.getValue(it.ingredientId) to it.grams * dinner.portionFactor },
            allergensMask = recipe.allergensMask,
            steps = recipe.steps,
            cookIngredients = lines.map { byId.getValue(it.ingredientId) to it.grams * dinner.prep.cookPortions },
            keepDays = recipe.keepDays,
            freezable = recipe.freezable,
        )
        KaloriScaffold(title = "Soir · Aujourd'hui", onBack = {}) { padding ->
            MealDetailContent(detail, editable = true, canToggleEaten = true, onReplace = {}, onToggleEaten = {}, modifier = Modifier.padding(padding))
        }
    }

    @Config(qualifiers = "w411dp-h1900dp-xxhdpi")
    @Test fun food_preferences() = shoot("food_preferences") { PreferencesPreview() }

    @Test fun food_preferences_dark() = shoot("food_preferences_dark", dark = true) { PreferencesPreview() }

    @Composable
    private fun PreferencesPreview() {
        val state = FoodPreferencesUiState(
            loaded = true,
            prefs = FoodPreferences(
                allergens = setOf(Allergen.PEANUTS, Allergen.SESAME),
                diets = setOf(Diet.NO_PORK),
                liked = setOf("saumon", "patate_douce", "avocat"),
                disliked = setOf("chou_bruxelles", "endive"),
            ),
            ingredients = ingredients,
            expandedAisles = setOf(Aisle.FRUITS_VEGETABLES),
            schedule = ScheduleState(
                available = true,
                mains = setOf(MealSlot.NOON, MealSlot.EVENING),
                snackMode = SnackMode.AUTO,
                organization = Organization(budgetEur = 100, budgetPeriod = BudgetPeriod.MONTH, priceTier = PriceTier.DISCOUNT, lunchLeftovers = true, weeklySnack = true),
                cheapestWeek = 21.0,
            ),
            saveState = SaveState.SAVED,
        )
        KaloriScaffold(title = "Mes goûts et restrictions", onBack = {}, actions = { SaveIndicator(state.saveState) }) { padding ->
            FoodPreferencesContent(
                state,
                FoodPreferencesActions({}, {}, {}, {}, {}, {}, {}, {}),
                Modifier.padding(padding),
            )
        }
    }

    @Test fun settings() = shoot("settings") {
        KaloriScaffold(title = "Paramètres") { padding ->
            SettingsContent(
                SettingsUiState(profile = profile, lockEnabled = true, lockAvailable = true, aiEnabled = false, aiKeySaved = false),
                SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun MealsScreenPreview(state: MealsUiState) {
    KaloriScaffold(title = "Repas") { padding ->
        MealsContent(state, {}, {}, {}, {}, {}, modifier = Modifier.padding(padding))
    }
}
