package fr.kalori.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import fr.kalori.app.core.database.entity.ExerciseEntity
import fr.kalori.app.core.database.entity.SessionSetLogEntity
import fr.kalori.app.core.database.entity.WorkoutSessionEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateExerciseEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateWithExercises
import fr.kalori.app.domain.model.WorkoutOrigin
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
abstract class WorkoutDao {
    // ── Exercices ──
    @Query("SELECT * FROM exercise ORDER BY name")
    abstract fun observeExercises(): Flow<List<ExerciseEntity>>

    @Upsert
    abstract suspend fun upsertExercises(exercises: List<ExerciseEntity>)

    // ── Modèles de séance ──
    @Transaction
    @Query("SELECT * FROM workout_template WHERE origin = :origin ORDER BY updatedAt DESC")
    abstract fun observeTemplates(origin: WorkoutOrigin): Flow<List<WorkoutTemplateWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_template WHERE id = :id")
    abstract suspend fun getTemplate(id: Long): WorkoutTemplateWithExercises?

    @Query("SELECT * FROM workout_template WHERE seedKey = :seedKey")
    abstract suspend fun getBySeedKey(seedKey: String): WorkoutTemplateEntity?

    @Insert
    abstract suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Update
    abstract suspend fun updateTemplate(template: WorkoutTemplateEntity)

    @Query("DELETE FROM workout_template WHERE id = :id")
    abstract suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM workout_template WHERE origin = 'PREMADE' AND seedKey NOT IN (:keepKeys)")
    abstract suspend fun deletePremadeNotIn(keepKeys: Collection<String>)

    @Query("DELETE FROM workout_template_exercise WHERE templateId = :templateId")
    abstract suspend fun deleteTemplateExercises(templateId: Long)

    @Insert
    abstract suspend fun insertTemplateExercises(exercises: List<WorkoutTemplateExerciseEntity>)

    /**
     * Crée ([WorkoutTemplateEntity.id] = 0) ou met à jour une séance avec sa liste d'exercices complète.
     * Utilisé par le mode modification (même écran pour la création et l'édition).
     */
    @Transaction
    open suspend fun saveTemplate(
        template: WorkoutTemplateEntity,
        exercises: List<WorkoutTemplateExerciseEntity>,
    ): Long {
        val id = if (template.id == 0L) insertTemplate(template) else template.id.also { updateTemplate(template) }
        deleteTemplateExercises(id)
        insertTemplateExercises(
            exercises.mapIndexed { index, e -> e.copy(id = 0, templateId = id, position = index) },
        )
        return id
    }

    // ── Séances réalisées ──
    @Insert
    abstract suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Insert
    abstract suspend fun insertSetLogs(logs: List<SessionSetLogEntity>)

    @Transaction
    open suspend fun saveSession(session: WorkoutSessionEntity, logs: List<SessionSetLogEntity>): Long {
        val id = insertSession(session)
        insertSetLogs(logs.map { it.copy(sessionId = id) })
        return id
    }

    @Query("SELECT * FROM workout_session WHERE completed = 1 AND startedAt >= :from ORDER BY startedAt")
    abstract fun observeCompletedSessions(from: Instant): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM session_set_log WHERE sessionId = :sessionId ORDER BY id")
    abstract suspend fun getSetLogs(sessionId: Long): List<SessionSetLogEntity>
}
