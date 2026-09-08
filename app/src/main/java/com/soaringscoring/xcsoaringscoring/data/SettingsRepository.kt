package com.soaringscoring.xcsoaringscoring.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.soaringscoring.xcsoaringscoring.api.DustDevilExchangeResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "ss_task_loader_settings")

/** One task file already saved to the device for a given day/class/handicap slot. */
@Serializable
data class DownloadedTaskVariant(
    val taskId: String,
    val fileName: String,
    val wasOfficialAtDownload: Boolean
)

/**
 * The last day/class(/handicap) drill-down download, and every variant (official or
 * alternate) that was actually saved for it - see docs/FEATURE-check-updated-task.md.
 * Single global slot, not a history - confirmed acceptable: a pilot is assumed to be
 * tracking one contest/class at a time.
 */
@Serializable
data class LastDownloadedTaskGroup(
    val contestId: String,
    val contestName: String,
    val classId: String,
    val className: String,
    val dayId: String,
    val dhtHandicap: Double? = null,
    val variants: List<DownloadedTaskVariant>,
    /** taskId last actually written to default.tsk, so Check can tell "already current." */
    val confirmedOfficialTaskId: String? = null
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val MEDIA_TREE_URI = stringPreferencesKey("media_tree_uri")
        val UPLOAD_API_KEY = stringPreferencesKey("upload_api_key")
        val ENTRY_ADDRESS = stringPreferencesKey("entry_address")
        val SELECTED_FOLDER_URIS = stringSetPreferencesKey("selected_folder_uris")
        val DUSTDEVIL_SESSION_JSON = stringPreferencesKey("dustdevil_session_json")
        val DUSTDEVIL_SELECTED_LOCAL_PART = stringPreferencesKey("dustdevil_selected_local_part")
        val LAST_DOWNLOADED_TASK_GROUP_JSON = stringPreferencesKey("last_downloaded_task_group_json")
        val DOWNLOAD_ALL_ALTERNATES = booleanPreferencesKey("download_all_alternates")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val apiKey: Flow<String> = context.dataStore.data.map { it[Keys.API_KEY].orEmpty() }
    val mediaTreeUri: Flow<String?> = context.dataStore.data.map { it[Keys.MEDIA_TREE_URI] }
    val uploadApiKey: Flow<String> = context.dataStore.data.map { it[Keys.UPLOAD_API_KEY].orEmpty() }
    val entryAddress: Flow<String> = context.dataStore.data.map { it[Keys.ENTRY_ADDRESS].orEmpty() }
    val selectedFolderUris: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.SELECTED_FOLDER_URIS] ?: emptySet() }

    /** Cached result of the last successful DustDevil.cloud sign-in exchange, if any. */
    val dustDevilSession: Flow<DustDevilExchangeResponse?> = context.dataStore.data.map { prefs ->
        prefs[Keys.DUSTDEVIL_SESSION_JSON]?.let {
            try {
                json.decodeFromString(DustDevilExchangeResponse.serializer(), it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /** `localPart` of the entry last picked for upload, so it's remembered between sessions. */
    val dustDevilSelectedLocalPart: Flow<String?> =
        context.dataStore.data.map { it[Keys.DUSTDEVIL_SELECTED_LOCAL_PART] }

    /** See `LastDownloadedTaskGroup` - the last day/class(/handicap) group downloaded, if any. */
    val lastDownloadedTaskGroup: Flow<LastDownloadedTaskGroup?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_DOWNLOADED_TASK_GROUP_JSON]?.let {
            try {
                json.decodeFromString(LastDownloadedTaskGroup.serializer(), it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Set-before-use-and-retain: whether a drill-down download grabs every candidate
     * task for a day or just the one flagged official. Defaults to `true` (download
     * everything) since that's the richer, connectivity-resilient behavior - see
     * docs/FEATURE-check-updated-task.md and CLAUDE.md gotcha 15.
     */
    val downloadAllAlternates: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DOWNLOAD_ALL_ALTERNATES] ?: true }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[Keys.API_KEY] = value }
    }

    suspend fun setMediaTreeUri(uri: String) {
        context.dataStore.edit { it[Keys.MEDIA_TREE_URI] = uri }
    }

    suspend fun setUploadSettings(uploadApiKey: String, entryAddress: String) {
        context.dataStore.edit {
            it[Keys.UPLOAD_API_KEY] = uploadApiKey
            it[Keys.ENTRY_ADDRESS] = entryAddress
        }
    }

    suspend fun setSelectedFolderUris(uris: Set<String>) {
        context.dataStore.edit { it[Keys.SELECTED_FOLDER_URIS] = uris }
    }

    suspend fun setDustDevilSession(session: DustDevilExchangeResponse) {
        val encoded = json.encodeToString(DustDevilExchangeResponse.serializer(), session)
        context.dataStore.edit { it[Keys.DUSTDEVIL_SESSION_JSON] = encoded }
    }

    suspend fun setDustDevilSelectedLocalPart(localPart: String) {
        context.dataStore.edit { it[Keys.DUSTDEVIL_SELECTED_LOCAL_PART] = localPart }
    }

    /** Signs out - clears the cached pilot/entries and the remembered selection. */
    suspend fun clearDustDevilSession() {
        context.dataStore.edit {
            it.remove(Keys.DUSTDEVIL_SESSION_JSON)
            it.remove(Keys.DUSTDEVIL_SELECTED_LOCAL_PART)
        }
    }

    suspend fun setLastDownloadedTaskGroup(group: LastDownloadedTaskGroup) {
        val encoded = json.encodeToString(LastDownloadedTaskGroup.serializer(), group)
        context.dataStore.edit { it[Keys.LAST_DOWNLOADED_TASK_GROUP_JSON] = encoded }
    }

    /**
     * Called whenever `downloadAllAlternates` changes - the stored record no longer
     * reliably describes what's on disk under the new setting, so it's cleared
     * outright rather than left stale. See CLAUDE.md gotcha 15.
     */
    suspend fun clearLastDownloadedTaskGroup() {
        context.dataStore.edit { it.remove(Keys.LAST_DOWNLOADED_TASK_GROUP_JSON) }
    }

    suspend fun setDownloadAllAlternates(value: Boolean) {
        context.dataStore.edit { it[Keys.DOWNLOAD_ALL_ALTERNATES] = value }
    }
}
