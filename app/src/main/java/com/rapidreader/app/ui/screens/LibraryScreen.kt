package com.rapidreader.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidreader.app.data.BookEntity
import com.rapidreader.app.data.OriginalKind
import com.rapidreader.app.data.originalKind
import com.rapidreader.app.premium.PremiumFeature
import com.rapidreader.app.premium.premiumLabel
import com.rapidreader.app.premium.rememberPremiumAction
import com.rapidreader.app.theme.BgColor
import com.rapidreader.app.theme.DimColor
import com.rapidreader.app.theme.LineColor
import com.rapidreader.app.theme.PanelColor
import com.rapidreader.app.theme.PivotColor
import com.rapidreader.app.theme.TextColor
import com.rapidreader.app.ui.viewmodel.BackupState
import com.rapidreader.app.ui.viewmodel.LibraryViewModel
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onOpenOriginal: (String, OriginalKind) -> Unit,
    onAddBook: () -> Unit,
    vm: LibraryViewModel = viewModel()
) {
    val books by vm.books.collectAsState()
    var expandedCover by remember { mutableStateOf<File?>(null) }
    val backupState by vm.backup.collectAsState()
    val canUndo by vm.canUndo.collectAsState()
    var showBackup by remember { mutableStateOf(false) }
    // Re-checked on open: the snapshot outlives the result message, so undo
    // stays reachable after "Done" is tapped, until the next restore replaces it.
    LaunchedEffect(showBackup) { if (showBackup) vm.refreshUndo() }

    // The system picker owns the destination, so the app never needs storage
    // permission and the backup can land anywhere the user can reach - including
    // Dropbox or Drive when those apps expose a document provider.
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) vm.backupTo(uri) else vm.dismissBackup() }
    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.previewRestore(uri) else vm.dismissBackup() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LEER RAPIDON",
                color = DimColor, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            TextButton(onClick = { showBackup = true }) { Text("Backup", color = DimColor) }
        }
        if (books.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No books yet. Add a PDF, EPUB, or text file to get started.",
                    color = DimColor, fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(books, key = { it.id }) { book ->
                    BookCard(
                        book,
                        coverFile = vm.coverFile(book),
                        onClick = { onOpenBook(book.id) },
                        onCoverClick = { file -> expandedCover = file },
                        onOpenOriginal = { kind -> onOpenOriginal(book.id, kind) },
                        onDelete = { vm.delete(book.id) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Button(
            onClick = onAddBook,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PivotColor, contentColor = Color(0xFF14090A))
        ) { Text("+ Add a book", fontWeight = FontWeight.SemiBold) }
    }

    expandedCover?.let { file ->
        CoverPreviewDialog(file, onDismiss = { expandedCover = null })
    }

    if (showBackup) {
        BackupDialog(
            state = backupState,
            onBackUp = { createBackup.launch(vm.suggestedFileName()) },
            onRestore = { openBackup.launch(BACKUP_MIME_TYPES) },
            onConfirmRestore = { uri -> vm.restoreFrom(uri) },
            onUndo = { vm.undoRestore() },
            canUndo = canUndo,
            onDismiss = { showBackup = false; vm.dismissBackup() }
        )
    }
}

// Some providers hand a synced .zip back as octet-stream, so filtering on
// application/zip alone can hide the user's own backup from the picker.
private val BACKUP_MIME_TYPES = arrayOf("application/zip", "application/octet-stream")

@Composable
private fun BackupDialog(
    state: BackupState,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    onConfirmRestore: (Uri) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    onDismiss: () -> Unit
) {
    val working = state is BackupState.Working
    AlertDialog(
        // Dismissing mid-write would leave a half-written zip with no way back
        // to the progress, so the scrim and back gesture are inert while working.
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = PanelColor,
        title = { Text("Backup", color = TextColor) },
        text = {
            when (state) {
                is BackupState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = PivotColor, modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(state.message, color = DimColor, fontSize = 13.sp)
                }
                is BackupState.Confirm -> Column {
                    Text(state.summary, color = TextColor, fontSize = 13.sp)
                    state.caution?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = PivotColor, fontSize = 12.sp)
                    }
                }
                is BackupState.Done -> Text(state.message, color = TextColor, fontSize = 13.sp)
                is BackupState.Error -> Text(state.message, color = PivotColor, fontSize = 13.sp)
                is BackupState.Idle -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "One .zip holding every book — text, cover and original file — " +
                            "plus your reading positions and speeds. You pick where it goes.",
                        color = DimColor, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Restoring puts your library back to that snapshot, so books that " +
                            "aren't in it are removed. If you have read further than the " +
                            "backup, that position is kept. You can undo straight afterwards.",
                        color = DimColor, fontSize = 13.sp
                    )
                    if (canUndo) {
                        TextButton(onClick = onUndo) {
                            Text("Undo last restore", color = PivotColor)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is BackupState.Idle ->
                    TextButton(onClick = onBackUp) { Text("Back up", color = PivotColor) }
                is BackupState.Confirm ->
                    TextButton(onClick = { onConfirmRestore(state.uri) }) {
                        Text("Restore", color = PivotColor)
                    }
                is BackupState.Working -> Unit
                else -> TextButton(onClick = onDismiss) { Text("Done", color = PivotColor) }
            }
        },
        dismissButton = {
            when (state) {
                is BackupState.Idle ->
                    TextButton(onClick = onRestore) { Text("Restore", color = DimColor) }
                is BackupState.Confirm ->
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DimColor) }
                is BackupState.Done ->
                    if (state.canUndo) {
                        TextButton(onClick = onUndo) { Text("Undo", color = DimColor) }
                    }
                else -> Unit
            }
        }
    )
}

@Composable
private fun BookCard(
    book: BookEntity,
    coverFile: File?,
    onClick: () -> Unit,
    onCoverClick: (File) -> Unit,
    onOpenOriginal: (OriginalKind) -> Unit,
    onDelete: () -> Unit
) {
    val pct = if (book.wordCount > 1) (book.idx * 100 / (book.wordCount - 1)).coerceIn(0, 100) else 0
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PanelColor)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        // Cover + title/badge live in their own row, and Original/Delete get
        // a second row below rather than sharing this one \u2014 cramming both
        // into a single row left the badge only a handful of dp once the
        // cover thumbnail and both trailing buttons had taken their share,
        // squeezing "EPUB"/"PDF" into an unreadable sliver (or, before
        // maxLines/softWrap were added below, one letter per line). Same
        // fix as ReaderScreen's narrow-screen "Browse" button wrap (PR #5).
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            CoverThumbnail(
                coverFile,
                Modifier.size(width = 44.dp, height = 64.dp),
                onClick = { coverFile?.let(onCoverClick) }
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book.title, color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                // maxLines/softWrap/overflow on both as a second line of
                // defense: even with its own row now, a long relative-time
                // string plus a source badge could still get tight enough to
                // wrap, and wrapping a badge with no space to break at means
                // one character per line \u2014 a tall, "stretched" badge instead
                // of a clipped one. Ellipsis degrades far more gracefully.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$pct% \u00b7 ${relTime(book.updatedAt)}", color = DimColor, fontSize = 12.sp,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        book.source.uppercase(), color = DimColor, fontSize = 11.sp,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BgColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            book.originalKind()?.let { kind ->
                val openOriginal = rememberPremiumAction(PremiumFeature.ORIGINAL_VIEW) { onOpenOriginal(kind) }
                TextButton(onClick = openOriginal) { Text(premiumLabel("Original"), color = DimColor) }
            }
            TextButton(onClick = onDelete) { Text("\u2715", color = DimColor) }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(LineColor)) {
            Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(PivotColor))
        }
    }
}

@Composable
private fun CoverThumbnail(file: File?, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val bitmap = remember(file?.path, file?.lastModified()) {
        file?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(BgColor)
            // Only tappable once there's actually something to zoom into —
            // otherwise this click would just steal the tap from the card
            // beneath it for no visible effect.
            .then(if (bitmap != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun CoverPreviewDialog(file: File, onDismiss: () -> Unit) {
    val bitmap = remember(file.path) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
    if (bitmap == null) {
        onDismiss()
        return
    }
    // Dialog's own window intercepts the back gesture/button and calls
    // onDismissRequest, so back-to-close comes for free alongside the
    // explicit X and the tap-outside scrim below.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(8.dp))
                    // Consumes the tap so it doesn't fall through to the
                    // scrim behind it and dismiss — only "outside" should.
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)
            ) { Text("✕", color = TextColor, fontSize = 20.sp) }
        }
    }
}

private fun relTime(ts: Long): String {
    if (ts <= 0) return ""
    val diff = System.currentTimeMillis() - ts
    val m = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 1440 -> "${m / 60}h ago"
        else -> "${m / 1440}d ago"
    }
}
