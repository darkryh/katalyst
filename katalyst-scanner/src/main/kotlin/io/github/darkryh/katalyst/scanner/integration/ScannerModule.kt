package io.github.darkryh.katalyst.scanner.integration

import io.github.darkryh.katalyst.scanner.core.DiscoveryConfig
import io.github.darkryh.katalyst.scanner.core.DiscoveryPredicate
import io.github.darkryh.katalyst.scanner.core.DiscoveryRegistry
import io.github.darkryh.katalyst.scanner.core.TypeDiscovery
import io.github.darkryh.katalyst.scanner.scanner.InMemoryDiscoveryRegistry
import io.github.darkryh.katalyst.scanner.scanner.KotlinMethodScanner
import io.github.darkryh.katalyst.scanner.scanner.ReflectionsTypeScanner

/**
 * Framework-neutral scanner components.
 */
data class ScannerComponents<T>(
    val config: DiscoveryConfig<T>,
    val discovery: TypeDiscovery<T>,
    val registry: DiscoveryRegistry<T>,
)

/**
 * Creates scanner components without binding them to a concrete DI container.
 *
 * Applications can register these objects in their selected container adapter
 * or use them directly.
 *
 * @param T The base type or marker interface
 * @param baseType The Class object for the base type
 * @param scanPackages Packages to scan (empty = scan entire classpath)
 * @param predicate Optional predicate for filtering
 * @return Scanner components providing TypeDiscovery and DiscoveryRegistry
 */
fun <T> scannerComponents(
    baseType: Class<T>,
    scanPackages: List<String> = emptyList(),
    predicate: DiscoveryPredicate<T>? = null
): ScannerComponents<T> {
    val config = DiscoveryConfig(
        scanPackages = scanPackages,
        predicate = predicate
    )
    val discovery = ReflectionsTypeScanner(baseType, config)
    val registry = InMemoryDiscoveryRegistry(baseType)

    return ScannerComponents(
        config = config,
        discovery = discovery,
        registry = registry,
    )
}

/**
 * Simplified component factory for common use cases.
 *
 * @param T The base type or marker interface
 * @param baseType The Class object for the base type
 * @param scanPackages Packages to scan
 * @return Scanner components
 */
fun <T> simpleScannerComponents(
    baseType: Class<T>,
    vararg scanPackages: String
): ScannerComponents<T> = scannerComponents(baseType, scanPackages.toList())

/**
 * Creates a method scanner.
 *
 * @param T The base type or marker interface
 * @return KotlinMethodScanner<T>
 */
fun <T> methodScanner(): KotlinMethodScanner<T> = KotlinMethodScanner()
