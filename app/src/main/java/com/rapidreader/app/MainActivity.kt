package com.rapidreader.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import com.rapidreader.app.navigation.AppNavHost
import com.rapidreader.app.theme.BgColor
import com.rapidreader.app.theme.RapidReaderTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // From targetSdk 35 the system draws us edge-to-edge whether we ask or
        // not, and android:statusBarColor/navigationBarColor stop having any
        // effect. Opt in explicitly so pre-35 devices lay out identically, then
        // inset the whole nav host in one place — none of the screens use a
        // Scaffold, so there is nothing applying insets per-screen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContent {
            RapidReaderTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(BgColor)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
