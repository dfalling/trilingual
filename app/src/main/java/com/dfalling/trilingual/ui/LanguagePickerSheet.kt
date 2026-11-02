package com.dfalling.trilingual.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfalling.trilingual.R
import com.dfalling.trilingual.data.Languages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matches =
        Languages.all.filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                Languages.displayName(it.code).contains(query, ignoreCase = true) ||
                it.nativeName.contains(query, ignoreCase = true)
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.languages), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.language_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
            )
        }
        LazyColumn {
            items(matches, key = { it.code }) { language ->
                val checked = language.code in selected
                val name = Languages.displayName(language.code)
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent =
                        if (!language.nativeName.equals(name, ignoreCase = true)) {
                            { Text(language.nativeName) }
                        } else {
                            null
                        },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle(language.code) }) },
                    modifier = Modifier.clickable { onToggle(language.code) },
                )
            }
        }
    }
}
