package com.rapidreader.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidreader.app.rsvp.RsvpEngine
import com.rapidreader.app.theme.DimColor
import com.rapidreader.app.theme.LineColor
import com.rapidreader.app.theme.PanelColor
import com.rapidreader.app.theme.PivotColor
import com.rapidreader.app.theme.TextColor
import com.rapidreader.app.ui.viewmodel.ReaderViewModel

@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    vm: ReaderViewModel = viewModel()
) {
    LaunchedEffect(bookId) { vm.load(bookId) }
    val ui by vm.ui.collectAsState()

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PivotColor)
        }
        return
    }

    val word = ui.words.getOrElse(ui.idx) { "" }
    val o = RsvpEngine.orpIndex(word).coerceAtMost((word.length - 1).coerceAtLeast(0))
    val pre = word.take(o)
    val orp = if (word.isNotEmpty()) word.substring(o, (o + 1).coerceAtMost(word.length)) else ""
    val post = if (word.isNotEmpty()) word.substring((o + 1).coerceAtMost(word.length)) else ""
    val minutesLeft = if (ui.wpm > 0) String.format("%.1f", (ui.words.size - ui.idx) / ui.wpm.toFloat()) else "0"

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("\u2190 Library", color = DimColor) }
            Text("${ui.idx + 1} / ${ui.words.size} \u00b7 ~$minutesLeft min left", color = DimColor, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))

        Box(
            Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(PanelColor),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pre, color = TextColor, fontSize = 40.sp, fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.End, modifier = Modifier.width(100.dp)
                )
                Text(orp, color = PivotColor, fontSize = 40.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Text(
                    post, color = TextColor, fontSize = 40.sp, fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Start, modifier = Modifier.width(100.dp)
                )
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Checkbox(
                    checked = ui.audioMode, onCheckedChange = { vm.setAudioMode(it) },
                    colors = CheckboxDefaults.colors(checkedColor = PivotColor)
                )
                Text("Audio mode \u2014 voice reads aloud, words follow speech", color = TextColor, fontSize = 13.sp)
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
