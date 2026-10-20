package fr.kalori.app.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.DisclaimerKind
import fr.kalori.app.feature.goal.GoalEditorViewModel
import fr.kalori.app.feature.goal.GoalForm
import fr.kalori.app.feature.meals.FoodPreferencesViewModel
import fr.kalori.app.feature.meals.foodPreferencesItems

private const val STEP_WELCOME = 0
private const val STEP_GOAL = 1
private const val STEP_PREFERENCES = 2

/** Premier lancement : avertissements, profil et rythme choisi, goûts et restrictions. */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    goalViewModel: GoalEditorViewModel = hiltViewModel(),
    prefsViewModel: FoodPreferencesViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }
    val goal by goalViewModel.state.collectAsStateWithLifecycle()
    val prefs by prefsViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(goalViewModel) { goalViewModel.saved.collect { step = STEP_PREFERENCES } }
    LaunchedEffect(prefsViewModel) { prefsViewModel.saved.collect { onFinished() } }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
            when (step) {
                STEP_WELCOME -> Welcome(onStart = { step = STEP_GOAL }, viewModel = onboardingViewModel)
                STEP_GOAL -> Column(Modifier.weight(1f)) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Ton profil et ton objectif", style = MaterialTheme.typography.headlineSmall)
                        GoalForm(state = goal, onEdit = goalViewModel::edit, onSelect = goalViewModel::select)
                    }
                    BottomActions(
                        onBack = { step = STEP_WELCOME },
                        primaryLabel = "Valider ce rythme",
                        primaryEnabled = goal.canSave,
                        onPrimary = goalViewModel::save,
                    )
                }
                else -> Column(Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Text(stringResource(R.string.meals_preferences), style = MaterialTheme.typography.headlineSmall) }
                        item { DisclaimerCard(stringResource(R.string.disclaimer_allergens), kind = DisclaimerKind.Warning) }
                        foodPreferencesItems(
                            state = prefs,
                            onToggleAllergen = prefsViewModel::toggleAllergen,
                            onToggleDiet = prefsViewModel::toggleDiet,
                            onQueryChange = prefsViewModel::setQuery,
                            onToggleLike = prefsViewModel::toggleLike,
                            onToggleDislike = prefsViewModel::toggleDislike,
                        )
                    }
                    BottomActions(
                        onBack = { step = STEP_GOAL },
                        primaryLabel = "Terminer",
                        primaryEnabled = prefs.loaded && !prefs.saving,
                        onPrimary = prefsViewModel::save,
                    )
                }
            }
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, viewModel: OnboardingViewModel) {
    val restoring by viewModel.restoring.collectAsStateWithLifecycle()
    val restoreError by viewModel.restoreError.collectAsStateWithLifecycle()
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restore)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Bienvenue dans Kalori", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Un plat par jour adapté à ton objectif, la liste de courses de la semaine, tes séances de sport " +
                "et le suivi de ta progression. Tout reste sur ce téléphone, dans une base chiffrée.",
            style = MaterialTheme.typography.bodyLarge,
        )
        DisclaimerCard(stringResource(R.string.disclaimer_not_medical), kind = DisclaimerKind.Warning)
        DisclaimerCard(stringResource(R.string.disclaimer_allergens), kind = DisclaimerKind.Warning)
        Text(
            "Kalori ne choisit pas à ta place : tu renseignes ton profil, l'app te propose des rythmes compatibles " +
                "avec des repères officiels (OMS, HAS, Anses, NICE) et tu choisis.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), enabled = !restoring) { Text("Commencer") }
        TextButton(
            onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !restoring,
        ) { Text(if (restoring) "Restauration…" else "Restaurer une sauvegarde Kalori") }
        restoreError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun BottomActions(onBack: () -> Unit, primaryLabel: String, primaryEnabled: Boolean, onPrimary: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Button(onClick = onPrimary, enabled = primaryEnabled) { Text(primaryLabel) }
    }
}
