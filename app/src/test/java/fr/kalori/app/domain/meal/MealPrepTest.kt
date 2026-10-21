package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.MealSlot
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class MealPrepTest {

    private val monday = LocalDate.of(2026, 10, 19)
    private val wednesday = monday.plusDays(2)
    private val batch = setOf("cookies")

    @Test
    fun `dinner cooked twice feeds the next lunch`() {
        val entries = listOf(
            PrepEntry(monday, MealSlot.EVENING, "chili", 1.2),
            PrepEntry(monday.plusDays(1), MealSlot.NOON, "chili", 1.4),
            PrepEntry(monday.plusDays(1), MealSlot.EVENING, "curry", 1.2),
        )
        val prep = MealPrepPlanner.analyze(entries, PlanSettings(lunchLeftovers = true), batch)
        val dinner = prep.getValue(monday to MealSlot.EVENING)
        assertEquals(PrepKind.COOK_WITH_LEFTOVERS, dinner.kind)
        assertEquals(2.6, dinner.cookPortions, 1e-9)
        assertEquals(PrepKind.LEFTOVERS, prep.getValue(monday.plusDays(1) to MealSlot.NOON).kind)
        assertEquals(0.0, prep.getValue(monday.plusDays(1) to MealSlot.NOON).cookPortions)
        // Lendemain pas encore planifié : on prévoit tout de même la boîte.
        assertEquals(PrepKind.COOK_WITH_LEFTOVERS, prep.getValue(monday.plusDays(1) to MealSlot.EVENING).kind)
    }

    @Test
    fun `without leftovers every meal is cooked on its own day`() {
        val entries = listOf(PrepEntry(monday, MealSlot.EVENING, "chili", 1.2), PrepEntry(monday.plusDays(1), MealSlot.NOON, "chili", 1.4))
        val prep = MealPrepPlanner.analyze(entries, PlanSettings(), batch)
        assertEquals(PrepInfo(PrepKind.COOK, 1.2), prep[monday to MealSlot.EVENING])
        assertEquals(PrepInfo(PrepKind.COOK, 1.4), prep[monday.plusDays(1) to MealSlot.NOON])
    }

    @Test
    fun `batch is prepared on its first day for the rest of the week`() {
        val entries = (2L..6L).map { PrepEntry(monday.plusDays(it), MealSlot.SNACK, "cookies", 1.0) }
        val prep = MealPrepPlanner.analyze(entries, PlanSettings(weeklySlots = setOf(MealSlot.SNACK)), batch)
        assertEquals(PrepInfo(PrepKind.BATCH_DAY, 5.0, batchDays = 5), prep[wednesday to MealSlot.SNACK])
        assertEquals(PrepKind.FROM_BATCH, prep.getValue(monday.plusDays(4) to MealSlot.SNACK).kind)
        assertEquals(0.0, prep.getValue(monday.plusDays(6) to MealSlot.SNACK).cookPortions)
    }

    @Test
    fun `a weekly recipe that needs no cooking is simply eaten each day`() {
        val entries = (0L..2L).map { PrepEntry(monday.plusDays(it), MealSlot.SNACK, "pomme_amandes", 1.0) }
        val prep = MealPrepPlanner.analyze(entries, PlanSettings(weeklySlots = setOf(MealSlot.SNACK)), batch)
        entries.forEach { assertEquals(PrepInfo(PrepKind.COOK, 1.0), prep[it.date to it.slot]) }
    }

    @Test
    fun `skipped lunch is not cooked the evening before`() {
        val entries = listOf(
            PrepEntry(monday, MealSlot.EVENING, "chili", 1.2),
            PrepEntry(monday.plusDays(1), MealSlot.NOON, "chili", 1.4, skipped = true),
        )
        val prep = MealPrepPlanner.analyze(entries, PlanSettings(lunchLeftovers = true), batch)
        assertEquals(PrepInfo(PrepKind.COOK, 1.2), prep[monday to MealSlot.EVENING])
    }
}
