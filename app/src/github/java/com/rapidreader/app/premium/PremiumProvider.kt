package com.rapidreader.app.premium

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The sideloaded build has everything unlocked and nothing to sell.
 *
 * It cannot use Play Billing — that only works for apps installed by the Play
 * Store — and this distribution deliberately targets people who would rather
 * not use the Store at all. So there is no purchase flow here and no billing
 * library on this flavour's classpath; `price` stays null, which is what makes
 * the upsell dialog hide its buy button.
 */
private object AlwaysPremium : PremiumSource {
    override val isPremium: StateFlow<Boolean> = MutableStateFlow(true)
    override val price: StateFlow<String?> = MutableStateFlow(null)
    override fun launchPurchase(activity: Activity) = Unit
    override fun refresh() = Unit
}

object PremiumProvider {
    fun create(context: Context): PremiumSource = AlwaysPremium
}
