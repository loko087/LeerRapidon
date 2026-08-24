package com.rapidreader.app.extract

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Last-resort cover lookup against the Open Library APIs
 * (https://openlibrary.org/dev/docs/api/covers), used only when a cover
 * couldn't be pulled straight from the uploaded file (embedded EPUB cover,
 * or a rendered PDF first page). Matches by title via the Search API to get
 * a cover id, then fetches the image from the Covers API. Every failure path
 * (no match, no network, bad response) returns null rather than throwing —
 * a missing cover is never a reason to fail an otherwise-good import.
 */
object OpenLibraryCovers {
    private const val TIMEOUT_MS = 8000
    private const val USER_AGENT = "RapidReader/1.0 (Android; +https://github.com)"

    suspend fun findByTitle(title: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val coverId = findCoverId(title) ?: return@withContext null
            // "L" (large) rather than "M" — the same file backs both the
            // library-list thumbnail and the tap-to-zoom preview, and "M" is
            // too soft once blown up to fill most of the screen.
            download("https://covers.openlibrary.org/b/id/$coverId-L.jpg?default=false")
        } catch (_: Exception) {
            null
        }
    }

    private fun findCoverId(title: String): Long? {
        val query = URLEncoder.encode(title, "UTF-8")
        val url = URL("https://openlibrary.org/search.json?title=$query&fields=cover_i&limit=1")
        val json = get(url) ?: return null
        val docs = JSONObject(json).optJSONArray("docs") ?: return null
        if (docs.length() == 0) return null
        val coverI = docs.getJSONObject(0).optLong("cover_i", -1)
        return if (coverI > 0) coverI else null
    }

    private fun get(url: URL): String? = withConnection(url) { conn ->
        if (conn.responseCode != 200) null
        else conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun download(url: String): ByteArray? = withConnection(URL(url)) { conn ->
        if (conn.responseCode != 200) null
        else conn.inputStream.use { it.readBytes() }
    }

    private fun <T> withConnection(url: URL, block: (HttpURLConnection) -> T?): T? {
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            block(conn)
        } finally {
            conn.disconnect()
        }
    }
}
