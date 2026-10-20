package fr.kalori.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.kalori.app.feature.meals.MealsScreen
import fr.kalori.app.feature.settings.SettingsScreen
import fr.kalori.app.feature.tracking.TrackingScreen
import fr.kalori.app.feature.workout.WorkoutScreen

@Composable
fun KaloriRoot(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            KaloriBottomBar(
                currentDestination = currentDestination,
                onNavigate = { destination -> navController.navigateToTopLevel(destination) },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TrackingRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TrackingRoute> { TrackingScreen() }
            composable<MealsRoute> { MealsScreen() }
            composable<WorkoutRoute> { WorkoutScreen() }
            composable<SettingsRoute> { SettingsScreen() }
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
