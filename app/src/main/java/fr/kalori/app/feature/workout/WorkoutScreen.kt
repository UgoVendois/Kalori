package fr.kalori.app.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.database.entity.ExerciseEntity
import fr.kalori.app.core.database.entity.WorkoutTemplateWithExercises
import fr.kalori.app.core.ui.components.KaloriScaffold

@Composable
fun WorkoutScreen(
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onStart: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: WorkoutHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    KaloriScaffold(
        title = stringResource(R.string.tab_workout),
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = onOpenLibrary) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "Bibliothèque d'exercices")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Créer ma séance")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryFilter.entries.forEach {
                        FilterChip(selected = state.filter == it, onClick = { viewModel.setFilter(it) }, label = { Text(it.label) })
                    }
                }
            }
            if (!state.loaded) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
                return@LazyColumn
            }

            item { SectionTitle("Mes séances") }
            if (state.mine.isEmpty()) {
                item {
                    Text(
                        "Aucune séance pour l'instant : crée la tienne ou ajoute une séance pré-faite.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.mine, key = { "mine_${it.template.id}" }) { template ->
                TemplateCard(template, state.exercises) {
                    FilledTonalButton(onClick = { onStart(template.template.id) }) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lancer")
                    }
                    OutlinedButton(onClick = { onEdit(template.template.id) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Modifier")
                    }
                }
            }

            item { SectionTitle("Séances pré-faites") }
            items(state.premade, key = { "premade_${it.template.template.id}" }) { item ->
                TemplateCard(item.template, state.exercises, recommended = item.recommended) {
                    OutlinedButton(onClick = { viewModel.addToMine(item.template.template.id) }) {
                        Icon(Icons.Outlined.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ajouter à mes séances")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun TemplateCard(
    template: WorkoutTemplateWithExercises,
    exercises: Map<String, ExerciseEntity>,
    recommended: Boolean = false,
    actions: @Composable () -> Unit,
) {
    val lines = template.orderedExercises
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(template.template.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                template.template.category?.let { Text(it.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            }
            if (recommended) AssistChip(onClick = {}, label = { Text("Adaptée à ton objectif") })
            template.template.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.take(7).forEach { line -> exercises[line.exerciseId]?.let { ExerciseIconBadge(it.icon, size = 32.dp) } }
                if (lines.size > 7) Text("+${lines.size - 7}", modifier = Modifier.align(Alignment.CenterVertically))
            }
            Text(
                lines.joinToString(" · ") { exercises[it.exerciseId]?.name ?: it.exerciseId },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}
