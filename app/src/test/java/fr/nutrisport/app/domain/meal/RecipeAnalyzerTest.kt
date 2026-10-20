package fr.nutrisport.app.domain.meal

import fr.nutrisport.app.domain.model.Allergen
import fr.nutrisport.app.domain.model.DietFlag
import fr.nutrisport.app.domain.model.Nutrition
import fr.nutrisport.app.domain.model.SeasonMask
import fr.nutrisport.app.domain.model.hasFlag
import fr.nutrisport.app.domain.model.toMask
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeAnalyzerTest {

    private val pasta = IngredientFacts(
        id = "pates",
        per100g = Nutrition(kcal = 360.0, protein = 12.0, fat = 1.5, carbs = 72.0, fiber = 3.0),
        allergensMask = listOf(Allergen.GLUTEN).toMask(),
        tracesMask = listOf(Allergen.EGGS).toMask(),
        dietMask = 0,
        seasonMask = SeasonMask.ALL_YEAR,
    )
    private val tomato = IngredientFacts(
        id = "tomate",
        per100g = Nutrition(kcal = 20.0, protein = 1.0, fat = 0.2, carbs = 3.0, fiber = 1.0),
        allergensMask = 0,
        tracesMask = 0,
        dietMask = 0,
        seasonMask = SeasonMask.of(6..10),
    )
    private val parmesan = IngredientFacts(
        id = "parmesan",
        per100g = Nutrition(kcal = 400.0, protein = 30.0, fat = 30.0, carbs = 1.0, fiber = 0.0),
        allergensMask = listOf(Allergen.MILK).toMask(),
        tracesMask = 0,
        dietMask = listOf(DietFlag.DAIRY).toMask(),
        seasonMask = SeasonMask.ALL_YEAR,
    )
    private val facts = listOf(pasta, tomato, parmesan).associateBy { it.id }

    @Test
    fun `nutrition is the sum of ingredients scaled by grams`() {
        val result = RecipeAnalyzer.analyze(
            listOf(RecipeLine("pates", 100.0), RecipeLine("tomate", 200.0), RecipeLine("parmesan", 10.0)),
            facts,
        )
        assertEquals(360.0 + 40.0 + 40.0, result.nutrition.kcal, 1e-9)
        assertEquals(12.0 + 2.0 + 3.0, result.nutrition.protein, 1e-9)
        assertEquals(3.0 + 2.0, result.nutrition.fiber, 1e-9)
    }

    @Test
    fun `allergens include declared traces`() {
        val result = RecipeAnalyzer.analyze(listOf(RecipeLine("pates", 80.0)), facts)
        assertTrue(result.allergensMask.hasFlag(Allergen.GLUTEN))
        assertTrue(result.allergensMask.hasFlag(Allergen.EGGS), "les traces doivent être traitées comme des allergènes")
        assertFalse(result.allergensMask.hasFlag(Allergen.MILK))
    }

    @Test
    fun `diet flags are merged and season is the intersection`() {
        val result = RecipeAnalyzer.analyze(
            listOf(RecipeLine("pates", 80.0), RecipeLine("tomate", 150.0), RecipeLine("parmesan", 15.0)),
            facts,
        )
        assertTrue(result.dietMask.hasFlag(DietFlag.DAIRY))
        assertEquals((6..10).toList(), SeasonMask.months(result.seasonMask))
    }

    @Test
    fun `unknown ingredient or empty recipe is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RecipeAnalyzer.analyze(listOf(RecipeLine("inconnu", 10.0)), facts)
        }
        assertFailsWith<IllegalArgumentException> { RecipeAnalyzer.analyze(emptyList(), facts) }
        assertFailsWith<IllegalArgumentException> {
            RecipeAnalyzer.analyze(listOf(RecipeLine("pates", 0.0)), facts)
        }
    }
}
