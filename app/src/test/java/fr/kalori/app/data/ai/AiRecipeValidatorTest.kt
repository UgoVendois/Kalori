package fr.kalori.app.data.ai

import fr.kalori.app.data.seed.SeedMapper
import fr.kalori.app.domain.meal.FoodPreferences
import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.RecipeOrigin
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AiRecipeValidatorTest {

    private val ingredients = SeedMapper.parse { File("src/main/assets/seed/$it").readText() }
        .ingredients.map(SeedMapper::ingredient).associateBy { it.id }

    private fun validate(proposal: AiProposal, prefs: FoodPreferences = FoodPreferences(), month: Int = 10) =
        AiRecipeValidator.validate(
            proposal = proposal,
            ingredients = ingredients,
            allowedIds = AiRecipeValidator.allowedIngredients(ingredients.values.toList(), prefs, month).map { it.id }.toSet(),
            prefs = prefs,
            model = "claude-opus-5-5",
            recipeId = "ai_test",
            now = Instant.EPOCH,
        )

    private val lentils = AiProposal(
        name = "Lentilles corail aux carottes et cumin",
        mainProtein = "PLANT",
        ingredients = listOf(AiProposalLine("lentilles_corail", 80), AiProposalLine("carotte", 150), AiProposalLine("riz_basmati", 60), AiProposalLine("cumin", 2)),
        steps = listOf("Cuire les lentilles.", "Ajouter les carottes et le cumin.", "Servir avec le riz."),
    )

    @Test
    fun `allowed list excludes allergens, traces, diets, dislikes and out-of-season produce`() {
        val prefs = FoodPreferences(
            allergens = setOf(Allergen.GLUTEN),
            diets = setOf(Diet.VEGETARIAN),
            disliked = setOf("carotte"),
        )
        val allowed = AiRecipeValidator.allowedIngredients(ingredients.values.toList(), prefs, month = 1).map { it.id }.toSet()
        listOf("pates", "galette_sarrasin", "poulet_filet", "saumon", "carotte", "tomate", "fraise").forEach {
            assertTrue(it !in allowed, "$it ne devrait pas être proposé")
        }
        listOf("lentilles_corail", "poireau", "riz_basmati").forEach { assertTrue(it in allowed, "$it devrait être proposé") }
    }

    @Test
    fun `a valid proposal is recomputed from Ciqual and labelled as AI`() {
        val result = assertIs<AiValidation.Valid>(validate(lentils))
        assertEquals(RecipeOrigin.AI, result.recipe.origin)
        assertEquals("claude-opus-5-5", result.recipe.aiModel)
        assertEquals(4, result.lines.size)
        assertTrue(result.recipe.kcal in 400.0..700.0, "${result.recipe.kcal}")
    }

    @Test
    fun `proposals outside the allowed list or violating constraints are rejected`() {
        val glutenFree = FoodPreferences(allergens = setOf(Allergen.GLUTEN))
        val withPasta = lentils.copy(ingredients = lentils.ingredients + AiProposalLine("pates", 80))
        assertIs<AiValidation.Rejected>(validate(withPasta, glutenFree))
        assertIs<AiValidation.Rejected>(validate(lentils.copy(ingredients = lentils.ingredients + AiProposalLine("inconnu", 50))))
        assertIs<AiValidation.Rejected>(validate(lentils.copy(ingredients = listOf(AiProposalLine("carotte", 900)))))
        assertIs<AiValidation.Rejected>(validate(lentils.copy(mainProtein = "DRAGON")))
        assertIs<AiValidation.Rejected>(validate(lentils.copy(steps = listOf("Tout mélanger."))))
        // 10 g de cumin seul : portion bien trop faible.
        assertIs<AiValidation.Rejected>(validate(lentils.copy(ingredients = listOf(AiProposalLine("cumin", 10), AiProposalLine("carotte", 20)))))
    }

    @Test
    fun `kosher rule on meat with dairy is checked on the whole recipe`() {
        val kosher = FoodPreferences(diets = setOf(Diet.KOSHER))
        val proposal = AiProposal(
            name = "Bœuf à la crème",
            mainProtein = "RED_MEAT",
            ingredients = listOf(AiProposalLine("boeuf_hache", 120), AiProposalLine("creme_legere", 40), AiProposalLine("riz_basmati", 70)),
            steps = listOf("Saisir le bœuf.", "Ajouter la crème."),
        )
        assertIs<AiValidation.Rejected>(validate(proposal, kosher))
    }
}
