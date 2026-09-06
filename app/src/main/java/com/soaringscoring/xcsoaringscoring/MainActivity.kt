package com.soaringscoring.xcsoaringscoring

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.soaringscoring.xcsoaringscoring.ui.AppViewModel
import com.soaringscoring.xcsoaringscoring.ui.screens.ContestListScreen
import com.soaringscoring.xcsoaringscoring.ui.screens.SettingsScreen
import com.soaringscoring.xcsoaringscoring.ui.screens.TaskListScreen
import com.soaringscoring.xcsoaringscoring.ui.screens.UploadScreen

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cold start via the DustDevil.cloud redirect (e.g. the OS killed the
        // Activity while the Custom Tab was in front) delivers it here instead
        // of onNewIntent.
        intent?.data?.let(::handleDustDevilRedirectIfMatching)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(viewModel, onStartDustDevilSignIn = ::launchDustDevilSignIn)
                }
            }
        }
    }

    // singleTask (see AndroidManifest.xml) routes the redirect here instead of
    // spinning up a second Activity instance and losing in-memory AppViewModel
    // state (ticked folders, selected contest, etc.) mid sign-in.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(::handleDustDevilRedirectIfMatching)
    }

    private fun handleDustDevilRedirectIfMatching(uri: Uri) {
        if (uri.scheme == BuildConfig.OAUTH_REDIRECT_SCHEME && uri.host == BuildConfig.OAUTH_REDIRECT_HOST) {
            viewModel.handleDustDevilRedirect(uri)
        }
    }

    private fun launchDustDevilSignIn(url: String) {
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, uri)
        } catch (e: Exception) {
            // No browser on this device supports Custom Tabs - fall back to a
            // plain external browser rather than leaving the pilot stuck.
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}

@Composable
private fun AppNavHost(viewModel: AppViewModel, onStartDustDevilSignIn: (String) -> Unit) {
    val navController: NavHostController = rememberNavController()
    val state by viewModel.uiState.collectAsState()

    val mediaTreePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onMediaTreeChosen(uri)
        }
    }

    NavHost(navController = navController, startDestination = "contests") {
        composable("contests") {
            ContestListScreen(
                state = state,
                onContestClick = {
                    viewModel.selectContest(it)
                    navController.navigate("tasks")
                },
                onSettingsClick = { navController.navigate("settings") },
                onUploadClick = { navController.navigate("upload") },
                onRetry = { viewModel.loadContests() },
                onToggleFolder = { viewModel.toggleFolderSelected(it.doc) },
                onSelectTimeFrame = { viewModel.selectTimeFrame(it) }
            )
        }
        composable("tasks") {
            val contest = state.selectedContest
            if (contest != null) {
                TaskListScreen(
                    contest = contest,
                    state = state,
                    onBack = {
                        viewModel.clearSelectedContest()
                        navController.popBackStack()
                    },
                    onSelectClass = { viewModel.selectClass(it) },
                    onDownload = { viewModel.downloadTask(it) },
                    onDownloadWaypoints = { viewModel.downloadWaypoints() },
                    onDismissStatus = { viewModel.clearStatusMessage() }
                )
            }
        }
        composable("settings") {
            SettingsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onChooseMediaFolder = {
                    // Point the system picker at Android/media as a starting hint.
                    mediaTreePicker.launch(null)
                },
                onSaveEntryAddress = {
                    viewModel.saveEntryAddress(it)
                    navController.popBackStack()
                },
                onSaveExpertKeys = { taskKey, uploadKey ->
                    viewModel.saveExpertKeys(taskKey, uploadKey)
                    navController.popBackStack()
                },
                onStartDustDevilSignIn = {
                    viewModel.dustDevilSignInUrl()?.let(onStartDustDevilSignIn)
                },
                onCancelDustDevilSignIn = { viewModel.cancelDustDevilSignIn() },
                onSignOutDustDevil = { viewModel.signOutDustDevil() },
                onDismissDustDevilError = { viewModel.dismissDustDevilError() }
            )
        }
        composable("upload") {
            UploadScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = { viewModel.refreshIgcFiles() },
                onSelectFile = { viewModel.selectFileForUpload(it) },
                onCancelPending = { viewModel.cancelPendingUpload() },
                onConfirmUpload = { viewModel.confirmUpload() },
                onDismissOutcome = { viewModel.dismissUploadOutcome() },
                onSelectDustDevilEntry = { viewModel.selectDustDevilEntry(it) }
            )
        }
    }
}
