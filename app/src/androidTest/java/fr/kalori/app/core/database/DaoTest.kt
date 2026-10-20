package fr.kalori.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.kalori.app.core.database.entity.IngredientEntity
import fr.kalori.app.core.database.entity.RecipeEntity
import fr.kalori.app.core.database.entity.RecipeIngredientEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateExerciseEntity
import fr.kalori.app.core.database.entity.ExerciseEntity
import fr.kalori.app.domain.model.Aisle
import fr.kalori.app.domain.model.ExerciseCategory
import fr.kalori.app.domain.model.ExerciseIcon
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.MuscleGroup
import fr.kalori.app.domain.model.PurchaseUnit
import fr.kalori.app.domain.model.RecipeOrigin
import fr.kalori.app.domain.model.RestrictionKind
import fr.kalori.app.domain.model.SeasonMask
import fr.kalori.app.domain.model.WorkoutGoal
import fr.kalori.app.domain.model.WorkoutOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** Base en mémoire, sans SQLCipher : on teste ici les requêtes, pas le chiffrement. */
@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun replaceRecipesAndDeactivateMissing() = runTest {
        val catalog = db.catalogDao()
        catalog.upsertIngredients(listOf(ingredient("riz"), ingredient("tomate")))
        catalog.replaceRecipes(
            listOf(recipe("r1"), recipe("r2")),
            listOf(RecipeIngredientEntity("r1", "riz", 70.0), RecipeIngredientEntity("r2", "tomate", 150.0)),
        )
        // Nouvelle version du catalogue : r1 change d'ingrédient, r2 disparaît.
        catalog.replaceRecipes(listOf(recipe("r1")), listOf(RecipeIngredientEntity("r1", "tomate", 100.0)))
        catalog.deactivateMissing(RecipeOrigin.BUNDLED, listOf("r1"))

        val active = catalog.getActiveRecipes()
        assertEquals(listOf("r1"), active.map { it.recipe.id })
        assertEquals(listOf("tomate"), active.single().ingredients.map { it.ingredientId })
        assertFalse(catalog.getRecipes(listOf("r2")).single().recipe.active)
    }

    @Test
    fun setWeightKeepsOneEntryPerDay() = runTest {
        val tracking = db.trackingDao()
        val day = LocalDate.of(2026, 10, 20)
        tracking.setWeight(day, 80.0)
        tracking.setWeight(day, 79.6)
        tracking.setWeight(day.plusDays(1), 79.4)

        val weights = tracking.observeWeights().first()
        assertEquals(listOf(79.6, 79.4), weights.map { it.weightKg })
    }

    @Test
    fun replaceRestrictionsOfOneKind() = runTest {
        val restrictions = db.restrictionDao()
        restrictions.replaceKind(RestrictionKind.ALLERGEN, listOf("GLUTEN", "MILK"))
        restrictions.replaceKind(RestrictionKind.DIET, listOf("VEGETARIAN"))
        restrictions.replaceKind(RestrictionKind.ALLERGEN, listOf("SESAME"))

        val all = restrictions.getAll().map { it.kind to it.code }.toSet()
        assertEquals(setOf(RestrictionKind.ALLERGEN to "SESAME", RestrictionKind.DIET to "VEGETARIAN"), all)
    }

    @Test
    fun saveTemplateCreatesThenUpdatesWithOrderedExercises() = runTest {
        val workouts = db.workoutDao()
        workouts.upsertExercises(listOf(exercise("pompes"), exercise("squat")))
        val now = Instant.EPOCH
        val template = WorkoutTemplateEntity(
            seedKey = null, name = "Ma séance", description = null, category = null,
            origin = WorkoutOrigin.USER, goal = WorkoutGoal.ANY, copiedFromId = null,
            createdAt = now, updatedAt = now,
        )
        val id = workouts.saveTemplate(template, listOf(line("pompes"), line("squat")))

        val saved = workouts.getTemplate(id)!!
        workouts.saveTemplate(saved.template.copy(name = "Renommée"), listOf(line("squat"), line("pompes")))

        val updated = workouts.getTemplate(id)!!
        assertEquals("Renommée", updated.template.name)
        assertEquals(listOf("squat", "pompes"), updated.orderedExercises.map { it.exerciseId })
        assertEquals(listOf(0, 1), updated.orderedExercises.map { it.position })
    }

    private fun ingredient(id: String) = IngredientEntity(
        id = id, name = id, ciqualCode = "0", ciqualName = id, aisle = Aisle.STARCHES,
        allergensMask = 0, tracesMask = 0, dietMask = 0, seasonMask = SeasonMask.ALL_YEAR,
        unit = PurchaseUnit.G, pieceGrams = null, pieceLabel = null,
        kcal = 100.0, protein = 1.0, fat = 1.0, carbs = 1.0, fiber = 1.0,
    )

    private fun recipe(id: String) = RecipeEntity(
        id = id, name = id, mainProtein = MainProtein.PLANT, steps = listOf("Cuire."),
        origin = RecipeOrigin.BUNDLED, aiModel = null, active = true, createdAt = Instant.EPOCH,
        kcal = 500.0, protein = 20.0, fat = 15.0, carbs = 60.0, fiber = 8.0,
        allergensMask = 0, dietMask = 0, seasonMask = SeasonMask.ALL_YEAR,
    )

    private fun exercise(id: String) = ExerciseEntity(
        id = id, name = id, category = ExerciseCategory.STREET, muscleGroup = MuscleGroup.FULL_BODY,
        icon = ExerciseIcon.SQUAT, bodyweight = true, timed = false, instructions = "",
    )

    private fun line(exerciseId: String) = WorkoutTemplateExerciseEntity(
        templateId = 0, exerciseId = exerciseId, position = 0,
        sets = 3, reps = 10, durationSec = null, loadKg = null, restSec = 60,
    )
}
