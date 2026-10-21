package fr.kalori.app.data.meal

import fr.kalori.app.core.database.entity.IngredientEntity
import fr.kalori.app.core.database.entity.RecipeEntity
import fr.kalori.app.core.database.entity.RecipeWithIngredients
import fr.kalori.app.core.database.entity.UserRestrictionEntity
import fr.kalori.app.domain.meal.FoodPreferences
import fr.kalori.app.domain.meal.MealCandidate
import fr.kalori.app.domain.meal.ShoppingIngredient
import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.model.RestrictionKind

fun RecipeEntity.nutrition() = Nutrition(kcal, protein, fat, carbs, fiber)

fun RecipeWithIngredients.toCandidate() = MealCandidate(
    id = recipe.id,
    mainProtein = recipe.mainProtein,
    mealType = recipe.mealType,
    nutrition = recipe.nutrition(),
    allergensMask = recipe.allergensMask,
    dietMask = recipe.dietMask,
    seasonMask = recipe.seasonMask,
    ingredientIds = ingredients.map { it.ingredientId }.toSet(),
)

fun IngredientEntity.toShoppingIngredient() = ShoppingIngredient(
    id = id,
    name = name,
    aisle = aisle,
    unit = unit,
    pieceGrams = pieceGrams,
    pieceLabel = pieceLabel,
)

/** Les codes inconnus (enum renommé, ingrédient retiré) sont ignorés plutôt que de faire planter l'app. */
fun List<UserRestrictionEntity>.toFoodPreferences(): FoodPreferences {
    fun codes(kind: RestrictionKind) = filter { it.kind == kind }.map { it.code }
    return FoodPreferences(
        allergens = codes(RestrictionKind.ALLERGEN).mapNotNull { runCatching { Allergen.valueOf(it) }.getOrNull() }.toSet(),
        diets = codes(RestrictionKind.DIET).mapNotNull { runCatching { Diet.valueOf(it) }.getOrNull() }.toSet(),
        liked = codes(RestrictionKind.LIKE).toSet(),
        disliked = codes(RestrictionKind.DISLIKE).toSet(),
    )
}
