package com.rapidreader.app.ui.screens

import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidreader.app.theme.DimColor
import com.rapidreader.app.theme.PivotColor
import com.rapidreader.app.ui.viewmodel.EpubViewerViewModel

@Composable
fun EpubViewerScreen(
    bookId: String,
    onBack: () -> Unit,
    onFastRead: () -> Unit,
    vm: EpubViewerViewModel = viewModel()
) {
    LaunchedEffect(bookId) { vm.load(bookId) }
    val ui by vm.ui.collectAsState()

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PivotColor)
        }
        return
    }
    if (ui.error != null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Library", color = DimColor) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(ui.error ?: "", color = PivotColor, fontSize = 13.sp)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Library", color = DimColor) }
            TextButton(onClick = onFastRead) { Text("Fast read", color = DimColor) }
        }
        EpubWebView(
            url = ui.chapterUrls[ui.index],
            onInternalLink = { url -> vm.indexOfUrl(url)?.let { vm.goTo(it) }; true },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = vm::prev, enabled = ui.index > 0) { Text("‹ Prev") }
            Text("Chapter ${ui.index + 1} / ${ui.chapterUrls.size}", color = DimColor, fontSize = 13.sp)
            OutlinedButton(onClick = vm::next, enabled = ui.index < ui.chapterUrls.lastIndex) { Text("Next ›") }
        }
    }
}

@Composable
private fun EpubWebView(
    url: String,
    onInternalLink: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val loaded = remember { arrayOfNulls<String>(1) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                // White, not the app's dark theme: EPUB content assumes a page
                // background and usually sets one via CSS, but when it doesn't
                // (or before its stylesheet loads), default black text needs a
                // light background to stay readable — matches the PDF viewer's
                // white page rendering for the same reason.
                setBackgroundColor(android.graphics.Color.WHITE)
                with(settings) {
                    // SECURITY: EPUBs are untrusted, user-supplied content. JS stays
                    // off — that's the actual security boundary here, not incidental.
                    javaScriptEnabled = false
                    // targetSdk 34 => this defaults to false; file:// won't load without it.
                    allowFileAccess = true
                    blockNetworkLoads = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean = onInternalLink(request.url.toString())
                }
            }
        },
        // Compare against a remembered ref, not wv.url — wv.url is null during
        // load and changes on redirects/anchors, which would cause reload loops.
        update = { wv ->
            if (loaded[0] != url) {
                loaded[0] = url
                wv.loadUrl(url)
            }
        },
        onRelease = { it.destroy() }
    )
}
