package fr.kalori.app.feature.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.kalori.app.core.di.IoDispatcher
import fr.kalori.app.core.logging.SafeLogger
import fr.kalori.app.data.backup.BackupFormatException
import fr.kalori.app.data.backup.BackupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Restauration d'une sauvegarde dès le premier lancement (changement de téléphone). */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _restoreError = MutableStateFlow<String?>(null)
    val restoreError: StateFlow<String?> = _restoreError.asStateFlow()

    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    /** En cas de succès, le premier lancement est marqué terminé et l'app s'ouvre directement. */
    fun restore(uri: Uri) {
        if (_restoring.value) return
        _restoring.value = true
        _restoreError.value = null
        viewModelScope.launch {
            try {
                val text = withContext(io) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: error("Fichier inaccessible")
                backupRepository.import(text)
            } catch (e: BackupFormatException) {
                _restoreError.value = e.message
            } catch (e: Exception) {
                SafeLogger.e(TAG, e) { "restore failed" }
                _restoreError.value = "La restauration a échoué."
            } finally {
                _restoring.value = false
            }
        }
    }

    private companion object {
        const val TAG = "OnboardingViewModel"
    }
}
