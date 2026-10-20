package fr.kalori.app.data.workout

import fr.kalori.app.core.database.dao.WorkoutDao
import fr.kalori.app.core.database.entity.ExerciseEntity
import fr.kalori.app.core.database.entity.SessionSetLogEntity
import fr.kalori.app.core.database.entity.WorkoutSessionEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateExerciseEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateWithExercises
import fr.kalori.app.core.di.IoDispatcher
import fr.kalori.app.data.profile.ProfileRepository
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.WorkoutGoal
import fr.kalori.app.domain.model.WorkoutOrigin
import fr.kalori.app.domain.workout.DraftExercise
import fr.kalori.app.domain.workout.SessionSet
import fr.kalori.app.domain.workout.WorkoutDraft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val profileRepository: ProfileRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    fun observeExercises(): Flow<List<ExerciseEntity>> = workoutDao.observeExercises()

    fun observeMine(): Flow<List<WorkoutTemplateWithExercises>> = workoutDao.observeTemplates(WorkoutOrigin.USER)

    fun observePremade(): Flow<List<WorkoutTemplateWithExercises>> = workoutDao.observeTemplates(WorkoutOrigin.PREMADE)

    /** Objectif sportif déduit de l'objectif de poids (pour recommander des séances pré-faites). */
    fun observeGoal(): Flow<WorkoutGoal?> = profileRepository.observeProfile().map { profile ->
        when (profile?.goalType) {
            GoalType.LOSE -> WorkoutGoal.LOSS
            GoalType.GAIN -> WorkoutGoal.GAIN
            GoalType.MAINTAIN -> WorkoutGoal.MAINTAIN
            null -> null
        }
    }

    suspend fun getTemplate(id: Long): WorkoutTemplateWithExercises? = withContext(io) { workoutDao.getTemplate(id) }

    /** « Ajouter à mes séances » : copie personnelle, la séance pré-faite reste intacte. */
    suspend fun copyPremade(templateId: Long): Long? = withContext(io) {
        val source = workoutDao.getTemplate(templateId) ?: return@withContext null
        val now = Instant.now(clock)
        workoutDao.saveTemplate(
            source.template.copy(
                id = 0,
                seedKey = null,
                origin = WorkoutOrigin.USER,
                copiedFromId = source.template.id,
                createdAt = now,
                updatedAt = now,
            ),
            source.orderedExercises,
        )
    }

    /** Crée ([templateId] = 0) ou met à jour une séance personnelle. */
    suspend fun save(templateId: Long, draft: WorkoutDraft, exercises: Map<String, ExerciseEntity>): Long = withContext(io) {
        require(draft.isValid) { "Séance incomplète" }
        val existing = if (templateId == 0L) null else workoutDao.getTemplate(templateId)?.template
        require(existing == null || existing.origin == WorkoutOrigin.USER) { "Une séance pré-faite ne se modifie pas" }
        val categories = draft.exercises.mapNotNull { exercises[it.exerciseId]?.category }.toSet()
        val now = Instant.now(clock)
        val template = WorkoutTemplateEntity(
            id = existing?.id ?: 0,
            seedKey = null,
            name = draft.name.trim(),
            description = existing?.description,
            category = categories.singleOrNull(),
            origin = WorkoutOrigin.USER,
            goal = existing?.goal ?: WorkoutGoal.ANY,
            copiedFromId = existing?.copiedFromId,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        workoutDao.saveTemplate(
            template,
            draft.exercises.map {
                WorkoutTemplateExerciseEntity(
                    templateId = template.id,
                    exerciseId = it.exerciseId,
                    position = 0,
                    sets = it.sets,
                    reps = it.reps,
                    durationSec = it.durationSec,
                    loadKg = it.loadKg,
                    restSec = it.restSec,
                )
            },
        )
    }

    suspend fun delete(templateId: Long) = withContext(io) {
        val template = workoutDao.getTemplate(templateId)?.template ?: return@withContext
        require(template.origin == WorkoutOrigin.USER) { "Une séance pré-faite ne se supprime pas" }
        workoutDao.deleteTemplate(templateId)
    }

    /** Enregistre une séance terminée : elle alimente le tableau de bord Suivi. */
    suspend fun saveSession(templateId: Long?, name: String, startedAt: Instant, sets: List<SessionSet>): Long =
        withContext(io) {
            workoutDao.saveSession(
                WorkoutSessionEntity(
                    templateId = templateId,
                    nameSnapshot = name,
                    startedAt = startedAt,
                    endedAt = Instant.now(clock),
                    completed = true,
                ),
                sets.map {
                    SessionSetLogEntity(
                        sessionId = 0,
                        exerciseId = it.exerciseId,
                        setIndex = it.setIndex,
                        reps = it.reps,
                        durationSec = it.durationSec,
                        loadKg = it.loadKg,
                    )
                },
            )
        }
}

/** Conversion d'une séance enregistrée vers le modèle d'édition. */
fun WorkoutTemplateWithExercises.toDraft(exercises: Map<String, ExerciseEntity>): WorkoutDraft = WorkoutDraft(
    name = template.name,
    exercises = orderedExercises.mapIndexed { index, e ->
        DraftExercise(
            key = index + 1L,
            exerciseId = e.exerciseId,
            timed = exercises[e.exerciseId]?.timed ?: (e.durationSec != null),
            sets = e.sets,
            reps = e.reps,
            durationSec = e.durationSec,
            loadKg = e.loadKg,
            restSec = e.restSec,
        )
    },
)
