package fr.kalori.app.feature.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.domain.workout.SessionSet

@Composable
fun WorkoutPlayerScreen(
    onDone: () -> Unit,
    viewModel: WorkoutPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmQuit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(viewModel) { viewModel.finished.collect { onDone() } }

    val requestQuit: () -> Unit = {
        if (state.progress.isStarted) {
            confirmQuit = true
        } else {
            onDone()
        }
    }
    BackHandler(onBack = requestQuit)

    KaloriScaffold(title = state.name.ifBlank { "Séance" }, onBack = requestQuit) { padding ->
        if (!state.loaded) {
            Row(Modifier.fillMaxWidth().padding(padding).padding(32.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@KaloriScaffold
        }
        val progress = state.progress
        Column(Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { if (progress.total == 0) 0f else progress.doneCount / progress.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            state.restRemainingSec?.let { remaining ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Repos", style = MaterialTheme.typography.labelLarge)
                            Text(formatDuration(remaining), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = viewModel::skipRest) { Text("Passer") }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val groups = progress.sets.withIndex().groupBy { it.value.exerciseIndex }
                groups.forEach { (exerciseIndex, sets) ->
                    val exercise = state.exercises[sets.first().value.exerciseId]
                    item(key = "header_$exerciseIndex") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            exercise?.let { ExerciseIconBadge(it.icon) }
                            Text(exercise?.name ?: "", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    items(sets, key = { "set_${it.index}" }) { (index, set) ->
                        SetRow(
                            set = set,
                            onToggle = { viewModel.toggle(index) },
                            onEdit = { editing = index },
                        )
                    }
                }
            }
            Text(
                "${progress.doneCount} / ${progress.total} séries",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = viewModel::finish,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(if (progress.isStarted) "Terminer la séance" else "Quitter") }
        }
    }

    editing?.let { index ->
        state.progress.sets.getOrNull(index)?.let { set ->
            AdjustDialog(
                set = set,
                onDismiss = { editing = null },
                onConfirm = { reps, load ->
                    viewModel.adjust(index, reps, load)
                    editing = null
                },
            )
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Quitter la séance ?") },
            text = { Text("Les séries cochées ne seront pas enregistrées. Pour les garder, utilise « Terminer la séance ».") },
            confirmButton = { TextButton(onClick = { confirmQuit = false; onDone() }) { Text("Quitter sans enregistrer") } },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Continuer") } },
        )
    }
}

@Composable
private fun SetRow(set: SessionSet, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = set.done, onCheckedChange = { onToggle() })
        Text(
            buildString {
                append("Série ${set.setIndex + 1} — ")
                append(if (set.durationSec != null) formatDuration(set.durationSec) else "${set.reps} répétitions")
                set.loadKg?.let { append(" · ${Format.decimal(it)} kg") }
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (set.durationSec == null) {
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Ajuster la série") }
        }
    }
}

@Composable
private fun AdjustDialog(set: SessionSet, onDismiss: () -> Unit, onConfirm: (Int?, Double?) -> Unit) {
    var reps by remember { mutableStateOf(set.reps?.toString() ?: "") }
    var load by remember { mutableStateOf(set.loadKg?.let { Format.decimal(it) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Série ${set.setIndex + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = reps,
                    onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 3) reps = v },
                    label = { Text("Répétitions réalisées") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = load,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == ',' || it == '.' } && v.length <= 6) load = v },
                    label = { Text("Charge (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reps.toIntOrNull(), load.replace(',', '.').toDoubleOrNull()) }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
