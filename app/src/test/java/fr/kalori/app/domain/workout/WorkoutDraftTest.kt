package fr.kalori.app.domain.workout

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutDraftTest {

    private val draft = WorkoutDraft()
        .rename("Haut du corps")
        .add("pompes", timed = false)
        .add("gainage", timed = true)
        .add("tractions", timed = false)

    @Test
    fun `new exercises get sensible defaults depending on their type`() {
        val pompes = draft.exercises[0]
        assertEquals(3, pompes.sets)
        assertEquals(10, pompes.reps)
        assertNull(pompes.durationSec)
        val gainage = draft.exercises[1]
        assertNull(gainage.reps)
        assertEquals(30, gainage.durationSec)
        assertEquals(listOf(1L, 2L, 3L), draft.exercises.map { it.key })
    }

    @Test
    fun `move reorders within bounds`() {
        assertEquals(listOf("gainage", "pompes", "tractions"), draft.move(2, -1).exercises.map { it.exerciseId })
        assertEquals(listOf("pompes", "tractions", "gainage"), draft.move(2, +1).exercises.map { it.exerciseId })
        assertEquals(draft, draft.move(1, -1), "la première ligne ne peut pas monter")
        assertEquals(draft, draft.move(3, +1), "la dernière ligne ne peut pas descendre")
        assertEquals(draft, draft.move(99, 1), "clé inconnue")
    }

    @Test
    fun `remove deletes only the targeted line`() {
        val twice = draft.add("pompes", timed = false)
        val removed = twice.remove(1)
        assertEquals(listOf("gainage", "tractions", "pompes"), removed.exercises.map { it.exerciseId })
    }

    @Test
    fun `updates are clamped`() {
        val updated = draft.update(1) { it.copy(sets = 50, reps = 0, loadKg = -5.0, restSec = 9999) }.exercises[0]
        assertEquals(10, updated.sets)
        assertEquals(1, updated.reps)
        assertNull(updated.loadKg)
        assertEquals(600, updated.restSec)
        val timed = draft.update(2) { it.copy(durationSec = 1, reps = 12) }.exercises[1]
        assertEquals(5, timed.durationSec)
        assertNull(timed.reps)
    }

    @Test
    fun `a draft needs a name and at least one exercise`() {
        assertTrue(draft.isValid)
        assertFalse(draft.rename("  ").isValid)
        assertFalse(WorkoutDraft(name = "Vide").isValid)
    }

    @Test
    fun `session progress expands sets and tracks completion`() {
        val progress = SessionProgress.from(draft.exercises)
        assertEquals(9, progress.total)
        val (afterFirst, rest) = progress.toggle(0)
        assertEquals(1, afterFirst.doneCount)
        assertEquals(90, rest)
        val (undone, noRest) = afterFirst.toggle(0)
        assertEquals(0, undone.doneCount)
        assertNull(noRest)
        val adjusted = afterFirst.adjust(0, reps = 12, loadKg = 20.0)
        assertEquals(12, adjusted.completedSets().single().reps)
        assertEquals(20.0, adjusted.completedSets().single().loadKg)
        // Une série en durée ne prend pas de répétitions.
        val timedIndex = progress.sets.indexOfFirst { it.exerciseId == "gainage" }
        assertNull(progress.adjust(timedIndex, reps = 15, loadKg = null).sets[timedIndex].reps)
    }
}
