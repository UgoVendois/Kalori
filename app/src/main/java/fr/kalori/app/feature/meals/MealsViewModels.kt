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
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.core.datastore.AppPreferences
import fr.kalori.app.core.di.ApplicationScope
import fr.kalori.app.core.security.ApiKeyStore
import fr.kalori.app.data.ai.AiMealService
import fr.kalori.app.data.ai.VariantResult
import fr.kalori.app.data.meal.DayMeal
import fr.kalori.app.data.meal.FoodPreferencesRepository
import fr.kalori.app.data.meal.MealDetail
import fr.kalori.app.data.meal.MealPlanRepository
import fr.kalori.app.data.meal.ReplaceResult
import fr.kalori.app.data.meal.ShoppingList
import fr.kalori.app.data.profile.GoalService
import fr.kalori.app.data.profile.Organization
import fr.kalori.app.data.profile.organization
import fr.kalori.app.data.profile.ProfileRepository
import fr.kalori.app.data.profile.enabledMains
import fr.kalori.app.data.profile.slotTargets
import fr.kalori.app.domain.meal.FoodPreferences
import fr.kalori.app.domain.meal.ShoppingListBuilder
import fr.kalori.app.domain.model.Aisle
import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.CookingLevel
import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.MealStatus
import fr.kalori.app.domain.model.SnackMode
import fr.kalori.app.domain.nutrition.NutritionTargets
import fr.kalori.app.navigation.MealDetailRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private val sharing = SharingStarted.WhileSubscribed(5_000)

@StringRes
fun ReplaceResult.messageRes(): Int? = when (this) {
    ReplaceResult.REPLACED -> R.string.meals_replaced
    ReplaceResult.NO_ALTERNATIVE -> R.string.meals_no_alternative
    ReplaceResult.NOT_PLANNED -> null
}

// ── Les repas de la semaine ──

data class MealsUiState(
    val loading: Boolean = true,
    val hasProfile: Boolean = false,
    val planning: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val dates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val meals: Map<LocalDate, List<DayMeal>> = emptyMap(),
    /** Créneaux actifs et leur cible, dans l'ordre de la journée. */
    val slotTargets: Map<MealSlot, NutritionTargets> = emptyMap(),
    val dailyKcal: Int = 0,
    val weeklyBudget: Int? = null,
) {
    val selectedMeals: List<DayMeal> get() = meals[selectedDate].orEmpty()

    /** Courses estimées des 7 jours affichés (restes et fournées comptés le jour où l'on cuisine). */
    val weekCost: Double get() = meals.values.flatten().sumOf { it.cookCost }
}

@HiltViewModel
class MealsViewModel @Inject constructor(
    private val repository: MealPlanRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val planning = MutableStateFlow(false)
    private val selectedDate = MutableStateFlow(repository.today())
    private val messageChannel = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    val state: StateFlow<MealsUiState> = combine(
        profileRepository.observeProfile(),
        repository.observeUpcoming(),
        planning,
        selectedDate,
    ) { profile, meals, isPlanning, selected ->
        val dates = repository.upcomingDates()
        MealsUiState(
            loading = false,
            hasProfile = profile != null,
            planning = isPlanning,
            today = repository.today(),
            dates = dates,
            selectedDate = selected.takeIf { it in dates } ?: dates.first(),
            meals = meals.groupBy { it.date },
            slotTargets = profile?.slotTargets().orEmpty(),
            dailyKcal = profile?.kcalTarget ?: 0,
            weeklyBudget = profile?.weeklyBudgetEur,
        )
    }.stateIn(viewModelScope, sharing, MealsUiState(today = repository.today(), selectedDate = repository.today()))

    init {
        viewModelScope.launch {
            // Dès que le profil existe (ou que ses repas changent), on complète les repas manquants.
            profileRepository.observeSlotTargets().filterNotNull().distinctUntilChanged().collect {
                planning.value = true
                try {
                    repository.ensureUpcomingPlanned()
                } finally {
                    planning.value = false
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun replace(meal: DayMeal) = viewModelScope.launch {
        repository.replace(meal.date, meal.slot).messageRes()?.let { messageChannel.send(it) }
    }

    fun toggleEaten(meal: DayMeal) = viewModelScope.launch {
        repository.setStatus(meal.date, meal.slot, if (meal.status == MealStatus.EATEN) MealStatus.PLANNED else MealStatus.EATEN)
    }
}

// ── Détail d'un repas ──

@HiltViewModel
class MealDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MealPlanRepository,
    private val aiMealService: AiMealService,
    preferences: AppPreferences,
    private val apiKeyStore: ApiKeyStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<MealDetailRoute>()
    val date: LocalDate = LocalDate.ofEpochDay(route.epochDay)
    val slot: MealSlot = MealSlot.valueOf(route.slot)
    val today: LocalDate get() = repository.today()

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    val detail: StateFlow<MealDetail?> = repository.observeDetail(date, slot).stateIn(viewModelScope, sharing, null)

    /** Les jours passés ne sont plus modifiables. */
    val editable: Boolean get() = !date.isBefore(repository.today())

    private val _aiRunning = MutableStateFlow(false)
    val aiRunning: StateFlow<Boolean> = _aiRunning.asStateFlow()

    /** Bouton visible seulement si l'option est activée et qu'une clé est enregistrée. */
    val aiAvailable: StateFlow<Boolean> = preferences.aiEnabled
        .map { it && apiKeyStore.hasKey() }
        .stateIn(viewModelScope, sharing, false)

    fun replace() = viewModelScope.launch {
        repository.replace(date, slot).messageRes()?.let { messageChannel.send(context.getString(it)) }
    }

    fun toggleEaten() = viewModelScope.launch {
        val meal = detail.value?.meal ?: return@launch
        repository.setStatus(date, slot, if (meal.status == MealStatus.EATEN) MealStatus.PLANNED else MealStatus.EATEN)
    }

    fun proposeAiVariant() {
        if (_aiRunning.value) return
        _aiRunning.value = true
        viewModelScope.launch {
            try {
                val message = when (val result = aiMealService.proposeVariant(date, slot)) {
                    is VariantResult.Applied -> "Nouvelle proposition de l'IA : ${result.name}"
                    is VariantResult.Failed -> result.message
                }
                messageChannel.send(message)
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

    /** Jours passés (du plus récent au plus ancien) et leurs repas dans l'ordre de la journée. */
    val history: StateFlow<List<Pair<LocalDate, List<DayMeal>>>?> = repository.observeHistory()
        .map { meals ->
            meals.groupBy { it.date }
                .toSortedMap(compareByDescending { it })
                .map { (date, list) -> date to list.sortedBy { it.slot.ordinal } }
        }
        .stateIn(viewModelScope, sharing, null)
}

// ── Goûts, restrictions et repas de la journée (enregistrement automatique) ──

enum class SaveState { IDLE, SAVING, SAVED }

data class ScheduleState(
    val available: Boolean = false,
    val mains: Set<MealSlot> = MealSlot.mains.toSet(),
    val snackMode: SnackMode = SnackMode.AUTO,
    val organization: Organization = Organization(),
    /** Ordre de grandeur du budget minimal pour l'objectif (plats les moins chers), en €/semaine. */
    val cheapestWeek: Int? = null,
)

data class FoodPreferencesUiState(
    val loaded: Boolean = false,
    val prefs: FoodPreferences = FoodPreferences(),
    val query: String = "",
    val ingredients: List<IngredientEntity> = emptyList(),
    val expandedAisles: Set<Aisle> = emptySet(),
    val schedule: ScheduleState = ScheduleState(),
    val saveState: SaveState = SaveState.IDLE,
)

@HiltViewModel
class FoodPreferencesViewModel @Inject constructor(
    private val repository: FoodPreferencesRepository,
    private val goalService: GoalService,
    private val profileRepository: ProfileRepository,
    private val mealPlanRepository: MealPlanRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val draft = MutableStateFlow(FoodPreferencesUiState())
    val state: StateFlow<FoodPreferencesUiState> = combine(draft, repository.observeIngredients()) { d, ingredients ->
        d.copy(ingredients = ingredients)
    }.stateIn(viewModelScope, sharing, FoodPreferencesUiState())

    private var pendingSave: Job? = null
    private var dirty = false

    init {
        viewModelScope.launch {
            val current = repository.get()
            val profile = profileRepository.getProfile()
            draft.update {
                it.copy(
                    loaded = true,
                    prefs = current,
                    schedule = profile?.let { scheduleOf(it) } ?: ScheduleState(),
                )
            }
        }
    }

    /** Recharge le réglage des repas (au premier lancement, le profil est créé après l'ouverture de l'écran). */
    fun refreshSchedule() = viewModelScope.launch {
        val profile = profileRepository.getProfile() ?: return@launch
        draft.update { it.copy(schedule = scheduleOf(profile)) }
    }

    fun toggleAllergen(allergen: Allergen) = editPrefs { it.copy(allergens = it.allergens.toggle(allergen)) }

    fun toggleDiet(diet: Diet) = editPrefs { it.copy(diets = it.diets.toggle(diet)) }

    /** Un appui fait tourner : neutre → j'aime → je n'aime pas → neutre. */
    fun cycleTaste(ingredientId: String) = editPrefs {
        when (ingredientId) {
            in it.liked -> it.copy(liked = it.liked - ingredientId, disliked = it.disliked + ingredientId)
            in it.disliked -> it.copy(disliked = it.disliked - ingredientId)
            else -> it.copy(liked = it.liked + ingredientId)
        }
    }

    fun clearTaste(ingredientId: String) = editPrefs { it.copy(liked = it.liked - ingredientId, disliked = it.disliked - ingredientId) }

    fun setQuery(query: String) = draft.update { it.copy(query = query) }

    fun toggleAisle(aisle: Aisle) = draft.update { it.copy(expandedAisles = it.expandedAisles.toggle(aisle)) }

    fun toggleMainSlot(slot: MealSlot) {
        val current = draft.value.schedule
        val mains = current.mains.toggle(slot).ifEmpty { return } // au moins un repas
        updateSchedule(current.copy(mains = mains))
    }

    fun setSnackMode(mode: SnackMode) = updateSchedule(draft.value.schedule.copy(snackMode = mode))

    /** Budget saisi au clavier : enregistré après une courte pause de frappe. */
    fun setBudget(text: String) {
        val budget = text.filter(Char::isDigit).take(4).toIntOrNull()?.takeIf { it > 0 }
        editOrganization(debounce = true) { it.copy(weeklyBudgetEur = budget) }
    }

    fun setCookingLevel(level: CookingLevel) = editOrganization { it.copy(cookingLevel = level) }

    fun setLunchLeftovers(enabled: Boolean) = editOrganization { it.copy(lunchLeftovers = enabled) }

    fun setWeeklyBreakfast(enabled: Boolean) = editOrganization { it.copy(weeklyBreakfast = enabled) }

    fun setWeeklySnack(enabled: Boolean) = editOrganization { it.copy(weeklySnack = enabled) }

    private var pendingOrganization: Job? = null

    private fun editOrganization(debounce: Boolean = false, change: (Organization) -> Organization) {
        val schedule = draft.value.schedule
        val organization = change(schedule.organization)
        draft.update { it.copy(schedule = schedule.copy(organization = organization), saveState = SaveState.SAVING) }
        pendingOrganization?.cancel()
        pendingOrganization = viewModelScope.launch {
            if (debounce) delay(SAVE_DEBOUNCE_MS)
            // Hors du ViewModel : quitter l'écran juste après ne perd pas le réglage.
            applicationScope.launch {
                goalService.setOrganization(organization)
                draft.update { it.copy(saveState = SaveState.SAVED) }
            }
        }
    }

    private suspend fun scheduleOf(profile: UserProfileEntity) = ScheduleState(
        available = true,
        mains = profile.enabledMains(),
        snackMode = profile.snackMode,
        organization = profile.organization(),
        cheapestWeek = mealPlanRepository.cheapestWeekEstimate(),
    )

    /** Termine l'enregistrement en cours (premier lancement), puis [onDone]. */
    fun flush(onDone: () -> Unit) {
        pendingSave?.cancel()
        viewModelScope.launch {
            if (dirty) save()
            onDone()
        }
    }

    override fun onCleared() {
        // Quitter l'écran ne doit jamais perdre une modification : on termine hors du ViewModel.
        if (dirty) {
            val prefs = draft.value.prefs
            applicationScope.launch { repository.save(prefs) }
        }
    }

    private fun updateSchedule(schedule: ScheduleState) {
        draft.update { it.copy(schedule = schedule, saveState = SaveState.SAVING) }
        applicationScope.launch {
            goalService.setMealSchedule(schedule.mains, schedule.snackMode)
            draft.update { it.copy(saveState = SaveState.SAVED) }
        }
    }

    private fun editPrefs(change: (FoodPreferences) -> FoodPreferences) {
        draft.update { it.copy(prefs = change(it.prefs), saveState = SaveState.SAVING) }
        dirty = true
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            save()
        }
    }

    private suspend fun save() {
        dirty = false
        repository.save(draft.value.prefs)
        draft.update { it.copy(saveState = SaveState.SAVED) }
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

    private companion object {
        const val SAVE_DEBOUNCE_MS = 600L
    }
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

    /** Prix saisi (texte libre, virgule acceptée) ; vide ou invalide revient à l'estimation. */
    fun setPrice(ingredientId: String, text: String) = viewModelScope.launch {
        val price = text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 && it < 1000 }
        repository.setPrice(ingredientId, price)
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
