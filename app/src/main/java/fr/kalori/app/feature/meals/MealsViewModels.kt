package fr.kalori.app.feature.meals

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.kalori.app.R
import fr.kalori.app.core.database.entity.IngredientEntity
import fr.kalori.app.core.datastore.AppPreferences
import fr.kalori.app.core.security.ApiKeyStore
import fr.kalori.app.data.ai.AiMealService
import fr.kalori.app.data.ai.VariantResult
import fr.kalori.app.data.meal.FoodPreferencesRepository
import fr.kalori.app.data.meal.MealDetail
import fr.kalori.app.data.meal.MealPlanRepository
import fr.kalori.app.data.meal.PlannedDay
import fr.kalori.app.data.meal.ReplaceResult
import fr.kalori.app.data.meal.ShoppingList
import fr.kalori.app.data.profile.ProfileRepository
import fr.kalori.app.domain.meal.FoodPreferences
import fr.kalori.app.domain.meal.ShoppingListBuilder
import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.MealStatus
import fr.kalori.app.navigation.MealDetailRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private val sharing = SharingStarted.WhileSubscribed(5_000)

// ── Liste des 7 prochains jours ──

data class MealsUiState(
    val loading: Boolean = true,
    val hasProfile: Boolean = false,
    val planning: Boolean = false,
    val today: LocalDate,
    val dates: List<LocalDate> = emptyList(),
    val days: Map<LocalDate, PlannedDay> = emptyMap(),
)

@HiltViewModel
class MealsViewModel @Inject constructor(
    private val repository: MealPlanRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val planning = MutableStateFlow(false)
    private val messageChannel = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    val state: StateFlow<MealsUiState> = combine(
        profileRepository.observeMealTarget(),
        repository.observeUpcoming(),
        planning,
    ) { target, days, isPlanning ->
        val today = repository.today()
        MealsUiState(
            loading = false,
            hasProfile = target != null,
            planning = isPlanning,
            today = today,
            dates = (0L..6L).map { today.plusDays(it) },
            days = days.associateBy { it.date },
        )
    }.stateIn(viewModelScope, sharing, MealsUiState(today = repository.today()))

    init {
        viewModelScope.launch {
            // Dès que le profil existe (ou change), on complète les jours manquants.
            profileRepository.observeMealTarget().filterNotNull().distinctUntilChanged().collect {
                planning.value = true
                try {
                    repository.ensureUpcomingPlanned()
                } finally {
                    planning.value = false
                }
            }
        }
    }

    fun replace(date: LocalDate) = viewModelScope.launch {
        repository.replace(date).messageRes()?.let { messageChannel.send(it) }
    }

    fun toggleEaten(day: PlannedDay) = viewModelScope.launch {
        repository.setStatus(day.date, if (day.status == MealStatus.EATEN) MealStatus.PLANNED else MealStatus.EATEN)
    }
}

// ── Détail d'un plat ──

@HiltViewModel
class MealDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MealPlanRepository,
    private val aiMealService: AiMealService,
    preferences: AppPreferences,
    private val apiKeyStore: ApiKeyStore,
) : ViewModel() {
    val date: LocalDate = LocalDate.ofEpochDay(savedStateHandle.toRoute<MealDetailRoute>().epochDay)
    val today: LocalDate get() = repository.today()

    private val messageChannel = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    val detail: StateFlow<MealDetail?> = repository.observeDetail(date).stateIn(viewModelScope, sharing, null)

    /** Les jours passés ne sont plus modifiables. */
    val editable: Boolean get() = !date.isBefore(repository.today())

    fun replace() = viewModelScope.launch {
        repository.replace(date).messageRes()?.let { messageChannel.send(it) }
    }

    // ── Variante par IA (option) ──

    private val _aiRunning = MutableStateFlow(false)
    val aiRunning: StateFlow<Boolean> = _aiRunning.asStateFlow()

    /** Bouton visible seulement si l'option est activée et qu'une clé est enregistrée. */
    val aiAvailable: StateFlow<Boolean> = preferences.aiEnabled
        .map { it && apiKeyStore.hasKey() }
        .stateIn(viewModelScope, sharing, false)

    private val aiMessageChannel = Channel<String>(Channel.BUFFERED)
    val aiMessages: Flow<String> = aiMessageChannel.receiveAsFlow()

    fun proposeAiVariant() {
        if (_aiRunning.value) return
        _aiRunning.value = true
        viewModelScope.launch {
            try {
                val message = when (val result = aiMealService.proposeVariant(date)) {
                    is VariantResult.Applied -> "Nouveau plat proposé par l'IA : ${result.name}"
                    is VariantResult.Failed -> result.message
                }
                aiMessageChannel.send(message)
            } finally {
                _aiRunning.value = false
            }
        }
    }
}

// ── Historique ──

@HiltViewModel
class MealHistoryViewModel @Inject constructor(
    private val repository: MealPlanRepository,
) : ViewModel() {
    val today: LocalDate get() = repository.today()
    val history: StateFlow<List<PlannedDay>?> = repository.observeHistory().stateIn(viewModelScope, sharing, null)
}

// ── Goûts et restrictions ──

data class FoodPreferencesUiState(
    val loaded: Boolean = false,
    val prefs: FoodPreferences = FoodPreferences(),
    val query: String = "",
    val ingredients: List<IngredientEntity> = emptyList(),
    val saving: Boolean = false,
)

@HiltViewModel
class FoodPreferencesViewModel @Inject constructor(
    private val repository: FoodPreferencesRepository,
) : ViewModel() {

    private val draft = MutableStateFlow(FoodPreferencesUiState())
    val state: StateFlow<FoodPreferencesUiState> = combine(draft, repository.observeIngredients()) { d, ingredients ->
        d.copy(ingredients = ingredients)
    }.stateIn(viewModelScope, sharing, FoodPreferencesUiState())

    private val savedChannel = Channel<Unit>(Channel.CONFLATED)
    /** Émis une fois l'enregistrement terminé (et les plats recalculés). */
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val current = repository.get()
            draft.update { it.copy(loaded = true, prefs = current) }
        }
    }

    fun toggleAllergen(allergen: Allergen) = editPrefs { it.copy(allergens = it.allergens.toggle(allergen)) }

    fun toggleDiet(diet: Diet) = editPrefs { it.copy(diets = it.diets.toggle(diet)) }

    /** J'aime / Je n'aime pas sont exclusifs ; un second appui retire le choix. */
    fun toggleLike(ingredientId: String) = editPrefs {
        it.copy(liked = it.liked.toggle(ingredientId), disliked = it.disliked - ingredientId)
    }

    fun toggleDislike(ingredientId: String) = editPrefs {
        it.copy(disliked = it.disliked.toggle(ingredientId), liked = it.liked - ingredientId)
    }

    fun setQuery(query: String) = draft.update { it.copy(query = query) }

    fun save() {
        if (draft.value.saving) return
        draft.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repository.save(draft.value.prefs)
                savedChannel.send(Unit)
            } finally {
                draft.update { it.copy(saving = false) }
            }
        }
    }

    private fun editPrefs(change: (FoodPreferences) -> FoodPreferences) = draft.update { it.copy(prefs = change(it.prefs)) }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
}

// ── Liste de courses ──

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: MealPlanRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val list: StateFlow<ShoppingList?> = repository.observeShoppingList().stateIn(viewModelScope, sharing, null)

    private val messageChannel = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    fun setChecked(ingredientId: String, checked: Boolean) = viewModelScope.launch {
        repository.setChecked(ingredientId, checked)
    }

    /** Copie les articles non cochés (tous si tout est coché) en texte brut, une ligne par article. */
    fun copy() {
        val current = list.value ?: return
        val items = current.sections.flatMap { it.items }
        val remaining = items.filter { it.ingredientId !in current.checked }.ifEmpty { items }
        if (remaining.isEmpty()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Liste de courses", ShoppingListBuilder.toPlainText(remaining)))
        viewModelScope.launch { messageChannel.send(R.string.shopping_copied) }
    }
}

@StringRes
fun ReplaceResult.messageRes(): Int? = when (this) {
    ReplaceResult.REPLACED -> R.string.meals_replaced
    ReplaceResult.NO_ALTERNATIVE -> R.string.meals_no_alternative
    ReplaceResult.NOT_PLANNED -> null
}
