package com.riptide.ui.navigation

import com.riptide.R

/**
 * Bottom destinations, in order: Transfer, Devices, Received.
 * Settings is a secondary screen that remembers its originating tab;
 * it is not a tab.
 */
enum class RiptideTab(val label: String, val iconRes: Int) {
    Transfer("Transfer", R.drawable.riptide_transfer),
    Devices("Devices", R.drawable.riptide_pc),
    Received("Received", R.drawable.riptide_receive)
}
