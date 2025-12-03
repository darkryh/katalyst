package io.github.darkryh.katalyst.di.internal

/**
 * Marker interface used to signal that a [KtorModule] represents a generated route
 * function. These modules can share the same implementation class while still needing
 * to be registered multiple times (once per discovered function).
 */
internal interface RouteModuleMarker
