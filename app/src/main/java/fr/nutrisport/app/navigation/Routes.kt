package fr.nutrisport.app.navigation

import kotlinx.serialization.Serializable

// Routes type-safe (Navigation Compose 2.8+). Les sous-écrans s'ajouteront à chaque étape.

@Serializable data object TrackingRoute

@Serializable data object MealsRoute

@Serializable data object WorkoutRoute

@Serializable data object SettingsRoute
