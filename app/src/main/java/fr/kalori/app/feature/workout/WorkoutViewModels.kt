package fr.kalori.app.feature.workout

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.kalori.app.core.database.entity.ExerciseEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateWithExercises
import fr.kalori.app.data.workout.WorkoutRepository
import fr.kalori.app.data.workout.toDraft
import fr.kalori.app.domain.model.ExerciseCategory
import fr.kalori.app.domain.model.WorkoutGoal
import fr.kalori.app.domain.workout.DraftExercise
import fr.kalori.app.domain.workout.SessionProgress
import fr.kalori.app.domain.workout.WorkoutDraft
import fr.kalori.app.navigation.WorkoutEditorRoute
import fr.kalori.app.navigation.WorkoutPlayerRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private val sharing = SharingStarted.WhileSubscribed(5_000)

enum class CategoryFilter(val label: String, val category: ExerciseCategory?) {
    ALL("Toutes", null),
    GYM("Salle", ExerciseCategory.GYM),
    STREET("Street", ExerciseCategory.STREET),
}

/** Une séance pré-faite, avec l'indication « adaptée à ton objectif ». */
data class PremadeItem(val template: WorkoutTemplateWithExercises, val recommended: Boolean)

data class WorkoutHomeState(
    val loaded: Boolean = false,
    val filter: CategoryFilter = CategoryFilter.ALL,
    val mine: List<WorkoutTemplateWithExercises> = emptyList(),
    val premade: List<PremadeItem> = emptyList(),
    val exercises: Map<String, ExerciseEntity> = emptyMap(),
)

// ── Accueil de l'onglet ──

@HiltViewModel
class WorkoutHomeViewModel @Inject constructor(
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(CategoryFilter.ALL)
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    val state: StateFlow<WorkoutHomeState> = combine(
        repository.observeMine(),
        repository.observePremade(),
        repository.observeExercises(),
        repository.observeGoal(),
        filter,
    ) { mine, premade, exercises, goal, f ->
        fun matches(t: WorkoutTemplateWithExercises): Boolean {
            val category = f.category ?: return true
            // Une séance mixte (catégorie nulle) apparaît dans les deux filtres.
            return t.template.category == null || t.template.category == category
        }
        WorkoutHomeState(
            loaded = true,
            filter = f,
            mine = mine.filter(::matches),
            premade = premade.filter(::matches)
                .map { PremadeItem(it, recommended = goal != null && (it.template.goal == goal || it.template.goal == WorkoutGoal.ANY)) }
                .sortedWith(compareByDescending<PremadeItem> { it.recommended }.thenBy { it.template.template.name }),
            exercises = exercises.associateBy { it.id },
        )
    }.stateIn(viewModelScope, sharing, WorkoutHomeState())

    fun setFilter(value: CategoryFilter) {
        filter.value = value
    }

    fun addToMine(templateId: Long) = viewModelScope.launch {
        repository.copyPremade(templateId)?.let { messageChannel.send("Séance ajoutée à « Mes séances »") }
    }
}

// ── Mode modification (création ET modification) ──

data class WorkoutEditorState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val draft: WorkoutDraft = WorkoutDraft(),
    val exercises: Map<String, ExerciseEntity> = emptyMap(),
    val showPicker: Boolean = false,
    val saving: Boolean = false,
)

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object Deleted : EditorEvent
}

@HiltViewModel
class WorkoutEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val templateId: Long = savedStateHandle.toRoute<WorkoutEditorRoute>().templateId
    private val _state = MutableStateFlow(WorkoutEditorState(isNew = templateId == 0L))
    val state: StateFlow<WorkoutEditorState> = _state.asStateFlow()

    private val eventChannel = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val exercises = repository.observeExercises().first().associateBy { it.id }
            val draft = if (templateId == 0L) {
                WorkoutDraft()
            } else {
                repository.getTemplate(templateId)?.toDraft(exercises) ?: WorkoutDraft()
            }
            _state.update { it.copy(loaded = true, draft = draft, exercises = exercises) }
        }
    }

    fun rename(name: String) = editDraft { it.rename(name) }

    fun showPicker(show: Boolean) = _state.update { it.copy(showPicker = show) }

    fun add(exercise: ExerciseEntity) {
        editDraft { it.add(exercise.id, exercise.timed) }
        showPicker(false)
    }

    fun remove(key: Long) = editDraft { it.remove(key) }

    fun move(key: Long, delta: Int) = editDraft { it.move(key, delta) }

    fun update(key: Long, change: (DraftExercise) -> DraftExercise) = editDraft { it.update(key, change) }

    fun save() {
        val s = _state.value
        if (!s.draft.isValid || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repository.save(templateId, s.draft, s.exercises)
                eventChannel.send(EditorEvent.Saved)
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun delete() {
        if (templateId == 0L) return
        viewModelScope.launch {
            repository.delete(templateId)
            eventChannel.send(EditorEvent.Deleted)
        }
    }

    private fun editDraft(change: (WorkoutDraft) -> WorkoutDraft) = _state.update { it.copy(draft = change(it.draft)) }
}

// ── Séance en cours ──

data class WorkoutPlayerState(
    val loaded: Boolean = false,
    val name: String = "",
    val progress: SessionProgress = SessionProgress(emptyList()),
    val exercises: Map<String, ExerciseEntity> = emptyMap(),
    /** Secondes de repos restantes, null hors repos. */
    val restRemainingSec: Int? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class WorkoutPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val templateId: Long = savedStateHandle.toRoute<WorkoutPlayerRoute>().templateId
    private val startedAt: Instant = Instant.now()
    private val _state = MutableStateFlow(WorkoutPlayerState())
    val state: StateFlow<WorkoutPlayerState> = _state.asStateFlow()

    private val finishedChannel = Channel<Unit>(Channel.CONFLATED)
    val finished: Flow<Unit> = finishedChannel.receiveAsFlow()

    private var restJob: Job? = null

    init {
        viewModelScope.launch {
            val exercises = repository.observeExercises().first().associateBy { it.id }
            val template = repository.getTemplate(templateId)
            val draft = template?.toDraft(exercises) ?: WorkoutDraft()
            _state.update {
                it.copy(
                    loaded = true,
                    name = draft.name,
                    progress = SessionProgress.from(draft.exercises),
                    exercises = exercises,
                )
            }
        }
    }

    fun toggle(index: Int) {
        val (progress, rest) = _state.value.progress.toggle(index)
        _state.update { it.copy(progress = progress) }
        if (rest != null) startRest(rest) else stopRest()
    }

    fun adjust(index: Int, reps: Int?, loadKg: Double?) =
        _state.update { it.copy(progress = it.progress.adjust(index, reps, loadKg)) }

    fun skipRest() = stopRest()

    /** Enregistre les séries réalisées. Sans aucune série cochée, rien n'est enregistré. */
    fun finish() {
        val s = _state.value
        if (s.saving) return
        if (!s.progress.isStarted) {
            finishedChannel.trySend(Unit)
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.saveSession(templateId, s.name, startedAt, s.progress.completedSets())
            finishedChannel.send(Unit)
        }
    }

    private fun startRest(seconds: Int) {
        restJob?.cancel()
        val endsAt = SystemClock.elapsedRealtime() + seconds * 1000L
        restJob = viewModelScope.launch {
            while (true) {
                val remaining = ((endsAt - SystemClock.elapsedRealtime()) / 1000.0).let { kotlin.math.ceil(it).toInt() }
                if (remaining <= 0) break
                _state.update { it.copy(restRemainingSec = remaining) }
                delay(250)
            }
            _state.update { it.copy(restRemainingSec = null) }
        }
    }

    private fun stopRest() {
        restJob?.cancel()
        _state.update { it.copy(restRemainingSec = null) }
    }
}

// ── Bibliothèque d'exercices ──

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    repository: WorkoutRepository,
) : ViewModel() {
    val exercises: StateFlow<List<ExerciseEntity>?> = repository.observeExercises().stateIn(viewModelScope, sharing, null)
}
