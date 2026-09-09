package com.rapidreader.app.data

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and writes the whole library as a single .zip the user chooses the
 * location of, so a backup can live anywhere they can reach with the system
 * file picker — including Dropbox or Drive, if those apps are installed.
 *
 * Layout mirrors <filesDir>/books exactly, with one manifest alongside it:
 *
 *   library.json                  every row: title, progress, speed, pointers
 *   books/<id>.txt                extracted text
 *   books/<id>.cover              cover thumbnail
 *   books/<id>.pdf                preserved original (PDF)
 *   books/<id>_epub/...           preserved original (EPUB, unzipped tree)
 *
 * Mirroring the directory rather than inventing a per-book folder means restore
 * is a straight write-back with no path rewriting, and it is the same shape a
 * user-chosen library folder would need — so that feature can reuse this.
 */
class LibraryBackup(private val booksDir: File) {

    private val prefix = "books/"

    fun write(
        resolver: ContentResolver,
        uri: Uri,
        books: List<BookEntity>,
        onProgress: (String) -> Unit
    ): Int {
        val out = resolver.openOutputStream(uri)
            ?: throw IllegalStateException("Couldn't open that location for writing.")
        out.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry("library.json"))
                zip.write(LibraryManifest.write(books).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                books.forEachIndexed { i, book ->
                    onProgress("Saving ${i + 1} of ${books.size}: ${book.title}")
                    addIfPresent(zip, File(booksDir, "${book.id}.txt"))
                    book.coverPath?.let { addIfPresent(zip, File(booksDir, it)) }
                    // The EPUB original is an unzipped directory, the PDF a single
                    // file — walkTopDown handles both without branching on kind.
                    book.originalPath?.let { addIfPresent(zip, File(booksDir, it)) }
                }
            }
        }
        return books.size
    }

    /**
     * Reads only library.json, without unpacking anything, so the archive can be
     * described to the user before they commit to restoring it. Cheap: the
     * manifest is written as the first entry, so this stops after one entry.
     */
    fun peek(resolver: ContentResolver, uri: Uri): BackupManifest {
        val input = resolver.openInputStream(uri)
            ?: throw IllegalStateException("Couldn't open that file.")
        input.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "library.json") {
                        return LibraryManifest.parse(zip.readBytes().toString(Charsets.UTF_8))
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        throw IllegalStateException(
            "That zip has no library.json — it doesn't look like a Leer Rapidon backup."
        )
    }

    /**
     * Extracts the archive back into <filesDir>/books and returns the rows it
     * carried. Writing to the database is the repository's job — this only
     * touches files, so a half-read archive can't leave orphan rows behind.
     */
    fun read(resolver: ContentResolver, uri: Uri, onProgress: (String) -> Unit): List<BookEntity> {
        var manifest: BackupManifest? = null
        val destCanonical = booksDir.canonicalPath + File.separator
        booksDir.mkdirs()

        val input = resolver.openInputStream(uri)
            ?: throw IllegalStateException("Couldn't open that file.")
        input.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                var count = 0
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        if (name == "library.json") {
                            manifest = LibraryManifest.parse(zip.readBytes().toString(Charsets.UTF_8))
                        } else if (name.startsWith(prefix)) {
                            val target = File(booksDir, name.removePrefix(prefix))
                            // Zip-slip guard: this archive came from the user's
                            // storage and is not necessarily one we wrote.
                            if (target.canonicalPath.startsWith(destCanonical)) {
                                if (++count % 10 == 1) onProgress("Restoring files…")
                                target.parentFile?.mkdirs()
                                target.outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return manifest?.books ?: throw IllegalStateException(
            "That zip has no library.json — it doesn't look like a Leer Rapidon backup."
        )
    }

    private fun addIfPresent(zip: ZipOutputStream, source: File) {
        if (!source.exists()) return
        source.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(booksDir).invariantSeparatorsPath
            zip.putNextEntry(ZipEntry(prefix + relative))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

}
