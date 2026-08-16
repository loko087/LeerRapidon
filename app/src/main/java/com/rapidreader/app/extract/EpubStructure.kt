package com.rapidreader.app.extract

import android.net.Uri
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory

/** The parts of an EPUB's OPF both consumers need, independent of byte source. */
data class EpubStructure(
    val title: String?,
    val opfDir: String,
    val spinePaths: List<String>
)

object EpubParser {
    /**
     * @param read returns the bytes at a zip-relative path, or null if absent.
     *             Backed by an in-memory map during extraction, or by files on
     *             disk (File(dir, path).readBytes()) for the original-form viewer.
     */
    fun parse(read: (String) -> ByteArray?): EpubStructure {
        val containerBytes = read("META-INF/container.xml")
            ?: throw IllegalStateException("Not a valid EPUB (missing container.xml)")
        val containerDoc = parseXml(containerBytes)
        val opfPath = containerDoc.getElementsByTagName("rootfile").item(0)
            .attributes.getNamedItem("full-path").nodeValue
        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") else ""

        val opfBytes = read(opfPath) ?: throw IllegalStateException("Could not find EPUB manifest")
        val opfDoc = parseXml(opfBytes)

        val manifest = mutableMapOf<String, String>()
        val items = opfDoc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val el = items.item(i)
            val id = el.attributes.getNamedItem("id")?.nodeValue
            val href = el.attributes.getNamedItem("href")?.nodeValue
            if (id != null && href != null) manifest[id] = href
        }

        var title: String? = null
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

        val spinePaths = spineIds.mapNotNull { id ->
            val href = manifest[id] ?: return@mapNotNull null
            normalizePath(opfDir, Uri.decode(href))
        }

        return EpubStructure(title, opfDir, spinePaths)
    }

    /** Resolves [href] against [opfDir], collapsing "." / ".." segments so the
     *  result can't escape the archive root and matches real filesystem paths. */
    fun normalizePath(opfDir: String, href: String): String {
        val combined = if (opfDir.isEmpty()) href else "$opfDir/$href"
        val segments = mutableListOf<String>()
        for (part in combined.split("/")) {
            when (part) {
                "", "." -> {}
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
                else -> segments.add(part)
            }
        }
        return segments.joinToString("/")
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        return builder.parse(bytes.inputStream())
    }
}
