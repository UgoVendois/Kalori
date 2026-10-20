package fr.kalori.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

/**
 * Préférences techniques NON sensibles (version des données embarquées, premier lancement, verrouillage).
 * Règle : aucune donnée personnelle ou de santé ici. Tout ce qui est sensible va dans la base chiffrée.
 * C'est pourquoi un DataStore non chiffré suffit (pas besoin de Tink ici).
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun seedHash(): String? = context.dataStore.data.first()[SEED_HASH]

    suspend fun setSeedHash(hash: String) {
        context.dataStore.edit { it[SEED_HASH] = hash }
    }

    /** true si la base a dû être réinitialisée (clé perdue) : l'utilisateur doit en être informé. */
    val dataResetNotice: Flow<Boolean> = context.dataStore.data.map { it[DATA_RESET_NOTICE] ?: false }

    suspend fun setDataResetNotice(show: Boolean) {
        context.dataStore.edit { it[DATA_RESET_NOTICE] = show }
    }

    /** true une fois le premier lancement terminé (profil, rythme, goûts). */
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[ONBOARDING_DONE] = done }
    }

    /** Verrouillage de l'app par biométrie ou code de l'appareil. */
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[APP_LOCK] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[APP_LOCK] = enabled }
    }

    /** Variantes de plats par IA activées (nécessite une clé API personnelle). */
    val aiEnabled: Flow<Boolean> = context.dataStore.data.map { it[AI_ENABLED] ?: false }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AI_ENABLED] = enabled }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val SEED_HASH = stringPreferencesKey("seed_hash")
        val DATA_RESET_NOTICE = booleanPreferencesKey("data_reset_notice")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
    }
}
