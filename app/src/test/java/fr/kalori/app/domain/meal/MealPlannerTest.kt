package fr.kalori.app.domain.meal

import fr.kalori.app.data.seed.SeedMapper
import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.model.SeasonMask
import fr.kalori.app.domain.model.toMask
import fr.kalori.app.domain.nutrition.MacroCalculator
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealPlannerTest {

    /** Catalogue réel embarqué dans l'app. */
    private val catalog: List<MealCandidate> by lazy {
        val bundle = SeedMapper.parse { File("src/main/assets/seed/$it").readText() }
        val facts = bundle.ingredients.map(SeedMapper::ingredient).associate { it.id to SeedMapper.facts(it) }
        bundle.recipes.map { seed ->
            val (r, lines) = SeedMapper.recipe(seed, facts, Instant.EPOCH)
            MealCandidate(
                id = r.id,
                mainProtein = r.mainProtein,
                nutrition = Nutrition(r.kcal, r.protein, r.fat, r.carbs, r.fiber),
                allergensMask = r.allergensMask,
                dietMask = r.dietMask,
                seasonMask = r.seasonMask,
                ingredientIds = lines.map { it.ingredientId }.toSet(),
            )
        }
    }
    private val byId by lazy { catalog.associateBy { it.id } }
    private val mealTarget = MacroCalculator.targets(2200, 75.0, GoalType.MAINTAIN).forMeal(0.4)
    private val start = LocalDate.of(2026, 10, 20)
    private val week = (0L..6L).map { start.plusDays(it) }

    @Test
    fun `declared allergens never pass, over 1000 random profiles`() {
        val random = Random(42)
        repeat(1000) {
            val allergens = Allergen.entries.filter { random.nextInt(4) == 0 }.toSet()
            val diets = Diet.entries.filter { random.nextInt(6) == 0 }.toSet()
            val prefs = FoodPreferences(allergens, diets)
            val day = start.plusDays(random.nextLong(0, 365))
            val plan = MealPlanner.plan(listOf(day), catalog, prefs, mealTarget, emptyList())
            val forbidden = allergens.toMask()
            plan.forEach { meal ->
                val recipe = byId.getValue(meal.recipeId)
                assertEquals(0, recipe.allergensMask and forbidden, "${recipe.id} contient un allergène de $allergens")
                assertTrue(DietRules.isCompatible(diets, recipe.dietMask), "${recipe.id} hors régime $diets")
            }
        }
    }

    @Test
    fun `disliked ingredients are excluded`() {
        val prefs = FoodPreferences(disliked = setOf("poulet_filet", "tomates_pelees", "carotte"))
        val plan = MealPlanner.plan(week, catalog, prefs, mealTarget, emptyList())
        assertEquals(7, plan.size)
        plan.forEach { assertTrue(byId.getValue(it.recipeId).ingredientIds.none { id -> id in prefs.disliked }) }
    }

    @Test
    fun `a week has no repeated dish and stays in season`() {
        for (month in 1..12) {
            val days = (0L..6L).map { LocalDate.of(2026, month, 1).plusDays(it) }
            val plan = MealPlanner.plan(days, catalog, FoodPreferences(), mealTarget, emptyList())
            assertEquals(7, plan.map { it.recipeId }.toSet().size, "répétition en $month")
            plan.forEach {
                assertTrue(SeasonMask.contains(byId.getValue(it.recipeId).seasonMask, month), "${it.recipeId} hors saison en $month")
                assertTrue(!it.outOfSeason)
            }
        }
    }

    @Test
    fun `no dish repeats within 14 days of history`() {
        val history = (1L..14L).map { d ->
            val meal = catalog[d.toInt()]
            MealContext(start.minusDays(d), meal.id, meal.mainProtein)
        }
        val plan = MealPlanner.plan(week, catalog, FoodPreferences(), mealTarget, history)
        // Règle : un même plat n'est jamais servi deux fois à moins de 14 jours d'écart.
        // (Un plat de J−14 redevient donc possible dès J.)
        plan.forEach { meal ->
            history.filter { it.recipeId == meal.recipeId }.forEach { past ->
                val gap = java.time.temporal.ChronoUnit.DAYS.between(past.date, meal.date)
                assertTrue(gap >= 14, "${meal.recipeId} reproposé ${gap} jours après")
            }
        }
    }

    @Test
    fun `strict vegan profile still gets a full week every month`() {
        val prefs = FoodPreferences(allergens = setOf(Allergen.GLUTEN), diets = setOf(Diet.VEGAN))
        for (month in 1..12) {
            val days = (0L..6L).map { LocalDate.of(2026, month, 3).plusDays(it) }
            assertEquals(7, MealPlanner.plan(days, catalog, prefs, mealTarget, emptyList()).size, "mois $month")
        }
    }

    @Test
    fun `portions are scaled toward the target within bounds`() {
        val plan = MealPlanner.plan(week, catalog, FoodPreferences(), mealTarget, emptyList())
        plan.forEach {
            assertTrue(it.portionFactor in MealPlanner.MIN_PORTION..MealPlanner.MAX_PORTION)
            assertTrue(kotlin.math.abs(it.nutrition.kcal - mealTarget.kcal) / mealTarget.kcal < 0.05, "${it.recipeId}")
        }
    }

    @Test
    fun `planning is deterministic for the same inputs`() {
        val a = MealPlanner.plan(week, catalog, FoodPreferences(), mealTarget, emptyList())
        val b = MealPlanner.plan(week, catalog, FoodPreferences(), mealTarget, emptyList())
        assertEquals(a, b)
    }

    @Test
    fun `replace proposes a different compatible dish and honours rejections`() {
        val prefs = FoodPreferences(diets = setOf(Diet.VEGETARIAN))
        val first = MealPlanner.plan(listOf(start), catalog, prefs, mealTarget, emptyList()).single()
        val second = assertNotNull(
            MealPlanner.replace(start, catalog, prefs, mealTarget, emptyList(), excluded = setOf(first.recipeId)),
        )
        assertNotEquals(first.recipeId, second.recipeId)
        val third = assertNotNull(
            MealPlanner.replace(start, catalog, prefs, mealTarget, emptyList(), setOf(first.recipeId, second.recipeId)),
        )
        assertTrue(third.recipeId !in setOf(first.recipeId, second.recipeId))
        assertTrue(DietRules.isCompatible(prefs.diets, byId.getValue(third.recipeId).dietMask))
    }

    @Test
    fun `small pool relaxes repetition and season but never the hard constraints`() {
        val summerOnly = MealCandidate(
            id = "ete", mainProtein = MainProtein.PLANT, nutrition = Nutrition(600.0, 25.0, 20.0, 70.0, 10.0),
            allergensMask = 0, dietMask = 0, seasonMask = SeasonMask.of(6..8), ingredientIds = setOf("tomate"),
        )
        val withGluten = summerOnly.copy(id = "gluten", allergensMask = listOf(Allergen.GLUTEN).toMask(), seasonMask = SeasonMask.ALL_YEAR)
        val prefs = FoodPreferences(allergens = setOf(Allergen.GLUTEN))
        val january = (0L..2L).map { LocalDate.of(2026, 1, 5).plusDays(it) }

        val plan = MealPlanner.plan(january, listOf(summerOnly, withGluten), prefs, mealTarget, emptyList())
        assertEquals(listOf("ete", "ete", "ete"), plan.map { it.recipeId })
        assertTrue(plan.all { it.outOfSeason })
        assertNull(MealPlanner.replace(january[0], listOf(summerOnly, withGluten), prefs, mealTarget, emptyList(), setOf("ete")))
    }

    @Test
    fun `consecutive days avoid the same main protein when possible`() {
        val plan = MealPlanner.plan(week, catalog, FoodPreferences(), mealTarget, emptyList())
        val proteins = plan.map { byId.getValue(it.recipeId).mainProtein }
        val sameAsPrevious = proteins.zipWithNext().count { (a, b) -> a == b }
        assertTrue(sameAsPrevious <= 1, "protéines : $proteins")
    }
}
