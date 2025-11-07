package com.ead.katalyst.scanner.integration

import com.ead.katalyst.scanner.core.DiscoveryConfig
import com.ead.katalyst.scanner.core.DiscoveryPredicate
import com.ead.katalyst.scanner.core.DiscoveryRegistry
import com.ead.katalyst.scanner.core.TypeDiscovery
import com.ead.katalyst.scanner.scanner.InMemoryDiscoveryRegistry
import com.ead.katalyst.scanner.scanner.KotlinMethodScanner
import com.ead.katalyst.scanner.scanner.ReflectionsTypeScanner
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module factory for scanner framework.
 *
 * **Usage:**
 * ```kotlin
 * install(Koin) {
 *     modules(
 *         scannerModule<Service>(
 *             baseType = Service::class.java,
 *             scanPackages = listOf("com.ead.xtory")
 *         ),
 *         // ... other modules
 *     )
 * }
 * ```
 *
 * @param T The base type or marker interface
 * @param baseType The Class object for the base type
 * @param scanPackages Packages to scan (empty = scan entire classpath)
 * @param predicate Optional predicate for filtering
 * @return Koin module providing TypeDiscovery and DiscoveryRegistry
 */
fun <T> scannerModule(
    baseType: Class<T>,
    scanPackages: List<String> = emptyList(),
    predicate: DiscoveryPredicate<T>? = null
): Module = module {

    // Provide the configuration
    single {
        DiscoveryConfig(
            scanPackages = scanPackages,
            predicate = predicate
        )
    }

    // Provide the scanner
    single<TypeDiscovery<T>> {
        ReflectionsTypeScanner(baseType, get<DiscoveryConfig<T>>())
    }

    // Provide the registry
    single<DiscoveryRegistry<T>> {
        InMemoryDiscoveryRegistry(baseType)
    }

    // Provide Koin-aware registry
    single<KoinDiscoveryRegistry<T>> {
        KoinDiscoveryRegistry(baseType, getKoin())
    }

    // Provide auto-discovery engine
    single<AutoDiscoveryEngine<T>> {
        AutoDiscoveryEngine(get<TypeDiscovery<T>>(), getKoin())
    }
}

/**
 * Simplified version for common use cases.
 *
 * **Usage:**
 * ```kotlin
 * install(Koin) {
 *     modules(
 *         simpleScannerModule<Service>(
 *             baseType = Service::class.java,
 *             scanPackages = arrayOf("com.ead.xtory")
 *         ),
 *         // ... other modules
 *     )
 * }
 * ```
 *
 * @param T The base type or marker interface
 * @param baseType The Class object for the base type
 * @param scanPackages Packages to scan
 * @return Koin module
 */
fun <T> simpleScannerModule(
    baseType: Class<T>,
    vararg scanPackages: String
): Module = scannerModule(baseType, scanPackages.toList())

/**
 * Koin module for method scanning capabilities.
 *
 * Provides KotlinMethodScanner for discovering methods in classes.
 * Can be used independently or as a complement to the main scanner module.
 *
 * **Usage:**
 * ```kotlin
 * install(Koin) {
 *     modules(
 *         methodScannerModule<RouteController>(),
 *         // ... other modules
 *     )
 * }
 * ```
 *
 * @param T The base type or marker interface
 * @return Koin module providing KotlinMethodScanner<T>
 */
fun <T> methodScannerModule(): Module = module {
    single { KotlinMethodScanner<T>() }
}
