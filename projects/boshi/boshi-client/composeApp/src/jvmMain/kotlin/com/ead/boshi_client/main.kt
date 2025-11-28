package com.ead.boshi_client

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ead.boshi_client.di.initModules

fun main() {
    initModules()

    return application {
        Window(
            onCloseRequest = ::exitApplication,
            alwaysOnTop = false,
            title = "Boshi",
        ) {
            BoshiApp()
        }
    }
}