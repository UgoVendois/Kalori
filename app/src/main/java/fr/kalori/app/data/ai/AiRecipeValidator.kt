package fr.kalori.app.data.ai

import fr.kalori.app.core.database.entity.IngredientEntity
import fr.kalori.app.core.database.entity.RecipeEntity
import fr.kalori.app.core.database.entity.RecipeIngredientEntity
import fr.kalori.app.data.seed.SeedMapper
import fr.kalori.app.domain.meal.DietRules
import fr.kalori.app.domain.meal.FoodPreferences
import fr.kalori.app.domain.meal.MealCandidate
import fr.kalori.app.domain.meal.MealPlanner
import fr.kalori.app.domain.meal.RecipeAnalyzer
import fr.kalori.app.domain.meal.RecipeLine
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.RecipeOrigin
import fr.kalori.app.domain.model.SeasonMask
import fr.kalori.app.domain.model.toMask
import kotlinx.serialization.Serializable
import java.time.Instant

/** Réponse attendue de l'IA (sortie structurée, schéma imposé). */
@Serializable
data class AiProposal(
    val name: String,
    val mainProtein: String,
    val ingredients: List<AiProposalLine>,
    val steps: List<String>,
)

@Serializable
data class AiProposalLine(val id: String, val grams: Int)

sealed interface AiValidation {
    data class Valid(val recipe: RecipeEntity, val lines: List<RecipeIngredientEntity>) : AiValidation
    data class Rejected(val reason: String) : AiValidation
}

/**
 * Règles appliquées AVANT et APRÈS l'appel à l'IA. Rien de ce que renvoie l'IA n'est cru sur parole :
 * les ingrédients doivent venir du catalogue, les nutriments sont recalculés (Ciqual) et les contraintes
 * strictes (allergènes et traces, régimes, aliments détestés) sont revérifiées comme pour toute recette.
 */
object AiRecipeValidator {

    const val MIN_GRAMS = 1
    const val MAX_GRAMS = 600
    const val MIN_KCAL = 250.0
    const val MAX_KCAL = 1200.0

    /**
     * Ingrédients proposés à l'IA : compatibles avec les allergènes (et traces), les régimes, les goûts,
     * et de saison. C'est la seule information de profil transmise, sous forme d'une liste d'aliments.
     */
    fun allowedIngredients(ingredients: List<IngredientEntity>, prefs: FoodPreferences, month: Int): List<IngredientEntity> {
        val allergenMask = prefs.allergens.toMask()
        return ingredients.filter {
            (it.allergensMask or it.tracesMask) and allergenMask == 0 &&
                DietRules.isCompatible(prefs.diets, it.dietMask) &&
                it.id !in prefs.disliked &&
                SeasonMask.contains(it.seasonMask, month)
        }
    }

    fun validate(
        proposal: AiProposal,
        ingredients: Map<String, IngredientEntity>,
        allowedIds: Set<String>,
        prefs: FoodPreferences,
        model: String,
        recipeId: String,
        now: Instant,
    ): AiValidation {
        val name = proposal.name.trim()
        if (name.isEmpty() || name.length > 80) return AiValidation.Rejected("nom invalide")
        val mainProtein = runCatching { MainProtein.valueOf(proposal.mainProtein) }.getOrNull()
            ?: return AiValidation.Rejected("protéine principale inconnue")
        val steps = proposal.steps.map { it.trim() }.filter { it.isNotEmpty() }
        if (steps.size !in 2..10 || steps.any { it.length > 400 }) return AiValidation.Rejected("étapes invalides")
        if (proposal.ingredients.isEmpty() || proposal.ingredients.size > 15) return AiValidation.Rejected("nombre d'ingrédients invalide")
        if (proposal.ingredients.map { it.id }.toSet().size != proposal.ingredients.size) return AiValidation.Rejected("ingrédient en double")
        proposal.ingredients.forEach {
            if (it.id !in allowedIds || it.id !in ingredients) return AiValidation.Rejected("ingrédient hors liste : ${it.id}")
            if (it.grams !in MIN_GRAMS..MAX_GRAMS) return AiValidation.Rejected("quantité invalide pour ${it.id}")
        }

        val facts = proposal.ingredients.associate { it.id to SeedMapper.facts(ingredients.getValue(it.id)) }
        val lines = proposal.ingredients.map { RecipeLine(it.id, it.grams.toDouble()) }
        val analysis = RecipeAnalyzer.analyze(lines, facts)
        if (analysis.nutrition.kcal !in MIN_KCAL..MAX_KCAL) return AiValidation.Rejected("portion de ${analysis.nutrition.kcal.toInt()} kcal hors bornes")

        // Contre-vérification stricte, identique à celle des recettes embarquées (dont la règle casher viande + lait).
        val candidate = MealCandidate(
            id = recipeId,
            mainProtein = mainProtein,
            nutrition = analysis.nutrition,
            allergensMask = analysis.allergensMask,
            dietMask = analysis.dietMask,
            seasonMask = analysis.seasonMask,
            ingredientIds = lines.map { it.ingredientId }.toSet(),
        )
        if (MealPlanner.eligible(listOf(candidate), prefs).isEmpty()) return AiValidation.Rejected("contraintes alimentaires non respectées")

        val recipe = RecipeEntity(
            id = recipeId,
            name = name,
            mainProtein = mainProtein,
            steps = steps,
            origin = RecipeOrigin.AI,
            aiModel = model,
            active = true,
            createdAt = now,
            kcal = analysis.nutrition.kcal,
            protein = analysis.nutrition.protein,
            fat = analysis.nutrition.fat,
            carbs = analysis.nutrition.carbs,
            fiber = analysis.nutrition.fiber,
            allergensMask = analysis.allergensMask,
            dietMask = analysis.dietMask,
            seasonMask = analysis.seasonMask,
        )
        return AiValidation.Valid(recipe, lines.map { RecipeIngredientEntity(recipeId, it.ingredientId, it.grams) })
    }
}
