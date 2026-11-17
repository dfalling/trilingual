package com.dfalling.trilingual

import com.dfalling.trilingual.translate.TranslationException
import com.dfalling.trilingual.translate.TranslationPrompt
import com.dfalling.trilingual.translate.Translator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorTest {
    private val translator = Translator("https://api.example.test/v1/openai")
    private val targets = listOf("es-419", "pt-BR")

    @Test
    fun `request body carries schema, messages and limits`() {
        val body = translator.requestBody("model-x", "Hi\nthere", "en", targets)

        assertEquals("model-x", body.getString("model"))
        assertEquals(16000L, body.getLong("max_tokens"))
        assertEquals("none", body.getString("reasoning_effort"))

        val jsonSchema = body.getJSONObject("response_format").getJSONObject("json_schema")
        assertEquals("json_schema", body.getJSONObject("response_format").getString("type"))
        assertTrue(jsonSchema.getBoolean("strict"))
        val language =
            jsonSchema
                .getJSONObject("schema")
                .getJSONObject("properties")
                .getJSONObject("translations")
                .getJSONObject("items")
                .getJSONObject("properties")
                .getJSONObject("language")
        assertEquals(targets, language.getJSONArray("enum").let { array -> (0 until array.length()).map(array::getString) })

        val messages = body.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals(TranslationPrompt.system, messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals(
            TranslationPrompt.userMessage("Hi\nthere", "en", targets),
            messages.getJSONObject(1).getString("content"),
        )
    }

    @Test
    fun `parseCompletion returns translations from the reply content`() {
        val body =
            """{"choices":[{"finish_reason":"stop","message":{"role":"assistant",
                "content":"{\"translations\":[{\"language\":\"pt-BR\",\"text\":\"Olá\"},{\"language\":\"es-419\",\"text\":\"Hola\"}]}"},
                "reasoning_content":"thinking not part of content"}]}"""
        val result = translator.parseCompletion(body, targets)
        assertEquals(listOf("es-419" to "Hola", "pt-BR" to "Olá"), result.toList())
    }

    @Test
    fun `parseCompletion maps a truncated reply to the too-long error`() {
        val body = """{"choices":[{"finish_reason":"length","message":{"content":"{\"translations\":"}}]}"""
        assertEquals(R.string.error_too_long, thrownException(body).text.id)
    }

    @Test
    fun `parseCompletion maps a content filter to the refused error`() {
        val body = """{"choices":[{"finish_reason":"content_filter","message":{"content":""}}]}"""
        assertEquals(R.string.error_refused, thrownException(body).text.id)
    }

    @Test
    fun `parseCompletion rejects an empty reply`() {
        val body = """{"choices":[{"finish_reason":"stop","message":{"content":""}}]}"""
        assertEquals(R.string.error_unreadable, thrownException(body).text.id)
    }

    @Test
    fun `parseCompletion rejects a JSON-null content`() {
        val body = """{"choices":[{"finish_reason":"stop","message":{"content":null}}]}"""
        assertEquals(R.string.error_unreadable, thrownException(body).text.id)
    }

    @Test
    fun `parseCompletion rejects a non-JSON reply`() {
        assertEquals(R.string.error_unreadable, thrownException("gateway timeout page").text.id)
    }

    @Test
    fun `parseCompletion rejects a reply missing a target language`() {
        val body =
            """{"choices":[{"finish_reason":"stop","message":{"content":"{\"translations\":[{\"language\":\"es-419\",\"text\":\"Hola\"}]}"}}]}"""
        assertEquals(R.string.error_missing_translation, thrownException(body).text.id)
    }

    private fun thrownException(body: String): TranslationException =
        assertThrows(TranslationException::class.java) { translator.parseCompletion(body, targets) }
}
