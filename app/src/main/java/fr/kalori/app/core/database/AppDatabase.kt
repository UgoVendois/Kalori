package fr.kalori.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import fr.kalori.app.core.database.dao.CatalogDao
import fr.kalori.app.core.database.dao.MealPlanDao
import fr.kalori.app.core.database.dao.ProfileDao
import fr.kalori.app.core.database.dao.RestrictionDao
import fr.kalori.app.core.database.dao.TrackingDao
import fr.kalori.app.core.database.dao.WorkoutDao
import fr.kalori.app.core.database.entity.DailyIntakeEntity
import fr.kalori.app.core.database.entity.ExerciseEntity
import fr.kalori.app.core.database.entity.IngredientEntity
import fr.kalori.app.core.database.entity.IngredientPriceEntity
import fr.kalori.app.core.database.entity.MealPlanEntryEntity
import fr.kalori.app.core.database.entity.RecipeEntity
import fr.kalori.app.core.database.entity.RecipeIngredientEntity
import fr.kalori.app.core.database.entity.SessionSetLogEntity
import fr.kalori.app.core.database.entity.ShoppingCheckEntity
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.core.database.entity.UserRestrictionEntity
import fr.kalori.app.core.database.entity.WeightEntryEntity
import fr.kalori.app.core.database.entity.WorkoutSessionEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateExerciseEntity

/**
 * Base locale unique, chiffrée par SQLCipher (voir DatabaseModule et DatabaseKeyManager).
 * Toute évolution du schéma exige une Migration testée : jamais de fallbackToDestructiveMigration,
 * qui effacerait les données de santé de l'utilisateur sans son accord.
 */
@Database(
    version = 4,
    exportSchema = true,
    entities = [
        UserProfileEntity::class,
        WeightEntryEntity::class,
        DailyIntakeEntity::class,
        UserRestrictionEntity::class,
        IngredientEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        MealPlanEntryEntity::class,
        IngredientPriceEntity::class,
        ShoppingCheckEntity::class,
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        WorkoutTemplateExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionSetLogEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun trackingDao(): TrackingDao
    abstract fun restrictionDao(): RestrictionDao
    abstract fun catalogDao(): CatalogDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val NAME = "kalori.db"
    }
}
