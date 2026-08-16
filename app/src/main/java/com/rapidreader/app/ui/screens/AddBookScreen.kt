package com.rapidreader.app.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidreader.app.theme.DimColor
import com.rapidreader.app.theme.PanelColor
import com.rapidreader.app.theme.PivotColor
import com.rapidreader.app.theme.TextColor
import com.rapidreader.app.ui.viewmodel.AddBookViewModel
import com.rapidreader.app.ui.viewmodel.AddState
import kotlinx.coroutines.launch

@Composable
fun AddBookScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    vm: AddBookViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            var name = "file"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
            }
            vm.importFile(uri, name)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("\u2190 Library", color = DimColor) }
            Text("ADD A BOOK", color = DimColor, fontSize = 13.sp)
            Spacer(Modifier.width(60.dp))
        }
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            is AddState.Idle -> {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelColor).padding(16.dp)
                ) {
                    Text("Upload a file", color = DimColor, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        filePicker.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain"))
                    }) { Text("Choose file") }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "PDF, EPUB, or plain text. Scanned PDFs are read with on-device OCR " +
                                "(slower, and less accurate than a real text layer).",
                        color = DimColor, fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                PasteTextCard(onSubmit = { title, text -> vm.importPastedText(title, text) })
            }
            is AddState.Parsing -> {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelColor).padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = PivotColor, modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(s.detail ?: "Reading \"${s.fileName}\"\u2026", color = TextColor)
                    }
                }
            }
            is AddState.Error -> {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelColor).padding(16.dp)
                ) {
                    Text(s.message, color = PivotColor, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.reset() }) { Text("Try again") }
                }
            }
            is AddState.Ready -> {
                var title by remember(s) { mutableStateOf(s.title) }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelColor).padding(16.dp)
                ) {
                    Text("Title", color = DimColor, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${s.wordCount} words extracted (${s.source})", color = DimColor, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { scope.launch { onSaved(vm.save(title)) } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PivotColor, contentColor = Color(0xFF14090A))
                    ) { Text("Save to library", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun PasteTextCard(onSubmit: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelColor).padding(16.dp)) {
        Text("Or paste text", color = DimColor, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            placeholder = { Text("Title") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Paste text here\u2026") },
            modifier = Modifier.fillMaxWidth().height(140.dp)
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = { onSubmit(title, text) }) { Text("Add pasted text") }
    }
}
