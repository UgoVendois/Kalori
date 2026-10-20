package fr.kalori.app.data.tracking

import fr.kalori.app.core.database.dao.MealPlanDao
import fr.kalori.app.core.database.dao.TrackingDao
import fr.kalori.app.core.database.dao.WorkoutDao
import fr.kalori.app.core.database.entity.DailyIntakeEntity
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.core.di.IoDispatcher
import fr.kalori.app.data.profile.ProfileRepository
import fr.kalori.app.domain.model.MealStatus
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.tracking.DatedValue
import fr.kalori.app.domain.tracking.TrackingAggregator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Toutes les données du tableau de bord, brutes (l'agrégation par période est faite par TrackingAggregator). */
data class TrackingData(
    val profile: UserProfileEntity?,
    val weights: List<DatedValue>,
    /** Apports par jour : plat « mangé » + autres apports saisis. */
    val dailyIntake: Map<LocalDate, Nutrition>,
    val extraToday: DailyIntakeEntity?,
    val sessionDates: List<LocalDate>,
)

@Singleton
class TrackingRepository @Inject constructor(
    private val trackingDao: TrackingDao,
    private val mealPlanDao: MealPlanDao,
    private val workoutDao: WorkoutDao,
    private val profileRepository: ProfileRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun observe(): Flow<TrackingData> = combine(
        profileRepository.observeProfile(),
        trackingDao.observeWeights(),
        mealPlanDao.observeAll(),
        trackingDao.observeAllIntakes(),
        workoutDao.observeCompletedSessions(Instant.EPOCH),
    ) { profile, weights, meals, extras, sessions ->
        val zone = ZoneId.systemDefault()
        TrackingData(
            profile = profile,
            weights = weights.map { DatedValue(it.date, it.weightKg) },
            dailyIntake = TrackingAggregator.dailyIntake(
                eatenMeals = meals.filter { it.status == MealStatus.EATEN }
                    .map { it.date to Nutrition(it.kcal, it.protein, it.fat, it.carbs, it.fiber) },
                extras = extras.map { it.date to Nutrition(it.kcal, it.proteinG, it.fatG, it.carbsG, it.fiberG) },
            ),
            extraToday = extras.firstOrNull { it.date == today() },
            sessionDates = sessions.map { it.startedAt.atZone(zone).toLocalDate() },
        )
    }

    suspend fun setWeight(date: LocalDate, weightKg: Double) = withContext(io) {
        require(weightKg in 30.0..300.0) { "Poids hors bornes" }
        trackingDao.setWeight(date, weightKg)
    }

    /** Remplace le total des autres apports du jour (en plus du plat du jour). */
    suspend fun setExtraIntake(date: LocalDate, nutrition: Nutrition) = withContext(io) {
        trackingDao.upsertIntake(
            DailyIntakeEntity(date, nutrition.kcal, nutrition.protein, nutrition.fat, nutrition.carbs, nutrition.fiber),
        )
    }
}
