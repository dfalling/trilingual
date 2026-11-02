package com.dfalling.trilingual.translate

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.models.ModelListParams
import com.dfalling.trilingual.R
import com.dfalling.trilingual.UiText
import com.dfalling.trilingual.data.ClaudeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.optionals.getOrNull

/** Result of checking an API key before saving it. */
sealed interface KeyCheck {
    data object Valid : KeyCheck

    data class Rejected(
        val reason: UiText,
    ) : KeyCheck

    /** The API couldn't be reached, so the key may still be fine. */
    data class Unverified(
        val reason: UiText,
    ) : KeyCheck
}

class Translator {
    private var cachedClient: Pair<String, AnthropicClient>? = null

    suspend fun translate(
        apiKey: String,
        model: String,
        text: String,
        source: String,
        targets: List<String>,
    ): Map<String, String> =
        withContext(Dispatchers.IO) {
            val params =
                MessageCreateParams
                    .builder()
                    .model(model)
                    .maxTokens(16000L)
                    .system(TranslationPrompt.system)
                    .outputConfig(
                        OutputConfig
                            .builder()
                            // Translation doesn't benefit from deep reasoning; low effort keeps it snappy.
                            .effort(OutputConfig.Effort.LOW)
                            .format(
                                JsonOutputFormat
                                    .builder()
                                    .schema(
                                        JsonOutputFormat.Schema
                                            .builder()
                                            .putAllAdditionalProperties(
                                                TranslationPrompt.schema(targets).mapValues { JsonValue.from(it.value) },
                                            ).build(),
                                    ).build(),
                            ).build(),
                    ).addUserMessage(TranslationPrompt.userMessage(text, source, targets))
                    .apply {
                        // If a safety classifier declines, let the API retry on a fallback model
                        // rather than failing the translation outright.
                        if (model != ClaudeModel.HAIKU.id) {
                            putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                            putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                        }
                    }.build()

            val message =
                try {
                    client(apiKey).messages().create(params)
                } catch (e: UnauthorizedException) {
                    throw TranslationException(UiText(R.string.error_key_rejected), e)
                } catch (e: PermissionDeniedException) {
                    throw TranslationException(UiText(R.string.error_no_model_access, model), e)
                } catch (e: RateLimitException) {
                    throw TranslationException(UiText(R.string.error_rate_limited), e)
                } catch (e: AnthropicServiceException) {
                    throw TranslationException(UiText(R.string.error_api, e.statusCode()), e)
                } catch (e: AnthropicIoException) {
                    throw TranslationException(UiText(R.string.error_network), e)
                }

            when (message.stopReason().getOrNull()) {
                StopReason.REFUSAL -> throw TranslationException(UiText(R.string.error_refused))
                StopReason.MAX_TOKENS -> throw TranslationException(UiText(R.string.error_too_long))
                else -> Unit
            }

            val json = message.content().mapNotNull { it.text().getOrNull()?.text() }.joinToString("")
            try {
                TranslationPrompt.parse(json, targets)
            } catch (e: org.json.JSONException) {
                throw TranslationException(UiText(R.string.error_unreadable), e)
            }
        }

    /** Checks a key against the model-listing endpoint, which is free and needs no model access. */
    suspend fun verifyKey(apiKey: String): KeyCheck =
        withContext(Dispatchers.IO) {
            try {
                client(apiKey).models().list(ModelListParams.builder().limit(1L).build())
                KeyCheck.Valid
            } catch (e: UnauthorizedException) {
                KeyCheck.Rejected(UiText(R.string.key_rejected))
            } catch (e: PermissionDeniedException) {
                KeyCheck.Rejected(UiText(R.string.key_no_access))
            } catch (e: AnthropicServiceException) {
                KeyCheck.Unverified(UiText(R.string.key_check_error, e.statusCode()))
            } catch (e: AnthropicIoException) {
                KeyCheck.Unverified(UiText(R.string.key_check_unreachable))
            }
        }

    @Synchronized
    private fun client(apiKey: String): AnthropicClient =
        cachedClient?.takeIf { it.first == apiKey }?.second
            ?: AnthropicOkHttpClient
                .builder()
                .apiKey(apiKey)
                .build()
                .also { cachedClient = apiKey to it }
}
