package fr.nutrisport.app.domain.meal

import fr.nutrisport.app.domain.model.Nutrition
import fr.nutrisport.app.domain.model.SeasonMask

/** Données d'un ingrédient nécessaires à l'analyse d'une recette. */
data class IngredientFacts(
    val id: String,
    val per100g: Nutrition,
    val allergensMask: Int,
    val tracesMask: Int,
    val dietMask: Int,
    val seasonMask: Int,
)

data class RecipeLine(val ingredientId: String, val grams: Double)

data class RecipeAnalysis(
    /** Pour une portion de base (facteur de portion = 1). */
    val nutrition: Nutrition,
    /** Allergènes présents ET traces déclarées : le filtre strict traite les deux de la même façon. */
    val allergensMask: Int,
    val dietMask: Int,
    /** Mois où TOUS les ingrédients de saison sont disponibles. */
    val seasonMask: Int,
)

/**
 * Calcul unique des propriétés d'une recette à partir de ses ingrédients.
 * Utilisé pour les recettes embarquées (import) ET pour les variantes générées par IA,
 * afin qu'aucune valeur fournie par l'IA ne soit jamais reprise telle quelle.
 */
object RecipeAnalyzer {

    fun analyze(lines: List<RecipeLine>, facts: Map<String, IngredientFacts>): RecipeAnalysis {
        require(lines.isNotEmpty()) { "Recette sans ingrédient" }
        var nutrition = Nutrition.ZERO
        var allergens = 0
        var diet = 0
        var season = SeasonMask.ALL_YEAR
        for (line in lines) {
            require(line.grams > 0) { "Quantité invalide pour ${line.ingredientId}" }
            val ingredient = requireNotNull(facts[line.ingredientId]) {
                "Ingrédient inconnu : ${line.ingredientId}"
            }
            nutrition += ingredient.per100g * (line.grams / 100.0)
            allergens = allergens or ingredient.allergensMask or ingredient.tracesMask
            diet = diet or ingredient.dietMask
            season = season and ingredient.seasonMask
        }
        return RecipeAnalysis(nutrition, allergens, diet, season)
    }
}
