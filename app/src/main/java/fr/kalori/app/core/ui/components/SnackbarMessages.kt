package fr.kalori.app.core.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

/** Affiche en snackbar les messages (identifiants de chaînes) émis par un ViewModel. */
@Composable
fun SnackbarMessages(messages: Flow<Int>, hostState: SnackbarHostState) {
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { hostState.showSnackbar(context.getString(it)) }
    }
}
