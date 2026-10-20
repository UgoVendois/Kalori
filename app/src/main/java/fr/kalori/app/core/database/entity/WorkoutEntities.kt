package fr.kalori.app.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import fr.kalori.app.domain.model.ExerciseCategory
import fr.kalori.app.domain.model.ExerciseIcon
import fr.kalori.app.domain.model.MuscleGroup
import fr.kalori.app.domain.model.WorkoutGoal
import fr.kalori.app.domain.model.WorkoutOrigin
import java.time.Instant

@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: ExerciseCategory,
    val muscleGroup: MuscleGroup,
    val icon: ExerciseIcon,
    val bodyweight: Boolean,
    /** true : l'exercice se mesure en durée (gainage, cardio) plutôt qu'en répétitions. */
    val timed: Boolean,
    val instructions: String,
)

/**
 * Séance : pré-faite ([WorkoutOrigin.PREMADE], identifiée par [seedKey], jamais modifiée par l'utilisateur)
 * ou personnelle ([WorkoutOrigin.USER], créée de zéro ou copiée d'une séance pré-faite).
 */
@Entity(
    tableName = "workout_template",
    indices = [Index(value = ["seedKey"], unique = true), Index("origin")],
)
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seedKey: String?,
    val name: String,
    val description: String?,
    val category: ExerciseCategory?,
    val origin: WorkoutOrigin,
    val goal: WorkoutGoal,
    val copiedFromId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "workout_template_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class WorkoutTemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val reps: Int?,
    val durationSec: Int?,
    val loadKg: Double?,
    val restSec: Int,
)

data class WorkoutTemplateWithExercises(
    @Embedded val template: WorkoutTemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val exercises: List<WorkoutTemplateExerciseEntity>,
) {
    val orderedExercises: List<WorkoutTemplateExerciseEntity> get() = exercises.sortedBy { it.position }
}

/** Séance réalisée. Le nom est copié pour que l'historique survive à la suppression du modèle. */
@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("templateId"), Index("startedAt")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    val nameSnapshot: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val completed: Boolean,
)

@Entity(
    tableName = "session_set_log",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionSetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val setIndex: Int,
    val reps: Int?,
    val durationSec: Int?,
    val loadKg: Double?,
)
