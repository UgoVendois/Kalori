package fr.kalori.app.feature.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.database.entity.DailyIntakeEntity
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.chart.BarChart
import fr.kalori.app.core.ui.chart.BarValue
import fr.kalori.app.core.ui.chart.ChartPalette
import fr.kalori.app.core.ui.chart.LineChart
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.Nutrition
import fr.kalori.app.domain.model.UnitSystem
import fr.kalori.app.domain.tracking.Period
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TrackingScreen(viewModel: TrackingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var weightDialog by remember { mutableStateOf(false) }
    var intakeDialog by remember { mutableStateOf(false) }

    KaloriScaffold(title = stringResource(R.string.tab_tracking)) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@KaloriScaffold
        }
        val weekly = state.period == Period.ALL
        val dateFormat: (LocalDate) -> String = { d -> if (weekly) "sem. ${Format.numericDate(d)}" else Format.numericDate(d) }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Filtres : une seule rangée, au-dessus de tous les graphiques.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Period.entries.forEach {
                        FilterChip(selected = state.period == it, onClick = { viewModel.setPeriod(it) }, label = { Text(it.label) })
                    }
                }
            }

            state.summary?.let { summary ->
                item { GoalCard(summary, state.unit, onAddWeight = { weightDialog = true }) }
            }

            item {
                ChartCard("Poids (${Format.weightUnit(state.unit)})") {
                    if (state.weights.isEmpty()) {
                        EmptyChart("Aucune pesée sur cette période.")
                    } else {
                        LineChart(
                            points = state.weights.map { it.date to Format.weightValue(it.value, state.unit) },
                            xRange = state.weightRange,
                            color = MaterialTheme.colorScheme.primary,
                            formatValue = { Format.decimal(it) },
                            formatDate = dateFormat,
                            description = "Évolution du poids : dernière valeur ${Format.weight(state.weights.last().value, state.unit)}",
                            target = state.summary?.targetKg?.let { Format.weightValue(it, state.unit) },
                            targetLabel = state.summary?.let { "Objectif ${Format.weight(it.targetKg, state.unit)}" },
                        )
                    }
                }
            }

            item {
                ChartCard(if (state.period == Period.ALL) "Calories (moyenne par jour, par semaine)" else "Calories par jour") {
                    if (state.intake.isEmpty()) {
                        EmptyChart("Pas encore de données : marque ton plat « Mangé » et ajoute tes autres apports.")
                    } else {
                        BarChart(
                            bars = state.intake.map { BarValue(it.start, listOf(it.nutrition.kcal)) },
                            slots = state.intakeSlots,
                            colors = listOf(MaterialTheme.colorScheme.primary),
                            formatValue = { "${it.roundToInt()}" },
                            formatDate = dateFormat,
                            description = "Calories : moyenne ${state.intake.map { it.nutrition.kcal }.average().roundToInt()} kcal par jour",
                            target = state.summary?.kcalTarget?.toDouble(),
                            targetLabel = state.summary?.let { "Objectif ${it.kcalTarget} kcal" },
                            tooltipText = { "${dateFormat(it.date)} : ${it.segments.sum().roundToInt()} kcal" },
                        )
                    }
                    OutlinedButton(onClick = { intakeDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Mes autres apports du jour")
                    }
                    Text(
                        "Total du jour = plat marqué « Mangé » + autres apports saisis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                ChartCard("Macronutriments (g par jour)") {
                    if (state.intake.isEmpty()) {
                        EmptyChart("Pas encore de données.")
                    } else {
                        val palette = ChartPalette.macros()
                        BarChart(
                            bars = state.intake.map { BarValue(it.start, listOf(it.nutrition.protein, it.nutrition.fat, it.nutrition.carbs)) },
                            slots = state.intakeSlots,
                            colors = palette,
                            formatValue = { "${it.roundToInt()}" },
                            formatDate = dateFormat,
                            description = "Macronutriments par jour",
                            tooltipText = {
                                "${dateFormat(it.date)} : P ${it.segments[0].roundToInt()} · L ${it.segments[1].roundToInt()} · G ${it.segments[2].roundToInt()} g"
                            },
                        )
                        // Légende avec moyennes : l'identité ne repose jamais sur la seule couleur.
                        val avg = average(state.intake.map { it.nutrition })
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LegendItem(palette[0], "Protéines", "${avg.protein.roundToInt()} g")
                            LegendItem(palette[1], "Lipides", "${avg.fat.roundToInt()} g")
                            LegendItem(palette[2], "Glucides", "${avg.carbs.roundToInt()} g")
                        }
                        Text(
                            "Fibres : ${avg.fiber.roundToInt()} g par jour en moyenne (repère : 30 g).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                ChartCard("Séances effectuées par semaine") {
                    BarChart(
                        bars = state.sessions.map { BarValue(it.weekStart, listOf(it.count.toDouble())) },
                        slots = state.sessions.map { it.weekStart },
                        colors = listOf(MaterialTheme.colorScheme.secondary),
                        formatValue = { "${it.roundToInt()}" },
                        formatDate = { "sem. ${Format.numericDate(it)}" },
                        description = "Séances par semaine : ${state.sessions.sumOf { it.count }} au total sur la période",
                        tooltipText = { "Semaine du ${Format.numericDate(it.date)} : ${it.segments.sum().roundToInt()} séance(s)" },
                    )
                    Text(
                        "${state.sessions.sumOf { it.count }} séance(s) sur la période",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { DisclaimerCard(stringResource(R.string.disclaimer_not_medical)) }
        }
    }

    if (weightDialog) {
        NumberDialog(
            title = "Pesée du jour",
            label = "Poids (${Format.weightUnit(state.unit)})",
            initial = state.summary?.currentKg?.let { Format.decimal(Format.weightValue(it, state.unit)) } ?: "",
            parse = { Format.parseWeight(it, state.unit) },
            valid = { it in 30.0..300.0 },
            onDismiss = { weightDialog = false },
            onConfirm = { viewModel.saveWeight(it); weightDialog = false },
        )
    }
    if (intakeDialog) {
        IntakeDialog(
            initial = state.extraToday,
            onDismiss = { intakeDialog = false },
            onConfirm = { viewModel.saveExtraIntake(it); intakeDialog = false },
        )
    }
}

@Composable
private fun GoalCard(summary: GoalSummary, unit: UnitSystem, onAddWeight: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Poids actuel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Format.weight(summary.currentKg, unit), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onAddWeight) {
                    Icon(Icons.Outlined.MonitorWeight, contentDescription = null)
                    Text("  Peser")
                }
            }
            val progress = summary.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp))
                Text(
                    "${(progress * 100).roundToInt()} % du chemin entre ${Format.weight(summary.startKg, unit)} et ${Format.weight(summary.targetKg, unit)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val gap = summary.currentKg - summary.targetKg
                Text(
                    if (abs(gap) < 0.05) "Pile sur ta cible de maintien." else "Écart à ta cible de maintien : ${if (gap > 0) "+" else "−"}${Format.weight(abs(gap), unit)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                when (summary.goal) {
                    GoalType.LOSE -> "Objectif : perte de poids · ${summary.kcalTarget} kcal par jour"
                    GoalType.GAIN -> "Objectif : prise de poids · ${summary.kcalTarget} kcal par jour"
                    GoalType.MAINTAIN -> "Objectif : maintien · ${summary.kcalTarget} kcal par jour"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun EmptyChart(text: String) {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(" $label $value", style = MaterialTheme.typography.bodySmall)
    }
}

private fun average(list: List<Nutrition>): Nutrition =
    if (list.isEmpty()) Nutrition.ZERO else list.fold(Nutrition.ZERO) { acc, n -> acc + n } * (1.0 / list.size)

@Composable
private fun NumberDialog(
    title: String,
    label: String,
    initial: String,
    parse: (String) -> Double?,
    valid: (Double) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val value = parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> if (v.all { it.isDigit() || it == ',' || it == '.' } && v.length <= 6) text = v },
                label = { Text(label) },
                singleLine = true,
                isError = value != null && !valid(value),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null && valid(value)) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Autres apports du jour (en plus du plat du jour) : kcal obligatoires, macros facultatives. */
@Composable
private fun IntakeDialog(initial: DailyIntakeEntity?, onDismiss: () -> Unit, onConfirm: (Nutrition) -> Unit) {
    fun Double.text() = if (this == 0.0) "" else this.roundToInt().toString()
    var kcal by remember { mutableStateOf(initial?.kcal?.text() ?: "") }
    var protein by remember { mutableStateOf(initial?.proteinG?.text() ?: "") }
    var fat by remember { mutableStateOf(initial?.fatG?.text() ?: "") }
    var carbs by remember { mutableStateOf(initial?.carbsG?.text() ?: "") }
    var fiber by remember { mutableStateOf(initial?.fiberG?.text() ?: "") }
    val kcalValue = kcal.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Autres apports du jour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Total de ce que tu as mangé aujourd'hui en dehors du plat du jour. Remplace la saisie précédente.",
                    style = MaterialTheme.typography.bodySmall,
                )
                IntField("Calories (kcal)", kcal) { kcal = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntField("Prot. (g)", protein, Modifier.weight(1f)) { protein = it }
                    IntField("Lip. (g)", fat, Modifier.weight(1f)) { fat = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntField("Gluc. (g)", carbs, Modifier.weight(1f)) { carbs = it }
                    IntField("Fibres (g)", fiber, Modifier.weight(1f)) { fiber = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        Nutrition(
                            kcal = kcalValue ?: 0.0,
                            protein = protein.toDoubleOrNull() ?: 0.0,
                            fat = fat.toDoubleOrNull() ?: 0.0,
                            carbs = carbs.toDoubleOrNull() ?: 0.0,
                            fiber = fiber.toDoubleOrNull() ?: 0.0,
                        ),
                    )
                },
                enabled = kcalValue != null && kcalValue <= 10_000,
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun IntField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 5) onChange(v) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
