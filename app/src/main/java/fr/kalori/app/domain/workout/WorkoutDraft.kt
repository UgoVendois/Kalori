package fr.kalori.app.domain.workout

/** Un exercice dans une séance en cours d'édition. [key] identifie la ligne (un exercice peut apparaître deux fois). */
data class DraftExercise(
    val key: Long,
    val exerciseId: String,
    /** true : mesuré en durée (gainage, cardio) plutôt qu'en répétitions. */
    val timed: Boolean,
    val sets: Int,
    val reps: Int?,
    val durationSec: Int?,
    val loadKg: Double?,
    val restSec: Int,
) {
    /** Ramène chaque valeur dans des bornes raisonnables. */
    fun clamped(): DraftExercise = copy(
        sets = sets.coerceIn(WorkoutDraft.SETS),
        reps = if (timed) null else (reps ?: WorkoutDraft.DEFAULT_REPS).coerceIn(WorkoutDraft.REPS),
        durationSec = if (timed) (durationSec ?: WorkoutDraft.DEFAULT_DURATION_SEC).coerceIn(WorkoutDraft.DURATION_SEC) else null,
        loadKg = loadKg?.takeIf { it > 0 }?.coerceAtMost(WorkoutDraft.MAX_LOAD_KG),
        restSec = restSec.coerceIn(WorkoutDraft.REST_SEC),
    )
}

/**
 * Séance en cours de création ou de modification (même modèle pour les deux cas).
 * Toutes les opérations renvoient une nouvelle instance.
 */
data class WorkoutDraft(
    val name: String = "",
    val exercises: List<DraftExercise> = emptyList(),
) {
    val isValid: Boolean get() = name.isNotBlank() && exercises.isNotEmpty()

    fun rename(newName: String) = copy(name = newName.take(MAX_NAME_LENGTH))

    fun add(exerciseId: String, timed: Boolean): WorkoutDraft {
        val key = (exercises.maxOfOrNull { it.key } ?: 0L) + 1
        val line = DraftExercise(
            key = key,
            exerciseId = exerciseId,
            timed = timed,
            sets = DEFAULT_SETS,
            reps = if (timed) null else DEFAULT_REPS,
            durationSec = if (timed) DEFAULT_DURATION_SEC else null,
            loadKg = null,
            restSec = if (timed) DEFAULT_TIMED_REST_SEC else DEFAULT_REST_SEC,
        )
        return copy(exercises = exercises + line)
    }

    fun remove(key: Long) = copy(exercises = exercises.filterNot { it.key == key })

    /** Déplace une ligne de [delta] positions (−1 = monter, +1 = descendre), sans sortir de la liste. */
    fun move(key: Long, delta: Int): WorkoutDraft {
        val from = exercises.indexOfFirst { it.key == key }
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, exercises.lastIndex)
        if (to == from) return this
        val list = exercises.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(exercises = list)
    }

    fun update(key: Long, change: (DraftExercise) -> DraftExercise) =
        copy(exercises = exercises.map { if (it.key == key) change(it).clamped() else it })

    companion object {
        val SETS = 1..10
        val REPS = 1..100
        val DURATION_SEC = 5..3600
        val REST_SEC = 0..600
        const val MAX_LOAD_KG = 500.0
        const val MAX_NAME_LENGTH = 60
        const val DEFAULT_SETS = 3
        const val DEFAULT_REPS = 10
        const val DEFAULT_DURATION_SEC = 30
        const val DEFAULT_REST_SEC = 90
        const val DEFAULT_TIMED_REST_SEC = 60
    }
}
