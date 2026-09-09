package com.rapidreader.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidreader.app.data.BookEntity
import com.rapidreader.app.data.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the backup/restore sheet is showing right now. */
sealed class BackupState {
    data object Idle : BackupState()
    data class Working(val message: String) : BackupState()

    /**
     * What the chosen archive is, shown before anything is written. Restoring is
     * already non-destructive, but an archive is not self-describing once picked
     * from a file list — "which of these three zips is this?" is only answerable
     * from the inside, so its date and size are surfaced before committing.
     */
    data class Confirm(
        val uri: Uri,
        val summary: String,
        val caution: String?
    ) : BackupState()

    /** [canUndo] is set after a restore, while the previous library is still stashed. */
    data class Done(val message: String, val canUndo: Boolean = false) : BackupState()
    data class Error(val message: String) : BackupState()
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository(app)

    val books: StateFlow<List<BookEntity>> = repo.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Backfills covers for books saved before cover thumbnails existed.
        // LibraryViewModel lives for the whole app session (library is the
        // start destination and stays on the back stack), so this fires
        // once per launch rather than every time the library is revisited.
        viewModelScope.launch { repo.backfillMissingCovers() }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.deleteBook(id) }
    }

    fun coverFile(book: BookEntity): File? = repo.resolveCover(book.coverPath)

    private val _backup = MutableStateFlow<BackupState>(BackupState.Idle)
    val backup: StateFlow<BackupState> = _backup.asStateFlow()

    private val _canUndo = MutableStateFlow(false)

    /** Whether a previous state is still on disk to roll back to. */
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    /** Cheap file check, so the sheet can offer undo after the result is dismissed. */
    fun refreshUndo() { _canUndo.value = repo.hasUndo() }

    /** Locale.US, not the default: this is a filename, not display text, and a
     *  locale-specific one sorts badly and confuses other tools. */
    fun suggestedFileName(): String =
        "LeerRapidon-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".zip"

    fun dismissBackup() { _backup.value = BackupState.Idle }

    fun backupTo(uri: Uri) = run("Backup failed.") {
        val n = repo.backupTo(uri) { _backup.value = BackupState.Working(it) }
        "Backed up $n ${plural(n)}."
    }

    /** Step one of a restore: read the archive's manifest and describe it. */
    fun previewRestore(uri: Uri) {
        _backup.value = BackupState.Working("Reading backup…")
        viewModelScope.launch {
            _backup.value = try {
                val p = repo.peekBackup(uri)
                BackupState.Confirm(
                    uri = uri,
                    summary = "Made ${whenText(p.createdAt)} · " +
                        "${p.bookCount} ${plural(p.bookCount)}.",
                    // Restoring replaces the library, so the count of books about
                    // to disappear is the thing worth seeing before committing.
                    caution = if (p.willRemove > 0) {
                        "This removes ${p.willRemove} ${plural(p.willRemove)} that " +
                            (if (p.willRemove == 1) "is" else "are") +
                            " on this device but not in the backup. You can undo it " +
                            "straight afterwards."
                    } else null
                )
            } catch (e: Exception) {
                BackupState.Error(e.message ?: "Couldn't read that backup.")
            }
        }
    }

    /** Display format follows the device locale, unlike the filename above. */
    private fun whenText(ts: Long): String =
        if (ts <= 0L) "an unknown date"
        else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))

    fun restoreFrom(uri: Uri) = run("Restore failed.", undoable = true) {
        val r = repo.restoreFrom(uri) { _backup.value = BackupState.Working(it) }
        if (r.removed == 0) "Restored ${r.restored} ${plural(r.restored)}."
        else "Restored ${r.restored} ${plural(r.restored)}, removed ${r.removed}."
    }

    fun undoRestore() = run("Couldn't undo that.") {
        val r = repo.undoRestore()
        if (r == null) "There was nothing to undo."
        else "Put the library back to ${r.restored} ${plural(r.restored)}."
    }

    private fun plural(n: Int) = if (n == 1) "book" else "books"

    private fun run(failed: String, undoable: Boolean = false, work: suspend () -> String) {
        _backup.value = BackupState.Working("Working…")
        viewModelScope.launch {
            _backup.value = try {
                BackupState.Done(work(), canUndo = undoable && repo.hasUndo())
                    .also { refreshUndo() }
            } catch (e: Exception) {
                // Surfaces the specific reason where there is one (wrong file,
                // newer format, unwritable location) rather than a generic failure.
                BackupState.Error(e.message ?: failed)
            }
        }
    }
}
