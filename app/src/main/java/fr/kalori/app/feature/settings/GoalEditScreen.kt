package fr.kalori.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.feature.goal.GoalEditorViewModel
import fr.kalori.app.feature.goal.GoalForm

/** Modifier son profil, son poids cible ou son rythme : le régime et les plats à venir sont recalculés. */
@Composable
fun GoalEditScreen(
    onDone: () -> Unit,
    viewModel: GoalEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onDone() } }

    KaloriScaffold(
        title = "Profil et objectif",
        onBack = onDone,
        actions = { TextButton(onClick = viewModel::save, enabled = state.canSave) { Text("Enregistrer") } },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ton poids actuel est repris de ta dernière pesée. Les durées sont recalculées à partir d'aujourd'hui.")
            GoalForm(state = state, onEdit = viewModel::edit, onSelect = viewModel::select)
        }
    }
}
