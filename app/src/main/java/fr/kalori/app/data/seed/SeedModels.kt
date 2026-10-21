package fr.kalori.app.data.seed

import fr.kalori.app.domain.model.Aisle
import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.DietFlag
import fr.kalori.app.domain.model.ExerciseCategory
import fr.kalori.app.domain.model.ExerciseIcon
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.MealType
import fr.kalori.app.domain.model.MuscleGroup
import fr.kalori.app.domain.model.PurchaseUnit
import fr.kalori.app.domain.model.WorkoutGoal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Format des fichiers app/src/main/assets/seed/*.json (générés par tools/build_seed.py
// pour les ingrédients et recettes, rédigés à la main pour les exercices et séances).
// Les enums sont décodés par leur nom : une valeur inconnue fait échouer l'import (fail fast).

@Serializable
data class SeedMeta(
    val seedHash: String,
    val generatedOn: String,
    val nutritionSource: NutritionSource,
)

@Serializable
data class NutritionSource(
    val name: String,
    val publisher: String,
    val doi: String,
    val licence: String,
    val attribution: String,
)

@Serializable
data class SeedIngredient(
    val id: String,
    val name: String,
    val ciqualCode: String,
    val ciqualName: String,
    val aisle: Aisle,
    val allergens: List<Allergen>,
    val traces: List<Allergen>,
    val diet: List<DietFlag>,
    val seasonMonths: List<Int>,
    val unit: PurchaseUnit,
    val pieceGrams: Double? = null,
    val pieceLabel: String? = null,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double,
    val kcalComputed: Boolean,
)

@Serializable
data class SeedRecipe(
    val id: String,
    val name: String,
    val mainProtein: MainProtein,
    val mealType: MealType = MealType.MAIN,
    val steps: List<String>,
    val ingredients: List<SeedRecipeLine>,
)

@Serializable
data class SeedRecipeLine(val ingredientId: String, val grams: Double)

@Serializable
data class SeedExercise(
    val id: String,
    val name: String,
    val category: ExerciseCategory,
    val muscleGroup: MuscleGroup,
    @SerialName("iconKey") val icon: ExerciseIcon,
    val bodyweight: Boolean,
    val timed: Boolean,
    val instructions: String,
)

@Serializable
data class SeedWorkout(
    val seedKey: String,
    val name: String,
    val category: ExerciseCategory,
    val goal: WorkoutGoal,
    val description: String,
    val exercises: List<SeedWorkoutExercise>,
)

@Serializable
data class SeedWorkoutExercise(
    val exerciseId: String,
    val sets: Int,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val loadKg: Double? = null,
    val restSec: Int,
)

data class SeedBundle(
    val meta: SeedMeta,
    val ingredients: List<SeedIngredient>,
    val recipes: List<SeedRecipe>,
    val exercises: List<SeedExercise>,
    val workouts: List<SeedWorkout>,
)
