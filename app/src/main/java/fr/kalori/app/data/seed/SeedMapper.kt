package fr.kalori.app.data.seed

import fr.kalori.app.core.database.entity.ExerciseEntity
import fr.kalori.app.core.database.entity.IngredientEntity
import fr.kalori.app.core.database.entity.RecipeEntity
import fr.kalori.app.core.database.entity.RecipeIngredientEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateExerciseEntity
import fr.kalori.app.domain.meal.IngredientFacts
import fr.kalori.app.domain.meal.RecipeAnalyzer
import fr.kalori.app.domain.meal.RecipeLine
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.model.RecipeOrigin
import fr.kalori.app.domain.model.SeasonMask
import fr.kalori.app.domain.model.WorkoutOrigin
import fr.kalori.app.domain.model.toMask
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant

/** Conversion pure (sans Android) des données embarquées : testable en test unitaire JVM. */
object SeedMapper {

    private val json = Json { ignoreUnknownKeys = false }

    fun parseMeta(text: String): SeedMeta = json.decodeFromString(SeedMeta.serializer(), text)

    /** [read] renvoie le contenu d'un fichier du dossier seed/ à partir de son nom. */
    fun parse(read: (String) -> String): SeedBundle = SeedBundle(
        meta = parseMeta(read("meta.json")),
        ingredients = json.decodeFromString(ListSerializer(SeedIngredient.serializer()), read("ingredients.json")),
        recipes = json.decodeFromString(ListSerializer(SeedRecipe.serializer()), read("recipes.json")),
        exercises = json.decodeFromString(ListSerializer(SeedExercise.serializer()), read("exercises.json")),
        workouts = json.decodeFromString(ListSerializer(SeedWorkout.serializer()), read("workouts.json")),
    )

    fun ingredient(seed: SeedIngredient) = IngredientEntity(
        id = seed.id,
        name = seed.name,
        ciqualCode = seed.ciqualCode,
        ciqualName = seed.ciqualName,
        aisle = seed.aisle,
        allergensMask = seed.allergens.toMask(),
        tracesMask = seed.traces.toMask(),
        dietMask = seed.diet.toMask(),
        seasonMask = SeasonMask.of(seed.seasonMonths),
        unit = seed.unit,
        pieceGrams = seed.pieceGrams,
        pieceLabel = seed.pieceLabel,
        kcal = seed.kcal,
        protein = seed.protein,
        fat = seed.fat,
        carbs = seed.carbs,
        fiber = seed.fiber,
    )

    fun facts(ingredient: IngredientEntity) = IngredientFacts(
        id = ingredient.id,
        per100g = Nutrition(ingredient.kcal, ingredient.protein, ingredient.fat, ingredient.carbs, ingredient.fiber),
        allergensMask = ingredient.allergensMask,
        tracesMask = ingredient.tracesMask,
        dietMask = ingredient.dietMask,
        seasonMask = ingredient.seasonMask,
    )

    fun recipe(
        seed: SeedRecipe,
        facts: Map<String, IngredientFacts>,
        now: Instant,
    ): Pair<RecipeEntity, List<RecipeIngredientEntity>> {
        val lines = seed.ingredients.map { RecipeLine(it.ingredientId, it.grams) }
        val analysis = RecipeAnalyzer.analyze(lines, facts)
        require(analysis.seasonMask != 0) { "Recette ${seed.id} : aucun mois de disponibilité" }
        val entity = RecipeEntity(
            id = seed.id,
            name = seed.name,
            mainProtein = seed.mainProtein,
            steps = seed.steps,
            origin = RecipeOrigin.BUNDLED,
            aiModel = null,
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
        return entity to lines.map { RecipeIngredientEntity(seed.id, it.ingredientId, it.grams) }
    }

    fun exercise(seed: SeedExercise) = ExerciseEntity(
        id = seed.id,
        name = seed.name,
        category = seed.category,
        muscleGroup = seed.muscleGroup,
        icon = seed.icon,
        bodyweight = seed.bodyweight,
        timed = seed.timed,
        instructions = seed.instructions,
    )

    /** [existing] : la séance pré-faite déjà en base (même seedKey), pour conserver son id. */
    fun premadeWorkout(
        seed: SeedWorkout,
        existing: WorkoutTemplateEntity?,
        now: Instant,
    ): Pair<WorkoutTemplateEntity, List<WorkoutTemplateExerciseEntity>> {
        val template = WorkoutTemplateEntity(
            id = existing?.id ?: 0,
            seedKey = seed.seedKey,
            name = seed.name,
            description = seed.description,
            category = seed.category,
            origin = WorkoutOrigin.PREMADE,
            goal = seed.goal,
            copiedFromId = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val exercises = seed.exercises.mapIndexed { index, e ->
            require((e.reps == null) != (e.durationSec == null)) {
                "Séance ${seed.seedKey} / ${e.exerciseId} : indiquer reps OU durationSec"
            }
            WorkoutTemplateExerciseEntity(
                templateId = template.id,
                exerciseId = e.exerciseId,
                position = index,
                sets = e.sets,
                reps = e.reps,
                durationSec = e.durationSec,
                loadKg = e.loadKg,
                restSec = e.restSec,
            )
        }
        return template to exercises
    }
}
