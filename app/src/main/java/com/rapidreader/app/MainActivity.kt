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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.rapidreader.app.navigation.AppNavHost
import com.rapidreader.app.premium.LocalPremium
import com.rapidreader.app.premium.PremiumProvider
import com.rapidreader.app.premium.PremiumSource
import com.rapidreader.app.theme.BgColor
import com.rapidreader.app.theme.RapidReaderTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : ComponentActivity() {

    // Which implementation this is depends on the flavour: Play Billing in
    // `play`, a constant `true` in `github`. Held by the Activity so the
    // billing connection outlives recomposition.
    private lateinit var premium: PremiumSource

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
        premium = PremiumProvider.create(this)
        setContent {
            CompositionLocalProvider(LocalPremium provides premium) {
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

    // A purchase can complete outside this process — restored on a new device,
    // or bought on the web — so re-check on the way back to the foreground
    // rather than trusting the value cached at startup.
    override fun onResume() {
        super.onResume()
        premium.refresh()
    }
}
