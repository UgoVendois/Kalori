package fr.kalori.app.feature.workout

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import fr.kalori.app.navigation.ExerciseLibraryRoute
import fr.kalori.app.navigation.WorkoutEditorRoute
import fr.kalori.app.navigation.WorkoutGraph
import fr.kalori.app.navigation.WorkoutPlayerRoute
import fr.kalori.app.navigation.WorkoutRoute

fun NavGraphBuilder.workoutGraph(navController: NavHostController) {
    navigation<WorkoutGraph>(startDestination = WorkoutRoute) {
        composable<WorkoutRoute> {
            WorkoutScreen(
                onCreate = { navController.navigate(WorkoutEditorRoute()) },
                onEdit = { navController.navigate(WorkoutEditorRoute(it)) },
                onStart = { navController.navigate(WorkoutPlayerRoute(it)) },
                onOpenLibrary = { navController.navigate(ExerciseLibraryRoute) },
            )
        }
        composable<WorkoutEditorRoute> { WorkoutEditorScreen(onDone = { navController.popBackStack() }) }
        composable<WorkoutPlayerRoute> { WorkoutPlayerScreen(onDone = { navController.popBackStack() }) }
        composable<ExerciseLibraryRoute> { ExerciseLibraryScreen(onBack = { navController.popBackStack() }) }
    }
}
