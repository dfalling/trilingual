package com.dfalling.trilingual.ui

import android.content.ClipData
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dfalling.trilingual.R
import com.dfalling.trilingual.data.Languages
import com.dfalling.trilingual.data.Settings
import com.dfalling.trilingual.data.TranslationEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showPicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it.resolve(resources)) }
    }
    // The feed is bottom-anchored like a chat; jump to the newest item when one arrives.
    LaunchedEffect(entries.firstOrNull()?.id, pending) {
        listState.animateScrollToItem(0)
    }

    val copy: (String) -> Unit = { text ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Translation", text)))
            snackbar.showSnackbar(resources.getString(R.string.copied))
        }
    }

    val pasteAndSend: () -> Unit = {
        scope.launch {
            val clip = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
            val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                snackbar.showSnackbar(resources.getString(R.string.nothing_to_paste))
            } else {
                // Add to any draft rather than replacing it, so nothing typed is lost.
                val draft = viewModel.input.trimEnd()
                viewModel.input = if (draft.isEmpty()) text else "$draft $text"
                viewModel.translate()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trilingual") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // Nothing to compose with until there's a key to translate with.
            settings?.takeIf { it.hasApiKey }?.let {
                Composer(
                    settings = it,
                    input = viewModel.input,
                    onInputChange = { text -> viewModel.input = text },
                    busy = pending != null,
                    onSelectSource = viewModel::selectSource,
                    onEditLanguages = { showPicker = true },
                    onSend = viewModel::translate,
                    onPasteAndSend = pasteAndSend,
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (settings?.hasApiKey != true) {
                // Also skips the feed, which would otherwise sit on top and swallow the prompt's taps.
                if (settings != null) ApiKeyPrompt(onOpenSettings, Modifier.align(Alignment.Center))
                return@Box
            }
            if (entries.isEmpty() && pending == null) {
                Text(
                    stringResource(R.string.empty_feed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                )
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                pending?.let { request ->
                    item(key = "pending") { PendingCard(request) }
                }
                items(entries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onCopy = copy,
                        onReuse = { viewModel.reuse(entry) },
                        onDelete = { viewModel.delete(entry) },
                    )
                }
            }
        }
    }

    if (showPicker) {
        settings?.let {
            LanguagePickerSheet(
                selected = it.languages,
                onToggle = viewModel::toggleLanguage,
                onDismiss = { showPicker = false },
            )
        }
    }
}

@Composable
private fun Composer(
    settings: Settings,
    input: String,
    onInputChange: (String) -> Unit,
    busy: Boolean,
    onSelectSource: (String) -> Unit,
    onEditLanguages: () -> Unit,
    onSend: () -> Unit,
    onPasteAndSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 8.dp),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(settings.languages, key = { it }) { code ->
                    val selected = code == settings.source
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSource(code) },
                        label = { Text(Languages.displayName(code)) },
                        leadingIcon =
                            if (selected) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else {
                                null
                            },
                    )
                }
                item(key = "add") {
                    AssistChip(
                        onClick = onEditLanguages,
                        label = { Text(stringResource(R.string.languages)) },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text(stringResource(R.string.write_in, Languages.displayName(settings.source))) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 6,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(4.dp))
                IconButton(onClick = onPasteAndSend, enabled = !busy && settings.hasApiKey) {
                    Icon(painterResource(R.drawable.ic_paste), contentDescription = stringResource(R.string.paste_and_translate))
                }
                FilledIconButton(
                    onClick = onSend,
                    enabled = !busy && input.isNotBlank() && settings.hasApiKey,
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.translate))
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: TranslationEntry,
    onCopy: (String) -> Unit,
    onReuse: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Languages.displayName(entry.sourceLanguage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    " · " + DateUtils.getRelativeTimeSpanString(entry.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_original)) },
                            onClick = {
                                menuOpen = false
                                onCopy(entry.sourceText)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_and_resend)) },
                            onClick = {
                                menuOpen = false
                                onReuse()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Text(
                entry.sourceText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 12.dp),
            )
            entry.translations.forEach { (code, text) ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp).padding(end = 12.dp))
                TranslationBlock(code, text, Modifier.clickable { onCopy(text) })
            }
        }
    }
}

@Composable
private fun TranslationBlock(
    code: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(end = 12.dp)) {
        Text(
            Languages.displayName(code),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PendingCard(request: PendingTranslation) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                Languages.displayName(request.source),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(request.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.translating_to, request.targets.joinToString { Languages.displayName(it) }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ApiKeyPrompt(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.api_key_prompt),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.add_api_key)) }
    }
}
