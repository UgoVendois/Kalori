package fr.kalori.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import fr.kalori.app.R
import kotlin.reflect.KClass

/** Les 4 onglets de la barre du bas, dans l'ordre d'affichage. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val labelRes: Int,
) {
    Tracking(TrackingGraph, TrackingGraph::class, Icons.Filled.Insights, Icons.Outlined.Insights, R.string.tab_tracking),
    Meals(MealsGraph, MealsGraph::class, Icons.Filled.Restaurant, Icons.Outlined.Restaurant, R.string.tab_meals),
    Workout(WorkoutGraph, WorkoutGraph::class, Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter, R.string.tab_workout),
    Settings(SettingsGraph, SettingsGraph::class, Icons.Filled.Settings, Icons.Outlined.Settings, R.string.tab_settings),
}
