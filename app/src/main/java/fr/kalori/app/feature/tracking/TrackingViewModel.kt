package fr.kalori.app.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.kalori.app.core.database.entity.DailyIntakeEntity
import fr.kalori.app.data.tracking.TrackingRepository
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.model.UnitSystem
import fr.kalori.app.domain.tracking.DatedValue
import fr.kalori.app.domain.tracking.IntakeBucket
import fr.kalori.app.domain.tracking.Period
import fr.kalori.app.domain.tracking.SessionBucket
import fr.kalori.app.domain.tracking.TrackingAggregator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class GoalSummary(
    val goal: GoalType,
    val startKg: Double,
    val targetKg: Double,
    val currentKg: Double,
    /** 0 à 1, null pour le maintien. */
    val progress: Double?,
    val kcalTarget: Int,
)

data class TrackingUiState(
    val loaded: Boolean = false,
    val period: Period = Period.MONTH,
    val today: LocalDate = LocalDate.now(),
    val summary: GoalSummary? = null,
    val weights: List<DatedValue> = emptyList(),
    val weightRange: ClosedRange<LocalDate> = LocalDate.now()..LocalDate.now(),
    /** Créneaux de l'axe X des apports (jours, ou lundis pour « Tout »). */
    val intakeSlots: List<LocalDate> = emptyList(),
    val intake: List<IntakeBucket> = emptyList(),
    val sessions: List<SessionBucket> = emptyList(),
    val extraToday: DailyIntakeEntity? = null,
    val unit: UnitSystem = UnitSystem.METRIC,
)

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val repository: TrackingRepository,
) : ViewModel() {

    private val period = MutableStateFlow(Period.MONTH)

    val state: StateFlow<TrackingUiState> = combine(repository.observe(), period) { data, p ->
        val today = repository.today()
        val profile = data.profile
        val current = data.weights.maxByOrNull { it.date }?.value ?: profile?.startWeightKg
        val weights = TrackingAggregator.weightSeries(data.weights, p, today)
        val intake = TrackingAggregator.intakeSeries(data.dailyIntake, p, today)
        TrackingUiState(
            loaded = true,
            period = p,
            today = today,
            summary = if (profile != null && current != null) {
                GoalSummary(
                    goal = profile.goalType,
                    startKg = profile.startWeightKg,
                    targetKg = profile.targetWeightKg,
                    currentKg = current,
                    progress = TrackingAggregator.goalProgress(profile.startWeightKg, profile.targetWeightKg, current, profile.goalType),
                    kcalTarget = profile.kcalTarget,
                )
            } else {
                null
            },
            weights = weights,
            weightRange = when (p) {
                Period.WEEK -> today.minusDays(6)..today
                Period.MONTH -> today.minusDays(29)..today
                Period.ALL -> (weights.firstOrNull()?.date ?: today)..maxOf(weights.lastOrNull()?.date ?: today, today)
            },
            intakeSlots = when (p) {
                Period.WEEK -> (6L downTo 0L).map { today.minusDays(it) }
                Period.MONTH -> (29L downTo 0L).map { today.minusDays(it) }
                Period.ALL -> {
                    val first = intake.firstOrNull()?.start ?: TrackingAggregator.weekStart(today)
                    val last = TrackingAggregator.weekStart(today)
                    generateSequence(first) { it.plusWeeks(1) }.takeWhile { !it.isAfter(last) }.toList()
                }
            },
            intake = intake,
            sessions = TrackingAggregator.sessionsPerWeek(data.sessionDates, p, today),
            extraToday = data.extraToday,
            unit = profile?.unitSystem ?: UnitSystem.METRIC,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackingUiState())

    fun setPeriod(value: Period) {
        period.value = value
    }

    fun saveWeight(weightKg: Double) = viewModelScope.launch {
        repository.setWeight(repository.today(), weightKg)
    }

    fun saveExtraIntake(nutrition: Nutrition) = viewModelScope.launch {
        repository.setExtraIntake(repository.today(), nutrition)
    }
}
