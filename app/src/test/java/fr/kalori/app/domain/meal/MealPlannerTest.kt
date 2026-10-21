package fr.kalori.app.domain.meal

import fr.kalori.app.data.seed.SeedMapper
import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.MealType
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.model.SeasonMask
import fr.kalori.app.domain.model.SnackMode
import fr.kalori.app.domain.model.toMask
import fr.kalori.app.domain.nutrition.MacroCalculator
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
                mealType = r.mealType,
                nutrition = Nutrition(r.kcal, r.protein, r.fat, r.carbs, r.fiber),
                allergensMask = r.allergensMask,
                dietMask = r.dietMask,
                seasonMask = r.seasonMask,
                ingredientIds = lines.map { it.ingredientId }.toSet(),
            )
        }
    }
    private val byId by lazy { catalog.associateBy { it.id } }
    private val daily = MacroCalculator.targets(2720, 80.0, GoalType.GAIN)
    private val slotTargets = MealSchedule.targets(daily, MealSlot.mains.toSet(), SnackMode.AUTO)
    private val start = LocalDate.of(2026, 10, 20)
    private val week = (0L..6L).map { start.plusDays(it) }

    private fun requests(days: List<LocalDate>, slots: Collection<MealSlot> = slotTargets.keys) =
        days.flatMap { d -> slots.map { SlotRequest(d, it, slotTargets.getValue(it)) } }

    private fun noon(days: List<LocalDate>) = requests(days, listOf(MealSlot.NOON))

    @Test
    fun `declared allergens never pass, over 1000 random profiles`() {
        val random = Random(42)
        repeat(1000) {
            val allergens = Allergen.entries.filter { random.nextInt(4) == 0 }.toSet()
            val diets = Diet.entries.filter { random.nextInt(6) == 0 }.toSet()
            val prefs = FoodPreferences(allergens, diets)
            val day = start.plusDays(random.nextLong(0, 365))
            val plan = MealPlanner.plan(requests(listOf(day)), catalog, prefs, emptyList())
            val forbidden = allergens.toMask()
            plan.forEach { meal ->
                val recipe = byId.getValue(meal.recipeId)
                assertEquals(0, recipe.allergensMask and forbidden, "${recipe.id} contient un allergène de $allergens")
                assertTrue(DietRules.isCompatible(diets, recipe.dietMask), "${recipe.id} hors régime $diets")
            }
        }
    }

    @Test
    fun `each slot gets a recipe of the right type`() {
        val plan = MealPlanner.plan(requests(week), catalog, FoodPreferences(), emptyList())
        assertEquals(7 * 4, plan.size)
        plan.forEach { assertEquals(it.slot.mealType, byId.getValue(it.recipeId).mealType, "${it.slot} → ${it.recipeId}") }
    }

    @Test
    fun `lunch and dinner differ and rarely share the main protein`() {
        val plan = MealPlanner.plan(requests(week), catalog, FoodPreferences(), emptyList())
        var sameProtein = 0
        week.forEach { day ->
            val noon = plan.single { it.date == day && it.slot == MealSlot.NOON }
            val evening = plan.single { it.date == day && it.slot == MealSlot.EVENING }
            assertNotEquals(noon.recipeId, evening.recipeId)
            if (byId.getValue(noon.recipeId).mainProtein == byId.getValue(evening.recipeId).mainProtein) sameProtein++
        }
        assertTrue(sameProtein <= 1, "même protéine midi et soir $sameProtein fois")
    }

    @Test
    fun `the day covers the daily target`() {
        val plan = MealPlanner.plan(requests(week), catalog, FoodPreferences(), emptyList())
        week.forEach { day ->
            val total = plan.filter { it.date == day }.sumOf { it.nutrition.kcal }
            assertTrue(kotlin.math.abs(total - daily.kcal) / daily.kcal < 0.08, "$day : $total kcal pour ${daily.kcal}")
        }
    }

    @Test
    fun `without breakfast the day still covers the target with lunch, dinner and a snack`() {
        val targets = MealSchedule.targets(daily, setOf(MealSlot.NOON, MealSlot.EVENING), SnackMode.AUTO)
        assertEquals(setOf(MealSlot.NOON, MealSlot.EVENING, MealSlot.SNACK), targets.keys)
        val plan = MealPlanner.plan(
            week.flatMap { d -> targets.map { (slot, target) -> SlotRequest(d, slot, target) } },
            catalog, FoodPreferences(), emptyList(),
        )
        week.forEach { day ->
            val meals = plan.filter { it.date == day }
            assertEquals(3, meals.size)
            val total = meals.sumOf { it.nutrition.kcal }
            assertTrue(kotlin.math.abs(total - daily.kcal) / daily.kcal < 0.08, "$day : $total kcal pour ${daily.kcal}")
        }
    }

    @Test
    fun `disliked ingredients are excluded`() {
        val prefs = FoodPreferences(disliked = setOf("poulet_filet", "tomates_pelees", "carotte", "banane"))
        val plan = MealPlanner.plan(requests(week), catalog, prefs, emptyList())
        assertEquals(28, plan.size)
        plan.forEach { assertTrue(byId.getValue(it.recipeId).ingredientIds.none { id -> id in prefs.disliked }) }
    }

    @Test
    fun `main dishes do not repeat within a week and stay in season`() {
        for (month in 1..12) {
            val days = (0L..6L).map { LocalDate.of(2026, month, 1).plusDays(it) }
            val plan = MealPlanner.plan(requests(days), catalog, FoodPreferences(), emptyList())
            val mains = plan.filter { it.slot.mealType == MealType.MAIN }
            assertEquals(14, mains.map { it.recipeId }.toSet().size, "répétition de plat en $month")
            plan.forEach {
                assertTrue(SeasonMask.contains(byId.getValue(it.recipeId).seasonMask, month), "${it.recipeId} hors saison en $month")
            }
        }
    }

    @Test
    fun `no dish repeats within its window of history`() {
        val mains = catalog.filter { it.mealType == MealType.MAIN }
        val history = (1L..14L).map { d ->
            val meal = mains[d.toInt()]
            MealContext(start.minusDays(d), MealSlot.NOON, meal.id, meal.mainProtein)
        }
        val plan = MealPlanner.plan(noon(week), catalog, FoodPreferences(), history)
        // Règle : un même plat n'est jamais servi deux fois à moins de 14 jours d'écart.
        plan.forEach { meal ->
            history.filter { it.recipeId == meal.recipeId }.forEach { past ->
                val gap = ChronoUnit.DAYS.between(past.date, meal.date)
                assertTrue(gap >= 14, "${meal.recipeId} reproposé $gap jours après")
            }
        }
    }

    @Test
    fun `strict vegan gluten-free profile still gets full days every month`() {
        val prefs = FoodPreferences(allergens = setOf(Allergen.GLUTEN), diets = setOf(Diet.VEGAN))
        for (month in 1..12) {
            val days = (0L..6L).map { LocalDate.of(2026, month, 3).plusDays(it) }
            assertEquals(28, MealPlanner.plan(requests(days), catalog, prefs, emptyList()).size, "mois $month")
        }
    }

    @Test
    fun `portions stay within the bounds of each meal type`() {
        val plan = MealPlanner.plan(requests(week), catalog, FoodPreferences(), emptyList())
        plan.forEach {
            assertTrue(it.portionFactor in MealPlanner.portionBounds(it.slot.mealType), "${it.recipeId} ×${it.portionFactor}")
        }
    }

    @Test
    fun `planning is deterministic for the same inputs`() {
        val a = MealPlanner.plan(requests(week), catalog, FoodPreferences(), emptyList())
        val b = MealPlanner.plan(requests(week), catalog, FoodPreferences(), emptyList())
        assertEquals(a, b)
    }

    @Test
    fun `replace proposes a different compatible dish and honours rejections`() {
        val prefs = FoodPreferences(diets = setOf(Diet.VEGETARIAN))
        val request = noon(listOf(start)).single()
        val first = MealPlanner.plan(listOf(request), catalog, prefs, emptyList()).single()
        val second = assertNotNull(MealPlanner.replace(request, catalog, prefs, emptyList(), excluded = setOf(first.recipeId)))
        assertNotEquals(first.recipeId, second.recipeId)
        val third = assertNotNull(MealPlanner.replace(request, catalog, prefs, emptyList(), setOf(first.recipeId, second.recipeId)))
        assertTrue(third.recipeId !in setOf(first.recipeId, second.recipeId))
        assertTrue(DietRules.isCompatible(prefs.diets, byId.getValue(third.recipeId).dietMask))
        assertEquals(MealType.MAIN, byId.getValue(third.recipeId).mealType)
    }

    @Test
    fun `small pool relaxes repetition and season but never the hard constraints`() {
        val summerOnly = MealCandidate(
            id = "ete", mainProtein = MainProtein.PLANT, mealType = MealType.MAIN,
            nutrition = Nutrition(600.0, 25.0, 20.0, 70.0, 10.0),
            allergensMask = 0, dietMask = 0, seasonMask = SeasonMask.of(6..8), ingredientIds = setOf("tomate"),
        )
        val withGluten = summerOnly.copy(id = "gluten", allergensMask = listOf(Allergen.GLUTEN).toMask(), seasonMask = SeasonMask.ALL_YEAR)
        val prefs = FoodPreferences(allergens = setOf(Allergen.GLUTEN))
        val january = noon((0L..2L).map { LocalDate.of(2026, 1, 5).plusDays(it) })

        val plan = MealPlanner.plan(january, listOf(summerOnly, withGluten), prefs, emptyList())
        assertEquals(listOf("ete", "ete", "ete"), plan.map { it.recipeId })
        assertTrue(plan.all { it.outOfSeason })
        assertNull(MealPlanner.replace(january[0], listOf(summerOnly, withGluten), prefs, emptyList(), setOf("ete")))
    }

    @Test
    fun `consecutive days avoid the same main protein at lunch`() {
        val plan = MealPlanner.plan(noon(week), catalog, FoodPreferences(), emptyList())
        val proteins = plan.map { byId.getValue(it.recipeId).mainProtein }
        val sameAsPrevious = proteins.zipWithNext().count { (a, b) -> a == b }
        assertTrue(sameAsPrevious <= 1, "protéines : $proteins")
    }
}
