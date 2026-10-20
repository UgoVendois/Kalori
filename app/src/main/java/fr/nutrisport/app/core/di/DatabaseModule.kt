package fr.nutrisport.app.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.nutrisport.app.core.database.AppDatabase
import fr.nutrisport.app.core.database.dao.CatalogDao
import fr.nutrisport.app.core.database.dao.ConsentDao
import fr.nutrisport.app.core.database.dao.MealPlanDao
import fr.nutrisport.app.core.database.dao.ProfileDao
import fr.nutrisport.app.core.database.dao.RestrictionDao
import fr.nutrisport.app.core.database.dao.TrackingDao
import fr.nutrisport.app.core.database.dao.WorkoutDao
import fr.nutrisport.app.core.datastore.AppPreferences
import fr.nutrisport.app.core.logging.SafeLogger
import fr.nutrisport.app.core.security.DatabaseKeyLostException
import fr.nutrisport.app.core.security.DatabaseKeyManager
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val TAG = "DatabaseModule"

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager,
        preferences: AppPreferences,
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        if (!keyManager.hasKey() && context.getDatabasePath(AppDatabase.NAME).exists()) {
            // Base orpheline (fichier de clé supprimé) : illisible avec une nouvelle clé.
            context.deleteDatabase(AppDatabase.NAME)
        }
        val passphrase = try {
            keyManager.getOrCreatePassphrase()
        } catch (e: DatabaseKeyLostException) {
            // La clé Keystore a disparu (réinitialisation de l'appareil, restauration partielle…) :
            // la base existante est définitivement illisible. On repart d'une base vide et on
            // prévient l'utilisateur au prochain affichage (il peut réimporter un export JSON).
            SafeLogger.e(TAG, e) { "database key lost, resetting local database" }
            context.deleteDatabase(AppDatabase.NAME)
            keyManager.destroy()
            runBlocking { preferences.setDataResetNotice(true) }
            keyManager.getOrCreatePassphrase()
        }
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }

    @Provides fun profileDao(db: AppDatabase): ProfileDao = db.profileDao()
    @Provides fun trackingDao(db: AppDatabase): TrackingDao = db.trackingDao()
    @Provides fun restrictionDao(db: AppDatabase): RestrictionDao = db.restrictionDao()
    @Provides fun consentDao(db: AppDatabase): ConsentDao = db.consentDao()
    @Provides fun catalogDao(db: AppDatabase): CatalogDao = db.catalogDao()
    @Provides fun mealPlanDao(db: AppDatabase): MealPlanDao = db.mealPlanDao()
    @Provides fun workoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
}
