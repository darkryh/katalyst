package com.ead.katalyst.config.yaml

import org.slf4j.LoggerFactory

/**
 * Loads and merges YAML configuration files based on active profile.
 *
 * **Load Order (highest to lowest priority):**
 * 1. `application-{KATALYST_PROFILE}.yaml` (if KATALYST_PROFILE env var set)
 * 2. `application.yaml` (base configuration)
 *
 * **Example:**
 * ```kotlin
 * val loader = YamlProfileLoader()
 * val config = loader.loadConfiguration()  // Merges base + profile configs
 * ```
 *
 * **Profile Examples:**
 * - Development: `KATALYST_PROFILE=dev` → loads `application-dev.yaml`
 * - Production: `KATALYST_PROFILE=prod` → loads `application-prod.yaml`
 * - Staging: `KATALYST_PROFILE=staging` → loads `application-staging.yaml`
 *
 * @param profileEnvVar Environment variable name for profile selection (default: KATALYST_PROFILE)
 * @param baseConfigFile Base configuration filename (default: application.yaml)
 */
class YamlProfileLoader(
    private val profileEnvVar: String = "KATALYST_PROFILE",
    private val baseConfigFile: String = "application.yaml",
    private val environmentReader: (String) -> String? = { envVar ->
        System.getenv(envVar)
    },
    private val propertyReader: (String) -> String? = { property ->
        System.getProperty(property)
    }
) {
    companion object {
        private val log = LoggerFactory.getLogger(YamlProfileLoader::class.java)
    }

    /**
     * Load and merge YAML configuration files.
     *
     * **Process:**
     * 1. Load base configuration from application.yaml
     * 2. Check for KATALYST_PROFILE environment variable
     * 3. If set, load profile-specific configuration (e.g., application-dev.yaml)
     * 4. Merge profile config into base config (profile values override base)
     * 5. Return merged configuration
     *
     * @return Merged configuration map
     */
    fun loadConfiguration(profileOverride: String? = null): Map<String, Any> {
        log.debug("Loading YAML configuration...")
        val baseConfig = loadYamlFile(baseConfigFile)
        val profile = (profileOverride ?: propertyReader("katalyst.profile") ?: environmentReader(profileEnvVar))
            ?.takeIf { it.isNotBlank() }

        return if (profile != null && profile.isNotBlank()) {
            val profileFile = "application-$profile.yaml"
            log.info("Loading profile-specific configuration: $profileFile")
            val profileConfig = loadYamlFile(profileFile)
            if (profileConfig.isEmpty()) {
                throw IllegalStateException("Profile '$profile' requested but $profileFile not found or empty. Add the file or remove the profile.")
            }
            val merged = baseConfig.merge(profileConfig)
            logProfileFallbacks(baseConfig, profileConfig, profile)
            merged.also { logActiveProfile(profile) }
        } else {
            baseConfig.also {
                log.info("No active profile set (using default configuration)")
            }
        }
    }

    /**
     * Load YAML file from classpath.
     *
     * **Behavior:**
     * - Returns empty map if file doesn't exist (allows optional profile files)
     * - Parses YAML content using YamlParser
     *
     * @param filename Filename to load from classpath
     * @return Parsed YAML as map, or empty map if not found
     */
    private fun loadYamlFile(filename: String): Map<String, Any> {
        val resource = this::class.java.classLoader.getResource(filename)
            ?: return emptyMap()

        val content = resource.readText()
        return YamlParser.parse(content)
    }

    /**
     * Recursively merge profile configuration into base configuration.
     *
     * **Merge Strategy:**
     * - For nested maps: recursively merge (profile values override base)
     * - For other types: profile values override base values completely
     *
     * **Example:**
     * ```
     * Base:    {a: {x: 1, y: 2}, b: 3}
     * Profile: {a: {y: 20}}
     * Result:  {a: {x: 1, y: 20}, b: 3}
     * ```
     *
     * @param other Profile configuration to merge in
     * @return Merged map with profile values overriding base values
     */
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.merge(other: Map<String, Any>): Map<String, Any> {
        val result = this.toMutableMap()
        for ((key, value) in other) {
            result[key] = when {
                value is Map<*, *> && result[key] is Map<*, *> -> {
                    (result[key] as Map<String, Any>).merge(value as Map<String, Any>)
                }
                else -> value
            }
        }
        return result
    }

    private fun logActiveProfile(profile: String) {
        log.info("Active profile: $profile")
    }

    private fun logProfileFallbacks(base: Map<String, Any>, profile: Map<String, Any>, profileName: String) {
        val baseKeys = flattenKeys(base)
        val profileKeys = flattenKeys(profile)
        val missing = baseKeys.subtract(profileKeys)
        if (missing.isNotEmpty()) {
            log.warn(
                "Profile '{}' is missing {} key(s); falling back to base configuration for those keys",
                profileName,
                missing.size
            )
            log.debug("Fallback keys: {}", missing.joinToString(", "))
        }
    }

    private fun flattenKeys(map: Map<String, Any>, prefix: String = ""): Set<String> {
        val keys = mutableSetOf<String>()
        map.forEach { (k, v) ->
            val key = if (prefix.isEmpty()) k else "$prefix.$k"
            keys.add(key)
            if (v is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                keys.addAll(flattenKeys(v as Map<String, Any>, key))
            }
        }
        return keys
    }
}
