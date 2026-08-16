package com.rapidreader.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidreader.app.data.BookEntity
import com.rapidreader.app.data.OriginalKind
import com.rapidreader.app.data.originalKind
import com.rapidreader.app.theme.BgColor
import com.rapidreader.app.theme.DimColor
import com.rapidreader.app.theme.LineColor
import com.rapidreader.app.theme.PanelColor
import com.rapidreader.app.theme.PivotColor
import com.rapidreader.app.theme.TextColor
import com.rapidreader.app.ui.viewmodel.LibraryViewModel
import java.util.concurrent.TimeUnit

@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onOpenOriginal: (String, OriginalKind) -> Unit,
    onAddBook: () -> Unit,
    vm: LibraryViewModel = viewModel()
) {
    val books by vm.books.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "RAPID READER",
            color = DimColor, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 20.dp)
        )
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
                        onClick = { onOpenBook(book.id) },
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
}

@Composable
private fun BookCard(
    book: BookEntity,
    onClick: () -> Unit,
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
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(book.title, color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        book.source.uppercase(), color = DimColor, fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BgColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("$pct% \u00b7 ${relTime(book.updatedAt)}", color = DimColor, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                book.originalKind()?.let { kind ->
                    TextButton(onClick = { onOpenOriginal(kind) }) { Text("Original", color = DimColor) }
                }
                TextButton(onClick = onDelete) { Text("\u2715", color = DimColor) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(LineColor)) {
            Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(PivotColor))
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
