package io.github.jgfyyvzss.xcsoaringscoring.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.jgfyyvzss.xcsoaringscoring.BuildConfig
import io.github.jgfyyvzss.xcsoaringscoring.ui.AppUiState
import io.github.jgfyyvzss.xcsoaringscoring.ui.TargetFolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onToggleDownloadAllAlternates: (Boolean) -> Unit,
    onToggleFolder: (TargetFolder) -> Unit,
    onChooseMediaFolder: () -> Unit,
    onSaveEntryAddress: (String) -> Unit,
    onSaveExpertKeys: (String, String) -> Unit,
    onStartDustDevilSignIn: () -> Unit,
    onCancelDustDevilSignIn: () -> Unit,
    onSignOutDustDevil: () -> Unit,
    onDismissDustDevilError: () -> Unit
) {
    var text by remember(state.personalKeyOverride) { mutableStateOf(state.personalKeyOverride) }
    var reveal by remember { mutableStateOf(false) }

    var uploadKeyText by remember(state.uploadApiKey) { mutableStateOf(state.uploadApiKey) }
    var uploadKeyReveal by remember { mutableStateOf(false) }
    var entryAddressText by remember(state.entryAddress) { mutableStateOf(state.entryAddress) }
    var showHelp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            DownloadAllAlternatesToggle(
                checked = state.downloadAllAlternates,
                onToggle = onToggleDownloadAllAlternates
            )
            HorizontalDivider()

            TargetFolderCheckboxes(state, onToggleFolder)
            HorizontalDivider()

            MediaFolderAccessSetting(state, onChooseMediaFolder)
            HorizontalDivider()

            DustDevilSignInSetting(
                state = state,
                onStartSignIn = onStartDustDevilSignIn,
                onCancelSignIn = onCancelDustDevilSignIn,
                onSignOut = onSignOutDustDevil,
                onDismissError = onDismissDustDevilError
            )

            HorizontalDivider()

            Column(Modifier.padding(16.dp)) {
                Text("Flight upload entry address", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Only if NOT signing in with DustDevil/SoaringScoring above.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = entryAddressText,
                    onValueChange = { entryAddressText = it },
                    label = { Text("Entry address (e.g. c2-skyrace)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onSaveEntryAddress(entryAddressText.trim()) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save")
                }
            }
            HorizontalDivider()

            Column(Modifier.padding(16.dp)) {
                Text(
                    "Expert Features",
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = FontStyle.Italic
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Override the app's built-in key. Most people never need either of these.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Tasks API key (optional)") },
                    singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { reveal = !reveal }) {
                            Text(if (reveal) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uploadKeyText,
                    onValueChange = { uploadKeyText = it },
                    label = { Text("Upload API key (optional)") },
                    singleLine = true,
                    visualTransformation = if (uploadKeyReveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { uploadKeyReveal = !uploadKeyReveal }) {
                            Text(if (uploadKeyReveal) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onSaveExpertKeys(text.trim(), uploadKeyText.trim()) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save")
                }
            }
            HorizontalDivider()

            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }
}

/**
 * Set-before-use-and-retain (see CLAUDE.md gotcha 15 / docs/FEATURE-check-updated-task.md) -
 * pick this once before an event and leave it. Toggling clears the stored "last
 * downloaded" Check record (handled in the ViewModel), so the help text tells pilots
 * to expect a re-download of today's task after flipping it. Moved here from the home
 * screen along with "Save to" (see DEVELOPMENT.md) - both are set-once-per-event
 * choices, not something to reconsider on every visit to the home screen.
 */
@Composable
private fun DownloadAllAlternatesToggle(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.padding(16.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Download Official and Alternate tasks.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/**
 * Sign in once and SoaringScoring resolves the pilot's own contest entries -
 * no more hand-typing a competition number and contest key below. See
 * DEVELOPMENT.md's "DustDevil.cloud sign-in" section: still being rolled out,
 * so this stays alongside the manual fields rather than replacing them yet.
 */
@Composable
private fun DustDevilSignInSetting(
    state: AppUiState,
    onStartSignIn: () -> Unit,
    onCancelSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDismissError: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Text("Sign in with DustDevil/SoaringScoring", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        when {
            state.dustDevilPilot != null -> {
                Text(
                    "Signed in as ${state.dustDevilPilot.name} (${state.dustDevilPilot.email}).",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state.dustDevilEntries.isEmpty()) {
                    Text(
                        "No contest entries found for this pilot yet - a contest DustDevil.cloud " +
                            "knows about but hasn't synced to SoaringScoring won't appear here.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "${state.dustDevilEntries.size} contest entry(ies) found - pick which one " +
                            "to upload to from the upload screen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (state.dustDevilSignInInProgress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Refreshing…", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancelSignIn) { Text("Cancel") }
                } else {
                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = onStartSignIn) { Text("Refresh") }
                        TextButton(onClick = onSignOut) { Text("Sign out") }
                    }
                }
            }
            BuildConfig.SS_DUSTDEVIL_CLIENT_KEY_ID.isBlank() -> Text(
                "Not available yet - waiting on SoaringScoring to approve this app's sign-in " +
                    "redirect. Use the manual fields below in the meantime.",
                style = MaterialTheme.typography.bodySmall
            )
            state.personalKeyOverride.isNotBlank() -> Text(
                "Unavailable while a personal API key override is set below - clear it to sign " +
                    "in, or keep using the manual fields below.",
                style = MaterialTheme.typography.bodySmall
            )
            state.dustDevilSignInInProgress -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Waiting for sign-in to complete…", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancelSignIn) { Text("Cancel") }
            }
            else -> {
                Text(
                    "Resolves your own contest entries automatically - replaces typing a " +
                        "competition number and contest key below.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onStartSignIn) { Text("Sign in with DustDevil/SoaringScoring") }
            }
        }

        state.dustDevilError?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismissError) { Text("Dismiss") }
        }
    }
}

/**
 * One bullet's content. Most are [Plain] text; [Emphasized] bold-highlights the one
 * phrase real tester feedback showed people miss (the SAF folder picker's confirm
 * button - see docs/FEATURE-first-run-help.md); [WithIcon] renders one of this app's
 * own on-screen icons next to a bullet that references it, so the step matches what's
 * actually visible rather than describing an icon in words alone.
 */
private sealed class HelpItem {
    data class Plain(val text: String) : HelpItem()
    data class Emphasized(val prefix: String, val bold: String, val suffix: String) : HelpItem()
    data class WithIcon(val icon: ImageVector, val text: String) : HelpItem()
}

/**
 * Deliberately minimal - just enough for a first-time pilot to get from
 * install to a loaded task. Expect this to grow as real usage surfaces gaps.
 *
 * Shown two ways (see docs/FEATURE-first-run-help.md): manually via this screen's
 * Help icon (unchanged, as always - [blocking] defaults to `false` here, tapping
 * outside still dismisses it same as before), and automatically on first launch
 * only, from `ContestListScreen` ([blocking] = `true` there, per that feature's
 * "dismissed only via an explicit button" requirement). Both trigger this exact
 * same dialog/content, not a second one, so there's only ever one copy to keep
 * current.
 */
@Composable
fun HelpDialog(onDismiss: () -> Unit, blocking: Boolean = false) {
    val points = listOf(
        HelpItem.Plain(
            "Grant access to your device's Android/media folder first - a one-time step " +
                "(Settings → Choose Android/media). In the system folder browser that " +
                "opens: Internal storage → Android → media. " +
       			"Once you're actually inside the \"media\" folder (not just seeing it " +
                "highlighted in a list), ",
            bold = "look for a confirm button - often \"USE THIS FOLDER\"",
            suffix = " - this is often missed; opening the folder " +
                "isn't the same as confirming it. Your phone may show one more confirmation " +
                "popup after that - tap Allow/OK."
        ),
        HelpItem.Plain("Recommended - Sign in to your DustDevil/SoaringScoring account in Settings"),
        HelpItem.Plain("In Settings, tick which XCSoar app(s) to save into and to select igc from."),
        HelpItem.Plain(
            "Toggle Download Alternates ON or OFF. Official tasks always download. If you turn on " +
                "Download Alternates all alternate tasks (eg A, B, C) are also downloaded, allowing " +
                "easy selection within XCSoar if needed."
        ),
       	HelpItem.Plain(
       		bold = "Navigation Icons"),
        HelpItem.WithIcon(
            Icons.Filled.Settings,
            "Settings page. Select Android/media access, Sign in to DustDevil, which installed " +
            "XCSoar(s) to save to, whether to download Alternate tasks, this help file. Find it at " +
            "the top of Main page."
        ),
        HelpItem.WithIcon(
            Icons.Filled.HelpOutline,
            "This help file. Find it at the top of Settings"
        ),
        HelpItem.WithIcon(
            Icons.Filled.UploadFile,
            "Upload igc files for scoring. Login to your account on the settings page first, usually " +
            "once at the start of an event is enough. Find it at the top of the main screen."
        ),
   		HelpItem.WithIcon(
            Icons.Filled.FolderOpen,
            "Select an igc file from a folder other than XCSoar. Allows for USB etc. " +
            "Find it at the top of the Upload screen."
        ),
        HelpItem.WithIcon(
            Icons.Filled.Place,
            "Waypoints for the whole contest. Download once via this icon in the task list's " +
                "top bar. You need to manually select the waypoint file in XCSoar - " +
                "Configuration | File Locations"
        ),
        HelpItem.Plain(
        	bold = "Usage."),
        HelpItem.Plain("Pick your contest, then your class (and handicap, if the day uses one)."),
        HelpItem.Plain(
            "Downloading an official task always overwrites the active default.tsk in XCSoar. " +
                "Copies of all tasks (official and alternates) are saved to XCSoar in case you " +
                "need to load them manually."
        ),
        HelpItem.Plain(
            "Once tasks have been downloaded a Check for updated task Button appears. Tapping " +
                "this checks if the official task has been changed or selected. If XCSoar is " +
                "running already use Task Manager to select the indicated task. If XCSoar is " +
                "not running, it will load the new official task automatically."
        ),
        HelpItem.Plain(
            "Turning ON or OFF the Download Official and Alternate tasks toggle clears download " +
                "history. You will need to re-download today's task(s) after toggling this. It " +
                "is recommended that you set it before the start of an event and leave it until " +
                "the end to avoid confusion."
        ),
        HelpItem.Plain(
            "If your current contest is not appearing for uploads try Refresh in Settings | " +
                "Sign in with DustDevil/SoaringScoring"
        ),
        HelpItem.Plain("! IT IS YOUR RESPONSIBILITY TO CONFIRM THE ACTIVE TASK IN XCSOAR IS THE CORRECT ONE !")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = !blocking,
            dismissOnBackPress = !blocking
        ),
        title = { Text("Getting started") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                points.forEach { point ->
                    when (point) {
                        is HelpItem.Plain -> Text("•  ${point.text}", style = MaterialTheme.typography.bodyMedium)
                        is HelpItem.Emphasized -> Text(
                            buildAnnotatedString {
                                append("•  ${point.prefix}")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(point.bold) }
                                append(point.suffix)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        is HelpItem.WithIcon -> Row {
                            Icon(point.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("•  ${point.text}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}
