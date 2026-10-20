package fr.kalori.app.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import fr.kalori.app.navigation.GoalEditRoute
import fr.kalori.app.navigation.LicensesRoute
import fr.kalori.app.navigation.SettingsGraph
import fr.kalori.app.navigation.SettingsRoute

fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    navigation<SettingsGraph>(startDestination = SettingsRoute) {
        composable<SettingsRoute> {
            SettingsScreen(
                onEditGoal = { navController.navigate(GoalEditRoute) },
                onOpenLicenses = { navController.navigate(LicensesRoute) },
            )
        }
        composable<GoalEditRoute> { GoalEditScreen(onDone = { navController.popBackStack() }) }
        composable<LicensesRoute> { LicensesScreen(onBack = { navController.popBackStack() }) }
    }
}
