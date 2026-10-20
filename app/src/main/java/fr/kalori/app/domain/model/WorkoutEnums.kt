package fr.kalori.app.domain.model

enum class ExerciseCategory(val label: String) {
    GYM("Salle"),
    STREET("Street"),
}

enum class MuscleGroup(val label: String) {
    CHEST("Pectoraux"),
    BACK("Dos"),
    SHOULDERS("Épaules"),
    ARMS("Bras"),
    LEGS("Jambes"),
    GLUTES("Fessiers"),
    CORE("Abdominaux et gainage"),
    FULL_BODY("Corps entier"),
    CARDIO("Cardio"),
}

/** Pictogramme d'un exercice, par type de mouvement (icônes originales, voir ASSETS_LICENSES.md). */
enum class ExerciseIcon {
    SQUAT, HINGE, LUNGE, BRIDGE, CALF,
    PUSH_HORIZONTAL, PUSH_VERTICAL, PULL_HORIZONTAL, PULL_VERTICAL,
    DIP, RAISE, CURL, TRICEPS,
    CORE, PLANK, CARDIO, JUMP,
}

enum class WorkoutOrigin { PREMADE, USER }

/** Objectif auquel une séance pré-faite est adaptée. */
enum class WorkoutGoal { ANY, LOSS, MAINTAIN, GAIN }
