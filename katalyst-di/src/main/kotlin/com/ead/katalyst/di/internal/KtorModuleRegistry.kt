package com.ead.katalyst.di.internal

import com.ead.katalyst.routes.KtorModule
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks auto-discovered [KtorModule] instances so they can be installed
 * in the Ktor pipeline after DI bootstrap completes.
 */
object KtorModuleRegistry {
    private val modules = CopyOnWriteArrayList<KtorModule>()

    fun register(module: KtorModule) {
        if (modules.none { it::class == module::class }) {
            modules += module
        }
    }

    fun consume(): List<KtorModule> {
        val snapshot = modules.toList()
        modules.clear()
        return snapshot
    }
}
