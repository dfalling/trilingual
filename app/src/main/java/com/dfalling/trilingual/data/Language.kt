package com.dfalling.trilingual.data

import java.util.Locale

/** A language the app can translate between. [code] is a BCP 47 tag and the stable storage key. */
data class Language(
    val code: String,
    val name: String,
    val nativeName: String,
)

object Languages {
    val all: List<Language> =
        listOf(
            Language("en", "English", "English"),
            Language("es-419", "Spanish (Latin America)", "Español (Latinoamérica)"),
            Language("es-ES", "Spanish (Spain)", "Español (España)"),
            Language("pt-BR", "Portuguese (Brazil)", "Português (Brasil)"),
            Language("pt-PT", "Portuguese (Portugal)", "Português (Portugal)"),
            Language("fr", "French", "Français"),
            Language("de", "German", "Deutsch"),
            Language("it", "Italian", "Italiano"),
            Language("nl", "Dutch", "Nederlands"),
            Language("ca", "Catalan", "Català"),
            Language("sv", "Swedish", "Svenska"),
            Language("nb", "Norwegian", "Norsk bokmål"),
            Language("da", "Danish", "Dansk"),
            Language("fi", "Finnish", "Suomi"),
            Language("pl", "Polish", "Polski"),
            Language("cs", "Czech", "Čeština"),
            Language("hu", "Hungarian", "Magyar"),
            Language("ro", "Romanian", "Română"),
            Language("el", "Greek", "Ελληνικά"),
            Language("tr", "Turkish", "Türkçe"),
            Language("uk", "Ukrainian", "Українська"),
            Language("ru", "Russian", "Русский"),
            Language("ar", "Arabic", "العربية"),
            Language("he", "Hebrew", "עברית"),
            Language("hi", "Hindi", "हिन्दी"),
            Language("bn", "Bengali", "বাংলা"),
            Language("ja", "Japanese", "日本語"),
            Language("ko", "Korean", "한국어"),
            Language("zh-Hans", "Chinese (Simplified)", "简体中文"),
            Language("zh-Hant", "Chinese (Traditional)", "繁體中文"),
            Language("th", "Thai", "ไทย"),
            Language("vi", "Vietnamese", "Tiếng Việt"),
            Language("id", "Indonesian", "Bahasa Indonesia"),
            Language("ms", "Malay", "Bahasa Melayu"),
            Language("tl", "Filipino", "Filipino"),
            Language("sw", "Swahili", "Kiswahili"),
        )

    private val byCode = all.associateBy { it.code }

    val defaultCodes = listOf("en", "es-419")

    fun byCode(code: String): Language? = byCode[code]

    /** English name for a code (used in prompts), falling back to the raw code for languages no longer in the catalog. */
    fun nameOf(code: String): String = byCode[code]?.name ?: code

    /**
     * Name to show in the UI, in [locale]'s language. English keeps the catalog's names; other
     * languages use the platform's locale data, e.g. "Portugués (Brasil)" in Spanish.
     */
    fun displayName(
        code: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        if (locale.language == "en") return nameOf(code)
        val name = Locale.forLanguageTag(code).getDisplayName(locale)
        return if (name.isBlank() || name == code) nameOf(code) else name.replaceFirstChar { it.titlecase(locale) }
    }
}
