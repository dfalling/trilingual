package com.dfalling.trilingual.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dfalling.trilingual.TrilingualApp
import com.dfalling.trilingual.data.Settings
import com.dfalling.trilingual.data.SettingsRepository
import com.dfalling.trilingual.data.TranslationDao
import com.dfalling.trilingual.data.TranslationEntry
import com.dfalling.trilingual.translate.KeyCheck
import com.dfalling.trilingual.translate.TranslationException
import com.dfalling.trilingual.translate.Translator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A translation in flight, shown at the bottom of the feed until it lands in history. */
data class PendingTranslation(
    val text: String,
    val source: String,
    val targets: List<String>,
)

sealed interface KeyStatus {
    data object Checking : KeyStatus

    data class Saved(
        val message: String,
    ) : KeyStatus

    data class Error(
        val message: String,
    ) : KeyStatus
}

class MainViewModel(
    private val settingsRepo: SettingsRepository,
    private val history: TranslationDao,
    private val translator: Translator,
) : ViewModel() {
    val settings: StateFlow<Settings?> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val entries: StateFlow<List<TranslationEntry>> =
        history.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _pending = MutableStateFlow<PendingTranslation?>(null)
    val pending: StateFlow<PendingTranslation?> = _pending.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    var input by mutableStateOf("")

    fun translate() {
        val current = settings.value ?: return
        val text = input.trim()
        if (text.isEmpty() || _pending.value != null) return
        val request = PendingTranslation(text, current.source, current.targets)
        _pending.value = request
        input = ""
        viewModelScope.launch {
            try {
                val apiKey = settingsRepo.apiKey() ?: throw TranslationException("Add your Anthropic API key in Settings.")
                val translations = translator.translate(apiKey, current.model, text, request.source, request.targets)
                history.insert(
                    TranslationEntry(
                        createdAt = System.currentTimeMillis(),
                        sourceLanguage = request.source,
                        sourceText = text,
                        translations = translations,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Give the text back so nothing the user typed is lost.
                if (input.isEmpty()) input = text
                _messages.send((e as? TranslationException)?.message ?: "Translation failed: ${e.message}")
            } finally {
                _pending.value = null
            }
        }
    }

    fun selectSource(code: String) {
        viewModelScope.launch { settingsRepo.setSource(code) }
    }

    fun toggleLanguage(code: String) {
        val current = settings.value ?: return
        viewModelScope.launch {
            if (code in current.languages) {
                if (current.languages.size <= 2) {
                    _messages.send("Keep at least two languages.")
                    return@launch
                }
                val remaining = current.languages - code
                val source = if (current.source == code) remaining.first() else current.source
                settingsRepo.setLanguages(remaining, source)
            } else {
                settingsRepo.setLanguages(current.languages + code, current.source)
            }
        }
    }

    /** Puts a past entry back in the composer, in the language it was written in. */
    fun reuse(entry: TranslationEntry) {
        input = entry.sourceText
        settings.value?.let { current ->
            viewModelScope.launch {
                if (entry.sourceLanguage in current.languages) {
                    settingsRepo.setSource(entry.sourceLanguage)
                } else {
                    settingsRepo.setLanguages(current.languages + entry.sourceLanguage, entry.sourceLanguage)
                }
            }
        }
    }

    fun delete(entry: TranslationEntry) {
        viewModelScope.launch { history.delete(entry.id) }
    }

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    /** Status line under the API key field in Settings. */
    var keyStatus by mutableStateOf<KeyStatus?>(null)
        private set

    /** Checks [key] with Anthropic and saves it unless it's rejected. Calls [onSaved] once it's stored. */
    fun saveApiKey(
        key: String,
        onSaved: () -> Unit,
    ) {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || keyStatus == KeyStatus.Checking) return
        keyStatus = KeyStatus.Checking
        viewModelScope.launch {
            keyStatus =
                when (val check = translator.verifyKey(trimmed)) {
                    KeyCheck.Valid -> {
                        settingsRepo.setApiKey(trimmed)
                        onSaved()
                        KeyStatus.Saved("Key works and is saved.")
                    }
                    is KeyCheck.Unverified -> {
                        // Don't block setup on a flaky connection; a bad key still surfaces on first translate.
                        settingsRepo.setApiKey(trimmed)
                        onSaved()
                        KeyStatus.Saved("Saved, but not verified. ${check.reason}")
                    }
                    is KeyCheck.Rejected -> KeyStatus.Error(check.reason)
                }
        }
    }

    fun removeApiKey() {
        keyStatus = null
        viewModelScope.launch { settingsRepo.setApiKey("") }
    }

    fun setModel(model: String) {
        viewModelScope.launch { settingsRepo.setModel(model) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TrilingualApp
                    MainViewModel(app.settings, app.history, app.translator)
                }
            }
    }
}
