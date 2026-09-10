package io.github.jgfyyvzss.xcsoaringscoring.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jgfyyvzss.xcsoaringscoring.R
import io.github.jgfyyvzss.xcsoaringscoring.api.Contest
import io.github.jgfyyvzss.xcsoaringscoring.api.TaskRow
import io.github.jgfyyvzss.xcsoaringscoring.ui.AppUiState
import io.github.jgfyyvzss.xcsoaringscoring.ui.ContestGrouping
import io.github.jgfyyvzss.xcsoaringscoring.ui.ContestTimeFrame
import io.github.jgfyyvzss.xcsoaringscoring.ui.UpdateCheckOutcome
import io.github.jgfyyvzss.xcsoaringscoring.util.dateOnly

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContestListScreen(
    state: AppUiState,
    onContestClick: (Contest) -> Unit,
    onSettingsClick: () -> Unit,
    onUploadClick: () -> Unit,
    onRetry: () -> Unit,
    onSelectTimeFrame: (ContestTimeFrame) -> Unit,
    onCheckForUpdate: () -> Unit,
    onConfirmUpdatedDownload: (TaskRow) -> Unit,
    onDismissUpdateOutcome: () -> Unit,
    onOpenLastDownloadedGroup: () -> Unit,
    onClearLastDownloadedGroup: () -> Unit,
    onDismissStatus: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onUploadClick) {
                        Icon(Icons.Filled.UploadFile, contentDescription = "Upload flight")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = {
            state.statusMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    action = { TextButton(onClick = onDismissStatus) { Text("OK") } }
                ) { Text(msg) }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            DownloadStatusLine(state = state, onClick = onSettingsClick)
            HorizontalDivider()

            if (state.lastDownloadedTaskGroup != null) {
                LastDownloadedTaskCheckCard(
                    state = state,
                    onCheckForUpdate = onCheckForUpdate,
                    onOpen = onOpenLastDownloadedGroup,
                    onClear = onClearLastDownloadedGroup
                )
                HorizontalDivider()
            }

            TabRow(selectedTabIndex = state.selectedTimeFrame.ordinal) {
                ContestTimeFrame.entries.forEach { timeFrame ->
                    Tab(
                        selected = state.selectedTimeFrame == timeFrame,
                        onClick = { onSelectTimeFrame(timeFrame) },
                        text = { Text(timeFrame.label) }
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.contestsLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.contestsError != null -> ErrorWithRetry(state.contestsError, onRetry)
                    else -> {
                        val groups = ContestGrouping.groupedFor(state.contests, state.selectedTimeFrame)
                        if (groups.isEmpty()) {
                            Text(
                                emptyMessageFor(state.selectedTimeFrame),
                                Modifier.align(Alignment.Center).padding(24.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                groups.forEach { group ->
                                    if (group.label.isNotEmpty()) {
                                        item {
                                            Text(
                                                group.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                            )
                                        }
                                    }
                                    items(group.contests) { contest ->
                                        ContestCard(contest = contest, onClick = { onContestClick(contest) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.updateCheckOutcome?.let { outcome ->
        UpdateCheckOutcomeDialog(
            outcome = outcome,
            onConfirmDownload = onConfirmUpdatedDownload,
            onDismiss = onDismissUpdateOutcome
        )
    }
}

/**
 * One-line, read-only summary of what a download will do with the current
 * Settings (Alternates + Save to, both moved to Settings - see DEVELOPMENT.md)
 * - replaces the two controls that used to sit here directly. Always tappable
 * through to Settings, in every state: the happy path jumps to the same
 * section it's summarizing, and the two action states below tell the pilot
 * exactly what to fix there rather than just naming the problem.
 */
@Composable
private fun DownloadStatusLine(state: AppUiState, onClick: () -> Unit) {
    val (text, isActionNeeded) = when {
        state.mediaTreeUri == null -> "Grant media access" to true
        state.targetFolders.none { it.selected } -> "Select file destination" to true
        else -> {
            val scope = if (state.downloadAllAlternates) "Official & Alt" else "Official"
            val folders = state.targetFolders.filter { it.selected }.mapNotNull { it.doc.name }
            "Saving $scope to ${folders.joinToString(" & ")}" to false
        }
    }
    val color = if (isActionNeeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
        if (isActionNeeded) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The last day/class(/handicap) drill-down download, with a one-tap way to resolve
 * whether the official task has since been decided or changed - see
 * docs/FEATURE-check-updated-task.md. Only shown once something's actually been
 * downloaded.
 *
 * "Open" and "Clear" (added alongside the Settings/status-line rework - see
 * DEVELOPMENT.md) are what actually get a pilot from "downloaded Day 1" to
 * "viewing Day 2": Open re-resolves the real contest/class and jumps into the
 * task list, where the existing date-driven filtering shows whatever day is
 * current - it deliberately doesn't try to reuse the stored day. Clear just
 * dismisses this card (same underlying record the Alternates toggle already
 * clears automatically) for a pilot who's done with this event.
 */
@Composable
private fun LastDownloadedTaskCheckCard(
    state: AppUiState,
    onCheckForUpdate: () -> Unit,
    onOpen: () -> Unit,
    onClear: () -> Unit
) {
    val group = state.lastDownloadedTaskGroup ?: return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${group.contestName} — ${group.className}" +
                        (group.dhtHandicap?.let { " (handicap $it)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Last downloaded task",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Clear")
            }
        }
        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (state.checkingForUpdate) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onCheckForUpdate) { Text("Check for updated task") }
                TextButton(onClick = onOpen) { Text("Open") }
            }
        }
    }
}

@Composable
private fun UpdateCheckOutcomeDialog(
    outcome: UpdateCheckOutcome,
    onConfirmDownload: (TaskRow) -> Unit,
    onDismiss: () -> Unit
) {
    when (outcome) {
        UpdateCheckOutcome.NoOfficialYet -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Not yet decided") },
            text = { Text("No official task has been published for this day yet.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
        UpdateCheckOutcome.NoChange -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("No change") },
            text = { Text("The official task hasn't changed since you last downloaded it.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
        is UpdateCheckOutcome.ConfirmedLocally -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Official task confirmed") },
            text = {
                Text(
                    "Already had it on file (${outcome.fileName}) - applied to default.tsk, " +
                        "no download needed."
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
        is UpdateCheckOutcome.NeedsDownload -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Task revised") },
            text = {
                Text(
                    "Day ${outcome.newTask.dayNumber}'s official task has been revised since " +
                        "you downloaded it. Download the updated version?"
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmDownload(outcome.newTask) }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
        is UpdateCheckOutcome.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Check failed") },
            text = { Text(outcome.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
    }
}

@Composable
private fun ContestCard(contest: Contest, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(contest.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            contest.organisationName?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${dateOnly(contest.startDate)} – ${dateOnly(contest.endDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun emptyMessageFor(timeFrame: ContestTimeFrame): String = when (timeFrame) {
    ContestTimeFrame.CURRENT -> "No contests currently underway."
    ContestTimeFrame.FUTURE -> "No upcoming contests found."
    ContestTimeFrame.PAST -> "No past contests found."
}

@Composable
private fun ErrorWithRetry(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
