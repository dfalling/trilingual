package com.dfalling.trilingual.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dfalling.trilingual.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    /** Active languages in the order the user added them; always at least two. */
    val languages: List<String>,
    /** The language the user is currently writing in; always one of [languages]. */
    val source: String,
    val model: String,
    val hasApiKey: Boolean,
) {
    val targets: List<String> get() = languages - source
}

enum class ClaudeModel(
    val id: String,
    val label: String,
    @param:StringRes val blurb: Int,
) {
    OPUS("claude-opus-5-5", "Claude Opus 5.5", R.string.model_opus_blurb),
    SONNET("claude-sonnet-5-5", "Claude Sonnet 5.5", R.string.model_sonnet_blurb),
    HAIKU("claude-haiku-5-5", "Claude Haiku 5.5", R.string.model_haiku_blurb),
    ;

    companion object {
        val default = OPUS
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(
    private val context: Context,
) {
    private object Keys {
        val languages = stringPreferencesKey("languages")
        val source = stringPreferencesKey("source")
        val model = stringPreferencesKey("model")
        val apiKey = stringPreferencesKey("api_key_encrypted")
    }

    val settings: Flow<Settings> =
        context.dataStore.data.map { prefs ->
            val languages =
                prefs[Keys.languages]
                    ?.split(',')
                    ?.filter { it.isNotBlank() }
                    ?.takeIf { it.size >= 2 }
                    ?: Languages.defaultCodes
            val source = prefs[Keys.source]?.takeIf { it in languages } ?: languages.first()
            Settings(
                languages = languages,
                source = source,
                model = prefs[Keys.model] ?: ClaudeModel.default.id,
                hasApiKey = prefs[Keys.apiKey] != null,
            )
        }

    suspend fun apiKey(): String? = context.dataStore.data.first()[Keys.apiKey]?.let(ApiKeyCipher::decrypt)

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { prefs ->
            if (key.isBlank()) {
                prefs.remove(Keys.apiKey)
            } else {
                prefs[Keys.apiKey] = ApiKeyCipher.encrypt(key.trim())
            }
        }
    }

    suspend fun setLanguages(
        languages: List<String>,
        source: String,
    ) {
        require(languages.size >= 2 && source in languages)
        context.dataStore.edit { prefs ->
            prefs[Keys.languages] = languages.joinToString(",")
            prefs[Keys.source] = source
        }
    }

    suspend fun setSource(source: String) {
        context.dataStore.edit { it[Keys.source] = source }
    }

    suspend fun setModel(model: String) {
        context.dataStore.edit { it[Keys.model] = model }
    }
}
