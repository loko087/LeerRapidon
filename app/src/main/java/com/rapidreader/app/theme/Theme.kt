package com.rapidreader.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgColor = Color(0xFF0F1216)
val PanelColor = Color(0xFF171C22)
val LineColor = Color(0xFF2A323C)
val TextColor = Color(0xFFE9E4D8)
val DimColor = Color(0xFF7A8494)
val PivotColor = Color(0xFFFF5346)

private val RapidReaderColors = darkColorScheme(
    background = BgColor,
    surface = PanelColor,
    primary = PivotColor,
    onPrimary = Color(0xFF14090A),
    onBackground = TextColor,
    onSurface = TextColor,
    outline = LineColor,
)

@Composable
fun RapidReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RapidReaderColors, content = content)
}
