package fr.kalori.app.data.profile

import fr.kalori.app.core.database.dao.TrackingDao
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.data.meal.MealPlanRepository
import fr.kalori.app.domain.model.ActivityLevel
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.Sex
import fr.kalori.app.domain.model.SnackMode
import fr.kalori.app.domain.model.toMask
import fr.kalori.app.domain.model.UnitSystem
import fr.kalori.app.domain.nutrition.BodyProfile
import fr.kalori.app.domain.nutrition.EnergyCalculator
import fr.kalori.app.domain.nutrition.PaceOption
import fr.kalori.app.domain.nutrition.PaceOptionsGenerator
import fr.kalori.app.domain.nutrition.PacePlan
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Données saisies pour calculer un objectif (unités métriques). */
data class ProfileDraft(
    val sex: Sex,
    val birthYear: Int,
    val birthMonth: Int,
    val heightCm: Double,
    val weightKg: Double,
    val targetWeightKg: Double,
    val activity: ActivityLevel,
)

/**
 * Calcul et enregistrement de l'objectif : utilisé au premier lancement et à chaque changement
 * de rythme ou d'objectif (Paramètres). L'utilisateur choisit toujours lui-même son rythme.
 */
@Singleton
class GoalService @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val trackingDao: TrackingDao,
    private val mealPlanRepository: MealPlanRepository,
    private val clock: Clock,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun ageYears(birthYear: Int, birthMonth: Int): Int = EnergyCalculator.ageYears(birthYear, birthMonth, today())

    /** [pregnantOrBreastfeeding] sert uniquement au calcul : il n'est jamais enregistré. */
    fun plan(draft: ProfileDraft, pregnantOrBreastfeeding: Boolean): PacePlan = PaceOptionsGenerator.generate(
        BodyProfile(
            sex = draft.sex,
            ageYears = ageYears(draft.birthYear, draft.birthMonth),
            heightCm = draft.heightCm,
            weightKg = draft.weightKg,
            targetWeightKg = draft.targetWeightKg,
            activity = draft.activity,
            pregnantOrBreastfeeding = pregnantOrBreastfeeding,
        ),
        today(),
    )

    suspend fun currentDraft(): ProfileDraft? {
        val profile = profileRepository.getProfile() ?: return null
        val weight = trackingDao.latestWeight()?.weightKg ?: profile.startWeightKg
        return ProfileDraft(
            sex = profile.sex,
            birthYear = profile.birthYear,
            birthMonth = profile.birthMonth,
            heightCm = profile.heightCm,
            weightKg = weight,
            targetWeightKg = profile.targetWeightKg,
            activity = profile.activityLevel,
        )
    }

    /** Rythme actuellement suivi (kg/semaine), pour le présélectionner à l'édition. */
    suspend fun currentPace(): Double? = profileRepository.getProfile()?.paceKgPerWeek

    /** Enregistre le profil et le rythme choisi, note la pesée du jour, puis recalcule les plats à venir. */
    suspend fun saveGoal(draft: ProfileDraft, plan: PacePlan.Available, option: PaceOption) {
        require(option in plan.options) { "Rythme non proposé" }
        val now = Instant.now(clock)
        val existing = profileRepository.getProfile()
        profileRepository.saveProfile(
            UserProfileEntity(
                sex = draft.sex,
                birthYear = draft.birthYear,
                birthMonth = draft.birthMonth,
                heightCm = draft.heightCm,
                startWeightKg = existing?.startWeightKg ?: draft.weightKg,
                targetWeightKg = draft.targetWeightKg,
                activityLevel = draft.activity,
                goalType = plan.goal,
                paceKgPerWeek = option.paceKgPerWeek,
                kcalTarget = option.targets.kcal,
                proteinG = option.targets.proteinG,
                fatG = option.targets.fatG,
                carbsG = option.targets.carbsG,
                fiberG = option.targets.fiberG,
                enabledSlotsMask = existing?.enabledSlotsMask ?: UserProfileEntity.DEFAULT_SLOTS_MASK,
                snackMode = existing?.snackMode ?: SnackMode.AUTO,
                unitSystem = existing?.unitSystem ?: UnitSystem.METRIC,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        trackingDao.setWeight(today(), draft.weightKg)
        mealPlanRepository.regenerateUpcoming()
    }

    /** Repas pris dans la journée (ex. sans petit-déjeuner) et mode de collation ; recompose les jours à venir. */
    suspend fun setMealSchedule(mains: Set<MealSlot>, snackMode: SnackMode) {
        val profile = profileRepository.getProfile() ?: return
        val mask = mains.filter { it in MealSlot.mains }.ifEmpty { listOf(MealSlot.NOON) }.toMask()
        if (mask == profile.enabledSlotsMask && snackMode == profile.snackMode) return
        profileRepository.saveProfile(profile.copy(enabledSlotsMask = mask, snackMode = snackMode, updatedAt = Instant.now(clock)))
        mealPlanRepository.regenerateUpcoming()
    }

    suspend fun setUnitSystem(unitSystem: UnitSystem) {
        val profile = profileRepository.getProfile() ?: return
        profileRepository.saveProfile(profile.copy(unitSystem = unitSystem, updatedAt = Instant.now(clock)))
    }
}
