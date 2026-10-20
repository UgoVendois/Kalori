package fr.kalori.app.core.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * État de déverrouillage de la session. L'app se reverrouille après [RELOCK_AFTER_MS]
 * passées en arrière-plan. Le verrou protège l'accès à l'interface ; les données, elles,
 * sont chiffrées au repos en permanence (SQLCipher).
 */
@Singleton
class AppLockState @Inject constructor() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var backgroundedAt: Long? = null

    fun markUnlocked() {
        _unlocked.value = true
    }

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        if (SystemClock.elapsedRealtime() - since > RELOCK_AFTER_MS) _unlocked.value = false
        backgroundedAt = null
    }

    private companion object {
        const val RELOCK_AFTER_MS = 30_000L
    }
}

/** Biométrie (empreinte, visage) ou, à défaut, code/schéma de l'appareil. */
object DeviceAuthenticator {
    // BIOMETRIC_WEAK | DEVICE_CREDENTIAL est la seule combinaison avec code de secours prise en charge dès Android 8.
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(errString.toString())
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
