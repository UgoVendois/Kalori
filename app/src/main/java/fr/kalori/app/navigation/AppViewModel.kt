package fr.kalori.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.kalori.app.core.datastore.AppPreferences
import fr.kalori.app.core.security.AppLockState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppGate { LOADING, ONBOARDING, LOCKED, READY }

/** Décide de ce qu'affiche l'écran racine : premier lancement, verrou ou app. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val lockState: AppLockState,
) : ViewModel() {

    val gate: StateFlow<AppGate> = combine(
        preferences.onboardingDone,
        preferences.appLockEnabled,
        lockState.unlocked,
    ) { onboardingDone, lockEnabled, unlocked ->
        when {
            !onboardingDone -> AppGate.ONBOARDING
            lockEnabled && !unlocked -> AppGate.LOCKED
            else -> AppGate.READY
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppGate.LOADING)

    /** La base a dû être réinitialisée (clé de chiffrement perdue) : on l'explique une fois. */
    val dataResetNotice: StateFlow<Boolean> =
        preferences.dataResetNotice.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun unlocked() = lockState.markUnlocked()

    /** Appareil sans verrouillage d'écran : le verrou de l'app ne peut pas fonctionner, on le désactive. */
    fun disableLock() = viewModelScope.launch {
        preferences.setAppLockEnabled(false)
        lockState.markUnlocked()
    }

    fun dismissResetNotice() = viewModelScope.launch { preferences.setDataResetNotice(false) }

    fun onboardingFinished() = viewModelScope.launch {
        lockState.markUnlocked()
        preferences.setOnboardingDone(true)
    }
}
