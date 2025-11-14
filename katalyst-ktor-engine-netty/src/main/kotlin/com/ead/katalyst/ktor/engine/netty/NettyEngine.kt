package com.ead.katalyst.ktor.engine.netty

import com.ead.katalyst.ktor.engine.KatalystKtorEngine

/**
 * Netty Ktor engine singleton instance.
 *
 * This object is used during DI bootstrap to explicitly select the Netty engine.
 *
 * **Usage in application:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     database(DatabaseConfig(...))
 *     scanPackages("com.example.app")
 *     engine(NettyEngine)  // Explicit engine selection
 * }
 * ```
 */
object NettyEngine : KatalystKtorEngine {
    override val engineType: String = "netty"
}
