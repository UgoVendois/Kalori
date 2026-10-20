package fr.kalori.app.feature.workout

import androidx.annotation.DrawableRes
import fr.kalori.app.R
import fr.kalori.app.domain.model.ExerciseIcon

/** Pictogramme original (tools/gen_exercise_icons.py) associé à chaque type de mouvement. */
@DrawableRes
fun ExerciseIcon.drawableRes(): Int = when (this) {
    ExerciseIcon.SQUAT -> R.drawable.ic_exercise_squat
    ExerciseIcon.HINGE -> R.drawable.ic_exercise_hinge
    ExerciseIcon.LUNGE -> R.drawable.ic_exercise_lunge
    ExerciseIcon.BRIDGE -> R.drawable.ic_exercise_bridge
    ExerciseIcon.CALF -> R.drawable.ic_exercise_calf
    ExerciseIcon.PUSH_HORIZONTAL -> R.drawable.ic_exercise_push_horizontal
    ExerciseIcon.PUSH_VERTICAL -> R.drawable.ic_exercise_push_vertical
    ExerciseIcon.PULL_HORIZONTAL -> R.drawable.ic_exercise_pull_horizontal
    ExerciseIcon.PULL_VERTICAL -> R.drawable.ic_exercise_pull_vertical
    ExerciseIcon.DIP -> R.drawable.ic_exercise_dip
    ExerciseIcon.RAISE -> R.drawable.ic_exercise_raise
    ExerciseIcon.CURL -> R.drawable.ic_exercise_curl
    ExerciseIcon.TRICEPS -> R.drawable.ic_exercise_triceps
    ExerciseIcon.CORE -> R.drawable.ic_exercise_core
    ExerciseIcon.PLANK -> R.drawable.ic_exercise_plank
    ExerciseIcon.CARDIO -> R.drawable.ic_exercise_cardio
    ExerciseIcon.JUMP -> R.drawable.ic_exercise_jump
}
