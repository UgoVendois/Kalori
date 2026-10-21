package fr.kalori.app.data.profile

import fr.kalori.app.core.database.dao.ProfileDao
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.domain.meal.Budget
import fr.kalori.app.domain.meal.MealSchedule
import fr.kalori.app.domain.meal.PlanSettings
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.toFlagSet
import fr.kalori.app.domain.nutrition.NutritionTargets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
) {
    fun observeProfile(): Flow<UserProfileEntity?> = profileDao.observe()

    suspend fun getProfile(): UserProfileEntity? = profileDao.get()

    suspend fun saveProfile(profile: UserProfileEntity) = profileDao.upsert(profile)

    /** Cibles de chaque repas de la journée, ou null tant que le profil n'est pas renseigné. */
    fun observeSlotTargets(): Flow<Map<MealSlot, NutritionTargets>?> = profileDao.observe().map { it?.slotTargets() }

    suspend fun getSlotTargets(): Map<MealSlot, NutritionTargets>? = profileDao.get()?.slotTargets()
}

fun UserProfileEntity.dailyTargets() = NutritionTargets(
    kcal = kcalTarget,
    proteinG = proteinG,
    fatG = fatG,
    carbsG = carbsG,
    fiberG = fiberG,
    notices = emptySet(),
)

fun UserProfileEntity.enabledMains(): Set<MealSlot> = enabledSlotsMask.toFlagSet<MealSlot>()

fun UserProfileEntity.slotTargets(): Map<MealSlot, NutritionTargets> =
    MealSchedule.targets(dailyTargets(), enabledMains(), snackMode)

/** Organisation de la semaine. Les restes du midi supposent un dîner et un midi tous deux pris. */
fun UserProfileEntity.planSettings(): PlanSettings {
    val mains = enabledMains()
    return PlanSettings(
        cookingLevel = cookingLevel,
        lunchLeftovers = lunchLeftovers && MealSlot.NOON in mains && MealSlot.EVENING in mains,
        weeklySlots = buildSet {
            if (weeklyBreakfast) add(MealSlot.MORNING)
            if (weeklySnack) add(MealSlot.SNACK)
        },
    )
}

fun UserProfileEntity.budget(): Budget? = budgetEur?.let { Budget(it, budgetPeriod) }
