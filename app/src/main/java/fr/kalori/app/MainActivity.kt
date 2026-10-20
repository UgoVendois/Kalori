package fr.kalori.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.AndroidEntryPoint
import fr.kalori.app.core.security.AppLockState
import fr.kalori.app.core.ui.theme.KaloriTheme
import fr.kalori.app.navigation.KaloriRoot
import javax.inject.Inject

/** FragmentActivity : requis par BiometricPrompt (verrou de l'app). */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appLockState: AppLockState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = appLockState.onForeground()
                override fun onStop(owner: LifecycleOwner) = appLockState.onBackground()
            },
        )
        setContent {
            KaloriTheme {
                KaloriRoot()
            }
        }
    }
}
