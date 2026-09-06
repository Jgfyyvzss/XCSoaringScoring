package com.soaringscoring.xcsoaringscoring.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soaringscoring.xcsoaringscoring.ui.AppUiState
import com.soaringscoring.xcsoaringscoring.ui.TargetFolder

/**
 * One-time permission grant for the Android/media folder. Belongs in Settings -
 * you set this up once and never need to touch it again afterwards.
 */
@Composable
fun MediaFolderAccessSetting(
    state: AppUiState,
    onChooseMediaFolder: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Android/media access", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onChooseMediaFolder) {
                Text(if (state.mediaTreeUri == null) "Choose Android/media" else "Change")
            }
        }
        if (state.mediaTreeUri == null) {
            Text(
                "Grant access to the Android/media folder once — that's where XCSoar and " +
                    "XCSoar Jet each keep their own Tasks folder. If you only have one " +
                    "variant installed, picking that app's folder directly also works.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            val names = state.targetFolders.mapNotNull { it.doc.name }
            Text(
                if (names.isEmpty()) "Access granted, but no XCSoar-like folders were found there."
                else "Access granted. Found: ${names.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Per-download choice of which detected XCSoar folder(s) to save into. Belongs on
 * the contest list (home) screen - this is something you might change from one
 * download to the next, e.g. switching between XCSoar and XCSoar Jet.
 */
@Composable
fun TargetFolderCheckboxes(
    state: AppUiState,
    onToggleFolder: (TargetFolder) -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save to", style = MaterialTheme.typography.titleMedium)
        }
        when {
            state.mediaTreeUri == null -> Text(
                "Grant folder access in Settings to choose which app(s) to save to.",
                style = MaterialTheme.typography.bodySmall
            )
            state.targetFolders.isEmpty() -> Text(
                "No XCSoar-like folders found. Check folder access in Settings.",
                style = MaterialTheme.typography.bodySmall
            )
            else -> state.targetFolders.forEach { folder ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onToggleFolder(folder) }
                ) {
                    Checkbox(checked = folder.selected, onCheckedChange = { onToggleFolder(folder) })
                    Text(folder.doc.name ?: "(unnamed)")
                }
            }
        }
    }
}
