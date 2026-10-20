package fr.kalori.app.data.profile

import fr.kalori.app.core.database.dao.ProfileDao
import fr.kalori.app.core.database.entity.UserProfileEntity
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

    /** Cible du plat du jour, ou null tant que le profil n'est pas renseigné. */
    fun observeMealTarget(): Flow<NutritionTargets?> = profileDao.observe().map { it?.mealTarget() }

    suspend fun getMealTarget(): NutritionTargets? = profileDao.get()?.mealTarget()
}

fun UserProfileEntity.dailyTargets() = NutritionTargets(
    kcal = kcalTarget,
    proteinG = proteinG,
    fatG = fatG,
    carbsG = carbsG,
    fiberG = fiberG,
    notices = emptySet(),
)

fun UserProfileEntity.mealTarget(): NutritionTargets = dailyTargets().forMeal(mealShare)
