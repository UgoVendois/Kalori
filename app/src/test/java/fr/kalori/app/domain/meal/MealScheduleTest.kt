package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.SnackMode
import fr.kalori.app.domain.nutrition.MacroCalculator
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MealScheduleTest {

    private val all = MealSlot.mains.toSet()

    @Test
    fun `high target adds a snack automatically`() {
        assertEquals(listOf(MealSlot.MORNING, MealSlot.NOON, MealSlot.EVENING, MealSlot.SNACK), MealSchedule.slots(all, SnackMode.AUTO, 2720))
        assertEquals(listOf(MealSlot.MORNING, MealSlot.NOON, MealSlot.EVENING), MealSchedule.slots(all, SnackMode.AUTO, 1800))
    }

    @Test
    fun `skipping breakfast keeps lunch and dinner and adds a snack if needed`() {
        val slots = MealSchedule.slots(setOf(MealSlot.NOON, MealSlot.EVENING), SnackMode.AUTO, 1800)
        assertEquals(listOf(MealSlot.NOON, MealSlot.EVENING, MealSlot.SNACK), slots)
        assertEquals(listOf(MealSlot.NOON, MealSlot.EVENING), MealSchedule.slots(setOf(MealSlot.NOON, MealSlot.EVENING), SnackMode.NEVER, 2720))
    }

    @Test
    fun `at least one meal is always kept`() {
        assertEquals(listOf(MealSlot.NOON), MealSchedule.slots(emptySet(), SnackMode.NEVER, 2000))
    }

    @Test
    fun `targets cover the whole day`() {
        val day = MacroCalculator.targets(2720, 80.0, GoalType.GAIN)
        val targets = MealSchedule.targets(day, all, SnackMode.AUTO)
        assertEquals(680, targets.getValue(MealSlot.MORNING).kcal)
        assertEquals(952, targets.getValue(MealSlot.NOON).kcal)
        assertEquals(816, targets.getValue(MealSlot.EVENING).kcal)
        assertEquals(272, targets.getValue(MealSlot.SNACK).kcal)
        assertTrue(abs(targets.values.sumOf { it.kcal } - 2720) <= 2)
    }

    @Test
    fun `without breakfast the snack gets a bigger share`() {
        val day = MacroCalculator.targets(2000, 70.0, GoalType.MAINTAIN)
        val targets = MealSchedule.targets(day, setOf(MealSlot.NOON, MealSlot.EVENING), SnackMode.AUTO)
        assertEquals(setOf(MealSlot.NOON, MealSlot.EVENING, MealSlot.SNACK), targets.keys)
        assertEquals(471, targets.getValue(MealSlot.SNACK).kcal) // 20 / 85 × 2000
    }
}
