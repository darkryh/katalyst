package com.ead.katalyst.websockets

import com.ead.katalyst.ktor.KtorModule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named

class WebSocketFeatureTest {

    @AfterTest
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun `feature modules register enable flag and plugin module`() {
        val modules = WebSocketFeature.provideModules()

        val koin = startKoin {
            modules(modules)
        }.koin

        assertTrue(koin.get<Boolean>(named("enableWebSockets")))
        val installedModules = koin.getAll<KtorModule>()
        assertTrue(installedModules.any { it is WebSocketPluginModule })
    }
}
