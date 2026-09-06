package com.soaringscoring.xcsoaringscoring.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * On Android 11+ apps can't reach `Android/media/...` with plain filesystem
 * APIs, but that path (unlike `Android/data`) is NOT blocked from the SAF
 * folder picker. So: the user grants access to `Android/media` once via
 * ACTION_OPEN_DOCUMENT_TREE, we persist that permission, and from then on
 * we can list/write into it like any other SAF tree.
 */
object XcsoarFolderStore {

    // Cache of (parent folder uri, filename) -> resolved file uri. Once we've found or
    // created the target file, every later write reuses this directly instead of
    // re-searching via findFile() each time - that search has been observed to
    // occasionally miss an existing document on some devices/providers.
    private val fileUriCache = mutableMapOf<Pair<Uri, String>, Uri>()

    /**
     * Looks at the direct children of the granted `Android/media` tree and
     * returns the ones that look like an XCSoar variant (name contains
     * "soar", same heuristic xcomps uses so it picks up XCSoar, XCSoar Jet,
     * and any future forks without hardcoding package names).
     */
    fun findXcsoarFolders(context: Context, mediaTreeUri: Uri): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(context, mediaTreeUri) ?: return emptyList()
        if (!root.isDirectory) return emptyList()

        val matches = LinkedHashMap<Uri, DocumentFile>()

        // The user may have picked the XCSoar-variant folder itself (e.g.
        // org.xcsoar) rather than its parent Android/media folder — count
        // that as a match too, instead of only ever looking one level down.
        if (root.name?.contains("soar", ignoreCase = true) == true) {
            matches[root.uri] = root
        }

        root.listFiles().forEach { child ->
            if (child.isDirectory && child.name?.contains("soar", ignoreCase = true) == true) {
                matches[child.uri] = child
            }
        }

        return matches.values.toList()
    }

    /**
     * Writes [bytes] into the correct tasks folder for [xcsoarFolder]. Different
     * XCSoar installs use different casing for this subfolder ("Tasks" vs "tasks") -
     * this looks for whichever one actually exists on THIS install and writes there.
     * It never creates that subfolder itself (XCSoar always creates it on first run);
     * if neither case exists, it falls back to writing directly into the XCSoar
     * folder's root, which XCSoar/XCSoar Jet also pick up correctly.
     */
    fun writeTaskFile(
        context: Context,
        xcsoarFolder: DocumentFile,
        filename: String,
        bytes: ByteArray
    ): Boolean = writeFile(context, xcsoarFolder, "tasks", filename, bytes)

    /**
     * Writes [bytes] into the correct waypoints folder for [xcsoarFolder]. Recent
     * XCSoar versions use a "waypoints" subfolder (same case-insensitive matching as
     * tasks); older versions have no such folder at all, so this falls back to the
     * XCSoar folder's root the same way writeTaskFile does. Never creates the
     * subfolder itself.
     */
    fun writeWaypointFile(
        context: Context,
        xcsoarFolder: DocumentFile,
        filename: String,
        bytes: ByteArray
    ): Boolean = writeFile(context, xcsoarFolder, "waypoints", filename, bytes)

    /**
     * Lists .igc flight logs found in [xcsoarFolder]'s "logs" subfolder (recent
     * XCSoar versions; case-insensitive same as tasks/waypoints), falling back to
     * the folder's root if there's no such subfolder. Read-only - never creates
     * anything. [xcsoarFolder]'s own name is attached to each result so the UI can
     * show which app (XCSoar vs XCSoar Jet) a file came from if the same-named
     * file happens to exist in both.
     */
    fun findIgcFiles(xcsoarFolder: DocumentFile): List<IgcFile> {
        val dir = resolveSubfolderOrRoot(xcsoarFolder, "logs")
        val sourceName = xcsoarFolder.name ?: "XCSoar"
        return dir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".igc", ignoreCase = true) == true }
            .map { IgcFile(it, sourceName) }
    }

    private fun writeFile(
        context: Context,
        xcsoarFolder: DocumentFile,
        preferredSubfolderName: String,
        filename: String,
        bytes: ByteArray
    ): Boolean {
        val target = resolveTargetFile(context, xcsoarFolder, preferredSubfolderName, filename)
            ?: return false

        return try {
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                out.write(bytes)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveTargetFile(
        context: Context,
        xcsoarFolder: DocumentFile,
        preferredSubfolderName: String,
        filename: String
    ): DocumentFile? {
        val cacheKey = xcsoarFolder.uri to filename

        // Fast path: already resolved this earlier in this app run - reuse the exact
        // same document reference rather than searching (and risking a miss) again.
        fileUriCache[cacheKey]?.let { cachedUri ->
            val cached = DocumentFile.fromSingleUri(context, cachedUri)
            if (cached != null && cached.isFile && cached.exists()) {
                return cached
            }
        }

        val writeDir = resolveSubfolderOrRoot(xcsoarFolder, preferredSubfolderName)
        val existing = writeDir.findFile(filename)
        val resolved = existing ?: writeDir.createFile("application/octet-stream", filename)
        resolved?.let { fileUriCache[cacheKey] = it.uri }
        return resolved
    }

    /**
     * Finds THIS install's subfolder, whatever case it happens to use. Never
     * creates it - it's always created by XCSoar itself. If it's genuinely not
     * there on either casing (e.g. an older XCSoar with no waypoints folder, or
     * one predating the logs subfolder), falls back to the XCSoar folder's root.
     */
    private fun resolveSubfolderOrRoot(xcsoarFolder: DocumentFile, preferredSubfolderName: String): DocumentFile =
        xcsoarFolder.listFiles()
            .firstOrNull { it.isDirectory && it.name.equals(preferredSubfolderName, ignoreCase = true) }
            ?: xcsoarFolder
}

data class IgcFile(val doc: DocumentFile, val sourceFolderName: String)
