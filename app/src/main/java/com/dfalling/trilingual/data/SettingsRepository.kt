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

/**
 * The models offered in Settings, served by DeepInfra. Ids are DeepInfra model strings;
 * a stored id from a version of the app that no longer knows it falls back to [default].
 */
enum class TranslateModel(
    val id: String,
    val label: String,
    @param:StringRes val blurb: Int,
) {
    QWEN("Qwen/Qwen3.5-397B-A17B", "Qwen3.5 397B", R.string.model_best_blurb),
    GLM("zai-org/GLM-5.3-Flash", "GLM 5.3 Flash", R.string.model_balanced_blurb),
    DEEPSEEK("deepseek-ai/DeepSeek-V4.1-Flash-1017", "DeepSeek V4.1 Flash", R.string.model_fast_blurb),
    ;

    companion object {
        val default = DEEPSEEK
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
            // A stored model id this build doesn't know marks an install from before the
            // DeepInfra switch, whose saved key belongs to the old provider: show the app as
            // keyless so that key is never sent to the new one. Saving a new key overwrites it.
            val migrated = prefs[Keys.model]?.let { id -> TranslateModel.entries.none { it.id == id } } == true
            Settings(
                languages = languages,
                source = source,
                model = prefs[Keys.model]?.takeIf { id -> TranslateModel.entries.any { it.id == id } }
                    ?: TranslateModel.default.id,
                hasApiKey = prefs[Keys.apiKey] != null && !migrated,
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
