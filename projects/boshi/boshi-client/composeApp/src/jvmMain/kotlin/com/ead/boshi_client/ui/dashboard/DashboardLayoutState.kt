package com.ead.boshi_client.ui.dashboard

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

class DashboardLayoutState {
    val isSidebarExpanded = mutableStateOf(true)
}

val DashboardCompositionLayoutState = compositionLocalOf<DashboardLayoutState> {
    error("DashboardCompositionLayoutState not provided")
}