package fr.kalori.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.core.datastore.AppPreferences
import fr.kalori.app.core.di.IoDispatcher
import fr.kalori.app.core.logging.SafeLogger
import fr.kalori.app.core.security.AppLockState
import fr.kalori.app.core.security.DeviceAuthenticator
import fr.kalori.app.data.backup.BackupFormatException
import fr.kalori.app.data.backup.BackupRepository
import fr.kalori.app.data.profile.GoalService
import fr.kalori.app.data.profile.ProfileRepository
import fr.kalori.app.domain.model.UnitSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class SettingsUiState(
    val profile: UserProfileEntity? = null,
    val lockEnabled: Boolean = false,
    val lockAvailable: Boolean = false,
    val busy: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    private val goalService: GoalService,
    private val preferences: AppPreferences,
    private val lockState: AppLockState,
    private val backupRepository: BackupRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        profileRepository.observeProfile(),
        preferences.appLockEnabled,
        busy,
    ) { profile, lock, isBusy ->
        SettingsUiState(profile, lock, DeviceAuthenticator.isAvailable(context), isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun defaultExportName(): String = "kalori-sauvegarde-${LocalDate.now()}.json"

    fun setMealShare(share: Double) = viewModelScope.launch {
        goalService.setMealShare(share)
        messageChannel.send("Portions des plats à venir recalculées")
    }

    fun setUnit(unit: UnitSystem) = viewModelScope.launch { goalService.setUnitSystem(unit) }

    fun setLock(enabled: Boolean) = viewModelScope.launch {
        // On vient de l'activer depuis l'app ouverte : pas de déverrouillage immédiat à demander.
        if (enabled) lockState.markUnlocked()
        preferences.setAppLockEnabled(enabled)
    }

    fun export(uri: Uri) = runBusy {
        val text = backupRepository.export()
        withContext(io) {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                ?: error("Fichier inaccessible")
        }
        messageChannel.send("Sauvegarde exportée. Garde ce fichier en lieu sûr : il n'est pas chiffré.")
    }

    fun import(uri: Uri) = runBusy {
        val text = withContext(io) {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Fichier inaccessible")
        }
        try {
            val summary = backupRepository.import(text)
            val skipped = if (summary.skipped > 0) " (${summary.skipped} élément(s) ignoré(s), absents de cette version)" else ""
            messageChannel.send(
                "Import terminé : ${summary.weights} pesée(s), ${summary.meals} plat(s), ${summary.workouts} séance(s) perso, ${summary.sessions} séance(s) réalisée(s)$skipped",
            )
        } catch (e: BackupFormatException) {
            messageChannel.send(e.message ?: "Sauvegarde invalide")
        }
    }

    fun wipeAll() = runBusy { backupRepository.wipeAll() }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                SafeLogger.e(TAG, e) { "settings action failed" }
                messageChannel.send("L'opération a échoué")
            } finally {
                busy.value = false
            }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
