package io.github.darkryh.katalyst.config.spi

import io.ktor.server.config.ApplicationConfig

/**
 * Pluggable config loader. Implementations reside in format modules
 * (e.g., YAML, HOCON) and are discovered via ServiceLoader.
 */
interface ConfigLoader {
    /**
     * Returns true if this loader can handle the given format or file extension.
     */
    fun supports(format: ConfigFormat): Boolean

    /**
     * Load ApplicationConfig from one or more config sources, respecting
     * Ktor's merge semantics (last wins).
     */
    fun load(paths: List<String>): ApplicationConfig
}

/**
 * Optional interface for loaders that understand profiles.
 */
interface ProfileAwareConfigLoader : ConfigLoader {
    fun defaultBaseName(): String = "application"
    fun profileEnvVar(): String = "KATALYST_PROFILE"

    /**
     * Resolve a list of config paths for the active profile.
     * Typically returns [base, base-profile] when profile is set.
     */
    fun resolveProfiledPaths(baseName: String = defaultBaseName()): List<String>
}
