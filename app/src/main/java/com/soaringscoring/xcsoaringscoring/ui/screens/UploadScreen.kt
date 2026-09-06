package com.soaringscoring.xcsoaringscoring.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soaringscoring.xcsoaringscoring.api.DustDevilEntry
import com.soaringscoring.xcsoaringscoring.storage.IgcFile
import com.soaringscoring.xcsoaringscoring.ui.AppUiState
import com.soaringscoring.xcsoaringscoring.ui.UploadOutcome
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectFile: (IgcFile) -> Unit,
    onCancelPending: () -> Unit,
    onConfirmUpload: () -> Unit,
    onDismissOutcome: () -> Unit,
    onSelectDustDevilEntry: (DustDevilEntry) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    // A signed-in entry (see DEVELOPMENT.md's "DustDevil.cloud sign-in") always
    // takes priority over the manual entry address - same fallback rule AppViewModel
    // uses when actually sending the upload.
    val destinationLabel = state.dustDevilSelectedEntry?.let {
        "${it.contestName}${it.className?.let { className -> " – $className" } ?: ""}"
    } ?: state.entryAddress

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload flight") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.dustDevilSelectedEntry == null && state.entryAddress.isBlank() -> Box(
                    Modifier.fillMaxSize().padding(24.dp)
                ) {
                    Text(
                        "Sign in with SoaringScoring or set your entry address in Settings " +
                            "first — that's how uploads are matched to your entry in the contest.",
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }
                state.igcFilesLoading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.igcFiles.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        "No .igc flight logs found in the logs folder of your selected XCSoar app(s).",
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }
                else -> Column {
                    if (state.dustDevilEntries.size > 1) {
                        DustDevilEntryPicker(
                            entries = state.dustDevilEntries,
                            selected = state.dustDevilSelectedEntry,
                            onSelect = onSelectDustDevilEntry
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.igcFiles) { file ->
                            IgcFileCard(file = file, onClick = { onSelectFile(file) })
                        }
                    }
                }
            }

            if (state.isUploading) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Row(
                            Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Uploading…")
                        }
                    }
                }
            }
        }
    }

    state.pendingUploadFile?.let { file ->
        AlertDialog(
            onDismissRequest = onCancelPending,
            title = { Text("Upload this flight?") },
            text = {
                Text("${file.doc.name}\n\nSends to entry $destinationLabel.")
            },
            confirmButton = {
                TextButton(onClick = onConfirmUpload) { Text("Upload") }
            },
            dismissButton = {
                TextButton(onClick = onCancelPending) { Text("Cancel") }
            }
        )
    }

    state.uploadOutcome?.let { outcome ->
        UploadOutcomeDialog(outcome = outcome, onDismiss = onDismissOutcome)
    }
}

/** Only shown when sign-in resolved more than one contest entry for this pilot. */
@Composable
private fun DustDevilEntryPicker(
    entries: List<DustDevilEntry>,
    selected: DustDevilEntry?,
    onSelect: (DustDevilEntry) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Uploading as", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            entries.forEach { entry ->
                FilterChip(
                    selected = entry.localPart == selected?.localPart,
                    onClick = { onSelect(entry) },
                    label = { Text("${entry.contestName}${entry.className?.let { " – $it" } ?: ""}") }
                )
            }
        }
    }
}

@Composable
private fun IgcFileCard(file: IgcFile, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.UploadFile, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.doc.name ?: "flight.igc", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                val modified = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(file.doc.lastModified()))
                val sizeKb = file.doc.length() / 1024
                Text(
                    "$modified · ${sizeKb}KB · ${file.sourceFolderName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UploadOutcomeDialog(outcome: UploadOutcome, onDismiss: () -> Unit) {
    when (outcome) {
        is UploadOutcome.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Uploaded") },
            text = {
                Column {
                    if (outcome.result.validationOk) {
                        Text("Flight uploaded successfully.")
                    } else {
                        Text("Flight uploaded, but validation found issues:")
                        Spacer(Modifier.height(8.dp))
                        outcome.result.validationIssues.forEach { issue ->
                            Text("• $issue", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
        is UploadOutcome.Failure -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Upload failed") },
            text = { Text(outcome.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
    }
}
