package com.ead.katalyst.ktor.engine.cio

import com.ead.katalyst.ktor.engine.KatalystKtorEngine

/**
 * CIO (Coroutine-based I/O) Ktor engine singleton instance.
 *
 * This object is used during DI bootstrap to explicitly select the CIO engine.
 *
 * **Usage in application:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     database(DatabaseConfig(...))
 *     scanPackages("com.example.app")
 *     engine(CioEngine)  // Explicit engine selection
 * }
 * ```
 */
object CioEngine : KatalystKtorEngine {
    override val engineType: String = "cio"
}
