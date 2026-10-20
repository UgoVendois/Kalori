package fr.kalori.app.data.seed

import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.DietFlag
import fr.kalori.app.domain.model.PurchaseUnit
import fr.kalori.app.domain.model.SeasonMask
import fr.kalori.app.domain.model.toMask
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Vérifie l'intégrité des données réellement embarquées dans l'APK (app/src/main/assets/seed).
 * Les tests unitaires Gradle s'exécutent depuis le dossier du module `app`.
 */
class SeedDataTest {

    private val bundle = SeedMapper.parse { name -> File("src/main/assets/seed/$name").readText() }
    private val ingredients = bundle.ingredients.map(SeedMapper::ingredient)
    private val facts = ingredients.associate { it.id to SeedMapper.facts(it) }

    @Test
    fun `identifiers are unique`() {
        assertUnique(bundle.ingredients.map { it.id }, "ingrédient")
        assertUnique(bundle.recipes.map { it.id }, "recette")
        assertUnique(bundle.exercises.map { it.id }, "exercice")
        assertUnique(bundle.workouts.map { it.seedKey }, "séance")
    }

    @Test
    fun `every recipe is analyzable, in season at least one month, and has a plausible portion`() {
        assertTrue(bundle.recipes.size >= 100, "catalogue trop petit : ${bundle.recipes.size}")
        for (seed in bundle.recipes) {
            val (recipe, lines) = SeedMapper.recipe(seed, facts, Instant.EPOCH)
            assertTrue(recipe.seasonMask != 0, "${seed.id} n'est disponible aucun mois")
            assertTrue(recipe.kcal in 350.0..900.0, "${seed.id} : ${recipe.kcal} kcal par portion")
            assertTrue(recipe.steps.isNotEmpty(), "${seed.id} sans étape")
            assertEquals(seed.ingredients.size, lines.size)
        }
    }

    @Test
    fun `every month offers enough vegan recipes to avoid repeating within two weeks`() {
        val veganForbidden = listOf(
            DietFlag.MEAT, DietFlag.PORK, DietFlag.FISH, DietFlag.SHELLFISH,
            DietFlag.DAIRY, DietFlag.EGG, DietFlag.HONEY,
        ).toMask()
        val recipes = bundle.recipes.map { SeedMapper.recipe(it, facts, Instant.EPOCH).first }
        for (month in 1..12) {
            val count = recipes.count { SeasonMask.contains(it.seasonMask, month) && it.dietMask and veganForbidden == 0 }
            assertTrue(count >= 14, "mois $month : seulement $count recettes véganes")
        }
    }

    @Test
    fun `nutrition values are complete and sane`() {
        for (i in bundle.ingredients) {
            assertTrue(i.kcal in 0.0..900.0, "${i.id} kcal=${i.kcal}")
            listOf(i.protein, i.fat, i.carbs, i.fiber).forEach {
                assertTrue(it in 0.0..100.0, "${i.id} : valeur hors bornes $it")
            }
            assertTrue(i.ciqualCode.isNotBlank(), "${i.id} sans code Ciqual")
            if (i.unit == PurchaseUnit.PIECE) {
                assertTrue((i.pieceGrams ?: 0.0) > 0, "${i.id} : poids à la pièce manquant")
            }
        }
    }

    @Test
    fun `allergen data covers the regulatory list where relevant`() {
        val declared = bundle.ingredients.flatMap { it.allergens }.toSet()
        // Le lupin n'est présent dans aucun ingrédient du catalogue : c'est attendu.
        val expected = Allergen.entries.toSet() - Allergen.LUPIN
        assertEquals(expected, declared, "allergènes jamais déclarés : ${expected - declared}")
    }

    @Test
    fun `premade workouts reference known exercises with reps or duration`() {
        val exercises = bundle.exercises.associateBy { it.id }
        for (workout in bundle.workouts) {
            assertTrue(workout.exercises.isNotEmpty(), "${workout.seedKey} vide")
            for (e in workout.exercises) {
                val exercise = exercises[e.exerciseId] ?: fail("${workout.seedKey} : exercice inconnu ${e.exerciseId}")
                assertEquals(exercise.timed, e.durationSec != null, "${workout.seedKey}/${e.exerciseId}")
                assertTrue(e.sets in 1..10 && e.restSec in 0..300, "${workout.seedKey}/${e.exerciseId}")
            }
            SeedMapper.premadeWorkout(workout, existing = null, now = Instant.EPOCH)
        }
    }

    @Test
    fun `meta declares the Ciqual source and licence`() {
        assertTrue(bundle.meta.nutritionSource.name.startsWith("Ciqual"))
        assertTrue(bundle.meta.nutritionSource.licence.contains("Etalab"))
        assertTrue(bundle.meta.seedHash.length >= 12)
    }

    private fun assertUnique(ids: List<String>, what: String) {
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(duplicates.isEmpty(), "$what en double : $duplicates")
    }
}
