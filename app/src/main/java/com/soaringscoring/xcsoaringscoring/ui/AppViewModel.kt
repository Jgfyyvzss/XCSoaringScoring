package com.soaringscoring.xcsoaringscoring.ui

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soaringscoring.xcsoaringscoring.BuildConfig
import com.soaringscoring.xcsoaringscoring.api.ApiResult
import com.soaringscoring.xcsoaringscoring.api.Contest
import com.soaringscoring.xcsoaringscoring.api.ContestClass
import com.soaringscoring.xcsoaringscoring.api.DustDevilEntry
import com.soaringscoring.xcsoaringscoring.api.DustDevilPilot
import com.soaringscoring.xcsoaringscoring.api.SoaringScoringApi
import com.soaringscoring.xcsoaringscoring.api.TaskRow
import com.soaringscoring.xcsoaringscoring.api.UploadResult
import com.soaringscoring.xcsoaringscoring.data.SettingsRepository
import com.soaringscoring.xcsoaringscoring.storage.IgcFile
import com.soaringscoring.xcsoaringscoring.storage.XcsoarFolderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TargetFolder(val doc: DocumentFile, val selected: Boolean)

sealed class UploadOutcome {
    data class Success(val result: UploadResult) : UploadOutcome()
    data class Failure(val message: String) : UploadOutcome()
}

data class AppUiState(
    val apiKey: String = "",
    val personalKeyOverride: String = "",
    val mediaTreeUri: Uri? = null,
    val targetFolders: List<TargetFolder> = emptyList(),

    val contests: List<Contest> = emptyList(),
    val contestsLoading: Boolean = false,
    val contestsError: String? = null,
    val selectedTimeFrame: ContestTimeFrame = ContestTimeFrame.CURRENT,

    val selectedContest: Contest? = null,
    val tasks: List<TaskRow> = emptyList(),
    val tasksLoading: Boolean = false,
    val tasksError: String? = null,

    val classes: List<ContestClass> = emptyList(),
    val classesLoading: Boolean = false,
    val classesError: String? = null,
    val selectedClass: ContestClass? = null,

    val downloadingTaskId: String? = null,
    val downloadingWaypoints: Boolean = false,
    val statusMessage: String? = null,

    // --- Flight upload ---
    val uploadApiKey: String = "",
    val entryAddress: String = "",
    val igcFiles: List<IgcFile> = emptyList(),
    val igcFilesLoading: Boolean = false,
    val pendingUploadFile: IgcFile? = null,
    val isUploading: Boolean = false,
    val uploadOutcome: UploadOutcome? = null,

    // --- DustDevil.cloud sign-in (see DEVELOPMENT.md) ---
    val dustDevilPilot: DustDevilPilot? = null,
    val dustDevilEntries: List<DustDevilEntry> = emptyList(),
    val dustDevilSelectedLocalPart: String? = null,
    val dustDevilSignInInProgress: Boolean = false,
    val dustDevilError: String? = null
) {
    val dustDevilSelectedEntry: DustDevilEntry?
        get() = dustDevilEntries.find { it.localPart == dustDevilSelectedLocalPart }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val api = SoaringScoringApi()
    private val settings = SettingsRepository(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedKey = settings.apiKey.first()
            val effectiveKey = savedKey.ifBlank { BuildConfig.SS_API_KEY }
            val treeUriString = settings.mediaTreeUri.first()
            val uploadKey = settings.uploadApiKey.first()
            val address = settings.entryAddress.first()
            val dustDevilSession = settings.dustDevilSession.first()
            val dustDevilSelectedLocalPart = settings.dustDevilSelectedLocalPart.first()
                ?: dustDevilSession?.entries?.firstOrNull()?.localPart
            _uiState.value = _uiState.value.copy(
                apiKey = effectiveKey,
                personalKeyOverride = savedKey,
                mediaTreeUri = treeUriString?.let(Uri::parse),
                uploadApiKey = uploadKey,
                entryAddress = address,
                dustDevilPilot = dustDevilSession?.pilot,
                dustDevilEntries = dustDevilSession?.entries ?: emptyList(),
                dustDevilSelectedLocalPart = dustDevilSelectedLocalPart
            )
            treeUriString?.let { refreshTargetFolders(Uri.parse(it)) }
            loadContests()
        }
    }

    fun loadContests() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(contestsLoading = true, contestsError = null)
            val key = _uiState.value.apiKey.ifBlank { null }
            when (val result = api.getContests(key)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    contests = result.data.sortedByDescending { it.startDate },
                    contestsLoading = false
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    contestsLoading = false,
                    contestsError = describeError(result)
                )
            }
        }
    }

    fun selectContest(contest: Contest) {
        _uiState.value = _uiState.value.copy(
            selectedContest = contest,
            tasks = emptyList(),
            tasksError = null,
            classes = emptyList(),
            classesError = null,
            selectedClass = null
        )
        viewModelScope.launch { settings.setLastContest(contest.id, contest.name) }
        loadTasks(contest)
        loadClasses(contest)
    }

    fun clearSelectedContest() {
        _uiState.value = _uiState.value.copy(
            selectedContest = null,
            tasks = emptyList(),
            tasksError = null,
            classes = emptyList(),
            classesError = null,
            selectedClass = null
        )
    }

    fun selectTimeFrame(timeFrame: ContestTimeFrame) {
        _uiState.value = _uiState.value.copy(selectedTimeFrame = timeFrame)
    }

    fun loadClasses(contest: Contest) {
        val key = _uiState.value.apiKey.ifBlank { null }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(classesLoading = true, classesError = null)
            when (val result = api.getClasses(contest.id, key)) {
                is ApiResult.Success -> {
                    val classes = result.data
                    _uiState.value = _uiState.value.copy(
                        classes = classes,
                        classesLoading = false,
                        // Auto-select when there's only one class - saves a tap for
                        // single-class contests, matching common practice.
                        selectedClass = classes.singleOrNull()
                    )
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    classesLoading = false,
                    classesError = describeError(result)
                )
            }
        }
    }

    fun selectClass(contestClass: ContestClass) {
        _uiState.value = _uiState.value.copy(selectedClass = contestClass)
    }

    fun loadTasks(contest: Contest) {
        val key = _uiState.value.apiKey
        if (key.isBlank()) {
            _uiState.value = _uiState.value.copy(
                tasksError = "Add an API key with the tasks:read scope in Settings first."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(tasksLoading = true, tasksError = null)
            when (val result = api.getTasks(contest.id, key)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    tasks = result.data.tasks.sortedWith(
                        compareBy({ it.dayNumber }, { it.className ?: "" })
                    ),
                    tasksLoading = false
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    tasksLoading = false,
                    tasksError = describeError(result)
                )
            }
        }
    }

    private fun describeError(failure: ApiResult.Failure): String = when (failure.code) {
        "MISSING_API_KEY" -> "No API key set. Add one in Settings."
        "INVALID_API_KEY" -> "That API key is invalid or has been revoked. Check it in Settings."
        "INSUFFICIENT_SCOPE" -> "This key doesn't have the tasks:read scope. Ask SoaringScoring to add it."
        else -> failure.message
    }

    fun saveApiKey(key: String) {
        val effectiveKey = key.ifBlank { BuildConfig.SS_API_KEY }
        _uiState.value = _uiState.value.copy(apiKey = effectiveKey, personalKeyOverride = key)
        viewModelScope.launch { settings.setApiKey(key) }
        loadContests()
    }

    // --- Folder selection (SAF) ---

    /** Call after ACTION_OPEN_DOCUMENT_TREE returns a uri for Android/media. */
    fun onMediaTreeChosen(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        resolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        _uiState.value = _uiState.value.copy(mediaTreeUri = uri)
        viewModelScope.launch {
            settings.setMediaTreeUri(uri.toString())
            refreshTargetFolders(uri)
        }
    }

    private suspend fun refreshTargetFolders(uri: Uri) {
        val found = XcsoarFolderStore.findXcsoarFolders(getApplication(), uri)
        val savedSelection = settings.selectedFolderUris.first()
        _uiState.value = _uiState.value.copy(
            targetFolders = found.map { TargetFolder(it, selected = it.uri.toString() in savedSelection) }
        )
    }

    fun toggleFolderSelected(doc: DocumentFile) {
        val updated = _uiState.value.targetFolders.map {
            if (it.doc.uri == doc.uri) it.copy(selected = !it.selected) else it
        }
        _uiState.value = _uiState.value.copy(targetFolders = updated)
        viewModelScope.launch {
            settings.setSelectedFolderUris(updated.filter { it.selected }.map { it.doc.uri.toString() }.toSet())
        }
    }

    // --- Download ---

    fun downloadTask(task: TaskRow) {
        val state = _uiState.value
        val key = state.apiKey
        val selectedFolders = state.targetFolders.filter { it.selected }
        if (key.isBlank()) {
            _uiState.value = state.copy(statusMessage = "Add an API key in Settings first.")
            return
        }
        if (selectedFolders.isEmpty()) {
            _uiState.value = state.copy(statusMessage = "Choose at least one XCSoar folder first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(downloadingTaskId = task.taskId, statusMessage = null)
            when (val result = api.downloadTaskFile(task.files.xcsoarTsk, key)) {
                is ApiResult.Success -> {
                    var okCount = 0
                    selectedFolders.forEach { folder ->
                        // Written under both names: soaringscoring_task.tsk is the
                        // stable name pilots load as the current task by hand on day
                        // one; default.tsk is the name XCSoar auto-loads on startup,
                        // so every day after that just needs the download, no manual
                        // load required.
                        val savedStableName = XcsoarFolderStore.writeTaskFile(
                            getApplication(),
                            folder.doc,
                            "soaringscoring_task.tsk",
                            result.data.bytes
                        )
                        val savedDefault = XcsoarFolderStore.writeTaskFile(
                            getApplication(),
                            folder.doc,
                            "default.tsk",
                            result.data.bytes
                        )
                        if (savedStableName && savedDefault) okCount++
                    }
                    _uiState.value = _uiState.value.copy(
                        downloadingTaskId = null,
                        statusMessage = if (okCount == selectedFolders.size)
                            "Task loaded into $okCount folder(s)."
                        else
                            "Loaded into $okCount of ${selectedFolders.size} folder(s) — check permissions."
                    )
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    downloadingTaskId = null,
                    statusMessage = "Download failed: ${describeError(result)}"
                )
            }
        }
    }

    /**
     * Downloads the SeeYou .cup waypoint file, once per contest rather than once
     * per task - the underlying turnpoint set is the same all week even though
     * the API bundles it with a specific day's task in the file itself. Any task
     * row's `files.seeyouCup` URL points at the same waypoint database, so we
     * just need one - the earliest day, for a stable/predictable choice.
     */
    fun downloadWaypoints() {
        val state = _uiState.value
        val key = state.apiKey
        val selectedFolders = state.targetFolders.filter { it.selected }
        val sourceTask = state.tasks.minByOrNull { it.dayNumber }

        if (key.isBlank()) {
            _uiState.value = state.copy(statusMessage = "Add an API key in Settings first.")
            return
        }
        if (selectedFolders.isEmpty()) {
            _uiState.value = state.copy(statusMessage = "Choose at least one XCSoar folder first.")
            return
        }
        if (sourceTask == null) {
            _uiState.value = state.copy(statusMessage = "No tasks loaded yet for this contest.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(downloadingWaypoints = true, statusMessage = null)
            when (val result = api.downloadTaskFile(sourceTask.files.seeyouCup, key)) {
                is ApiResult.Success -> {
                    val fileName = result.data.fileName?.takeIf { it.isNotBlank() }
                        ?: "soaringscoring_waypoint.cup"
                    var okCount = 0
                    selectedFolders.forEach { folder ->
                        val ok = XcsoarFolderStore.writeWaypointFile(
                            getApplication(),
                            folder.doc,
                            fileName,
                            result.data.bytes
                        )
                        if (ok) okCount++
                    }
                    _uiState.value = _uiState.value.copy(
                        downloadingWaypoints = false,
                        statusMessage = if (okCount == selectedFolders.size)
                            "Waypoints loaded into $okCount folder(s)."
                        else
                            "Loaded into $okCount of ${selectedFolders.size} folder(s) — check permissions."
                    )
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    downloadingWaypoints = false,
                    statusMessage = "Download failed: ${describeError(result)}"
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    // --- DustDevil.cloud sign-in ---

    /**
     * URL to open in a Chrome Custom Tab to start sign-in, or null if it's not
     * available right now: either the redirect URI hasn't been approved yet
     * (`client_key_id` unconfigured - see local.properties.example), or a personal
     * API key override is set. Sign-in must use the exact same key that started
     * the flow to redeem the code later, so it's restricted to the app's built-in
     * key only rather than risk a silent mismatch - see DEVELOPMENT.md.
     */
    fun dustDevilSignInUrl(): String? {
        if (BuildConfig.SS_DUSTDEVIL_CLIENT_KEY_ID.isBlank()) return null
        if (_uiState.value.personalKeyOverride.isNotBlank()) return null
        _uiState.value = _uiState.value.copy(dustDevilSignInInProgress = true, dustDevilError = null)
        return api.dustDevilMobileStartUrl(BuildConfig.SS_DUSTDEVIL_CLIENT_KEY_ID)
    }

    /** Call if the pilot backs out of the Custom Tab without completing sign-in. */
    fun cancelDustDevilSignIn() {
        _uiState.value = _uiState.value.copy(dustDevilSignInInProgress = false)
    }

    /** Call from `onNewIntent` with the redirect URI caught by the manifest intent-filter. */
    fun handleDustDevilRedirect(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                dustDevilSignInInProgress = false,
                dustDevilError = "Sign-in didn't return a code - try again."
            )
            return
        }
        viewModelScope.launch {
            // Must be the app's own built-in key - the same one whose client_key_id
            // started the flow. A personal override here would make the exchange
            // fail (indistinguishable from an expired/reused code).
            when (val result = api.exchangeDustDevilCode(code, BuildConfig.SS_API_KEY)) {
                is ApiResult.Success -> {
                    val firstLocalPart = result.data.entries.firstOrNull()?.localPart
                    settings.setDustDevilSession(result.data)
                    firstLocalPart?.let { settings.setDustDevilSelectedLocalPart(it) }
                    _uiState.value = _uiState.value.copy(
                        dustDevilSignInInProgress = false,
                        dustDevilError = null,
                        dustDevilPilot = result.data.pilot,
                        dustDevilEntries = result.data.entries,
                        dustDevilSelectedLocalPart = firstLocalPart
                    )
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    dustDevilSignInInProgress = false,
                    dustDevilError = describeDustDevilError(result)
                )
            }
        }
    }

    fun selectDustDevilEntry(entry: DustDevilEntry) {
        _uiState.value = _uiState.value.copy(dustDevilSelectedLocalPart = entry.localPart)
        viewModelScope.launch { settings.setDustDevilSelectedLocalPart(entry.localPart) }
    }

    fun signOutDustDevil() {
        _uiState.value = _uiState.value.copy(
            dustDevilPilot = null,
            dustDevilEntries = emptyList(),
            dustDevilSelectedLocalPart = null
        )
        viewModelScope.launch { settings.clearDustDevilSession() }
    }

    fun dismissDustDevilError() {
        _uiState.value = _uiState.value.copy(dustDevilError = null)
    }

    /**
     * This endpoint's errors are bare HTTP status codes with different meanings at
     * different steps (400 only at the mobile-start redirect, 401/403/404 only at
     * exchange) - not the {code, message} JSON envelope describeError()/
     * describeUploadError() key off. See docs/DustDevil_OAuth_reference.md.
     */
    private fun describeDustDevilError(failure: ApiResult.Failure): String = when (failure.httpCode) {
        400 -> "Sign-in isn't approved yet - ask SoaringScoring to approve this app's redirect URI."
        401, 403 -> "Sign-in couldn't be verified - the app's key may be missing the contests:read scope."
        404 -> "Sign-in link expired or was already used - try signing in again."
        else -> failure.message
    }

    // --- Flight upload ---

    fun saveEntryAddress(address: String) {
        _uiState.value = _uiState.value.copy(entryAddress = address)
        viewModelScope.launch { settings.setEntryAddress(address) }
    }

    private fun saveUploadApiKey(key: String) {
        _uiState.value = _uiState.value.copy(uploadApiKey = key)
        viewModelScope.launch { settings.setUploadApiKey(key) }
    }

    /** Settings' "Expert Features" section saves both override keys from one button. */
    fun saveExpertKeys(taskApiKeyOverride: String, uploadApiKeyOverride: String) {
        saveApiKey(taskApiKeyOverride)
        saveUploadApiKey(uploadApiKeyOverride)
    }

    /** Scans every selected XCSoar folder's logs (recent versions) for .igc files. */
    fun refreshIgcFiles() {
        val selectedFolders = _uiState.value.targetFolders.filter { it.selected }
        if (selectedFolders.isEmpty()) {
            _uiState.value = _uiState.value.copy(igcFiles = emptyList())
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(igcFilesLoading = true)
            val found = withContext(Dispatchers.IO) {
                selectedFolders.flatMap { XcsoarFolderStore.findIgcFiles(it.doc) }
            }
            _uiState.value = _uiState.value.copy(
                igcFiles = found.sortedByDescending { it.doc.lastModified() },
                igcFilesLoading = false
            )
        }
    }

    fun selectFileForUpload(file: IgcFile) {
        _uiState.value = _uiState.value.copy(pendingUploadFile = file)
    }

    fun cancelPendingUpload() {
        _uiState.value = _uiState.value.copy(pendingUploadFile = null)
    }

    fun confirmUpload() {
        val state = _uiState.value
        val file = state.pendingUploadFile ?: return
        // Signed in via DustDevil: identity comes from sign-in, always via the
        // app's own built-in key. Otherwise fall back to the v1 manual key/address
        // entry - kept working deliberately, since a contest DustDevil.cloud
        // hasn't synced to SoaringScoring yet won't appear in the entries list
        // either (see DEVELOPMENT.md). SoaringScoring's key now carries both
        // tasks:read and flights:write, so the manual path's fallback key is the
        // same effective key as everything else, not a required separate one.
        val signedInEntry = state.dustDevilSelectedEntry
        val key = if (signedInEntry != null) BuildConfig.SS_API_KEY else state.uploadApiKey.ifBlank { state.apiKey }
        val address = signedInEntry?.localPart ?: state.entryAddress

        if (key.isBlank()) {
            _uiState.value = state.copy(
                pendingUploadFile = null,
                uploadOutcome = UploadOutcome.Failure("No API key available. Add one in Settings.")
            )
            return
        }
        if (address.isBlank()) {
            _uiState.value = state.copy(
                pendingUploadFile = null,
                uploadOutcome = UploadOutcome.Failure("Set your entry address in Settings first.")
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pendingUploadFile = null, isUploading = true)
            val bytes = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(file.doc.uri)?.use { it.readBytes() }
                }
            } catch (e: Exception) {
                null
            }

            if (bytes == null) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadOutcome = UploadOutcome.Failure("Could not read that file.")
                )
                return@launch
            }

            when (val result = api.uploadFlight(address, key, bytes, file.doc.name ?: "flight.igc")) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadOutcome = UploadOutcome.Success(result.data)
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadOutcome = UploadOutcome.Failure(describeUploadError(result))
                )
            }
        }
    }

    private fun describeUploadError(failure: ApiResult.Failure): String = when (failure.code) {
        "MISSING_API_KEY" -> "No upload API key set. Add one in Settings."
        "INVALID_API_KEY" -> "That upload API key is invalid or has been revoked."
        "INSUFFICIENT_SCOPE" -> "This key doesn't have the flights:write scope."
        "INVALID_ADDRESS" -> "That entry address doesn't look right - check it against the pilot downloads page."
        "ENTRY_NOT_FOUND" -> "No contest entry matches that address - check the competition number and contest key."
        "NO_OFFICIAL_TASK" -> "No official task is set yet for your class today."
        else -> failure.message
    }

    fun dismissUploadOutcome() {
        _uiState.value = _uiState.value.copy(uploadOutcome = null)
    }
}
