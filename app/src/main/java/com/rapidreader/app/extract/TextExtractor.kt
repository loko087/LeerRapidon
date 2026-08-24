package com.rapidreader.app.extract

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

data class ExtractResult(
    val text: String,
    val title: String,
    val source: String,
    // Cover art pulled straight from the file: an EPUB's declared cover
    // image, or a render of a PDF's first page. Null when the file has
    // neither — the caller falls back to an Open Library title search.
    val cover: ByteArray? = null
)

object TextExtractor {

    @Volatile private var pdfBoxInitialized = false

    suspend fun extract(
        context: Context,
        uri: Uri,
        fileNameHint: String?,
        onProgress: (String) -> Unit = {}
    ): ExtractResult =
        withContext(Dispatchers.IO) {
            val fileName = fileNameHint ?: resolveDisplayName(context, uri) ?: "file"
            val ext = fileName.substringAfterLast('.', "").lowercase()
            when (ext) {
                "pdf" -> extractPdf(context, uri, fileName, onProgress)
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

    private fun extractPdf(
        context: Context,
        uri: Uri,
        fileName: String,
        onProgress: (String) -> Unit
    ): ExtractResult {
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

        val (text, usedOcr, cover) = try {
            input.use { stream ->
                PDDocument.load(stream, memSetting).use { doc ->
                    val layerText = PDFTextStripper().getText(doc)
                    val cover = renderCover(doc)
                    if (layerText.isNotBlank()) Triple(layerText, false, cover)
                    else Triple(ocrPdf(context, doc, onProgress), true, cover)
                }
            }
        } catch (oom: OutOfMemoryError) {
            throw IllegalStateException("This PDF is too large to process on this device.")
        }

        if (text.isBlank()) {
            throw IllegalStateException(
                "No text found — this looks like a blank or too low-quality scan for OCR to read"
            )
        }
        return ExtractResult(text, baseTitle(fileName), if (usedOcr) "pdf-ocr" else "pdf", cover)
    }

    // Renders the first page as a cover thumbnail — a PDF has no embedded
    // "cover" concept, but the first page is almost always it. Sized in DPI
    // (not a fixed pixel scale) so odd page dimensions still land near the
    // target width. Never lets a render failure fail the whole import.
    private fun renderCover(doc: PDDocument): ByteArray? {
        if (doc.numberOfPages == 0) return null
        return try {
            val page = doc.getPage(0)
            val targetWidthPx = 300f
            val dpi = (targetWidthPx / (page.mediaBox.width / 72f)).coerceIn(30f, 300f)
            val bitmap = PDFRenderer(doc).renderImageWithDPI(0, dpi)
            val out = ByteArrayOutputStream()
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            } finally {
                bitmap.recycle()
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    // No text layer — fall back to on-device OCR, rendering each page to a
    // bitmap and running ML Kit's bundled (offline) text recognizer over it.
    private fun ocrPdf(context: Context, doc: PDDocument, onProgress: (String) -> Unit): String {
        if (!pdfBoxInitialized) {
            synchronized(this) {
                if (!pdfBoxInitialized) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    pdfBoxInitialized = true
                }
            }
        }
        val renderer = PDFRenderer(doc)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val sb = StringBuilder()
            val pageCount = doc.numberOfPages
            for (page in 0 until pageCount) {
                onProgress("Scanning page ${page + 1} of $pageCount…")
                val bitmap = renderer.renderImageWithDPI(page, 200f)
                try {
                    val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                    sb.append(result.text).append("\n\n")
                } finally {
                    bitmap.recycle()
                }
            }
            return sb.toString()
        } finally {
            recognizer.close()
        }
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

        val structure = EpubParser.parse { path -> entries[path] }

        val sb = StringBuilder()
        for (path in structure.spinePaths) {
            val bytes = entries[path] ?: continue
            sb.append(htmlToText(String(bytes, Charsets.UTF_8))).append("\n\n")
        }
        val text = sb.toString()
        if (text.isBlank()) throw IllegalStateException("No readable text found in this EPUB")
        val cover = structure.coverPath?.let { entries[it] }
        return ExtractResult(text, structure.title ?: baseTitle(fileName), "epub", cover)
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
