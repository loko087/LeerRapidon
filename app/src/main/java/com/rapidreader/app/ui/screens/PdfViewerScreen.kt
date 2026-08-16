package com.rapidreader.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidreader.app.theme.DimColor
import com.rapidreader.app.theme.PivotColor
import com.rapidreader.app.ui.viewmodel.PdfViewerViewModel
import kotlin.math.abs
import kotlin.math.min

@Composable
fun PdfViewerScreen(
    bookId: String,
    onBack: () -> Unit,
    onFastRead: () -> Unit,
    vm: PdfViewerViewModel = viewModel()
) {
    LaunchedEffect(bookId) { vm.load(bookId) }
    val ui by vm.ui.collectAsState()

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PivotColor)
        }
        return
    }
    if (ui.error != null || ui.pageCount == 0) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Library", color = DimColor) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.error ?: "Nothing to show.", color = PivotColor, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onFastRead) { Text("Fast read") }
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = ui.initialPage) { ui.pageCount }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom/pan whenever the user lands on a different page.
    LaunchedEffect(pagerState.settledPage) {
        zoom = 1f
        offset = Offset.Zero
        vm.onPageSettled(pagerState.settledPage)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Library", color = DimColor) }
            Text("${pagerState.currentPage + 1} / ${ui.pageCount}", color = DimColor, fontSize = 13.sp)
            TextButton(onClick = onFastRead) { Text("Fast read", color = DimColor) }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            pageSpacing = 8.dp,
            // Once zoomed in, horizontal drags must pan the page, not flip it.
            userScrollEnabled = zoom <= 1.01f,
            key = { it }
        ) { page ->
            PdfPage(
                page = page,
                render = vm::renderPage,
                zoom = if (page == pagerState.settledPage) zoom else 1f,
                offset = if (page == pagerState.settledPage) offset else Offset.Zero,
                onTransform = { z, o -> zoom = z; offset = o }
            )
        }
    }
}

@Composable
private fun PdfPage(
    page: Int,
    render: suspend (Int, Int) -> Bitmap?,
    zoom: Float,
    offset: Offset,
    onTransform: (Float, Offset) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var bitmap by remember(page) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(page, boxSize.width) {
        if (boxSize.width > 0) bitmap = render(page, boxSize.width)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pastSlop = false
                    var accum = 0f
                    var currentZoom = zoom
                    var currentOffset = offset
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.fastAny { it.isConsumed }) break

                        val pointers = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)

                        if (!pastSlop) {
                            accum += abs(1 - zoomChange) *
                                event.calculateCentroidSize(useCurrent = false) +
                                panChange.getDistance()
                            if (accum > viewConfiguration.touchSlop) pastSlop = true
                        }
                        if (pastSlop) {
                            // Only claim the gesture when it's unambiguously ours:
                            // two fingers is always a pinch; one finger only pans
                            // while already zoomed in — at 1x it belongs to the pager.
                            val mine = pointers > 1 || currentZoom > 1.01f
                            if (mine) {
                                val newZoom = (currentZoom * zoomChange).coerceIn(1f, 6f)
                                val newOffset = centroid + panChange -
                                    (centroid - currentOffset) * (newZoom / currentZoom)
                                currentZoom = newZoom
                                currentOffset = clampOffset(newOffset, newZoom, boxSize, bitmap)
                                onTransform(currentZoom, currentOffset)
                                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.fastAny { it.pressed })
                }
            }
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoom, scaleY = zoom,
                        translationX = offset.x, translationY = offset.y,
                        transformOrigin = TransformOrigin(0f, 0f)
                    )
            )
        } else {
            CircularProgressIndicator(color = PivotColor, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Content is letterboxed inside the box, so clamp to the painted rect, not
 *  the node bounds — otherwise the user can drag the page into empty space. */
private fun clampOffset(o: Offset, scale: Float, box: IntSize, bmp: Bitmap?): Offset {
    if (bmp == null || box.width == 0 || box.height == 0) return Offset.Zero
    val fit = min(box.width.toFloat() / bmp.width, box.height.toFloat() / bmp.height)
    val fw = bmp.width * fit
    val fh = bmp.height * fit
    val marginX = (box.width - fw) / 2f
    val marginY = (box.height - fh) / 2f
    fun axis(v: Float, margin: Float, content: Float, viewport: Float): Float {
        val scaled = content * scale
        return if (scaled <= viewport) (viewport - scaled) / 2f - scale * margin
        else v.coerceIn(viewport - scaled - scale * margin, -scale * margin)
    }
    return Offset(
        axis(o.x, marginX, fw, box.width.toFloat()),
        axis(o.y, marginY, fh, box.height.toFloat())
    )
}
