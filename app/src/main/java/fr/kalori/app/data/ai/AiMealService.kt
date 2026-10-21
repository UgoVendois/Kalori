package fr.kalori.app.data.ai

import fr.kalori.app.domain.model.CookingLevel
import java.util.Locale
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import fr.kalori.app.core.database.dao.CatalogDao
import fr.kalori.app.core.di.IoDispatcher
import fr.kalori.app.core.logging.SafeLogger
import fr.kalori.app.core.security.ApiKeyStore
import fr.kalori.app.data.meal.MealPlanRepository
import fr.kalori.app.domain.model.MainProtein
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.MealType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VariantResult {
    data class Applied(val name: String) : VariantResult
    data class Failed(val message: String) : VariantResult
}

/**
 * Variante de plat proposée par Claude (option désactivée par défaut, clé API personnelle).
 *
 * Données envoyées (minimisées) : la liste des ingrédients AUTORISÉS (déjà filtrée localement selon
 * allergènes, régimes, goûts et saison — les restrictions elles-mêmes ne sont pas envoyées), la cible du
 * repas (kcal, protéines, budget), le niveau de cuisine, le mois, les aliments aimés et les noms des plats de la
 * semaine à éviter.
 * Ni identifiant, ni poids, ni âge, ni allergie déclarée.
 * La réponse est revalidée et recalculée localement (AiRecipeValidator) avant d'être utilisée.
 */
@Singleton
class AiMealService @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val catalogDao: CatalogDao,
    private val mealPlanRepository: MealPlanRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun proposeVariant(date: LocalDate, slot: MealSlot): VariantResult = withContext(io) {
        val apiKey = apiKeyStore.read() ?: return@withContext VariantResult.Failed("Ajoute d'abord ta clé API dans Paramètres.")
        val context = mealPlanRepository.variantContext(date, slot) ?: return@withContext VariantResult.Failed("Aucun repas prévu à ce moment-là.")
        val ingredients = catalogDao.getIngredients()
        val allowed = AiRecipeValidator.allowedIngredients(ingredients, context.prefs, date.monthValue)
        if (allowed.size < 5) return@withContext VariantResult.Failed("Trop peu d'ingrédients compatibles avec tes restrictions.")
        val liked = ingredients.filter { it.id in context.prefs.liked }.map { it.name }

        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16_000L)
            .system(SYSTEM_PROMPT)
            .addUserMessage(
                buildString {
                    appendLine("Repas : ${mealKind(context.mealType)}")
                    appendLine("Mois : ${date.monthValue}")
                    appendLine("Cible pour ce plat : environ ${context.target.kcal} kcal et au moins ${context.target.proteinG} g de protéines.")
                    context.budget?.let { appendLine("Budget : environ ${"%.2f".format(Locale.FRANCE, it)} € d'ingrédients pour cette portion (produits courants de supermarché).") }
                    appendLine(
                        when (context.cookingLevel) {
                            CookingLevel.SIMPLE -> "Niveau de cuisine : simple (20 minutes environ, peu d'ingrédients et d'ustensiles)."
                            CookingLevel.NORMAL -> "Niveau de cuisine : courant (45 minutes au plus)."
                            CookingLevel.ELABORATE -> "Niveau de cuisine : élaboré accepté (mijotés, plusieurs préparations)."
                        },
                    )
                    if (context.mustTravel) appendLine("Le plat est cuisiné en double et emporté le lendemain midi : il doit se conserver un jour au frais et se réchauffer.")
                    if (liked.isNotEmpty()) appendLine("Aliments appréciés (à privilégier si possible) : ${liked.joinToString(", ")}")
                    appendLine("Plats déjà prévus cette semaine (à ne pas reproduire) : ${context.weekDishNames.joinToString(" ; ")}")
                    appendLine("Ingrédients autorisés (id : nom) :")
                    allowed.forEach { appendLine("${it.id} : ${it.name}") }
                },
            )
            .outputConfig(
                BetaOutputConfig.builder()
                    // Tâche simple : effort explicite (défaut du modèle), pas de réflexion longue nécessaire.
                    .effort(BetaOutputConfig.Effort.MEDIUM)
                    .format(BetaJsonOutputFormat.builder().schema(schema(allowed.map { it.id })).build())
                    .build(),
            )
            // Si la requête est refusée par un filtre de sécurité, l'API bascule elle-même sur un autre modèle.
            .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
            .fallbacksDefault()
            .build()

        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        val response = try {
            client.beta().messages().create(params)
        } catch (e: UnauthorizedException) {
            return@withContext VariantResult.Failed("Clé API refusée : vérifie-la dans Paramètres.")
        } catch (e: PermissionDeniedException) {
            return@withContext VariantResult.Failed("Cette clé API n'a pas accès au modèle.")
        } catch (e: RateLimitException) {
            return@withContext VariantResult.Failed("Trop de demandes pour l'instant, réessaie dans une minute.")
        } catch (e: AnthropicServiceException) {
            SafeLogger.e(TAG, e) { "anthropic service error ${e.statusCode()}" }
            return@withContext VariantResult.Failed("Le service d'IA est indisponible (erreur ${e.statusCode()}).")
        } catch (e: AnthropicIoException) {
            return@withContext VariantResult.Failed("Pas de connexion au service d'IA.")
        } finally {
            client.close()
        }

        if (response.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
            return@withContext VariantResult.Failed("L'IA n'a pas pu proposer de plat pour cette demande.")
        }
        val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        val proposal = runCatching { json.decodeFromString(AiProposal.serializer(), text) }.getOrElse {
            return@withContext VariantResult.Failed("Réponse de l'IA illisible, réessaie.")
        }
        when (
            val validation = AiRecipeValidator.validate(
                proposal = proposal,
                ingredients = ingredients.associateBy { it.id },
                allowedIds = allowed.map { it.id }.toSet(),
                prefs = context.prefs,
                mealType = context.mealType,
                model = response.model().toString(),
                recipeId = "ai_${UUID.randomUUID()}",
                now = Instant.now(clock),
            )
        ) {
            is AiValidation.Rejected -> VariantResult.Failed("Proposition écartée (${validation.reason}). Réessaie.")
            is AiValidation.Valid -> {
                mealPlanRepository.applyRecipe(date, slot, validation.recipe, validation.lines)
                VariantResult.Applied(validation.recipe.name)
            }
        }
    }

    private fun mealKind(type: MealType): String = when (type) {
        MealType.BREAKFAST -> "petit-déjeuner (repas du matin)"
        MealType.MAIN -> "plat principal (midi ou soir)"
        MealType.SNACK -> "collation légère"
    }

    /** Schéma imposé à la réponse : les identifiants d'ingrédients sont limités à la liste autorisée. */
    private fun schema(allowedIds: List<String>): BetaJsonOutputFormat.Schema {
        val line = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "id" to mapOf("type" to "string", "enum" to allowedIds),
                "grams" to mapOf("type" to "integer"),
            ),
            "required" to listOf("id", "grams"),
            "additionalProperties" to false,
        )
        return BetaJsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "name" to mapOf("type" to "string"),
                        "mainProtein" to mapOf("type" to "string", "enum" to MainProtein.entries.map { it.name }),
                        "ingredients" to mapOf("type" to "array", "items" to line),
                        "steps" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                    ),
                ),
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("name", "mainProtein", "ingredients", "steps")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }

    companion object {
        const val MODEL = "claude-opus-5-5"
        private const val TAG = "AiMealService"

        private val SYSTEM_PROMPT = """
            Tu es le cuisinier de Kalori, une application de nutrition. Propose UN repas pour une personne, du type
            indiqué (petit-déjeuner, plat principal ou collation), simple à préparer à la maison, de saison et
            différent des repas déjà prévus.

            Règles :
            - Utilise uniquement des ingrédients de la liste fournie, désignés par leur id exact. Ne mentionne aucun
              autre ingrédient dans les étapes (sel, poivre et eau exceptés, qui ne se listent pas).
            - Quantités en grammes de partie comestible crue, pour une portion, entre 1 et 600 g par ingrédient.
            - Vise la cible de calories et de protéines indiquée ; les valeurs nutritionnelles seront recalculées
              à partir d'une table officielle, inutile de les donner.
            - 2 à 6 étapes courtes, en français, à l'impératif.
            - Nom en français, 60 caractères au plus.
            - mainProtein : la source principale de protéines du repas.
        """.trimIndent()
    }
}
