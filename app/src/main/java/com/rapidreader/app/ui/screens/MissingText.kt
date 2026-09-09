package com.rapidreader.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidreader.app.theme.DimColor
import com.rapidreader.app.theme.PanelColor
import com.rapidreader.app.theme.PivotColor
import com.rapidreader.app.theme.TextColor

/**
 * Shown when a book's row is still in the library but the text file behind it
 * is gone. Both reading screens can hit this, so the wording lives in one place.
 *
 * Without it the reader renders a real-looking book with zero words — an empty
 * frame, a dead slider and "1 / 0" — which reads as a bug rather than as missing
 * data, and gives no hint that the fix is to restore or re-add the file.
 */
@Composable
internal fun MissingTextScreen(title: String, backLabel: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text(backLabel, color = DimColor) }
        }
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelColor).padding(16.dp)
        ) {
            // maxLines/ellipsis for the same reason as the library card's title:
            // a long title on a 360dp screen otherwise pushes the card open.
            Text(
                title.ifBlank { "This book" },
                color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text("Its text isn't on this device any more.", color = PivotColor, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "The library entry is still here but the file behind it is gone — that " +
                    "happens if the app's data was cleared, or after a restore onto a " +
                    "fresh install. Add the file again from the library to bring it back.",
                color = DimColor, fontSize = 12.sp
            )
        }
    }
}
