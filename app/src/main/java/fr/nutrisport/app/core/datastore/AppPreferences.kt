package fr.nutrisport.app.core.datastore

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
 * Préférences techniques NON sensibles (version des données embarquées, avis à afficher).
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

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val SEED_HASH = stringPreferencesKey("seed_hash")
        val DATA_RESET_NOTICE = booleanPreferencesKey("data_reset_notice")
    }
}
