package fr.kalori.app.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.kalori.app.core.database.AppDatabase
import fr.kalori.app.core.database.MIGRATION_2_3
import fr.kalori.app.core.database.MIGRATION_3_4
import fr.kalori.app.core.database.migration1To2
import fr.kalori.app.core.database.dao.CatalogDao
import fr.kalori.app.core.database.dao.MealPlanDao
import fr.kalori.app.core.database.dao.ProfileDao
import fr.kalori.app.core.database.dao.RestrictionDao
import fr.kalori.app.core.database.dao.TrackingDao
import fr.kalori.app.core.database.dao.WorkoutDao
import fr.kalori.app.core.datastore.AppPreferences
import fr.kalori.app.core.logging.SafeLogger
import fr.kalori.app.core.security.DatabaseKeyLostException
import fr.kalori.app.core.security.DatabaseKeyManager
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
            .addMigrations(migration1To2(), MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    @Provides fun profileDao(db: AppDatabase): ProfileDao = db.profileDao()
    @Provides fun trackingDao(db: AppDatabase): TrackingDao = db.trackingDao()
    @Provides fun restrictionDao(db: AppDatabase): RestrictionDao = db.restrictionDao()
    @Provides fun catalogDao(db: AppDatabase): CatalogDao = db.catalogDao()
    @Provides fun mealPlanDao(db: AppDatabase): MealPlanDao = db.mealPlanDao()
    @Provides fun workoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
}
