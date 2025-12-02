package com.ead.katalyst.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun DependencyHandlerScope.implementation(dependency: Provider<MinimalExternalModuleDependency>) =
    add("implementation", dependency)

internal fun DependencyHandlerScope.api(dependency: Provider<MinimalExternalModuleDependency>) =
    add("api", dependency)

internal fun DependencyHandlerScope.testImplementation(dependency: Provider<MinimalExternalModuleDependency>) =
    add("testImplementation", dependency)

internal fun DependencyHandlerScope.testFixturesImplementation(dependency: Provider<MinimalExternalModuleDependency>) =
    add("testFixturesImplementation", dependency)

internal fun VersionCatalog.library(alias: String) =
    findLibrary(alias).orElseThrow { IllegalArgumentException("Missing library alias: $alias") }
