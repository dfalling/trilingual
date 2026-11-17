package com.dfalling.trilingual.translate

import com.dfalling.trilingual.R
import com.dfalling.trilingual.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

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

/**
 * Calls DeepInfra's OpenAI-compatible Chat Completions API. One request covers all target
 * languages; the reply is constrained to [TranslationPrompt.schema] via `response_format`
 * with a JSON schema, mirroring the structured outputs the app relied on before.
 */
class Translator(private val baseUrl: String = BASE_URL) {
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // Non-streaming completions send no bytes until generation finishes, so the
            // read timeout has to cover a full slow generation.
            .readTimeout(600, TimeUnit.SECONDS)
            .callTimeout(660, TimeUnit.SECONDS)
            .build()

    suspend fun translate(
        apiKey: String,
        model: String,
        text: String,
        source: String,
        targets: List<String>,
    ): Map<String, String> =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url("$baseUrl/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(requestBody(model, text, source, targets).toString().toRequestBody(JSON))
                    .build()

            parseCompletion(executeWithRetry(request, model), targets)
        }

    /**
     * Checks a key — and access to [model] — with a one-token completion, which costs a
     * fraction of a cent. DeepInfra's model-listing endpoint is public, so it can't verify
     * a key.
     */
    suspend fun verifyKey(
        apiKey: String,
        model: String,
    ): KeyCheck =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject()
                    .put("model", model)
                    .put("max_tokens", 1L)
                    .put(
                        "messages",
                        JSONArray().put(JSONObject().put("role", "user").put("content", ".")),
                    )
            val request =
                Request
                    .Builder()
                    .url("$baseUrl/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(body.toString().toRequestBody(JSON))
                    .build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> KeyCheck.Valid
                        401 -> KeyCheck.Rejected(UiText(R.string.key_rejected))
                        403 -> KeyCheck.Rejected(UiText(R.string.key_no_access))
                        else -> KeyCheck.Unverified(UiText(R.string.key_check_error, response.code))
                    }
                }
            } catch (e: IOException) {
                KeyCheck.Unverified(UiText(R.string.key_check_unreachable))
            }
        }

    /** The chat-completions request body: system + user messages and a strict JSON schema. */
    internal fun requestBody(
        model: String,
        text: String,
        source: String,
        targets: List<String>,
    ): JSONObject =
        JSONObject()
            .put("model", model)
            // DeepInfra caps max_tokens per model; this fits under the smallest hard cap.
            .put("max_tokens", 16000L)
            // Translation doesn't benefit from deep reasoning; turning it off keeps requests
            // snappy. It's a no-op on models that can't reason.
            .put("reasoning_effort", "none")
            .put(
                "response_format",
                JSONObject()
                    .put("type", "json_schema")
                    .put(
                        "json_schema",
                        JSONObject()
                            .put("name", "translation")
                            .put("strict", true)
                            .put("schema", JSONObject(TranslationPrompt.schema(targets))),
                    ),
            ).put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", TranslationPrompt.system))
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", TranslationPrompt.userMessage(text, source, targets)),
                    ),
            )

    /**
     * Extracts the reply's JSON from a chat-completions response and parses it, mapping
     * truncation and refusal finish reasons to the same errors the old API produced.
     */
    internal fun parseCompletion(
        body: String,
        targets: List<String>,
    ): Map<String, String> {
        val response =
            try {
                JSONObject(body)
            } catch (e: JSONException) {
                throw TranslationException(UiText(R.string.error_unreadable), e)
            }
        val choice = response.optJSONArray("choices")?.optJSONObject(0)
        when (choice?.optString("finish_reason")) {
            "length" -> throw TranslationException(UiText(R.string.error_too_long))
            "content_filter" -> throw TranslationException(UiText(R.string.error_refused))
        }
        val message = choice?.optJSONObject("message")
        // Android's org.json turns a JSON-null content into the string "null", so test
        // isNull rather than emptiness of optString.
        val content = if (message == null || message.isNull("content")) "" else message.optString("content")
        if (content.isBlank()) throw TranslationException(UiText(R.string.error_unreadable))
        return try {
            TranslationPrompt.parse(content, targets)
        } catch (e: JSONException) {
            throw TranslationException(UiText(R.string.error_unreadable), e)
        }
    }

    /**
     * Runs the request, retrying transient failures (408/429/5xx) with backoff like the
     * previous SDK did; anything else surfaces as its mapped error.
     */
    private suspend fun executeWithRetry(
        request: Request,
        model: String,
    ): String {
        var attempt = 0
        var backoffMs = 1000L
        while (true) {
            val code =
                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) return response.body?.string().orEmpty()
                        response.code
                    }
                } catch (e: IOException) {
                    throw TranslationException(UiText(R.string.error_network), e)
                }
            attempt++
            if (attempt > RETRIES || code !in RETRYABLE) throw httpError(code, model)
            delay(backoffMs)
            backoffMs *= 2
        }
    }

    private fun httpError(
        code: Int,
        model: String,
    ): TranslationException =
        when (code) {
            401 -> TranslationException(UiText(R.string.error_key_rejected))
            402 -> TranslationException(UiText(R.string.error_no_credit))
            403 -> TranslationException(UiText(R.string.error_no_model_access, model))
            429 -> TranslationException(UiText(R.string.error_rate_limited))
            else -> TranslationException(UiText(R.string.error_api, code))
        }

    companion object {
        const val BASE_URL = "https://api.deepinfra.com/v1/openai"
        private const val RETRIES = 2
        private val RETRYABLE = setOf(408, 429) + (500..599)
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
