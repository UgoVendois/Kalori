package fr.kalori.app.feature.goal

import fr.kalori.app.core.ui.Format
import fr.kalori.app.domain.nutrition.BlockReason
import fr.kalori.app.domain.nutrition.HiddenPace
import fr.kalori.app.domain.nutrition.HiddenReason
import fr.kalori.app.domain.nutrition.MacroNotice
import fr.kalori.app.domain.nutrition.PlanWarning

/**
 * Textes des garde-fous. Formulations sans allégation médicale ni promesse de résultat :
 * on décrit une estimation et on renvoie vers un professionnel quand c'est nécessaire.
 */
object GoalTexts {

    fun blocked(reason: BlockReason, minTargetWeightKg: Double?): String = when (reason) {
        BlockReason.INVALID_INPUT ->
            "Vérifie tes informations : taille entre 120 et 230 cm, poids entre 30 et 300 kg."
        BlockReason.MINOR ->
            "Kalori ne propose pas d'objectif de poids avant 18 ans. Les besoins pendant la croissance " +
                "sont particuliers : parles-en à ton médecin ou à un diététicien."
        BlockReason.PREGNANCY_OR_BREASTFEEDING ->
            "Pendant la grossesse ou l'allaitement, les besoins changent et doivent être suivis par un " +
                "professionnel de santé (médecin, sage-femme, diététicien). Kalori ne propose pas d'objectif calorique."
        BlockReason.TARGET_BMI_TOO_LOW ->
            "Ce poids cible correspond à un IMC inférieur à 18,5, seuil de maigreur selon l'OMS. " +
                "Choisis un poids cible d'au moins ${Format.decimal(minTargetWeightKg ?: 0.0)} kg."
        BlockReason.NO_SAFE_LOSS_PACE ->
            "Aucun rythme de perte ne respecte les garde-fous pour ton profil (plafond de 1 % du poids par " +
                "semaine et apport minimal de sécurité). Tu peux choisir le maintien, et demander conseil à un " +
                "professionnel pour un objectif de perte."
    }

    fun hidden(hidden: HiddenPace): String {
        val pace = "${Format.decimal(hidden.paceKgPerWeek, 2)} kg/semaine"
        return when (hidden.reason) {
            HiddenReason.ABOVE_WEEKLY_CAP -> "$pace : trop rapide pour ton poids, non proposé."
            HiddenReason.BELOW_CALORIE_FLOOR -> "$pace : l'apport passerait sous le minimum de sécurité, non proposé."
        }
    }

    fun warning(warning: PlanWarning): String = when (warning) {
        PlanWarning.SENIOR_MUSCLE_LOSS ->
            "Après 70 ans, la perte de masse musculaire et la dénutrition sont des risques importants : " +
                "un suivi par un professionnel est recommandé, avec un apport suffisant en protéines et de l'activité physique."
        PlanWarning.LOW_ESTIMATED_NEEDS ->
            "Tes besoins estimés sont très bas : l'objectif a été relevé au minimum de sécurité. " +
                "Fais évaluer tes besoins par un professionnel."
    }

    fun notice(notice: MacroNotice): String = when (notice) {
        MacroNotice.PROTEIN_ABOVE_ANSES_RANGE ->
            "Protéines au-dessus du repère Anses pour la population générale (10 à 20 % de l'énergie), " +
                "fréquent chez les personnes sportives."
        MacroNotice.PROTEIN_REDUCED_FOR_CARBS ->
            "Protéines limitées pour garder au moins 40 % de glucides (repère Anses)."
    }

    const val ESTIMATE_NOTICE =
        "Durées et dates sont des estimations : la perte ou la prise ralentit souvent avec le temps " +
            "et varie d'une personne à l'autre."
}
