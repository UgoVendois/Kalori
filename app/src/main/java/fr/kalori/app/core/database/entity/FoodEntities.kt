package fr.kalori.app.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import fr.kalori.app.domain.model.Aisle
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.MealStatus
import fr.kalori.app.domain.model.PurchaseUnit
import fr.kalori.app.domain.model.RecipeOrigin
import java.time.Instant
import java.time.LocalDate

/** Ingrédient du catalogue embarqué. Valeurs pour 100 g, issues de Ciqual (voir docs/INGREDIENTS_CIQUAL.md). */
@Entity(tableName = "ingredient")
data class IngredientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ciqualCode: String,
    val ciqualName: String,
    val aisle: Aisle,
    val allergensMask: Int,
    val tracesMask: Int,
    val dietMask: Int,
    val seasonMask: Int,
    val unit: PurchaseUnit,
    val pieceGrams: Double?,
    val pieceLabel: String?,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double,
)

/**
 * Recette (embarquée ou générée par IA). Les champs calculés (nutriments, masques) sont
 * produits par RecipeAnalyzer à l'import : jamais saisis à la main ni repris de l'IA.
 */
@Entity(tableName = "recipe", indices = [Index("active"), Index("origin")])
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mainProtein: MainProtein,
    val steps: List<String>,
    val origin: RecipeOrigin,
    /** Modèle d'IA ayant proposé la recette (transparence AI Act), null pour les recettes embarquées. */
    val aiModel: String?,
    /** false : retirée du catalogue, conservée pour l'historique des repas passés. */
    val active: Boolean,
    val createdAt: Instant,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double,
    val allergensMask: Int,
    val dietMask: Int,
    val seasonMask: Int,
)

@Entity(
    tableName = "recipe_ingredient",
    primaryKeys = ["recipeId", "ingredientId"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("ingredientId")],
)
data class RecipeIngredientEntity(
    val recipeId: String,
    val ingredientId: String,
    /** Grammes comestibles pour une portion de base. */
    val grams: Double,
)

data class RecipeWithIngredients(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<RecipeIngredientEntity>,
)

/** Plat prévu pour un jour donné. Les nutriments sont figés au moment de la planification. */
@Entity(
    tableName = "meal_plan_entry",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("recipeId")],
)
data class MealPlanEntryEntity(
    @PrimaryKey val date: LocalDate,
    val recipeId: String,
    val portionFactor: Double,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double,
    val status: MealStatus,
    /** Recettes déjà écartées par « Remplacer » pour ce jour (évite de les reproposer). */
    val rejectedRecipeIds: List<String>,
)

@Entity(
    tableName = "shopping_check",
    primaryKeys = ["weekStart", "ingredientId"],
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ingredientId")],
)
data class ShoppingCheckEntity(
    val weekStart: LocalDate,
    val ingredientId: String,
    val checked: Boolean,
)
