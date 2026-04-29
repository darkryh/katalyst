package io.github.darkryh.katalyst.di.planning

import io.github.darkryh.katalyst.di.analysis.DependencyGraph
import io.github.darkryh.katalyst.di.discovery.DiscoverySnapshot

/**
 * The validated intent for automatic DI registration.
 *
 * The current implementation still delegates graph details to the existing
 * [DependencyGraph], but registration and validation now have a named planning
 * boundary that can absorb future graph replacement without changing bootstrap.
 */
data class BindingPlan(
    val discovery: DiscoverySnapshot,
    val graph: DependencyGraph
)
