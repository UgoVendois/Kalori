package fr.kalori.app.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.database.entity.ExerciseEntity
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.domain.workout.DraftExercise

/** Mode modification : un seul écran pour créer (modèle vide) ou modifier une séance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(
    onDone: () -> Unit,
    viewModel: WorkoutEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.events.collect { onDone() } }

    KaloriScaffold(
        title = if (state.isNew) "Créer ma séance" else "Modifier la séance",
        onBack = onDone,
        actions = {
            TextButton(onClick = viewModel::save, enabled = state.draft.isValid && !state.saving) {
                Text(stringResource(R.string.save))
            }
        },
    ) { padding ->
        if (!state.loaded) {
            Row(Modifier.fillMaxWidth().padding(padding).padding(32.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@KaloriScaffold
        }
        val lines = state.draft.exercises
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.draft.name,
                    onValueChange = viewModel::rename,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nom de la séance") },
                    isError = state.draft.name.isBlank(),
                )
            }
            if (lines.isEmpty()) {
                item {
                    Text(
                        "Ajoute au moins un exercice.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(lines, key = { _, line -> line.key }) { index, line ->
                LineCard(
                    line = line,
                    exercise = state.exercises[line.exerciseId],
                    canMoveUp = index > 0,
                    canMoveDown = index < lines.lastIndex,
                    onMove = { viewModel.move(line.key, it) },
                    onRemove = { viewModel.remove(line.key) },
                    onUpdate = { change -> viewModel.update(line.key, change) },
                )
            }
            item {
                OutlinedButton(onClick = { viewModel.showPicker(true) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ajouter un exercice")
                }
            }
            if (!state.isNew) {
                item {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Supprimer la séance", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (state.showPicker) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showPicker(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text("Choisir un exercice", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            ExerciseList(
                exercises = state.exercises.values.toList(),
                onSelect = viewModel::add,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${state.draft.name} » ?") },
            text = { Text("La séance est retirée de « Mes séances ». L'historique des séances déjà réalisées est conservé.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineCard(
    line: DraftExercise,
    exercise: ExerciseEntity?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onUpdate: ((DraftExercise) -> DraftExercise) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                exercise?.let { ExerciseIconBadge(it.icon, size = 36.dp) }
                Text(
                    exercise?.name ?: line.exerciseId,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Monter")
                }
                IconButton(onClick = { onMove(+1) }, enabled = canMoveDown) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Descendre")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Retirer l'exercice")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Stepper("Séries", "${line.sets}") { delta -> onUpdate { it.copy(sets = it.sets + delta) } }
                if (line.timed) {
                    Stepper("Durée", formatDuration(line.durationSec ?: 0)) { delta ->
                        onUpdate { it.copy(durationSec = (it.durationSec ?: 0) + delta * 5) }
                    }
                } else {
                    Stepper("Répétitions", "${line.reps ?: 0}") { delta -> onUpdate { it.copy(reps = (it.reps ?: 0) + delta) } }
                }
                Stepper("Repos", formatDuration(line.restSec)) { delta -> onUpdate { it.copy(restSec = it.restSec + delta * 15) } }
            }
            if (exercise?.bodyweight != true || line.loadKg != null) {
                LoadField(line.key, line.loadKg) { load -> onUpdate { it.copy(loadKg = load) } }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onStep: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStep(-1) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Diminuer $label")
            }
            Text(value, style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { onStep(+1) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Augmenter $label")
            }
        }
    }
}

@Composable
private fun LoadField(key: Long, loadKg: Double?, onChange: (Double?) -> Unit) {
    var text by remember(key) { mutableStateOf(loadKg?.let { Format.decimal(it) } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            if (value.all { it.isDigit() || it == ',' || it == '.' } && value.length <= 6) {
                text = value
                onChange(value.replace(',', '.').toDoubleOrNull())
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Charge (kg, facultatif)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
