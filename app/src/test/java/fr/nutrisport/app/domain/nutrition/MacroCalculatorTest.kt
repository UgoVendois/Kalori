package fr.nutrisport.app.domain.nutrition

import fr.nutrisport.app.domain.model.GoalType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacroCalculatorTest {

    @Test
    fun `fat is 35 percent of energy and fiber 30 g`() {
        val t = MacroCalculator.targets(2000, 70.0, GoalType.MAINTAIN)
        assertEquals(78, t.fatG, "lipides") // 700 kcal / 9 = 77,8 g
        assertEquals(30, t.fiberG)
    }

    @Test
    fun `protein follows g per kg when room allows`() {
        val t = MacroCalculator.targets(2600, 70.0, GoalType.MAINTAIN)
        assertEquals(84, t.proteinG) // 1,2 g/kg × 70 kg
        assertTrue(t.notices.isEmpty())
    }

    @Test
    fun `carbs never drop below 40 percent`() {
        // 80 kg en perte à 1500 kcal : 1,6 g/kg = 128 g (34 %) laisserait 31 % de glucides.
        val t = MacroCalculator.targets(1500, 80.0, GoalType.LOSE)
        val carbsShare = t.carbsG * 4.0 / t.kcal
        assertTrue(carbsShare >= 0.40 - 0.005, "glucides $carbsShare")
        assertTrue(MacroNotice.PROTEIN_REDUCED_FOR_CARBS in t.notices)
    }

    @Test
    fun `protein above 20 percent is flagged but allowed`() {
        val t = MacroCalculator.targets(2000, 90.0, GoalType.LOSE)
        assertTrue(MacroNotice.PROTEIN_ABOVE_ANSES_RANGE in t.notices)
    }

    @Test
    fun `energy from macros matches the target`() {
        for (kcal in listOf(1200, 1800, 2500, 3200)) {
            val t = MacroCalculator.targets(kcal, 75.0, GoalType.GAIN)
            val fromMacros = t.proteinG * 4 + t.carbsG * 4 + t.fatG * 9
            assertTrue(kotlin.math.abs(fromMacros - kcal) <= 10, "$kcal vs $fromMacros")
        }
    }

    @Test
    fun `meal share is bounded between 30 and 50 percent`() {
        val day = MacroCalculator.targets(2000, 70.0, GoalType.MAINTAIN)
        assertEquals(800, day.forMeal(0.40).kcal)
        assertEquals(1000, day.forMeal(0.9).kcal)
        assertEquals(600, day.forMeal(0.1).kcal)
    }
}
