@file:UseSerializers(InstantSerializer::class)

package fr.kalori.app.data.backup

import androidx.room.withTransaction
import fr.kalori.app.BuildConfig
import fr.kalori.app.core.database.AppDatabase
import fr.kalori.app.core.database.InstantSerializer
import fr.kalori.app.core.database.entity.DailyIntakeEntity
import fr.kalori.app.core.database.entity.MealPlanEntryEntity
import fr.kalori.app.core.database.entity.RecipeEntity
import fr.kalori.app.core.database.entity.RecipeIngredientEntity
import fr.kalori.app.core.database.entity.SessionSetLogEntity
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.core.database.entity.UserRestrictionEntity
import fr.kalori.app.core.database.entity.WeightEntryEntity
import fr.kalori.app.core.database.entity.WorkoutSessionEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateExerciseEntity
import fr.kalori.app.core.datastore.AppPreferences
import fr.kalori.app.core.di.IoDispatcher
import fr.kalori.app.data.seed.SeedImporter
import fr.kalori.app.domain.model.RecipeOrigin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sauvegarde complète des données personnelles (format JSON lisible).
 * Le catalogue embarqué (ingrédients, recettes fournies, exercices, séances pré-faites) n'est pas exporté :
 * il est réinstallé avec l'app. Les recettes générées par IA, elles, sont exportées.
 */
@Serializable
data class KaloriBackup(
    val format: Int,
    val exportedAt: Instant,
    val appVersion: String,
    val profile: UserProfileEntity?,
    val weights: List<WeightEntryEntity>,
    val extraIntakes: List<DailyIntakeEntity>,
    val restrictions: List<UserRestrictionEntity>,
    val aiRecipes: List<RecipeEntity>,
    val aiRecipeIngredients: List<RecipeIngredientEntity>,
    val mealPlan: List<MealPlanEntryEntity>,
    val workoutTemplates: List<WorkoutTemplateEntity>,
    val workoutTemplateExercises: List<WorkoutTemplateExerciseEntity>,
    val sessions: List<WorkoutSessionEntity>,
    val sessionSets: List<SessionSetLogEntity>,
)

data class ImportSummary(val weights: Int, val meals: Int, val workouts: Int, val sessions: Int, val skipped: Int)

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class BackupRepository @Inject constructor(
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val seedImporter: SeedImporter,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(): String = withContext(io) {
        val workouts = database.workoutDao()
        val aiRecipes = database.catalogDao().getRecipesByOrigin(RecipeOrigin.AI)
        val templates = workouts.getUserTemplates()
        json.encodeToString(
            KaloriBackup.serializer(),
            KaloriBackup(
                format = FORMAT,
                exportedAt = Instant.now(clock),
                appVersion = BuildConfig.VERSION_NAME,
                profile = database.profileDao().get(),
                weights = database.trackingDao().getAllWeights(),
                extraIntakes = database.trackingDao().getAllIntakes(),
                restrictions = database.restrictionDao().getAll(),
                aiRecipes = aiRecipes.map { it.recipe },
                aiRecipeIngredients = aiRecipes.flatMap { it.ingredients },
                mealPlan = database.mealPlanDao().getAll(),
                workoutTemplates = templates.map { it.template },
                workoutTemplateExercises = templates.flatMap { it.exercises },
                sessions = workouts.getAllSessions(),
                sessionSets = workouts.getAllSetLogs(),
            ),
        )
    }

    /**
     * Remplace toutes les données personnelles par celles de la sauvegarde.
     * Les éléments qui référencent un contenu absent de ce catalogue (recette ou exercice retiré) sont ignorés.
     */
    suspend fun import(text: String): ImportSummary = withContext(io) {
        val backup = try {
            json.decodeFromString(KaloriBackup.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException("Fichier illisible : ce n'est pas une sauvegarde Kalori.", e)
        }
        if (backup.format > FORMAT) throw BackupFormatException("Sauvegarde créée par une version plus récente de Kalori.")
        seedImporter.importIfNeeded()

        val catalog = database.catalogDao()
        val workouts = database.workoutDao()
        val tracking = database.trackingDao()
        var skipped = 0
        var mealCount = 0

        database.withTransaction {
            clearPersonalData()
            backup.profile?.let { database.profileDao().upsert(it) }
            tracking.insertWeights(backup.weights)
            tracking.upsertIntakes(backup.extraIntakes)
            database.restrictionDao().insertAll(backup.restrictions)

            val ingredientIds = catalog.getIngredients().map { it.id }.toSet()
            val aiLines = backup.aiRecipeIngredients.filter { it.ingredientId in ingredientIds }
            val aiRecipes = backup.aiRecipes.filter { r -> aiLines.any { it.recipeId == r.id } }
            catalog.replaceRecipes(aiRecipes, aiLines.filter { line -> aiRecipes.any { it.id == line.recipeId } })

            val recipeIds = catalog.getRecipeIds().toSet()
            val meals = backup.mealPlan.filter { it.recipeId in recipeIds }
            skipped += backup.mealPlan.size - meals.size
            database.mealPlanDao().upsertAll(meals)
            mealCount = meals.size

            val exerciseIds = workouts.getExerciseIds().toSet()
            val templateIds = backup.workoutTemplates.map { it.id }.toSet()
            backup.workoutTemplates.forEach { workouts.insertTemplate(it) }
            val templateLines = backup.workoutTemplateExercises.filter { it.exerciseId in exerciseIds && it.templateId in templateIds }
            skipped += backup.workoutTemplateExercises.size - templateLines.size
            workouts.insertTemplateExercises(templateLines)

            // Une séance réalisée à partir d'une séance pré-faite garde son historique, sans lien vers le modèle.
            workouts.insertSessions(backup.sessions.map { if (it.templateId in templateIds) it else it.copy(templateId = null) })
            val sessionIds = backup.sessions.map { it.id }.toSet()
            val sets = backup.sessionSets.filter { it.sessionId in sessionIds && it.exerciseId in exerciseIds }
            skipped += backup.sessionSets.size - sets.size
            workouts.insertSetLogs(sets)
        }
        if (backup.profile != null) preferences.setOnboardingDone(true)
        ImportSummary(backup.weights.size, mealCount, backup.workoutTemplates.size, backup.sessions.size, skipped)
    }

    /** Efface toutes les données personnelles et revient au premier lancement. Le catalogue est réinstallé. */
    suspend fun wipeAll() = withContext(io) {
        database.withTransaction { clearPersonalData() }
        preferences.clear()
        seedImporter.importIfNeeded()
    }

    private suspend fun clearPersonalData() {
        database.profileDao().deleteAll()
        database.trackingDao().deleteAllWeights()
        database.trackingDao().deleteAllIntakes()
        database.restrictionDao().deleteAll()
        database.mealPlanDao().deleteAllChecks()
        database.mealPlanDao().deleteAll()
        database.workoutDao().deleteAllSessions()
        database.workoutDao().deleteUserTemplates()
        // Les recettes IA ne sont plus référencées par aucun plan : on les retire du catalogue actif.
        database.catalogDao().deactivateMissing(RecipeOrigin.AI, emptyList())
    }

    private companion object {
        /** 2 : plusieurs repas par jour (créneaux). Les sauvegardes au format 1 restent importables. */
        const val FORMAT = 2
    }
}
