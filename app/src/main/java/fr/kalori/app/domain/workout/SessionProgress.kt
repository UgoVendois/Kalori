package fr.kalori.app.domain.workout

/** Une série à réaliser pendant une séance. */
data class SessionSet(
    val exerciseId: String,
    /** Position de l'exercice dans la séance (un exercice peut apparaître deux fois). */
    val exerciseIndex: Int,
    val setIndex: Int,
    val reps: Int?,
    val durationSec: Int?,
    val loadKg: Double?,
    val restSec: Int,
    val done: Boolean = false,
)

/** Progression d'une séance en cours : quelles séries sont faites. */
data class SessionProgress(val sets: List<SessionSet>) {

    val doneCount: Int get() = sets.count { it.done }
    val total: Int get() = sets.size
    val isStarted: Boolean get() = doneCount > 0
    val isComplete: Boolean get() = total > 0 && doneCount == total

    /** Coche ou décoche une série. Renvoie aussi le repos à lancer (null si la série est décochée). */
    fun toggle(index: Int): Pair<SessionProgress, Int?> {
        val set = sets.getOrNull(index) ?: return this to null
        val updated = set.copy(done = !set.done)
        val progress = copy(sets = sets.toMutableList().also { it[index] = updated })
        return progress to updated.restSec.takeIf { updated.done && it > 0 }
    }

    /** Ajuste les répétitions ou la charge réellement réalisées sur une série. */
    fun adjust(index: Int, reps: Int?, loadKg: Double?): SessionProgress {
        val set = sets.getOrNull(index) ?: return this
        val adjusted = set.copy(
            reps = if (set.reps != null) reps?.coerceIn(WorkoutDraft.REPS) ?: set.reps else null,
            loadKg = loadKg?.takeIf { it > 0 }?.coerceAtMost(WorkoutDraft.MAX_LOAD_KG),
        )
        return copy(sets = sets.toMutableList().also { it[index] = adjusted })
    }

    /** Séries effectivement réalisées, à enregistrer. */
    fun completedSets(): List<SessionSet> = sets.filter { it.done }

    companion object {
        fun from(exercises: List<DraftExercise>): SessionProgress = SessionProgress(
            exercises.flatMapIndexed { exerciseIndex, e ->
                (0 until e.sets).map { setIndex ->
                    SessionSet(
                        exerciseId = e.exerciseId,
                        exerciseIndex = exerciseIndex,
                        setIndex = setIndex,
                        reps = e.reps,
                        durationSec = e.durationSec,
                        loadKg = e.loadKg,
                        restSec = e.restSec,
                    )
                }
            },
        )
    }
}
