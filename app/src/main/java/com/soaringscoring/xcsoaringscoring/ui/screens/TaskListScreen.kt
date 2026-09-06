package com.soaringscoring.xcsoaringscoring.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soaringscoring.xcsoaringscoring.api.Contest
import com.soaringscoring.xcsoaringscoring.api.ContestClass
import com.soaringscoring.xcsoaringscoring.api.TaskRow
import com.soaringscoring.xcsoaringscoring.ui.AppUiState
import com.soaringscoring.xcsoaringscoring.ui.ContestGrouping
import com.soaringscoring.xcsoaringscoring.ui.ContestTimeFrame
import com.soaringscoring.xcsoaringscoring.ui.TaskFiltering
import com.soaringscoring.xcsoaringscoring.util.dateOnly

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    contest: Contest,
    state: AppUiState,
    onBack: () -> Unit,
    onSelectClass: (ContestClass) -> Unit,
    onDownload: (TaskRow) -> Unit,
    onDownloadWaypoints: () -> Unit,
    onDismissStatus: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contest.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.downloadingWaypoints) {
                        CircularProgressIndicator(
                            Modifier.padding(12.dp).size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onDownloadWaypoints) {
                            Icon(Icons.Filled.Place, contentDescription = "Get waypoints for this contest")
                        }
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
            SelectedFoldersSummary(state)
            HorizontalDivider()
            ClassChipsRow(state = state, onSelectClass = onSelectClass)
            HorizontalDivider()

            val timeFrame = ContestGrouping.categorize(contest)
            val visibleTasks = TaskFiltering.visibleTasks(state.tasks, state.selectedClass, timeFrame)

            when {
                state.tasksLoading || state.classesLoading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.tasksError != null -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(state.tasksError, modifier = Modifier.align(Alignment.Center), textAlign = TextAlign.Center)
                }
                state.classesError != null -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(state.classesError, modifier = Modifier.align(Alignment.Center), textAlign = TextAlign.Center)
                }
                state.selectedClass == null -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        if (state.classes.isEmpty()) "No classes found for this contest."
                        else "Choose a class above to see its tasks.",
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }
                visibleTasks.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        emptyTasksMessage(timeFrame),
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleTasks) { task ->
                        TaskCard(
                            task = task,
                            isDownloading = state.downloadingTaskId == task.taskId,
                            onDownload = { onDownload(task) }
                        )
                    }
                }
            }
        }
    }
}

private fun emptyTasksMessage(timeFrame: ContestTimeFrame): String =
    when (timeFrame) {
        ContestTimeFrame.CURRENT -> "No task published for today yet."
        else -> "No published tasks for this class."
    }

@Composable
private fun ClassChipsRow(state: AppUiState, onSelectClass: (ContestClass) -> Unit) {
    if (state.classes.size <= 1) return // nothing meaningful to choose between
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.classes) { contestClass ->
            FilterChip(
                selected = state.selectedClass?.id == contestClass.id,
                onClick = { onSelectClass(contestClass) },
                label = { Text(contestClass.code ?: contestClass.name) }
            )
        }
    }
}

@Composable
private fun SelectedFoldersSummary(state: AppUiState) {
    val selectedNames = state.targetFolders.filter { it.selected }.mapNotNull { it.doc.name }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (selectedNames.isEmpty()) "No folders selected — pick some on the contest list screen"
            else "Saving to: ${selectedNames.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TaskCard(task: TaskRow, isDownloading: Boolean, onDownload: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Day ${task.dayNumber} — ${task.className ?: task.displayLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                val extra = buildString {
                    append(dateOnly(task.date))
                    if (task.isOfficialTask) append(" · official")
                    task.dhtHandicap?.let { append(" · handicap $it") }
                }
                Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isDownloading) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = "Load into XCSoar")
                }
            }
        }
    }
}
