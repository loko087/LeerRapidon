package com.rapidreader.app.extract

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class ExtractResult(val text: String, val title: String, val source: String)

object TextExtractor {

    suspend fun extract(context: Context, uri: Uri, fileNameHint: String?): ExtractResult =
        withContext(Dispatchers.IO) {
            val fileName = fileNameHint ?: resolveDisplayName(context, uri) ?: "file"
            val ext = fileName.substringAfterLast('.', "").lowercase()
            when (ext) {
                "pdf" -> extractPdf(context, uri, fileName)
                "epub" -> extractEpub(context, uri, fileName)
                else -> extractTxt(context, uri, fileName)
            }
        }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return null
    }

    private fun baseTitle(fileName: String) = fileName.substringBeforeLast('.')

    private fun extractTxt(context: Context, uri: Uri, fileName: String): ExtractResult {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Couldn't open file")
        return ExtractResult(text, baseTitle(fileName), "txt")
    }

    private fun extractPdf(context: Context, uri: Uri, fileName: String): ExtractResult {
        val sizeBytes = queryFileSize(context, uri)
        val maxBytes = 250L * 1024 * 1024 // confirmed: 171MB uncompressed-image PDF OOMs a 192MB heap

        if (sizeBytes != null && sizeBytes > maxBytes) {
            throw IllegalStateException(
                "This PDF is ${sizeBytes / (1024 * 1024)}MB, likely from uncompressed images — " +
                        "try compressing it before importing, or use a smaller file."
            )
        }

        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Couldn't open file")
        val memSetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)

        val text = try {
            input.use { stream ->
                PDDocument.load(stream, memSetting).use { doc -> PDFTextStripper().getText(doc) }
            }
        } catch (oom: OutOfMemoryError) {
            throw IllegalStateException("This PDF is too large to process on this device.")
        }

        if (text.isBlank()) {
            throw IllegalStateException(
                "No extractable text — this PDF may be a scanned image without a text layer"
            )
        }
        return ExtractResult(text, baseTitle(fileName), "pdf")
    }

    private fun queryFileSize(context: Context, uri: Uri): Long? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) return cursor.getLong(idx)
        }
        return null
    }
    private fun extractEpub(context: Context, uri: Uri, fileName: String): ExtractResult {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val buffer = ByteArrayOutputStream()
                        val data = ByteArray(8192)
                        var count: Int
                        while (zis.read(data).also { count = it } != -1) buffer.write(data, 0, count)
                        entries[entry.name] = buffer.toByteArray()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalStateException("Couldn't open file")

        val containerBytes = entries["META-INF/container.xml"]
            ?: throw IllegalStateException("Not a valid EPUB (missing container.xml)")
        val containerDoc = parseXml(containerBytes)
        val opfPath = containerDoc.getElementsByTagName("rootfile").item(0)
            .attributes.getNamedItem("full-path").nodeValue
        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") else ""

        val opfBytes = entries[opfPath] ?: throw IllegalStateException("Could not find EPUB manifest")
        val opfDoc = parseXml(opfBytes)

        val manifest = mutableMapOf<String, String>()
        val items = opfDoc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val el = items.item(i)
            val id = el.attributes.getNamedItem("id")?.nodeValue
            val href = el.attributes.getNamedItem("href")?.nodeValue
            if (id != null && href != null) manifest[id] = href
        }

        var title = baseTitle(fileName)
        val dcTitles = opfDoc.getElementsByTagName("dc:title")
        val titleNodes = if (dcTitles.length > 0) dcTitles else opfDoc.getElementsByTagName("title")
        if (titleNodes.length > 0) {
            val t = titleNodes.item(0).textContent?.trim()
            if (!t.isNullOrEmpty()) title = t
        }

        val spineIds = mutableListOf<String>()
        val itemrefs = opfDoc.getElementsByTagName("itemref")
        for (i in 0 until itemrefs.length) {
            val idref = itemrefs.item(i).attributes.getNamedItem("idref")?.nodeValue
            if (idref != null) spineIds.add(idref)
        }

        val sb = StringBuilder()
        for (id in spineIds) {
            val href = manifest[id] ?: continue
            val decodedHref = Uri.decode(href)
            val path = if (opfDir.isNotEmpty()) "$opfDir/$decodedHref" else decodedHref
            val bytes = entries[path] ?: continue
            sb.append(htmlToText(String(bytes, Charsets.UTF_8))).append("\n\n")
        }
        val text = sb.toString()
        if (text.isBlank()) throw IllegalStateException("No readable text found in this EPUB")
        return ExtractResult(text, title, "epub")
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        return builder.parse(bytes.inputStream())
    }

    // Regex-based tag stripping tolerates the imperfect/near-XHTML markup real
    // EPUB content documents often ship with, where strict XML parsing would fail.
    private fun htmlToText(html: String): String {
        val noScripts = html.replace(Regex("(?is)<(script|style).*?</\\1>"), " ")
        val noTags = noScripts.replace(Regex("(?s)<[^>]+>"), " ")
        val unescaped = noTags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return unescaped.replace(Regex("\\s+"), " ").trim()
    }
}
