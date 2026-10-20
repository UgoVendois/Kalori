package fr.kalori.app.data.seed

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.kalori.app.core.database.AppDatabase
import fr.kalori.app.core.datastore.AppPreferences
import fr.kalori.app.core.di.IoDispatcher
import fr.kalori.app.core.logging.SafeLogger
import fr.kalori.app.domain.model.RecipeOrigin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Importe le catalogue embarqué (ingrédients, recettes, exercices, séances pré-faites) dans la base.
 *
 * L'import n'est rejoué que si l'empreinte (meta.json → seedHash) change, c'est-à-dire après une
 * mise à jour de l'app contenant de nouvelles données. Les données de l'utilisateur ne sont jamais
 * touchées : les recettes retirées sont désactivées (pas supprimées) pour garder l'historique.
 */
@Singleton
class SeedImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val _ready = MutableStateFlow(false)

    /** true une fois le catalogue disponible en base (les écrans Repas et Séance l'attendent). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    suspend fun importIfNeeded() = mutex.withLock {
        withContext(ioDispatcher) {
            val meta = SeedMapper.parseMeta(readAsset("meta.json"))
            if (preferences.seedHash() != meta.seedHash) {
                val bundle = SeedMapper.parse(::readAsset)
                importBundle(bundle)
                preferences.setSeedHash(meta.seedHash)
                SafeLogger.d(TAG) { "seed imported: ${bundle.recipes.size} recipes" }
            }
            _ready.value = true
        }
    }

    private suspend fun importBundle(bundle: SeedBundle) {
        val now = Instant.now()
        val ingredients = bundle.ingredients.map(SeedMapper::ingredient)
        val facts = ingredients.associate { it.id to SeedMapper.facts(it) }
        val recipes = bundle.recipes.map { SeedMapper.recipe(it, facts, now) }
        val catalog = database.catalogDao()
        val workouts = database.workoutDao()

        database.withTransaction {
            catalog.upsertIngredients(ingredients)
            catalog.replaceRecipes(recipes.map { it.first }, recipes.flatMap { it.second })
            catalog.deactivateMissing(RecipeOrigin.BUNDLED, recipes.map { it.first.id })

            workouts.upsertExercises(bundle.exercises.map(SeedMapper::exercise))
            for (seed in bundle.workouts) {
                val (template, exercises) = SeedMapper.premadeWorkout(seed, workouts.getBySeedKey(seed.seedKey), now)
                workouts.saveTemplate(template, exercises)
            }
            workouts.deletePremadeNotIn(bundle.workouts.map { it.seedKey })
        }
    }

    private fun readAsset(name: String): String =
        context.assets.open("$SEED_DIR/$name").bufferedReader().use { it.readText() }

    private companion object {
        const val TAG = "SeedImporter"
        const val SEED_DIR = "seed"
    }
}
