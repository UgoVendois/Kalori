package fr.kalori.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import fr.kalori.app.core.database.entity.IngredientEntity
import fr.kalori.app.core.database.entity.RecipeEntity
import fr.kalori.app.core.database.entity.RecipeIngredientEntity
import fr.kalori.app.core.database.entity.RecipeWithIngredients
import fr.kalori.app.domain.model.RecipeOrigin
import kotlinx.coroutines.flow.Flow

/** Catalogue alimentaire : ingrédients et recettes. */
@Dao
abstract class CatalogDao {
    @Query("SELECT * FROM ingredient ORDER BY name")
    abstract fun observeIngredients(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredient")
    abstract suspend fun getIngredients(): List<IngredientEntity>

    @Upsert
    abstract suspend fun upsertIngredients(ingredients: List<IngredientEntity>)

    @Transaction
    @Query("SELECT * FROM recipe WHERE active = 1")
    abstract suspend fun getActiveRecipes(): List<RecipeWithIngredients>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id")
    abstract fun observeRecipe(id: String): Flow<RecipeWithIngredients?>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id IN (:ids)")
    abstract suspend fun getRecipes(ids: Collection<String>): List<RecipeWithIngredients>

    @Upsert
    abstract suspend fun upsertRecipes(recipes: List<RecipeEntity>)

    @Query("DELETE FROM recipe_ingredient WHERE recipeId IN (:recipeIds)")
    abstract suspend fun deleteRecipeIngredients(recipeIds: Collection<String>)

    @Upsert
    abstract suspend fun upsertRecipeIngredients(lines: List<RecipeIngredientEntity>)

    /** Retire du catalogue les recettes d'une origine absentes de [keepIds] (conservées pour l'historique). */
    @Query("UPDATE recipe SET active = 0 WHERE origin = :origin AND id NOT IN (:keepIds)")
    abstract suspend fun deactivateMissing(origin: RecipeOrigin, keepIds: Collection<String>)

    /** Remplace intégralement des recettes et leurs ingrédients. */
    @Transaction
    open suspend fun replaceRecipes(recipes: List<RecipeEntity>, lines: List<RecipeIngredientEntity>) {
        upsertRecipes(recipes)
        deleteRecipeIngredients(recipes.map { it.id })
        upsertRecipeIngredients(lines)
    }
}
