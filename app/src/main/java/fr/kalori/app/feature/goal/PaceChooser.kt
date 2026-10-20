package fr.kalori.app.feature.goal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.kalori.app.R
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.DisclaimerKind
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.nutrition.PaceOption
import fr.kalori.app.domain.nutrition.PacePlan

/** Affiche le résultat de PaceOptionsGenerator : refus expliqué, ou options à choisir. */
@Composable
fun PaceChooser(
    plan: PacePlan,
    selectedPace: Double?,
    onSelect: (PaceOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DisclaimerCard(stringResource(R.string.disclaimer_not_medical))
        when (plan) {
            is PacePlan.Blocked -> DisclaimerCard(
                GoalTexts.blocked(plan.reason, plan.minTargetWeightKg),
                kind = DisclaimerKind.Warning,
            )
            is PacePlan.Available -> {
                Text(
                    "Dépense quotidienne estimée : ${plan.maintenanceKcal} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                )
                plan.warnings.forEach { DisclaimerCard(GoalTexts.warning(it), kind = DisclaimerKind.Warning) }
                plan.options.forEach { option ->
                    PaceCard(
                        option = option,
                        goal = plan.goal,
                        selected = option.paceKgPerWeek == selectedPace,
                        onClick = { onSelect(option) },
                    )
                }
                plan.hidden.forEach {
                    Text(GoalTexts.hidden(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (plan.goal != GoalType.MAINTAIN) {
                    Text(GoalTexts.ESTIMATE_NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PaceCard(option: PaceOption, goal: GoalType, selected: Boolean, onClick: () -> Unit) {
    val t = option.targets
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = onClick)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when (goal) {
                        GoalType.MAINTAIN -> "Maintien"
                        GoalType.LOSE -> "Perdre ${Format.decimal(option.paceKgPerWeek, 2)} kg par semaine"
                        GoalType.GAIN -> "Prendre ${Format.decimal(option.paceKgPerWeek, 2)} kg par semaine"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("${t.kcal} kcal par jour", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Protéines ${t.proteinG} g · Lipides ${t.fatG} g · Glucides ${t.carbsG} g · Fibres ${t.fiberG} g",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (option.weeksToTarget != null && option.estimatedArrival != null) {
                    Text(
                        "Durée estimée : ${option.weeksToTarget} semaines · arrivée vers le ${Format.numericDate(option.estimatedArrival)}/${option.estimatedArrival.year}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                t.notices.forEach {
                    Text(GoalTexts.notice(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
