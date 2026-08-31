package com.rapidreader.app.premium

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.rapidreader.app.theme.DimColor
import com.rapidreader.app.theme.PanelColor
import com.rapidreader.app.theme.PivotColor
import com.rapidreader.app.theme.TextColor
import kotlinx.coroutines.flow.StateFlow

/** The two things the one-time unlock buys. */
enum class PremiumFeature(val title: String, val pitch: String) {
    AUDIO_MODE(
        "Audio mode",
        "Have the book read aloud in your device's own voice, with the display " +
            "following the words as they are spoken."
    ),
    ORIGINAL_VIEW(
        "Original view",
        "Read a PDF page by page or an EPUB chapter by chapter, laid out exactly " +
            "as the file was made, with pinch-zoom."
    ),
}

/**
 * Where the app's entitlement comes from.
 *
 * The `play` flavour backs this with Play Billing. The `github` flavour
 * returns a constant `true` and links no billing code at all — a sideloaded
 * build cannot talk to Play Billing, and that distribution is aimed at people
 * who would rather not use the Store in the first place.
 */
interface PremiumSource {
    val isPremium: StateFlow<Boolean>

    /** Formatted price, or null when this build has nothing to sell. */
    val price: StateFlow<String?>

    fun launchPurchase(activity: Activity)

    /** Re-checks entitlement with the store, if this build has one. */
    fun refresh()
}

val LocalPremium = staticCompositionLocalOf<PremiumSource> {
    error("No PremiumSource provided — MainActivity should install one.")
}

/** Walks the Compose context chain to the hosting Activity, which billing needs. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Returns an onClick that runs [onAllowed] when the user is entitled and
 * otherwise opens the upsell, so a gated control keeps a single call site
 * instead of every screen re-implementing the check and the dialog.
 */
@Composable
fun rememberPremiumAction(feature: PremiumFeature, onAllowed: () -> Unit): () -> Unit {
    val premium = LocalPremium.current
    val entitled by premium.isPremium.collectAsState()
    var showUpsell by remember { mutableStateOf(false) }

    if (showUpsell) PremiumUpsellDialog(feature) { showUpsell = false }

    return { if (entitled) onAllowed() else showUpsell = true }
}

/** Appends a quiet marker to a control's label while it is still locked. */
@Composable
fun premiumLabel(base: String): String {
    val entitled by LocalPremium.current.isPremium.collectAsState()
    return if (entitled) base else "$base · Premium"
}

@Composable
private fun PremiumUpsellDialog(feature: PremiumFeature, onDismiss: () -> Unit) {
    val premium = LocalPremium.current
    val price by premium.price.collectAsState()
    val activity = LocalContext.current.findActivity()

    AlertDialog(
        onDismissRequest = onDismiss,
        // Material3 tints the default dialog surface by elevation, which reads
        // noticeably lighter than the app's own panels. Pin it to PanelColor.
        containerColor = PanelColor,
        title = { Text(feature.title, color = TextColor) },
        text = {
            Text(
                feature.pitch + "\n\nOne payment unlocks it permanently — no " +
                    "subscription, and it stays unlocked on any device signed in " +
                    "to the same Google account.",
                color = DimColor
            )
        },
        confirmButton = {
            // No price and no activity means the store never answered. Offering
            // a button that cannot open a purchase flow is worse than hiding it.
            if (price != null && activity != null) {
                TextButton(onClick = { premium.launchPurchase(activity); onDismiss() }) {
                    Text("Unlock for $price", color = PivotColor)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now", color = DimColor) }
        }
    )
}
