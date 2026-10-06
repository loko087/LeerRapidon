package com.rapidreader.app.data

import org.json.JSONArray
import org.json.JSONObject

/** What a library.json carries: when it was written, and the rows in it. */
data class BackupManifest(val createdAt: Long, val books: List<BookEntity>)

/**
 * The library.json read and written by both the backup zip and the mirrored
 * library folder. One format, one parser — the folder is deliberately the same
 * shape as the archive so a folder can be zipped into a backup and back again.
 */
object LibraryManifest {

    /** Bumped only if the layout changes incompatibly; readers refuse newer. */
    const val FORMAT_VERSION = 1

    fun write(books: List<BookEntity>, createdAt: Long = System.currentTimeMillis()): String {
        val array = JSONArray()
        for (b in books) {
            array.put(
                JSONObject()
                    .put("id", b.id)
                    .put("title", b.title)
                    .put("source", b.source)
                    .put("wordCount", b.wordCount)
                    .put("idx", b.idx)
                    .put("wpm", b.wpm)
                    .put("updatedAt", b.updatedAt)
                    .put("originalPath", b.originalPath)
                    .put("originalPos", b.originalPos)
                    .put("coverPath", b.coverPath)
                    .put("archived", b.archived)
            )
        }
        return JSONObject()
            .put("format", FORMAT_VERSION)
            .put("app", "LeerRapidon")
            .put("createdAt", createdAt)
            .put("books", array)
            .toString(2)
    }

    fun parse(json: String): BackupManifest {
        val root = JSONObject(json)
        if (root.optInt("format", 1) > FORMAT_VERSION) {
            throw IllegalStateException(
                "This was written by a newer version of the app. Update, then try again."
            )
        }
        val array = root.optJSONArray("books") ?: JSONArray()
        val books = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            BookEntity(
                id = o.getString("id"),
                title = o.optString("title", "Untitled"),
                source = o.optString("source", "text"),
                wordCount = o.optInt("wordCount", 0),
                idx = o.optInt("idx", 0),
                wpm = o.optInt("wpm", 300),
                updatedAt = o.optLong("updatedAt", 0L),
                originalPath = o.stringOrNull("originalPath"),
                originalPos = if (o.isNull("originalPos")) null else o.optInt("originalPos"),
                coverPath = o.stringOrNull("coverPath"),
                // Absent in backups made before archiving existed: unarchived.
                archived = o.optBoolean("archived", false)
            )
        }
        return BackupManifest(root.optLong("createdAt", 0L), books)
    }

    // optString() answers "" for both a missing key and a real empty string, and
    // these three are meaningfully nullable — "" would make originalKind() claim
    // an original exists.
    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
