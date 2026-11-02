package com.dfalling.trilingual

import com.dfalling.trilingual.data.Languages
import com.dfalling.trilingual.data.TranslationsConverter
import com.dfalling.trilingual.translate.TranslationException
import com.dfalling.trilingual.translate.TranslationPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TranslationPromptTest {
    @Test
    fun `parse returns translations in target order`() {
        val json =
            """{"translations":[{"language":"pt-BR","text":" Olá "},{"language":"es-419","text":"Hola"}]}"""
        val result = TranslationPrompt.parse(json, listOf("es-419", "pt-BR"))
        assertEquals(listOf("es-419" to "Hola", "pt-BR" to "Olá"), result.toList())
    }

    @Test(expected = TranslationException::class)
    fun `parse fails when a target is missing`() {
        TranslationPrompt.parse("""{"translations":[{"language":"es-419","text":"Hola"}]}""", listOf("es-419", "fr"))
    }

    @Test
    fun `user message wraps text and lists targets`() {
        val message = TranslationPrompt.userMessage("Hi\nthere", "en", listOf("es-419", "pt-BR"))
        assertTrue(message.contains("<text>\nHi\nthere\n</text>"))
        assertTrue(message.contains("- pt-BR: Portuguese (Brazil)"))
    }

    @Test
    fun `history converter preserves order`() {
        val map = linkedMapOf("pt-BR" to "Olá", "es-419" to "Hola", "fr" to "Salut")
        val roundTrip = TranslationsConverter.fromJson(TranslationsConverter.toJson(map))
        assertEquals(map.toList(), roundTrip.toList())
    }

    @Test
    fun `default languages exist in catalog`() {
        Languages.defaultCodes.forEach { assertNotNull(Languages.byCode(it)) }
        assertEquals(Languages.all.size, Languages.all.map { it.code }.toSet().size)
    }

    @Test
    fun `display names follow the phone's language`() {
        assertEquals("Portuguese (Brazil)", Languages.displayName("pt-BR", Locale.ENGLISH))
        assertEquals("Portugués (Brasil)", Languages.displayName("pt-BR", Locale.forLanguageTag("es")))
        assertEquals("Espanhol (Espanha)", Languages.displayName("es-ES", Locale.forLanguageTag("pt-BR")))
    }
}
