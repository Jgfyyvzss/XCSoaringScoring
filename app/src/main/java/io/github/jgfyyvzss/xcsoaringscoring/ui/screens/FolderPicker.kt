package io.github.jgfyyvzss.xcsoaringscoring.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.jgfyyvzss.xcsoaringscoring.ui.AppUiState
import io.github.jgfyyvzss.xcsoaringscoring.ui.TargetFolder

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
                "Grant access to the Android/media folder once. The app will find installed XCSoar and " +
                    "XCSoar Jet etc.",
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
 * Choice of which detected XCSoar folder(s) to save into. Moved from the home
 * screen into Settings (see DEVELOPMENT.md) alongside Download Alternates - both
 * are set-once-per-event choices, not something to reconsider on every visit to
 * the home screen; a read-only summary of both now lives there instead
 * (`DownloadStatusLine` in `ContestListScreen.kt`).
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
                "Grant Android/media access below first.",
                style = MaterialTheme.typography.bodySmall
            )
            state.targetFolders.isEmpty() -> Text(
                "No XCSoar-like folders found. Check Android/media access below.",
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
