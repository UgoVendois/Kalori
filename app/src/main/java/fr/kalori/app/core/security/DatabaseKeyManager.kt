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
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** La phrase secrète de la base existe mais ne peut plus être déchiffrée (clé Keystore perdue). */
class DatabaseKeyLostException(cause: Throwable) : Exception("Clé de chiffrement de la base perdue", cause)

/**
 * Gère la phrase secrète SQLCipher de la base de données.
 *
 * - Phrase secrète : 32 octets aléatoires (SecureRandom), générés au premier lancement.
 * - Elle est chiffrée (AES-256-GCM, Tink) par un jeu de clés lui-même protégé par une clé
 *   maîtresse **non extractible** de l'Android Keystore (matériel sécurisé si disponible).
 * - Le fichier chiffré est dans noBackupFilesDir : jamais sauvegardé ni transféré.
 *
 * Limites (documentées dans la politique de sécurité) : sur un appareil rooté, un attaquant
 * qui exécute du code dans le processus de l'app pendant qu'elle tourne peut lire les données
 * déchiffrées en mémoire ; si le Keystore est réinitialisé, la base devient illisible.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyFile: File get() = File(context.noBackupFilesDir, KEY_FILE)

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

    /** true si une base chiffrée a déjà été créée sur cet appareil. */
    fun hasKey(): Boolean = keyFile.exists()

    /**
     * Retourne la phrase secrète, en la créant si nécessaire.
     * @throws DatabaseKeyLostException si la phrase existe mais ne peut plus être déchiffrée.
     */
    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val file = keyFile
        if (file.exists()) {
            return try {
                aead().decrypt(file.readBytes(), ASSOCIATED_DATA)
            } catch (e: GeneralSecurityException) {
                throw DatabaseKeyLostException(e)
            } catch (e: java.io.IOException) {
                // Jeu de clés illisible (clé maîtresse du Keystore disparue).
                throw DatabaseKeyLostException(e)
            }
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val tmp = File(file.parentFile, "$KEY_FILE.tmp")
        tmp.writeBytes(aead().encrypt(passphrase, ASSOCIATED_DATA))
        check(tmp.renameTo(file)) { "Écriture de la clé impossible" }
        return passphrase
    }

    /** Supprime la phrase secrète et le jeu de clés (suppression du compte ou clé perdue). */
    @Synchronized
    fun destroy() {
        keyFile.delete()
        context.deleteSharedPreferences(KEYSET_PREFS)
        aeadCache = null
    }

    private companion object {
        const val KEY_FILE = "db_passphrase.enc"
        const val KEYSET_NAME = "kalori_db_keyset"
        const val KEYSET_PREFS = "kalori_db_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://kalori_db_master_key"
        const val PASSPHRASE_BYTES = 32
        val ASSOCIATED_DATA = "kalori-db-passphrase-v1".toByteArray()
    }
}
