package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.BudgetPeriod
import fr.kalori.app.domain.model.CookingLevel
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.PriceTier
import fr.kalori.app.domain.model.SnackMode
import fr.kalori.app.domain.nutrition.MacroCalculator
import fr.kalori.app.domain.nutrition.NutritionTargets
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Budget, niveau de cuisine, restes du soir pour le midi et recettes de la semaine. */
class MealOrganizationTest {

    private val catalog: List<MealCandidate> get() = TestCatalog.candidates
    private val byId: Map<String, MealCandidate> by lazy { catalog.associateBy { it.id } }
    private val daily: NutritionTargets = MacroCalculator.targets(2720, WEIGHT_KG, GoalType.GAIN)
    private val targets: Map<MealSlot, NutritionTargets> = MealSchedule.targets(daily, MealSlot.mains.toSet(), SnackMode.AUTO)
    /** Du lundi 19 au dimanche 25 octobre 2026 : une semaine complète. */
    private val monday = LocalDate.of(2026, 10, 19)
    private val week: List<LocalDate> = (0L..6L).map { monday.plusDays(it) }

    private fun requests(days: List<LocalDate> = week, weeklyBudget: Double? = null): List<SlotRequest> = days.flatMap { d ->
        targets.map { (slot, target) ->
            SlotRequest(d, slot, target, budget = weeklyBudget?.let { it / 7.0 * target.kcal / daily.kcal })
        }
    }

    private fun weekCost(plan: List<PlannedMeal>): Double = plan.sumOf { it.cost }

    @Test
    fun `every recipe has a positive estimated cost`() {
        catalog.forEach { assertTrue(it.cost > 0.05, "${it.id} : ${it.cost} €") }
    }

    @Test
    fun `lunch is last night's dinner when leftovers are enabled`() {
        val settings = PlanSettings(lunchLeftovers = true)
        val plan = MealPlanner.plan(requests(), catalog, FoodPreferences(), emptyList(), settings)
        val bySlot = plan.associateBy { it.date to it.slot }
        week.drop(1).forEach { day ->
            val dinner = bySlot.getValue(day.minusDays(1) to MealSlot.EVENING)
            val lunch = bySlot.getValue(day to MealSlot.NOON)
            assertEquals(dinner.recipeId, lunch.recipeId, "$day")
        }
        plan.filter { it.slot == MealSlot.EVENING || it.slot == MealSlot.NOON }.forEach {
            assertTrue(MealPlanner.isLeftoverFriendly(byId.getValue(it.recipeId)), "${it.recipeId} ne se transporte pas")
        }
        // Les dîners restent variés d'un soir à l'autre.
        val dinners = plan.filter { it.slot == MealSlot.EVENING }.map { it.recipeId }
        assertEquals(dinners.size, dinners.toSet().size)
    }

    @Test
    fun `weekly snack and breakfast keep one recipe per calendar week, batch first`() {
        val settings = PlanSettings(weeklySlots = setOf(MealSlot.SNACK, MealSlot.MORNING))
        val twoWeeks = (0L..13L).map { monday.plusDays(it) }
        val plan = MealPlanner.plan(requests(twoWeeks), catalog, FoodPreferences(), emptyList(), settings)
        for (slot in listOf(MealSlot.SNACK, MealSlot.MORNING)) {
            val perWeek = plan.filter { it.slot == slot }.groupBy { MealPlanner.weekStart(it.date) }
            assertEquals(2, perWeek.size)
            perWeek.values.forEach { meals ->
                assertEquals(7, meals.size)
                assertEquals(1, meals.map { it.recipeId }.toSet().size, "$slot : ${meals.map { it.recipeId }}")
            }
            // Deux semaines de suite : pas la même fournée.
            assertTrue(perWeek.values.map { it.first().recipeId }.toSet().size == 2)
        }
        plan.filter { it.slot == MealSlot.SNACK }.forEach { assertTrue(byId.getValue(it.recipeId).batch, it.recipeId) }
    }

    @Test
    fun `weekly recipe already started this week is kept when planning the following days`() {
        val settings = PlanSettings(weeklySlots = setOf(MealSlot.SNACK))
        val context = listOf(MealContext(monday, MealSlot.SNACK, "cookies_avoine_chocolat", byId.getValue("cookies_avoine_chocolat").mainProtein))
        val plan = MealPlanner.plan(requests(week.drop(1)), catalog, FoodPreferences(), context, settings)
        plan.filter { it.slot == MealSlot.SNACK }.forEach { assertEquals("cookies_avoine_chocolat", it.recipeId) }
    }

    @Test
    fun `a budget lowers the week's cost and is met when it is realistic`() {
        val free = weekCost(MealPlanner.plan(requests(), catalog, FoodPreferences(), emptyList()))
        val budget = 55
        val tight = weekCost(MealPlanner.plan(requests(weeklyBudget = budget.toDouble()), catalog, FoodPreferences(), emptyList()))
        assertTrue(tight < free, "avec budget $tight €, sans budget $free €")
        assertTrue(tight <= budget, "$tight € pour un budget de $budget €")
    }

    @Test
    fun `an unrealistic budget still yields full days, at the lowest cost`() {
        val plan = MealPlanner.plan(requests(weeklyBudget = 10.0), catalog, FoodPreferences(), emptyList())
        assertEquals(week.size * targets.size, plan.size)
        week.forEach { day ->
            val total = plan.filter { it.date == day }.sumOf { it.nutrition.kcal }
            assertTrue(kotlin.math.abs(total - daily.kcal) / daily.kcal < 0.10, "$day : $total kcal")
        }
    }

    @Test
    fun `100 euros a month is enough at entry-level prices, with variety and protein`() {
        val weekly = Budget(100, BudgetPeriod.MONTH).perWeek
        val discount = TestCatalog.candidates(PriceTier.DISCOUNT)
        for (settings in listOf(PlanSettings(CookingLevel.NORMAL), PlanSettings(CookingLevel.NORMAL, lunchLeftovers = true, weeklySlots = setOf(MealSlot.SNACK)))) {
            val plan = MealPlanner.plan(requests(weeklyBudget = weekly), discount, FoodPreferences(), emptyList(), settings)
            assertTrue(weekCost(plan) <= weekly, "${weekCost(plan)} € pour ${"%.1f".format(weekly)} € par semaine ($settings)")
            // Budget serré : la cible protéique (1,6 g/kg) n'est pas toujours atteinte, mais on reste au-dessus de 1,3 g/kg.
            val protein = plan.sumOf { it.nutrition.protein } / 7
            assertTrue(protein >= 1.3 * WEIGHT_KG, "protéines : $protein g/j")
            val mains = plan.filter { it.slot == MealSlot.NOON || it.slot == MealSlot.EVENING }.map { it.recipeId }.toSet()
            assertTrue(mains.size >= 7, "seulement ${mains.size} plats différents")
        }
    }

    @Test
    fun `an unreachable budget is exceeded rather than serving the same dish every day`() {
        val plan = MealPlanner.plan(requests(weeklyBudget = 10.0), catalog, FoodPreferences(), emptyList())
        val mains = plan.filter { it.slot == MealSlot.NOON || it.slot == MealSlot.EVENING }.map { it.recipeId }
        assertTrue(mains.toSet().size >= 7, "seulement ${mains.toSet().size} plats différents")
    }

    @Test
    fun `simple cooking level only proposes simple recipes`() {
        val settings = PlanSettings(cookingLevel = CookingLevel.SIMPLE)
        val plan = MealPlanner.plan(requests(), catalog, FoodPreferences(), emptyList(), settings)
        plan.forEach { assertEquals(1, byId.getValue(it.recipeId).difficulty, it.recipeId) }
    }

    @Test
    fun `elaborate cooking level brings more involved dishes than simple`() {
        fun avgDifficulty(level: CookingLevel): Double = MealPlanner.plan(requests(), catalog, FoodPreferences(), emptyList(), PlanSettings(cookingLevel = level))
            .filter { it.slot == MealSlot.NOON || it.slot == MealSlot.EVENING }
            .map { byId.getValue(it.recipeId).difficulty }.average()
        assertTrue(avgDifficulty(CookingLevel.ELABORATE) > avgDifficulty(CookingLevel.SIMPLE))
    }

    @Test
    fun `leftovers, weekly snack and budget work together`() {
        val settings = PlanSettings(cookingLevel = CookingLevel.SIMPLE, lunchLeftovers = true, weeklySlots = setOf(MealSlot.SNACK))
        val plan = MealPlanner.plan(requests(weeklyBudget = 60.0), catalog, FoodPreferences(), emptyList(), settings)
        assertEquals(week.size * targets.size, plan.size)
        assertEquals(1, plan.filter { it.slot == MealSlot.SNACK }.map { it.recipeId }.toSet().size)
        assertTrue(weekCost(plan) <= 60 * 1.1, "${weekCost(plan)} €")
    }

    private companion object {
        const val WEIGHT_KG = 80.0
    }
}
