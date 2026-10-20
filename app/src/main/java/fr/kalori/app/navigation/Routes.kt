package fr.kalori.app.navigation

import kotlinx.serialization.Serializable

// Routes type-safe (Navigation Compose 2.8+). Chaque onglet est un sous-graphe : l'onglet reste
// sélectionné quand on ouvre un de ses sous-écrans.

// ── Suivi ──
@Serializable data object TrackingGraph
@Serializable data object TrackingRoute

// ── Repas ──
@Serializable data object MealsGraph
@Serializable data object MealsRoute
@Serializable data class MealDetailRoute(val epochDay: Long)
@Serializable data object MealHistoryRoute
@Serializable data object FoodPreferencesRoute
@Serializable data object ShoppingListRoute

// ── Séance ──
@Serializable data object WorkoutGraph
@Serializable data object WorkoutRoute

// ── Paramètres ──
@Serializable data object SettingsGraph
@Serializable data object SettingsRoute
