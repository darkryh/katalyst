package io.github.darkryh.katalyst.di.planning

import io.github.darkryh.katalyst.di.analysis.DependencyAnalyzer
import io.github.darkryh.katalyst.di.discovery.DiscoverySnapshot
import org.koin.core.Koin
import kotlin.reflect.KClass

class BindingPlanBuilder(
    private val koin: Koin,
    private val scanPackages: Array<String>
) {
    fun build(
        discovery: DiscoverySnapshot,
        additionalAvailableTypes: Set<KClass<*>> = emptySet()
    ): BindingPlan {
        val analyzer = DependencyAnalyzer(
            discoveredTypes = discovery.asValidationMap(),
            koin = koin,
            scanPackages = scanPackages,
            additionalAvailableTypes = additionalAvailableTypes
        )

        return BindingPlan(
            discovery = discovery,
            graph = analyzer.buildGraph()
        )
    }
}
