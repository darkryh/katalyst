package io.github.darkryh.katalyst.initializr

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.darkryh.katalyst.initializr.ui.InitializrApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) {
        InitializrApp()
    }
}
