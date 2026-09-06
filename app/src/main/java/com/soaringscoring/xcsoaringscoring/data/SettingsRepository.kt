package com.soaringscoring.xcsoaringscoring.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.soaringscoring.xcsoaringscoring.api.DustDevilExchangeResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "ss_task_loader_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val LAST_CONTEST_ID = stringPreferencesKey("last_contest_id")
        val LAST_CONTEST_NAME = stringPreferencesKey("last_contest_name")
        val MEDIA_TREE_URI = stringPreferencesKey("media_tree_uri")
        val UPLOAD_API_KEY = stringPreferencesKey("upload_api_key")
        val ENTRY_ADDRESS = stringPreferencesKey("entry_address")
        val SELECTED_FOLDER_URIS = stringSetPreferencesKey("selected_folder_uris")
        val DUSTDEVIL_SESSION_JSON = stringPreferencesKey("dustdevil_session_json")
        val DUSTDEVIL_SELECTED_LOCAL_PART = stringPreferencesKey("dustdevil_selected_local_part")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val apiKey: Flow<String> = context.dataStore.data.map { it[Keys.API_KEY].orEmpty() }
    val lastContestId: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_CONTEST_ID] }
    val lastContestName: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_CONTEST_NAME] }
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

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[Keys.API_KEY] = value }
    }

    suspend fun setLastContest(id: String, name: String) {
        context.dataStore.edit {
            it[Keys.LAST_CONTEST_ID] = id
            it[Keys.LAST_CONTEST_NAME] = name
        }
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
}
