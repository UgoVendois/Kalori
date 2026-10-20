package fr.kalori.app.feature.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.kalori.app.core.database.entity.ExerciseEntity
import fr.kalori.app.core.ui.Format
import fr.kalori.app.domain.model.ExerciseIcon
import java.text.Normalizer

/** Pictogramme d'un exercice dans une pastille. */
@Composable
fun ExerciseIconBadge(icon: ExerciseIcon, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon.drawableRes()),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}

/**
 * Liste d'exercices filtrable (catégorie, recherche).
 * [onSelect] non nul : mode sélection (éditeur) ; sinon un appui déplie la description.
 */
@Composable
fun ExerciseList(
    exercises: List<ExerciseEntity>,
    onSelect: ((ExerciseEntity) -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    var filter by rememberSaveable { mutableStateOf(CategoryFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val normalizedQuery = query.normalized()
    val shown = exercises
        .filter { filter.category == null || it.category == filter.category }
        .filter { normalizedQuery.isBlank() || it.name.normalized().contains(normalizedQuery) || it.muscleGroup.label.normalized().contains(normalizedQuery) }
        .sortedWith(compareBy({ it.category }, { it.name }))

    LazyColumn(modifier, contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryFilter.entries.forEach {
                    FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it.label) })
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                label = { Text("Rechercher un exercice ou un muscle") },
            )
        }
        items(shown, key = { it.id }) { exercise ->
            val isExpanded = expanded == exercise.id
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { if (onSelect != null) onSelect(exercise) else expanded = if (isExpanded) null else exercise.id }
                    .animateContentSize()
                    .padding(vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExerciseIconBadge(exercise.icon)
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${exercise.category.label} · ${exercise.muscleGroup.label}" + if (exercise.timed) " · en durée" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isExpanded) {
                    Text(
                        exercise.instructions,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 52.dp, top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Résumé d'une ligne de séance : « 3 × 10 · 20 kg · repos 1 min 30 ». */
fun lineSummary(sets: Int, reps: Int?, durationSec: Int?, loadKg: Double?, restSec: Int): String = buildString {
    append("$sets × ")
    append(if (durationSec != null) formatDuration(durationSec) else "${reps ?: 0}")
    if (loadKg != null) append(" · ${Format.decimal(loadKg)} kg")
    append(" · repos ${formatDuration(restSec)}")
}

fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${"%02d".format(seconds % 60)}"
}

private fun String.normalized(): String =
    Normalizer.normalize(lowercase().replace("œ", "oe"), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()
