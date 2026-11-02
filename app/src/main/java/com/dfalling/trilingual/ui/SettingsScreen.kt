package com.dfalling.trilingual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dfalling.trilingual.BuildConfig
import com.dfalling.trilingual.R
import com.dfalling.trilingual.data.ClaudeModel

private const val API_KEYS_URL = "https://platform.claude.com/settings/keys"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var keyDraft by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val keyStatus = viewModel.keyStatus
    val checking = keyStatus == KeyStatus.Checking
    val save = { viewModel.saveApiKey(keyDraft) { keyDraft = "" } }
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Text(stringResource(R.string.api_key_title), style = MaterialTheme.typography.titleMedium)
            Text(
                if (settings?.hasApiKey == true) {
                    stringResource(R.string.api_key_saved_hint)
                } else {
                    stringResource(R.string.api_key_missing_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { uriHandler.openUri(API_KEYS_URL) }) { Text(stringResource(R.string.get_a_key)) }
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                enabled = !checking,
                isError = keyStatus is KeyStatus.Error,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val clip = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                                val pasted = clip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                if (!pasted.isNullOrEmpty()) keyDraft = pasted
                            }
                        },
                        enabled = !checking,
                        modifier = Modifier.padding(end = 4.dp),
                    ) { Text(stringResource(R.string.paste)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            when (val status = keyStatus) {
                KeyStatus.Checking ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.checking_key),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                is KeyStatus.Saved ->
                    Text(status.message.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                is KeyStatus.Error ->
                    Text(status.message.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = save, enabled = keyDraft.isNotBlank() && !checking) { Text(stringResource(R.string.save_key)) }
                if (settings?.hasApiKey == true) {
                    OutlinedButton(onClick = viewModel::removeApiKey, enabled = !checking) { Text(stringResource(R.string.remove_key)) }
                }
            }

            Text(stringResource(R.string.model), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            ClaudeModel.entries.forEach { model ->
                val selected = settings?.model == model.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected, role = Role.RadioButton) { viewModel.setModel(model.id) },
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
                        Text(model.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(model.blurb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear_history)) }

            Text(
                "Trilingual ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    confirmClear = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
