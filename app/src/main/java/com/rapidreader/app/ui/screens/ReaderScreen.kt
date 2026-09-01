package com.rapidreader.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidreader.app.data.OriginalKind
import com.rapidreader.app.premium.LocalPremium
import com.rapidreader.app.premium.PremiumFeature
import com.rapidreader.app.premium.premiumLabel
import com.rapidreader.app.premium.rememberPremiumAction
import com.rapidreader.app.rsvp.RsvpEngine
import com.rapidreader.app.theme.DimColor
import com.rapidreader.app.theme.LineColor
import com.rapidreader.app.theme.PanelColor
import com.rapidreader.app.theme.PivotColor
import com.rapidreader.app.theme.TextColor
import com.rapidreader.app.ui.viewmodel.ReaderViewModel
import java.util.Locale

// SpeechController clamps the TTS rate at 3x (SpeechController.kt), so speech
// stops getting any faster once wpm/180 would exceed that — around here.
private const val AUDIO_SPEED_CAP_WPM = 560

@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenOriginal: (OriginalKind) -> Unit,
    onBrowseText: () -> Unit,
    vm: ReaderViewModel = viewModel()
) {
    LaunchedEffect(bookId) { vm.load(bookId) }
    val ui by vm.ui.collectAsState()

    // RSVP playback advances without any touch input, so the system's
    // inactivity timeout would otherwise dim/lock the screen mid-read.
    // Only suppress it while actually playing; paused, the screen should
    // lock like normal.
    val view = LocalView.current
    DisposableEffect(ui.playing) {
        view.keepScreenOn = ui.playing
        onDispose { view.keepScreenOn = false }
    }

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PivotColor)
        }
        return
    }

    val frameEnd = (ui.idx + ui.wordsPerFrame).coerceIn(ui.idx, ui.words.size)
    val frame = ui.words.subList(ui.idx, frameEnd)
    // In audio mode the pivot tracks whichever word speech is on right now;
    // otherwise it's the visual fixation point at the middle of the frame.
    val focusOffset = if (ui.audioMode) ui.spokenOffset.coerceIn(0, (frame.size - 1).coerceAtLeast(0)) else frame.size / 2
    val word = frame.getOrElse(focusOffset) { "" }
    val o = RsvpEngine.orpIndex(word).coerceAtMost((word.length - 1).coerceAtLeast(0))
    val pre = word.take(o)
    val orp = if (word.isNotEmpty()) word.substring(o, (o + 1).coerceAtMost(word.length)) else ""
    val post = if (word.isNotEmpty()) word.substring((o + 1).coerceAtMost(word.length)) else ""
    val minutesLeft = if (ui.wpm > 0) String.format("%.1f", (ui.words.size - ui.idx) / ui.wpm.toFloat()) else "0"
    val frameFontSize = when (frame.size) { 1 -> 40.sp; 2 -> 34.sp; 3 -> 30.sp; 4 -> 26.sp; else -> 22.sp }

    // Scrollable so the settings panel stays reachable in landscape or on
    // short screens, where the fixed word-display box alone can eat most of
    // the available height and leave no room for Speed/Words per frame/Audio.
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        // Two rows, not one: on narrow phones (~360dp portrait), cramming the
        // back button, progress text, and both mode-switch buttons into a
        // single Row overflowed and wrapped "Browse" letter-by-letter.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("\u2190 Library", color = DimColor) }
            Text(
                "${ui.idx + 1} / ${ui.words.size} \u00b7 ~$minutesLeft min left",
                color = DimColor, fontSize = 13.sp, maxLines = 1
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onBrowseText) { Text("Browse", color = DimColor) }
            ui.originalKind?.let { kind ->
                val openOriginal = rememberPremiumAction(PremiumFeature.ORIGINAL_VIEW) { onOpenOriginal(kind) }
                TextButton(onClick = openOriginal) { Text(premiumLabel("Original"), color = DimColor) }
            }
        }
        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(
            Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(PanelColor),
            contentAlignment = Alignment.Center
        ) {
            if (frame.size <= 1) {
                // Pre/post sit in equal-width columns so the pivot letter
                // (orp) stays in a constant screen position as words change
                // length — the whole point of pivot-aligned RSVP. They used
                // to be a hardcoded 100dp regardless of how much wider the
                // box actually was, clipping long words well before the box
                // itself ran out of room (e.g. "awareness" losing its last
                // letter). Now they use the real available width, and only
                // shrink the font — together, not independently, so the
                // pivot position doesn't jump between pre/orp/post — for the
                // rare word that's too long even for that.
                val textMeasurer = rememberTextMeasurer()
                val sideWidth = ((maxWidth - 56.dp) / 2).coerceAtLeast(60.dp)
                val fittedSize = rememberFittedWordFontSize(textMeasurer, pre, post, frameFontSize, 14.sp, sideWidth)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pre, color = TextColor, fontSize = fittedSize, fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.End, softWrap = false, overflow = TextOverflow.Clip,
                        modifier = Modifier.width(sideWidth)
                    )
                    Text(
                        orp, color = PivotColor, fontSize = fittedSize, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, softWrap = false, overflow = TextOverflow.Clip
                    )
                    Text(
                        post, color = TextColor, fontSize = fittedSize, fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Start, softWrap = false, overflow = TextOverflow.Clip,
                        modifier = Modifier.width(sideWidth)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    frame.forEachIndexed { i, w ->
                        if (i > 0) Spacer(Modifier.width(10.dp))
                        if (i == focusOffset) {
                            Text(pre, color = TextColor, fontSize = frameFontSize, fontFamily = FontFamily.Serif, softWrap = false, overflow = TextOverflow.Clip)
                            Text(orp, color = PivotColor, fontSize = frameFontSize, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, softWrap = false, overflow = TextOverflow.Clip)
                            Text(post, color = TextColor, fontSize = frameFontSize, fontFamily = FontFamily.Serif, softWrap = false, overflow = TextOverflow.Clip)
                        } else {
                            Text(w, color = TextColor, fontSize = frameFontSize, fontFamily = FontFamily.Serif, softWrap = false, overflow = TextOverflow.Clip)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Slider(
            value = ui.idx.toFloat(),
            onValueChange = { vm.scrub(it.toInt()) },
            valueRange = 0f..(ui.words.size - 1).coerceAtLeast(0).toFloat(),
            colors = SliderDefaults.colors(thumbColor = PivotColor, activeTrackColor = PivotColor, inactiveTrackColor = LineColor)
        )
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = { vm.scrub((ui.idx - 10).coerceAtLeast(0)) }) { Text("\u2039 10") }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { if (ui.playing) vm.pause() else vm.play() },
                colors = ButtonDefaults.buttonColors(containerColor = PivotColor, contentColor = Color(0xFF14090A)),
                modifier = Modifier.widthIn(min = 110.dp)
            ) { Text(if (ui.playing) "Pause" else "Read", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = { vm.scrub(0) }) { Text("Restart") }
        }
        Spacer(Modifier.height(20.dp))

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelColor).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Speed", color = DimColor, fontSize = 14.sp)
                Text("${ui.wpm} wpm", color = TextColor, fontSize = 14.sp)
            }
            Slider(
                value = ui.wpm.toFloat(),
                onValueChange = { vm.setWpm(it.toInt()) },
                valueRange = 100f..900f, steps = 31,
                colors = SliderDefaults.colors(thumbColor = PivotColor, activeTrackColor = PivotColor, inactiveTrackColor = LineColor)
            )
            if (ui.audioMode && ui.wpm > AUDIO_SPEED_CAP_WPM) {
                Text(
                    "Audio mode tops out around $AUDIO_SPEED_CAP_WPM wpm — higher speeds won't read any faster.",
                    color = PivotColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Words per frame", color = DimColor, fontSize = 14.sp)
                Text("${ui.wordsPerFrame}", color = TextColor, fontSize = 14.sp)
            }
            Slider(
                value = ui.wordsPerFrame.toFloat(),
                onValueChange = { vm.setWordsPerFrame(it.toInt()) },
                valueRange = 1f..5f, steps = 3,
                colors = SliderDefaults.colors(thumbColor = PivotColor, activeTrackColor = PivotColor, inactiveTrackColor = LineColor)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                // While locked the checkbox opens the upsell instead of toggling,
                // so the control never silently does nothing when tapped.
                val entitled by LocalPremium.current.isPremium.collectAsState()
                val toggleAudio = rememberPremiumAction(PremiumFeature.AUDIO_MODE) {
                    vm.setAudioMode(!ui.audioMode)
                }
                Checkbox(
                    checked = ui.audioMode, onCheckedChange = { toggleAudio() },
                    colors = CheckboxDefaults.colors(checkedColor = PivotColor)
                )
                Text(
                    if (entitled) "Audio mode \u2014 voice reads aloud, words follow speech"
                    else "Audio mode \u2014 Premium",
                    color = TextColor, fontSize = 13.sp
                )
            }
            if (ui.audioMode) {
                LanguagePicker(
                    current = ui.language,
                    // Lambda, not a pre-computed list: ReaderScreen recomposes
                    // on every word during playback, and this only actually
                    // needs to run when the dropdown is opened.
                    availableLanguages = { vm.availableLanguages() },
                    onSelect = { vm.setLanguage(it) }
                )
            }
            if (ui.audioMode && !ui.ttsOk) {
                Text(
                    "No voice installed for this language \u2014 continuing in visual mode.",
                    color = PivotColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun LanguagePicker(
    current: Locale,
    availableLanguages: () -> List<Locale>,
    onSelect: (Locale) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Language", color = DimColor, fontSize = 14.sp)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(current.getDisplayName(current), color = TextColor)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                // The device's engine may have no voices installed at all
                // yet (first launch, before any language pack download) \u2014
                // an empty menu is a reasonable, non-crashing outcome.
                availableLanguages().forEach { locale ->
                    DropdownMenuItem(
                        text = { Text(locale.getDisplayName(locale)) },
                        onClick = { onSelect(locale); expanded = false }
                    )
                }
            }
        }
    }
}

// Largest font size (down to [minFontSize]) at which both [pre] and [post]
// measure within [maxSideWidth] \u2014 shrunk together, not independently, so
// the pivot letter's screen position doesn't jump around as it would if
// pre/orp/post could each pick a different size.
@Composable
private fun rememberFittedWordFontSize(
    textMeasurer: TextMeasurer,
    pre: String,
    post: String,
    baseFontSize: TextUnit,
    minFontSize: TextUnit,
    maxSideWidth: Dp
): TextUnit {
    val density = LocalDensity.current
    return remember(pre, post, baseFontSize, maxSideWidth, density) {
        val maxWidthPx = with(density) { maxSideWidth.toPx() }
        var size = baseFontSize
        while (true) {
            val style = TextStyle(fontSize = size, fontFamily = FontFamily.Serif)
            val preFits = pre.isEmpty() || textMeasurer.measure(pre, style).size.width <= maxWidthPx
            val postFits = post.isEmpty() || textMeasurer.measure(post, style).size.width <= maxWidthPx
            // Checked at minFontSize too (not just strictly above it) so an
            // even more extreme word's floor size still reflects an actual
            // measurement, not just wherever the step size happened to land.
            if ((preFits && postFits) || size <= minFontSize) break
            size = (size.value - 2f).sp
        }
        if (size < minFontSize) minFontSize else size
    }
}
