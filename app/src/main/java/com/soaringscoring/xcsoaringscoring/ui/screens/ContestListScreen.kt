package com.soaringscoring.xcsoaringscoring.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.soaringscoring.xcsoaringscoring.R
import com.soaringscoring.xcsoaringscoring.api.Contest
import com.soaringscoring.xcsoaringscoring.ui.AppUiState
import com.soaringscoring.xcsoaringscoring.ui.ContestGrouping
import com.soaringscoring.xcsoaringscoring.ui.ContestTimeFrame
import com.soaringscoring.xcsoaringscoring.ui.TargetFolder
import com.soaringscoring.xcsoaringscoring.util.dateOnly

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContestListScreen(
    state: AppUiState,
    onContestClick: (Contest) -> Unit,
    onSettingsClick: () -> Unit,
    onUploadClick: () -> Unit,
    onRetry: () -> Unit,
    onToggleFolder: (TargetFolder) -> Unit,
    onSelectTimeFrame: (ContestTimeFrame) -> Unit
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
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TargetFolderCheckboxes(state, onToggleFolder)
            HorizontalDivider()

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
