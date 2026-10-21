package fr.kalori.app.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.DisclaimerKind
import fr.kalori.app.core.ui.components.IconBadge
import fr.kalori.app.core.ui.theme.Accent
import fr.kalori.app.core.ui.theme.Kalori
import fr.kalori.app.feature.goal.GoalEditorViewModel
import fr.kalori.app.feature.goal.GoalForm
import fr.kalori.app.feature.meals.FoodPreferencesViewModel
import fr.kalori.app.feature.meals.actions
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
    LaunchedEffect(step) { if (step == STEP_PREFERENCES) prefsViewModel.refreshSchedule() }

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
                        foodPreferencesItems(prefs, prefsViewModel.actions())
                    }
                    BottomActions(
                        onBack = { step = STEP_GOAL },
                        primaryLabel = "Terminer",
                        primaryEnabled = prefs.loaded,
                        onPrimary = { prefsViewModel.flush(onFinished) },
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Brush.linearGradient(Kalori.colors.heroGradient))
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                Text("Kalori", style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text(
                    "Tes repas de la journée, ta liste de courses et tes séances, au même endroit.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }
        Feature(Icons.Outlined.Restaurant, Kalori.colors.noon, "Repas sur mesure", "Matin, midi, soir et collation, adaptés à ton objectif et à tes goûts.")
        Feature(Icons.Outlined.ShoppingCart, Kalori.colors.snack, "Courses de la semaine", "Regroupées par rayon, à cocher ou copier en un geste.")
        Feature(Icons.Outlined.FitnessCenter, Kalori.colors.evening, "Séances Salle et Street", "Prêtes à l'emploi ou créées par toi.")
        Feature(Icons.Outlined.Insights, Kalori.colors.morning, "Ta progression", "Poids, calories, macros et séances, en un coup d'œil.")
        DisclaimerCard(stringResource(R.string.disclaimer_not_medical))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !restoring) {
            Text("Commencer", style = MaterialTheme.typography.titleMedium)
        }
        TextButton(
            onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !restoring,
        ) { Text(if (restoring) "Restauration…" else "Restaurer une sauvegarde Kalori") }
        restoreError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Feature(icon: ImageVector, accent: Accent, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBadge(icon, accent, size = 44.dp)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
