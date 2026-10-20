package fr.kalori.app.core.logging

import android.util.Log
import fr.kalori.app.BuildConfig

/**
 * Seul point d'entrée autorisé pour les logs de l'app.
 *
 * Règles :
 * - Ne jamais passer de donnée personnelle ou de santé (poids, allergies, régimes, e-mail, uid, jetons).
 *   Uniquement des messages techniques ("plan regenerated", "db opened").
 * - Les messages sont des lambdas : en release, ils ne sont même pas évalués.
 * - En release, rien n'est écrit (R8 supprime aussi android.util.Log, voir proguard-rules.pro).
 */
object SafeLogger {

    fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }

    fun w(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.w(tag, message())
    }

    /** Le message de l'exception n'est pas logué : il peut contenir des données (ex. requête SQL). */
    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (BuildConfig.DEBUG) {
            val type = throwable?.let { " [${it::class.java.simpleName}]" }.orEmpty()
            Log.e(tag, message() + type)
        }
    }
}
