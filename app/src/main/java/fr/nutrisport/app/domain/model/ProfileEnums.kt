package fr.nutrisport.app.domain.model

enum class Sex(val label: String) {
    FEMALE("Femme"),
    MALE("Homme"),
    /** Les calculs utilisent alors la moyenne des formules homme et femme. */
    UNSPECIFIED("Je préfère ne pas répondre"),
}

/** Facteurs d'activité physique appliqués au métabolisme de base (Mifflin-St Jeor). */
enum class ActivityLevel(val factor: Double, val label: String, val description: String) {
    SEDENTARY(1.2, "Sédentaire", "Travail assis, peu ou pas de sport"),
    LIGHT(1.375, "Légèrement actif", "Sport léger 1 à 3 fois par semaine"),
    MODERATE(1.55, "Modérément actif", "Sport modéré 3 à 5 fois par semaine"),
    ACTIVE(1.725, "Très actif", "Sport intense 6 à 7 fois par semaine"),
    VERY_ACTIVE(1.9, "Extrêmement actif", "Travail physique et entraînement quotidien"),
}

enum class GoalType(val label: String) {
    LOSE("Perdre du poids"),
    MAINTAIN("Me maintenir"),
    GAIN("Prendre du poids"),
}

enum class UnitSystem { METRIC, IMPERIAL }

enum class ConsentType {
    /** Traitement des données de santé (art. 9.2.a RGPD) — indispensable au fonctionnement. */
    HEALTH_DATA,
    /** Envoi d'une requête minimisée au fournisseur d'IA (variantes de plats, Premium). */
    AI_TRANSFER,
    /** Rapports de plantage anonymisés. */
    CRASH_REPORTS,
}
