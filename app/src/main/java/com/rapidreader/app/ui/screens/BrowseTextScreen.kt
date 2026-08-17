package com.rapidreader.app.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidreader.app.rsvp.BrowseWord
import com.rapidreader.app.theme.DimColor
import com.rapidreader.app.theme.PivotColor
import com.rapidreader.app.theme.TextColor
import com.rapidreader.app.ui.viewmodel.BrowseTextViewModel
import kotlinx.coroutines.launch

@Composable
fun BrowseTextScreen(
    bookId: String,
    onBack: () -> Unit,
    onWordSelected: () -> Unit,
    vm: BrowseTextViewModel = viewModel()
) {
    LaunchedEffect(bookId) { vm.load(bookId) }
    val ui by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PivotColor)
        }
        return
    }

    // Land on wherever the reader currently is, not the top of the book.
    LaunchedEffect(ui.paragraphs) {
        val startParagraph = ui.paragraphs.indexOfFirst { p -> p.any { it.wordIndex >= ui.currentIdx } }
        if (startParagraph >= 0) listState.scrollToItem(startParagraph)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = DimColor) }
            Text(ui.title, color = DimColor, fontSize = 13.sp)
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            itemsIndexed(ui.paragraphs) { _, paragraph ->
                ParagraphText(
                    words = paragraph,
                    currentIdx = ui.currentIdx,
                    onWordTap = { wordIndex ->
                        scope.launch {
                            vm.jumpTo(wordIndex)
                            onWordSelected()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ParagraphText(words: List<BrowseWord>, currentIdx: Int, onWordTap: (Int) -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Character offsets of each word within the rendered string, built
    // alongside it so a tap position maps back to a specific BrowseWord.
    val spans = remember(words) { mutableStateOf(emptyList<IntRange>()) }
    val annotated = remember(words, currentIdx) {
        val ranges = mutableListOf<IntRange>()
        val built = buildAnnotatedString {
            words.forEach { w ->
                if (length > 0) append(" ")
                val start = length
                append(w.text)
                ranges.add(start until length)
                if (w.wordIndex == currentIdx) {
                    addStyle(SpanStyle(color = PivotColor, fontWeight = FontWeight.Bold), start, length)
                }
            }
        }
        spans.value = ranges
        built
    }
    Text(
        annotated,
        color = TextColor,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .pointerInput(words) {
                detectTapGestures { pos ->
                    val charOffset = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                    val hitIndex = spans.value.indexOfFirst { charOffset in it.first..it.last + 1 }
                    if (hitIndex >= 0) onWordTap(words[hitIndex].wordIndex)
                }
            },
        onTextLayout = { layout = it }
    )
}
