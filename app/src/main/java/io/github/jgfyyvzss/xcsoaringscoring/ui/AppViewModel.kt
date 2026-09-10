package io.github.jgfyyvzss.xcsoaringscoring.ui

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jgfyyvzss.xcsoaringscoring.BuildConfig
import io.github.jgfyyvzss.xcsoaringscoring.api.ApiResult
import io.github.jgfyyvzss.xcsoaringscoring.api.Contest
import io.github.jgfyyvzss.xcsoaringscoring.api.ContestClass
import io.github.jgfyyvzss.xcsoaringscoring.api.DustDevilEntry
import io.github.jgfyyvzss.xcsoaringscoring.api.DustDevilPilot
import io.github.jgfyyvzss.xcsoaringscoring.api.SoaringScoringApi
import io.github.jgfyyvzss.xcsoaringscoring.api.TaskRow
import io.github.jgfyyvzss.xcsoaringscoring.api.UploadResult
import io.github.jgfyyvzss.xcsoaringscoring.data.DownloadedTaskVariant
import io.github.jgfyyvzss.xcsoaringscoring.data.LastDownloadedTaskGroup
import io.github.jgfyyvzss.xcsoaringscoring.data.SettingsRepository
import io.github.jgfyyvzss.xcsoaringscoring.storage.IgcFile
import io.github.jgfyyvzss.xcsoaringscoring.storage.XcsoarFolderStore
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

/** See docs/FEATURE-check-updated-task.md - checkForUpdatedTask()'s result. */
sealed class UpdateCheckOutcome {
    object NoOfficialYet : UpdateCheckOutcome()
    object NoChange : UpdateCheckOutcome()
    data class ConfirmedLocally(val fileName: String) : UpdateCheckOutcome()
    data class NeedsDownload(val newTask: TaskRow) : UpdateCheckOutcome()
    data class Error(val message: String) : UpdateCheckOutcome()
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

    val downloadingGroupKey: TaskGroupKey? = null,
    val downloadingWaypoints: Boolean = false,
    val statusMessage: String? = null,

    // --- Check for updated official task (see docs/FEATURE-check-updated-task.md) ---
    val lastDownloadedTaskGroup: LastDownloadedTaskGroup? = null,
    val checkingForUpdate: Boolean = false,
    val updateCheckOutcome: UpdateCheckOutcome? = null,
    // "Open" on the Check card - re-fetching the real Contest before navigating (see
    // openLastDownloadedGroup()) can take a moment with no feedback otherwise.
    val openingLastDownloadedGroup: Boolean = false,
    // Set-before-use-and-retain - see setDownloadAllAlternates().
    val downloadAllAlternates: Boolean = true,
    // Defaults true (not false) so the brief window before settings finish loading
    // from DataStore never flashes the dialog for a returning pilot who's long since
    // dismissed it - see docs/FEATURE-first-run-help.md.
    val hasSeenFirstRunHelp: Boolean = true,

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
            val lastDownloadedTaskGroup = settings.lastDownloadedTaskGroup.first()
            val downloadAllAlternates = settings.downloadAllAlternates.first()
            val hasSeenFirstRunHelp = settings.hasSeenFirstRunHelp.first()
            _uiState.value = _uiState.value.copy(
                apiKey = effectiveKey,
                personalKeyOverride = savedKey,
                mediaTreeUri = treeUriString?.let(Uri::parse),
                uploadApiKey = uploadKey,
                entryAddress = address,
                lastDownloadedTaskGroup = lastDownloadedTaskGroup,
                downloadAllAlternates = downloadAllAlternates,
                hasSeenFirstRunHelp = hasSeenFirstRunHelp,
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

    /**
     * Set-before-use-and-retain, by design: a pilot picks this once before an event
     * and leaves it - changing mid-comp is their call, not something this app tries
     * to handle gracefully. What it *does* guarantee is a clean, explainable state
     * either way: flipping this always clears the stored "last downloaded" Check
     * record, since that record's `variants` list would otherwise no longer reliably
     * describe what's actually on disk under the new setting. See
     * docs/FEATURE-check-updated-task.md and CLAUDE.md gotcha 15.
     */
    fun setDownloadAllAlternates(value: Boolean) {
        _uiState.value = _uiState.value.copy(downloadAllAlternates = value, lastDownloadedTaskGroup = null)
        viewModelScope.launch {
            settings.setDownloadAllAlternates(value)
            settings.clearLastDownloadedTaskGroup()
        }
    }

    // --- Download ---

    /**
     * Downloads every candidate task in [group] by default (not just one) -
     * SoaringScoring publishes alternates before a day's task is made official, so a
     * drill-down download grabs all of them onto the device; only the one flagged
     * `isOfficialTask` (if any) also becomes `default.tsk`. Narrows to just the
     * official task instead when `downloadAllAlternates` is off (unless none is
     * flagged official yet, in which case there's nothing to narrow to). See
     * docs/FEATURE-check-updated-task.md.
     */
    fun downloadTaskGroup(group: TaskGroup) {
        val state = _uiState.value
        val key = state.apiKey
        val selectedFolders = state.targetFolders.filter { it.selected }
        val contest = state.selectedContest
        val contestClass = state.selectedClass
        if (key.isBlank()) {
            _uiState.value = state.copy(statusMessage = "Add an API key in Settings first.")
            return
        }
        if (selectedFolders.isEmpty()) {
            _uiState.value = state.copy(statusMessage = "Choose at least one XCSoar folder first.")
            return
        }
        if (contest == null || contestClass == null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(downloadingGroupKey = group.key, statusMessage = null)

            // downloadAllAlternates = false narrows to just the official task - but if
            // nothing's flagged official yet (the day hasn't firmed up), there's nothing
            // to narrow to, so fall back to everything rather than download nothing.
            val officialOnly = group.variants.filter { it.isOfficialTask }
            val toDownload = if (state.downloadAllAlternates || officialOnly.isEmpty()) group.variants else officialOnly

            val downloadedVariants = mutableListOf<DownloadedTaskVariant>()
            var failureMessage: String? = null

            toDownload.forEach { task ->
                val result = downloadAndWriteVariant(task, selectedFolders, key)
                result.variant?.let { downloadedVariants += it }
                result.failureMessage?.let { failureMessage = it }
            }

            if (downloadedVariants.isNotEmpty()) {
                val updatedGroup = LastDownloadedTaskGroup(
                    contestId = contest.id,
                    contestName = contest.name,
                    classId = contestClass.id,
                    className = contestClass.name,
                    dayId = group.key.dayId,
                    dhtHandicap = group.key.dhtHandicap,
                    variants = downloadedVariants,
                    confirmedOfficialTaskId = downloadedVariants.firstOrNull { it.wasOfficialAtDownload }?.taskId
                )
                settings.setLastDownloadedTaskGroup(updatedGroup)
                _uiState.value = _uiState.value.copy(lastDownloadedTaskGroup = updatedGroup)
            }

            val activeCount = downloadedVariants.count { it.wasOfficialAtDownload }
            val alternateCount = downloadedVariants.size - activeCount
            val baseMessage =
                "Downloaded $activeCount active task, $alternateCount alternates into ${selectedFolders.size} folder(s)."
            _uiState.value = _uiState.value.copy(
                downloadingGroupKey = null,
                statusMessage = if (failureMessage != null) "$baseMessage Some downloads failed: $failureMessage" else baseMessage
            )
        }
    }

    private class VariantWriteResult(val variant: DownloadedTaskVariant?, val failureMessage: String?)

    /**
     * Downloads one task's file and writes it under its retained server filename to
     * every ticked folder; additionally writes to `default.tsk` if it's the official
     * task. A variant only counts as saved if at least one folder write actually
     * succeeded - never record something as downloaded that never reached disk.
     */
    private suspend fun downloadAndWriteVariant(
        task: TaskRow,
        selectedFolders: List<TargetFolder>,
        apiKey: String
    ): VariantWriteResult {
        return when (val result = api.downloadTaskFile(task.files.xcsoarTsk, apiKey)) {
            is ApiResult.Success -> {
                val fileName = taskFileName(result.data.fileName, task.taskId)
                var okCount = 0
                selectedFolders.forEach { folder ->
                    val savedNamed = XcsoarFolderStore.writeTaskFile(
                        getApplication(), folder.doc, fileName, result.data.bytes
                    )
                    if (task.isOfficialTask) {
                        XcsoarFolderStore.writeTaskFile(getApplication(), folder.doc, "default.tsk", result.data.bytes)
                    }
                    if (savedNamed) okCount++
                }
                VariantWriteResult(
                    variant = if (okCount > 0) DownloadedTaskVariant(task.taskId, fileName, task.isOfficialTask) else null,
                    failureMessage = null
                )
            }
            is ApiResult.Failure -> VariantWriteResult(variant = null, failureMessage = describeError(result))
        }
    }

    /**
     * TEMPORARY WORKAROUND (see CLAUDE.md / DEVELOPMENT.md) - raised with the
     * SoaringScoring dev, not yet resolved on their end: the tasks endpoint
     * currently returns the same `displayLabel` for every alternate on a day, with
     * no other human-distinguishable field, so there's no way to tell task A from
     * task B by name alone - and no guarantee the server's own download filename
     * (when it supplies one) is any more distinguishing, since it may well be
     * derived from that same label. Until the dev resolves this, every saved task
     * filename gets a taskId stub appended - unconditionally, not just when
     * multiple variants are downloaded together - so files are always
     * distinguishable in the Tasks folder regardless of what the server names them.
     * Revert to trusting the server's filename outright once this is fixed upstream.
     */
    private fun taskFileName(serverFileName: String?, taskId: String): String {
        val base = serverFileName?.takeIf { it.isNotBlank() } ?: "soaringscoring_task.tsk"
        val stub = taskId.takeLast(8)
        val dotIndex = base.lastIndexOf('.')
        return if (dotIndex > 0) "${base.substring(0, dotIndex)}_$stub${base.substring(dotIndex)}" else "${base}_$stub"
    }

    /**
     * Resolves "what is the current official task for the last thing I downloaded,"
     * reusing already-downloaded bytes with no network file transfer when possible -
     * see docs/FEATURE-check-updated-task.md. Only needs a lightweight metadata call
     * (`getTasks()`); the local-file-first path matters most exactly when
     * connectivity is worst (out at the launch, vs. at the pilot briefing).
     */
    fun checkForUpdatedTask() {
        val group = _uiState.value.lastDownloadedTaskGroup ?: return
        val key = _uiState.value.apiKey
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(checkingForUpdate = true, updateCheckOutcome = null)
            when (val result = api.getTasks(group.contestId, key)) {
                is ApiResult.Success -> {
                    val officialRow = result.data.tasks.firstOrNull {
                        it.dayId == group.dayId && it.classId == group.classId &&
                            it.dhtHandicap == group.dhtHandicap && it.isOfficialTask
                    }
                    val outcome = resolveUpdateOutcome(group, officialRow)
                    _uiState.value = _uiState.value.copy(checkingForUpdate = false, updateCheckOutcome = outcome)
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    checkingForUpdate = false,
                    updateCheckOutcome = UpdateCheckOutcome.Error(describeError(result))
                )
            }
        }
    }

    private suspend fun resolveUpdateOutcome(group: LastDownloadedTaskGroup, officialRow: TaskRow?): UpdateCheckOutcome {
        if (officialRow == null) return UpdateCheckOutcome.NoOfficialYet
        if (officialRow.taskId == group.confirmedOfficialTaskId) return UpdateCheckOutcome.NoChange

        val cachedVariant = group.variants.firstOrNull { it.taskId == officialRow.taskId }
        if (cachedVariant != null) {
            val selectedFolders = _uiState.value.targetFolders.filter { it.selected }
            var bytes: ByteArray? = null
            for (folder in selectedFolders) {
                bytes = XcsoarFolderStore.readTaskFile(getApplication(), folder.doc, cachedVariant.fileName)
                if (bytes != null) break
            }
            if (bytes != null) {
                var wroteAny = false
                selectedFolders.forEach { folder ->
                    if (XcsoarFolderStore.writeTaskFile(getApplication(), folder.doc, "default.tsk", bytes)) wroteAny = true
                }
                if (wroteAny) {
                    val updatedGroup = group.copy(confirmedOfficialTaskId = officialRow.taskId)
                    settings.setLastDownloadedTaskGroup(updatedGroup)
                    _uiState.value = _uiState.value.copy(lastDownloadedTaskGroup = updatedGroup)
                    return UpdateCheckOutcome.ConfirmedLocally(cachedVariant.fileName)
                }
                // Local copy was found but couldn't be written anywhere (e.g.
                // permissions revoked) - fall through to a fresh network download
                // rather than silently doing nothing.
            }
            // findFile() miss or read failure - safe to just treat as "never seen
            // this task before" and fetch it fresh below.
        }
        return UpdateCheckOutcome.NeedsDownload(officialRow)
    }

    /** Pilot confirmed the `NeedsDownload` dialog - fetch and apply the new official task. */
    fun confirmUpdatedTaskDownload(task: TaskRow) {
        val state = _uiState.value
        val group = state.lastDownloadedTaskGroup ?: return
        val key = state.apiKey
        val selectedFolders = state.targetFolders.filter { it.selected }
        _uiState.value = state.copy(updateCheckOutcome = null)
        if (selectedFolders.isEmpty()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Choose at least one XCSoar folder first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(checkingForUpdate = true)
            val result = downloadAndWriteVariant(task, selectedFolders, key)
            if (result.variant != null) {
                val updatedGroup = group.copy(
                    variants = group.variants.filterNot { it.taskId == result.variant.taskId } + result.variant,
                    confirmedOfficialTaskId = result.variant.taskId
                )
                settings.setLastDownloadedTaskGroup(updatedGroup)
                _uiState.value = _uiState.value.copy(
                    checkingForUpdate = false,
                    lastDownloadedTaskGroup = updatedGroup,
                    statusMessage = "Official task updated and loaded."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    checkingForUpdate = false,
                    statusMessage = "Download failed: ${result.failureMessage ?: "unknown error"}"
                )
            }
        }
    }

    fun dismissUpdateCheckOutcome() {
        _uiState.value = _uiState.value.copy(updateCheckOutcome = null)
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

    /**
     * "Open" action on the home screen's Check card - jumps back into this
     * contest/class regardless of which day was last downloaded. Deliberately
     * ignores the stored `dayId`: this re-fetches a real `Contest`/`ContestClass`
     * (never trusting the stored name-only fields, same discipline as normal
     * browsing) and selects them exactly like tapping through the contest list
     * would - `TaskListScreen`'s own date-driven filtering then shows whatever
     * day is actually current, closing the gap where a pilot's stored group
     * still points at yesterday's day but the contest/class are still right.
     *
     * Takes a completion callback rather than being fire-and-forget like
     * `selectContest()`: MainActivity's "tasks" route renders nothing at all
     * when `selectedContest` is null (see its `composable("tasks")` block), so
     * navigating there before the contest resolves would be a dead-end blank
     * screen with no way back short of the OS back button. [onResolved] only
     * fires once the contest is actually found; a missing class still resolves
     * (same as normal browsing before picking a class) but a missing contest
     * does not navigate at all - the failure surfaces via `statusMessage` on
     * the home screen instead. `openingLastDownloadedGroup` covers the wait
     * for this first fetch (a large contest list can take a moment) with no
     * other feedback otherwise - real user feedback, not a hypothetical.
     *
     * Deliberately doesn't call `selectContest()`/`loadClasses()` - those fire
     * their own classes fetch, which would race a second one here needed to
     * find this specific class by id (whichever finished last would silently
     * win `selectedClass`, sometimes back to null). One classes fetch instead,
     * selecting the matched class directly - `loadTasks()` alone is safe to
     * reuse since nothing else calls it concurrently for this contest.
     */
    fun openLastDownloadedGroup(onResolved: () -> Unit) {
        val group = _uiState.value.lastDownloadedTaskGroup ?: return
        val key = _uiState.value.apiKey.ifBlank { null }
        _uiState.value = _uiState.value.copy(openingLastDownloadedGroup = true)
        viewModelScope.launch {
            val contest = (api.getContests(key) as? ApiResult.Success)?.data
                ?.find { it.id == group.contestId }
            if (contest == null) {
                _uiState.value = _uiState.value.copy(
                    openingLastDownloadedGroup = false,
                    statusMessage = "Couldn't find that contest anymore - it may have been removed."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                openingLastDownloadedGroup = false,
                selectedContest = contest,
                tasks = emptyList(),
                tasksError = null,
                classes = emptyList(),
                classesError = null,
                selectedClass = null
            )
            onResolved()
            loadTasks(contest)

            _uiState.value = _uiState.value.copy(classesLoading = true, classesError = null)
            when (val result = api.getClasses(contest.id, key)) {
                is ApiResult.Success -> {
                    val classes = result.data
                    _uiState.value = _uiState.value.copy(
                        classes = classes,
                        classesLoading = false,
                        // Prefer the class this group was actually for; fall back to the
                        // usual single-class auto-select if it's gone missing.
                        selectedClass = classes.find { it.id == group.classId } ?: classes.singleOrNull()
                    )
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    classesLoading = false,
                    classesError = describeError(result)
                )
            }
        }
    }

    /** Explicit "Clear" on the Check card - same underlying clear as toggling Alternates. */
    fun clearLastDownloadedGroup() {
        _uiState.value = _uiState.value.copy(lastDownloadedTaskGroup = null)
        viewModelScope.launch { settings.clearLastDownloadedTaskGroup() }
    }

    /**
     * First-run help dialog dismissed (either its automatic first-launch showing or
     * the manual Settings help icon) - see docs/FEATURE-first-run-help.md. Persists
     * so the automatic showing never fires again.
     */
    fun dismissFirstRunHelp() {
        _uiState.value = _uiState.value.copy(hasSeenFirstRunHelp = true)
        viewModelScope.launch { settings.setHasSeenFirstRunHelp(true) }
    }

    // --- DustDevil.cloud sign-in ---

    /**
     * URL to open in a Chrome Custom Tab to start sign-in, or null if it's not
     * available right now: either the redirect URI hasn't been approved yet
     * (`client_key_id` unconfigured - see local.properties.example), or a personal
     * API key override is set. Sign-in must use the exact same key that started
     * the flow to redeem the code later, so it's restricted to the app's built-in
     * key only rather than risk a silent mismatch - see DEVELOPMENT.md.
     *
     * Also used for the "Refresh" action on an already-signed-in pilot: DustDevil's
     * API has no lighter-weight way to re-fetch `entries` (see
     * DustDevil_OAuth_reference.md), so picking up contest-side changes means
     * re-running this same flow. `handleDustDevilRedirect()` doesn't clear the
     * existing session first, so the old entries stay visible until the new ones
     * arrive.
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
        val previousLocalPart = _uiState.value.dustDevilSelectedLocalPart
        viewModelScope.launch {
            // Must be the app's own built-in key - the same one whose client_key_id
            // started the flow. A personal override here would make the exchange
            // fail (indistinguishable from an expired/reused code).
            when (val result = api.exchangeDustDevilCode(code, BuildConfig.SS_API_KEY)) {
                is ApiResult.Success -> {
                    val entries = result.data.entries
                    // This path also runs for a "Refresh" from Settings (re-running sign-in
                    // on an already-signed-in pilot to pick up DustDevil-side changes - there's
                    // no lighter-weight refresh endpoint, see DustDevil_OAuth_reference.md).
                    // Keep whichever entry was already selected if it's still present, rather
                    // than silently jumping back to the first entry on every refresh.
                    val selectedLocalPart = entries.find { it.localPart == previousLocalPart }?.localPart
                        ?: entries.firstOrNull()?.localPart
                    settings.setDustDevilSession(result.data)
                    selectedLocalPart?.let { settings.setDustDevilSelectedLocalPart(it) }
                    _uiState.value = _uiState.value.copy(
                        dustDevilSignInInProgress = false,
                        dustDevilError = null,
                        dustDevilPilot = result.data.pilot,
                        dustDevilEntries = entries,
                        dustDevilSelectedLocalPart = selectedLocalPart
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

    /**
     * One-off alternative to the ticked-folder scan above: lets a pilot browse to
     * any folder (e.g. Downloads) and replaces `igcFiles` with whatever .igc files
     * are found there, via the same `findIgcFiles()` used for XCSoar's own folders
     * - its logs-subfolder-or-root fallback already does the right thing for a
     * plain folder of files. Deliberately doesn't call
     * `takePersistableUriPermission()` on the picked tree URI: this is a
     * browse-once-for-this-visit action, not a folder the app should remember -
     * leaving and re-entering the Upload screen re-runs `refreshIgcFiles()`
     * (see its `LaunchedEffect` in `UploadScreen`) and reverts to the normal scan.
     */
    fun browseIgcFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(igcFilesLoading = true)
            val folder = DocumentFile.fromTreeUri(getApplication(), treeUri)
            val found = withContext(Dispatchers.IO) {
                folder?.let { XcsoarFolderStore.findIgcFiles(it) } ?: emptyList()
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
                    uploadOutcome = UploadOutcome.Failure(
                        describeUploadError(result, addressFromSignIn = signedInEntry != null)
                    )
                )
            }
        }
    }

    /**
     * [addressFromSignIn] distinguishes the two address-related failures - telling a
     * signed-in pilot to "check the competition number" is actively wrong advice
     * when they never typed one; the localPart came from the exchange response.
     * Full table: docs/FlightUpload_API_errors.md.
     */
    private fun describeUploadError(failure: ApiResult.Failure, addressFromSignIn: Boolean): String = when (failure.code) {
        "MISSING_API_KEY" -> "No upload API key set. Add one in Settings."
        "INVALID_API_KEY" -> "That upload API key is invalid or has been revoked."
        "INSUFFICIENT_SCOPE" -> "This key doesn't have the flights:write scope."
        "INVALID_ADDRESS" -> if (addressFromSignIn)
            "The entry address from your signed-in account doesn't look right - try signing out and back in."
        else
            "That entry address doesn't look right - check it against the pilot downloads page."
        "ENTRY_NOT_FOUND" -> if (addressFromSignIn)
            "No contest entry matches your signed-in account for this - it may not have synced " +
                "to SoaringScoring yet, or try signing out and back in."
        else
            "No contest entry matches that address - check the competition number and contest key."
        "NO_OFFICIAL_TASK" -> "No official task is set yet for your class today."
        "EMPTY_OR_UNREADABLE_BODY" -> "That file didn't upload correctly - no flight data was received. Try again."
        "INTERNAL" -> "SoaringScoring had a server error - try again shortly."
        else -> failure.message
    }

    fun dismissUploadOutcome() {
        _uiState.value = _uiState.value.copy(uploadOutcome = null)
    }
}
