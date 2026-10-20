package fr.kalori.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import fr.kalori.app.feature.lock.LockScreen
import fr.kalori.app.feature.meals.mealsGraph
import fr.kalori.app.feature.onboarding.OnboardingScreen
import fr.kalori.app.feature.settings.SettingsScreen
import fr.kalori.app.feature.tracking.TrackingScreen
import fr.kalori.app.feature.workout.WorkoutScreen

@Composable
fun KaloriRoot(appViewModel: AppViewModel = hiltViewModel()) {
    val gate by appViewModel.gate.collectAsStateWithLifecycle()
    val resetNotice by appViewModel.dataResetNotice.collectAsStateWithLifecycle()

    when (gate) {
        AppGate.LOADING -> Surface(Modifier.fillMaxSize()) {}
        AppGate.ONBOARDING -> OnboardingScreen(onFinished = appViewModel::onboardingFinished)
        AppGate.LOCKED -> LockScreen(onUnlocked = appViewModel::unlocked, onDisableLock = appViewModel::disableLock)
        AppGate.READY -> MainScaffold()
    }

    if (resetNotice && gate == AppGate.READY) {
        AlertDialog(
            onDismissRequest = appViewModel::dismissResetNotice,
            confirmButton = { TextButton(onClick = appViewModel::dismissResetNotice) { Text("Compris") } },
            title = { Text("Données réinitialisées") },
            text = {
                Text(
                    "La clé de chiffrement de tes données n'était plus disponible (réinitialisation du téléphone ou " +
                        "du verrouillage). Les données précédentes sont illisibles et ont été effacées. Tu peux " +
                        "réimporter un export depuis Paramètres.",
                )
            },
        )
    }
}

@Composable
private fun MainScaffold(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        // Les écrans gèrent eux-mêmes la barre d'état (TopAppBar) ; la barre d'onglets gère le bas.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            KaloriBottomBar(
                currentDestination = currentDestination,
                onNavigate = { destination -> navController.navigateToTopLevel(destination) },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TrackingGraph,
            modifier = Modifier.padding(innerPadding),
        ) {
            navigation<TrackingGraph>(startDestination = TrackingRoute) {
                composable<TrackingRoute> { TrackingScreen() }
            }
            mealsGraph(navController)
            navigation<WorkoutGraph>(startDestination = WorkoutRoute) {
                composable<WorkoutRoute> { WorkoutScreen() }
            }
            navigation<SettingsGraph>(startDestination = SettingsRoute) {
                composable<SettingsRoute> { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun KaloriBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination.isOnTab(destination)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

private fun NavDestination?.isOnTab(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true

/** Navigation entre onglets : une seule instance par onglet, état de chaque onglet conservé. */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
