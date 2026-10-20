package fr.kalori.app.feature.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import fr.kalori.app.core.security.DeviceAuthenticator

@Composable
fun LockScreen(onUnlocked: () -> Unit, onDisableLock: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val available = remember(context) { DeviceAuthenticator.isAvailable(context) }
    var error by remember { mutableStateOf<String?>(null) }

    fun unlock() {
        if (activity == null) return
        DeviceAuthenticator.authenticate(
            activity = activity,
            title = "Déverrouiller Kalori",
            onSuccess = onUnlocked,
            onFailure = { error = it },
        )
    }

    LaunchedEffect(Unit) { if (available) unlock() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Kalori est verrouillée", style = MaterialTheme.typography.headlineSmall)
            if (available) {
                Button(onClick = ::unlock) { Text("Déverrouiller") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            } else {
                Text(
                    "Aucun verrouillage d'écran (code, schéma, empreinte) n'est configuré sur ce téléphone : " +
                        "le verrou de Kalori ne peut pas fonctionner.",
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onDisableLock) { Text("Désactiver le verrou et continuer") }
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
