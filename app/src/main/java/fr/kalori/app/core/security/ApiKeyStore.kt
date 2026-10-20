package fr.kalori.app.core.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clé API personnelle du fournisseur d'IA, chiffrée (AES-256-GCM, Tink) par une clé maîtresse
 * de l'Android Keystore. Jamais dans le code, jamais dans git, jamais dans les sauvegardes.
 */
@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file: File get() = File(context.noBackupFilesDir, FILE_NAME)

    // Construit à la demande et remis à zéro par destroy()/clear() : après effacement du jeu de clés,
    // un objet en cache chiffrerait encore avec l'ancienne clé, illisible au redémarrage suivant.
    private var aeadCache: Aead? = null

    private fun aead(): Aead = aeadCache ?: run {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            .also { aeadCache = it }
    }

    fun hasKey(): Boolean = file.exists()

    @Synchronized
    fun save(apiKey: String) {
        require(apiKey.isNotBlank())
        file.writeBytes(aead().encrypt(apiKey.trim().toByteArray(), ASSOCIATED_DATA))
    }

    /** null si aucune clé, ou si elle ne peut plus être déchiffrée (elle est alors effacée). */
    @Synchronized
    fun read(): String? {
        if (!file.exists()) return null
        return try {
            String(aead().decrypt(file.readBytes(), ASSOCIATED_DATA))
        } catch (e: GeneralSecurityException) {
            clear()
            null
        } catch (e: java.io.IOException) {
            clear()
            null
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        context.deleteSharedPreferences(KEYSET_PREFS)
        aeadCache = null
    }

    private companion object {
        const val FILE_NAME = "ai_api_key.enc"
        const val KEYSET_NAME = "kalori_ai_keyset"
        const val KEYSET_PREFS = "kalori_ai_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://kalori_ai_master_key"
        val ASSOCIATED_DATA = "kalori-ai-api-key-v1".toByteArray()
    }
}
