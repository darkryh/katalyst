package io.github.darkryh.katalyst.config.yaml

import io.github.darkryh.katalyst.core.config.ConfigException
import org.yaml.snakeyaml.Yaml

/**
 * Parses YAML content and applies environment variable substitution.
 *
 * **Process:**
 * 1. Parse YAML string using SnakeYAML
 * 2. Validate that root is a map
 * 3. Substitute environment variables recursively
 * 4. Return configuration map
 *
 * **Example:**
 * ```kotlin
 * val yaml = """
 *   database:
 *     url: ${DB_URL:jdbc:postgresql://localhost:5432/db}
 *     username: ${DB_USER:postgres}
 * """
 * val config = YamlParser.parse(yaml)
 * ```
 */
object YamlParser {
    private val substitutor = EnvironmentVariableSubstitutor()

    /**
     * Parse YAML content and apply environment variable substitution.
     *
     * **Process:**
     * 1. Load and parse YAML using SnakeYAML
     * 2. Validate root is a map (YAML must be object-like)
     * 3. Substitute environment variables in all values
     * 4. Return parsed configuration map
     *
     * @param content YAML content as string
     * @return Parsed and substituted configuration map
     * @throws ConfigException if YAML is invalid or root is not a map
     */
    @Suppress("UNCHECKED_CAST")
    fun parse(content: String): Map<String, Any> {
        return try {
            val yaml = Yaml()
            val parsed = yaml.load<Any>(content) ?: return emptyMap()

            // Validate that YAML root is a map (not array or scalar)
            if (parsed !is Map<*, *>) {
                throw ConfigException("YAML root must be a map, got ${parsed::class.simpleName}")
            }

            val map = parsed as Map<String, Any>
            // Apply environment variable substitution to all values
            substitutor.substitute(map)
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("Failed to parse YAML: ${e.message}", e)
        }
    }
}
