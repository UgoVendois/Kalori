package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.DietFlag
import fr.kalori.app.domain.model.toMask
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DietRulesTest {

    private fun mask(vararg flags: DietFlag) = flags.toList().toMask()

    @Test
    fun `vegetarian accepts dairy and eggs but no flesh`() {
        assertTrue(DietRules.isCompatible(Diet.VEGETARIAN, mask(DietFlag.DAIRY, DietFlag.EGG)))
        assertFalse(DietRules.isCompatible(Diet.VEGETARIAN, mask(DietFlag.FISH)))
        assertFalse(DietRules.isCompatible(Diet.VEGETARIAN, mask(DietFlag.SHELLFISH)))
    }

    @Test
    fun `vegan refuses every animal product including honey`() {
        assertTrue(DietRules.isCompatible(Diet.VEGAN, 0))
        listOf(DietFlag.MEAT, DietFlag.DAIRY, DietFlag.EGG, DietFlag.HONEY).forEach {
            assertFalse(DietRules.isCompatible(Diet.VEGAN, mask(it)), "$it")
        }
    }

    @Test
    fun `halal refuses pork and alcohol`() {
        assertTrue(DietRules.isCompatible(Diet.HALAL, mask(DietFlag.MEAT)))
        assertFalse(DietRules.isCompatible(Diet.HALAL, mask(DietFlag.MEAT, DietFlag.PORK)))
        assertFalse(DietRules.isCompatible(Diet.HALAL, mask(DietFlag.ALCOHOL)))
    }

    @Test
    fun `kosher refuses pork, shellfish and meat with dairy`() {
        assertTrue(DietRules.isCompatible(Diet.KOSHER, mask(DietFlag.MEAT)))
        assertTrue(DietRules.isCompatible(Diet.KOSHER, mask(DietFlag.FISH, DietFlag.DAIRY)))
        assertFalse(DietRules.isCompatible(Diet.KOSHER, mask(DietFlag.MEAT, DietFlag.DAIRY)))
        assertFalse(DietRules.isCompatible(Diet.KOSHER, mask(DietFlag.SHELLFISH)))
    }

    @Test
    fun `lactose free accepts aged cheese but not milk`() {
        assertTrue(DietRules.isCompatible(Diet.LACTOSE_FREE, mask(DietFlag.DAIRY)))
        assertFalse(DietRules.isCompatible(Diet.LACTOSE_FREE, mask(DietFlag.DAIRY, DietFlag.LACTOSE)))
    }

    @Test
    fun `several diets must all be satisfied`() {
        val diets = setOf(Diet.PESCATARIAN, Diet.NO_ALCOHOL)
        assertTrue(DietRules.isCompatible(diets, mask(DietFlag.FISH)))
        assertFalse(DietRules.isCompatible(diets, mask(DietFlag.FISH, DietFlag.ALCOHOL)))
    }
}
