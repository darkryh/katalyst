package com.ead.katalyst.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Scanner DI Module that provides class discovery and reflection utilities.
 *
 * This module provides tools for discovering classes, extracting metadata,
 * and resolving generic type parameters at runtime.
 *
 * Note: Scanner utilities like KotlinMethodScanner and GenericTypeExtractor
 * are available as direct imports from the scanner module and do not need
 * DI registration, as they are stateless utility classes.
 *
 * **Usage:**
 * ```kotlin
 * install(Koin) {
 *     modules(
 *         coreDIModule(DatabaseConfig(...)),
 *         scannerDIModule()
 *     )
 * }
 * ```
 */
fun scannerDIModule(): Module = module {
    val logger = LoggerFactory.getLogger("ScannerDIModule")
    logger.info("Initializing Scanner DI Module")

    // ============= Scanner Utilities =============
    logger.debug("Scanner utilities are available as direct imports")
    logger.debug("  - KotlinMethodScanner<T>: Generic scanner for discovering methods")
    logger.debug("  - GenericTypeExtractor: Utility for resolving generic type parameters")

    logger.info("Scanner DI Module initialized successfully")
}
