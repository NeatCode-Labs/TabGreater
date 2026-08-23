package com.neatcode.tabgreater.ui.settings

import com.neatcode.tabgreater.BuildConfig

/**
 * The two donation endpoints. Both are NeatCode Labs' own; donations are voluntary and unlock
 * nothing in the app, so nothing else in the code may branch on them.
 */
object Donate {

    /** Ko-fi page, opened with `ACTION_VIEW` from the donate dialog. */
    const val KOFI_URL = "https://ko-fi.com/neatcodelabs"

    /** Monero primary address, copied to the clipboard verbatim. */
    const val MONERO_ADDRESS =
        "45TiAPismHb5TbJdX5iscCShfwQ9gSZyMcxKXjjEyabjf98dV2y8F7SHaConCAUkqUNbHuCKZk4NE4d6xpiCBRvMNPEWu1b"

    /**
     * The donate row is shown in every flavour. Google Play's payments policy treats a tip that
     * goes 100 % to the creator and unlocks nothing as a peer-to-peer payment, which does not
     * require Play Billing — so the `play` flavour keeps it too. Flip this to hide it everywhere.
     */
    const val ENABLED = true

    /** The distribution track that ships the full permission set: GitHub / F-Droid. */
    private const val FOSS_DISTRIBUTION = "foss"

    /**
     * Whether this build holds `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for the one-tap battery
     * dialog; the `play` flavour removes that permission and goes to the list screen instead.
     */
    val isFossBuild: Boolean
        get() = BuildConfig.DISTRIBUTION == FOSS_DISTRIBUTION
}
