package fr.kalori.app

import android.app.Application
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import fr.kalori.app.core.di.ApplicationScope
import fr.kalori.app.core.logging.SafeLogger
import fr.kalori.app.data.seed.SeedImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KaloriApp : Application() {

    // Lazy : la base (et donc le Keystore) n'est ouverte que dans la coroutine, hors du thread principal.
    @Inject lateinit var seedImporter: Lazy<SeedImporter>

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { seedImporter.get().importIfNeeded() }
                .onFailure { SafeLogger.e(TAG, it) { "seed import failed" } }
        }
    }

    private companion object {
        const val TAG = "KaloriApp"
    }
}
