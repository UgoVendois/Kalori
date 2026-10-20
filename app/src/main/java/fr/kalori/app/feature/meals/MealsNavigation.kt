package fr.kalori.app.feature.meals

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import fr.kalori.app.navigation.FoodPreferencesRoute
import fr.kalori.app.navigation.MealDetailRoute
import fr.kalori.app.navigation.MealHistoryRoute
import fr.kalori.app.navigation.MealsGraph
import fr.kalori.app.navigation.MealsRoute
import fr.kalori.app.navigation.ShoppingListRoute

fun NavGraphBuilder.mealsGraph(navController: NavHostController) {
    navigation<MealsGraph>(startDestination = MealsRoute) {
        composable<MealsRoute> {
            MealsScreen(
                onOpenDay = { navController.navigate(MealDetailRoute(it.toEpochDay())) },
                onOpenPreferences = { navController.navigate(FoodPreferencesRoute) },
                onOpenHistory = { navController.navigate(MealHistoryRoute) },
                onOpenShopping = { navController.navigate(ShoppingListRoute) },
            )
        }
        composable<MealDetailRoute> { MealDetailScreen(onBack = { navController.popBackStack() }) }
        composable<MealHistoryRoute> {
            MealHistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenDay = { navController.navigate(MealDetailRoute(it.toEpochDay())) },
            )
        }
        composable<FoodPreferencesRoute> { FoodPreferencesScreen(onDone = { navController.popBackStack() }) }
        composable<ShoppingListRoute> { ShoppingListScreen(onBack = { navController.popBackStack() }) }
    }
}
