package io.github.darkryh.katalyst.config.yaml

import io.github.darkryh.katalyst.core.config.ConfigException
import org.yaml.snakeyaml.LoaderOptions
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
 * **Hardening (explicitly pinned, never inherited from SnakeYAML defaults):**
 * - Duplicate mapping keys are **rejected**. SnakeYAML's own default is to allow them and let
 *   the later value overwrite the earlier one, which silently discards configuration.
 * - Alias expansion is capped ([MAX_ALIASES_FOR_COLLECTIONS]) so an anchor/alias bomb
 *   ("billion laughs") fails fast instead of exhausting memory.
 * - Nesting depth is capped ([NESTING_DEPTH_LIMIT]) so a pathologically deep document cannot
 *   blow the composer's stack.
 *
 * These limits are set explicitly rather than relying on the SnakeYAML defaults so that a
 * dependency upgrade cannot silently remove them.
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
    /** Maximum number of alias references to non-scalar nodes allowed in one document. */
    private const val MAX_ALIASES_FOR_COLLECTIONS = 50

    /** Maximum nesting depth allowed in one document. */
    private const val NESTING_DEPTH_LIMIT = 50

    /**
     * Parse YAML content and apply environment variable substitution.
     *
     * **Process:**
     * 1. Load and parse YAML using SnakeYAML
     * 2. Validate root is a map (YAML must be object-like)
     * 3. Substitute environment variables in all values
     * 4. Return parsed configuration map
     *
     * Substitution runs exactly once here. Callers that need the raw, unsubstituted document
     * (because they own the substitution step themselves) must use [parseRaw] instead —
     * running a second substitution pass over an already-substituted map corrupts any value
     * that legitimately contains a literal `${...}` sequence.
     *
     * @param content YAML content as string
     * @param envProvider Environment lookup provider (defaults to [System.getenv])
     * @return Parsed and substituted configuration map
     * @throws ConfigException if YAML is invalid or root is not a map
     */
    fun parse(
        content: String,
        envProvider: (String) -> String? = { name -> System.getenv(name) }
    ): Map<String, Any> = EnvironmentVariableSubstitutor(envProvider).substitute(parseRaw(content))

    /**
     * Parse YAML content **without** applying environment variable substitution.
     *
     * `${VAR}` / `${VAR:default}` placeholders are left untouched, so exactly one component in
     * the pipeline is responsible for resolving them. See [parse] for the substituting variant.
     *
     * @param content YAML content as string
     * @return Parsed configuration map with placeholders left verbatim
     * @throws ConfigException if YAML is invalid or root is not a map
     */
    @Suppress("UNCHECKED_CAST")
    internal fun parseRaw(content: String): Map<String, Any> {
        return try {
            val options = LoaderOptions().apply {
                // YAML 1.2 requires unique keys; SnakeYAML defaults to allowing duplicates and
                // letting the last one win, which loses configuration without a word.
                isAllowDuplicateKeys = false
                maxAliasesForCollections = MAX_ALIASES_FOR_COLLECTIONS
                nestingDepthLimit = NESTING_DEPTH_LIMIT
            }
            val yaml = Yaml(options)
            val parsed = yaml.load<Any>(content) ?: return emptyMap()

            // Validate that YAML root is a map (not array or scalar)
            if (parsed !is Map<*, *>) {
                throw ConfigException("YAML root must be a map, got ${parsed::class.simpleName}")
            }

            parsed as Map<String, Any>
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("Failed to parse YAML: ${e.message}", e)
        }
    }
}
