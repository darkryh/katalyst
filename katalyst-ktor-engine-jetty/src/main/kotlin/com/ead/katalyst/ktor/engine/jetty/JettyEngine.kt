package com.ead.katalyst.ktor.engine.jetty

import com.ead.katalyst.ktor.engine.KatalystKtorEngine

/**
 * Jetty Ktor engine singleton instance.
 *
 * This object is used during DI bootstrap to explicitly select the Jetty engine.
 *
 * **Usage in application:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     database(DatabaseConfig(...))
 *     scanPackages("com.example.app")
 *     engine(JettyEngine)  // Explicit engine selection
 * }
 * ```
 */
object JettyEngine : KatalystKtorEngine {
    override val engineType: String = "jetty"
}
