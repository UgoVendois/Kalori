package fr.kalori.app.feature.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.kalori.app.data.profile.GoalService
import fr.kalori.app.data.profile.ProfileDraft
import fr.kalori.app.domain.model.ActivityLevel
import fr.kalori.app.domain.model.Sex
import fr.kalori.app.domain.nutrition.PaceOption
import fr.kalori.app.domain.nutrition.PacePlan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalFormState(
    val loaded: Boolean = false,
    val sex: Sex? = null,
    val birthYear: String = "",
    val birthMonth: String = "",
    val height: String = "",
    val weight: String = "",
    val target: String = "",
    val activity: ActivityLevel? = null,
    /** Utilisé pour le calcul uniquement, jamais enregistré. */
    val pregnantOrBreastfeeding: Boolean = false,
    val plan: PacePlan? = null,
    val selectedPace: Double? = null,
    val saving: Boolean = false,
) {
    val canSave: Boolean
        get() = !saving && plan is PacePlan.Available && plan.options.any { it.paceKgPerWeek == selectedPace }
}

@HiltViewModel
class GoalEditorViewModel @Inject constructor(
    private val goalService: GoalService,
) : ViewModel() {

    private val _state = MutableStateFlow(GoalFormState())
    val state: StateFlow<GoalFormState> = _state.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.CONFLATED)
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val draft = goalService.currentDraft()
            val pace = goalService.currentPace()
            _state.update { s ->
                if (draft == null) {
                    s.copy(loaded = true)
                } else {
                    s.copy(
                        loaded = true,
                        sex = draft.sex,
                        birthYear = draft.birthYear.toString(),
                        birthMonth = draft.birthMonth.toString(),
                        height = draft.heightCm.clean(),
                        weight = draft.weightKg.clean(),
                        target = draft.targetWeightKg.clean(),
                        activity = draft.activity,
                        selectedPace = pace,
                    )
                }
            }
            recompute()
        }
    }

    fun edit(change: (GoalFormState) -> GoalFormState) {
        _state.update(change)
        recompute()
    }

    fun select(option: PaceOption) = _state.update { it.copy(selectedPace = option.paceKgPerWeek) }

    fun save() {
        val s = _state.value
        val plan = s.plan as? PacePlan.Available ?: return
        val option = plan.options.firstOrNull { it.paceKgPerWeek == s.selectedPace } ?: return
        val draft = s.toDraft() ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                goalService.saveGoal(draft, plan, option)
                savedChannel.send(Unit)
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private fun recompute() = _state.update { s ->
        val draft = s.toDraft()
        val plan = draft?.let { goalService.plan(it, s.pregnantOrBreastfeeding) }
        // Le maintien n'a qu'une option : on la sélectionne d'office ; sinon on garde le choix s'il existe encore.
        val options = (plan as? PacePlan.Available)?.options.orEmpty()
        val selected = when {
            options.size == 1 -> options.single().paceKgPerWeek
            options.any { it.paceKgPerWeek == s.selectedPace } -> s.selectedPace
            else -> null
        }
        s.copy(plan = plan, selectedPace = selected)
    }

    private fun GoalFormState.toDraft(): ProfileDraft? {
        val year = birthYear.toIntOrNull() ?: return null
        val month = birthMonth.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        if (year !in 1900..goalService.today().year) return null
        return ProfileDraft(
            sex = sex ?: return null,
            birthYear = year,
            birthMonth = month,
            heightCm = height.toDecimal() ?: return null,
            weightKg = weight.toDecimal() ?: return null,
            targetWeightKg = target.toDecimal() ?: return null,
            activity = activity ?: return null,
        )
    }

    private fun String.toDecimal(): Double? = replace(',', '.').trim().toDoubleOrNull()

    private fun Double.clean(): String =
        if (this % 1.0 == 0.0) toInt().toString() else toString().replace('.', ',')
}

/** Formulaire du profil et de l'objectif, suivi du choix du rythme. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalForm(
    state: GoalFormState,
    onEdit: ((GoalFormState) -> GoalFormState) -> Unit,
    onSelect: (PaceOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sexe (pour l'estimation de la dépense)", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { sex ->
                FilterChip(
                    selected = state.sex == sex,
                    onClick = { onEdit { it.copy(sex = sex, pregnantOrBreastfeeding = it.pregnantOrBreastfeeding && sex != Sex.MALE) } },
                    label = { Text(sex.label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Année de naissance", state.birthYear, Modifier.weight(1f), decimal = false) { v -> onEdit { it.copy(birthYear = v) } }
            NumberField("Mois (1-12)", state.birthMonth, Modifier.weight(1f), decimal = false) { v -> onEdit { it.copy(birthMonth = v) } }
        }
        NumberField("Taille (cm)", state.height, Modifier.fillMaxWidth()) { v -> onEdit { it.copy(height = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Poids actuel (kg)", state.weight, Modifier.weight(1f)) { v -> onEdit { it.copy(weight = v) } }
            NumberField("Poids cible (kg)", state.target, Modifier.weight(1f)) { v -> onEdit { it.copy(target = v) } }
        }

        Text("Niveau d'activité", style = MaterialTheme.typography.titleSmall)
        ActivityLevel.entries.forEach { level ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = state.activity == level, onClick = { onEdit { it.copy(activity = level) } }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.activity == level, onClick = { onEdit { it.copy(activity = level) } })
                Column {
                    Text(level.label, style = MaterialTheme.typography.bodyLarge)
                    Text(level.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (state.sex != null && state.sex != Sex.MALE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Enceinte ou en période d'allaitement (non enregistré)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.pregnantOrBreastfeeding,
                    onCheckedChange = { checked -> onEdit { it.copy(pregnantOrBreastfeeding = checked) } },
                )
            }
        }

        state.plan?.let { plan ->
            Text("Choisis ton rythme", style = MaterialTheme.typography.titleMedium)
            PaceChooser(plan = plan, selectedPace = state.selectedPace, onSelect = onSelect)
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier,
    decimal: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all { it.isDigit() || (decimal && (it == ',' || it == '.')) } && v.length <= 6) onChange(v) },
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}
