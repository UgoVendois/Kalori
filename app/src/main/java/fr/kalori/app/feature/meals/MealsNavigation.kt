package fr.kalori.app.feature.meals

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import fr.kalori.app.data.meal.DayMeal
import fr.kalori.app.navigation.FoodPreferencesRoute
import fr.kalori.app.navigation.MealDetailRoute
import fr.kalori.app.navigation.MealHistoryRoute
import fr.kalori.app.navigation.MealsGraph
import fr.kalori.app.navigation.MealsRoute
import fr.kalori.app.navigation.ShoppingListRoute

fun NavGraphBuilder.mealsGraph(navController: NavHostController) {
    val openMeal: (DayMeal) -> Unit = { navController.navigate(MealDetailRoute(it.date.toEpochDay(), it.slot.name)) }
    navigation<MealsGraph>(startDestination = MealsRoute) {
        composable<MealsRoute> {
            MealsScreen(
                onOpenMeal = openMeal,
                onOpenPreferences = { navController.navigate(FoodPreferencesRoute) },
                onOpenHistory = { navController.navigate(MealHistoryRoute) },
                onOpenShopping = { navController.navigate(ShoppingListRoute) },
            )
        }
        composable<MealDetailRoute> { MealDetailScreen(onBack = { navController.popBackStack() }) }
        composable<MealHistoryRoute> { MealHistoryScreen(onBack = { navController.popBackStack() }, onOpenMeal = openMeal) }
        composable<FoodPreferencesRoute> { FoodPreferencesScreen(onBack = { navController.popBackStack() }) }
        composable<ShoppingListRoute> { ShoppingListScreen(onBack = { navController.popBackStack() }) }
    }
}
