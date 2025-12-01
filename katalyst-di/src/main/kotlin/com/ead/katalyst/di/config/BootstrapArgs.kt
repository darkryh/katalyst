package com.ead.katalyst.di.config

/**
 * Parsed bootstrap arguments used during application startup.
 *
 * Captures:
 * - Raw args provided to the process
 * - Ktor-compatible args (custom Katalyst flags removed)
 * - Optional profile override (used by YAML profile loader)
 * - Whether to force server configuration to come only from CLI args
 */
data class BootstrapArgs(
    val rawArgs: Array<String> = emptyArray(),
    val ktorArgs: Array<String> = emptyArray(),
    val profileOverride: String? = null,
    val forceCliConfig: Boolean = false
) {
    companion object {
        val EMPTY = BootstrapArgs()

        /**
        * Parse the provided CLI arguments into structured bootstrap options.
        *
        * Recognized custom flags:
        * - --force / -force / force / --katalyst.force : bypass config provider for server config
        * - --profile=<name> / --katalyst-profile=<name> : override profile (e.g., dev)
        *
        * All other arguments are passed through to Ktor unchanged.
        */
        fun parse(args: Array<String>): BootstrapArgs {
            if (args.isEmpty()) return EMPTY

            val sanitized = mutableListOf<String>()
            var forceCli = false
            var profile: String? = null

            var index = 0
            while (index < args.size) {
                val arg = args[index]
                when {
                    arg.equals("force", ignoreCase = true) ||
                        arg.equals("--force", ignoreCase = true) ||
                        arg.equals("-force", ignoreCase = true) ||
                        arg.equals("--katalyst.force", ignoreCase = true) ||
                        arg.equals("--force-cli", ignoreCase = true) ||
                        arg.equals("--force-config", ignoreCase = true) -> {
                        forceCli = true
                    }

                    arg.startsWith("--profile=") -> {
                        profile = arg.substringAfter("=").ifBlank { null }
                    }

                    arg.equals("--profile", ignoreCase = true) -> {
                        profile = args.getOrNull(index + 1)?.ifBlank { null }
                        if (profile != null) index++
                    }

                    arg.startsWith("--katalyst-profile=") -> {
                        profile = arg.substringAfter("=").ifBlank { null }
                    }

                    arg.equals("--katalyst-profile", ignoreCase = true) -> {
                        profile = args.getOrNull(index + 1)?.ifBlank { null }
                        if (profile != null) index++
                    }

                    else -> sanitized += arg
                }
                index++
            }

            return BootstrapArgs(
                rawArgs = args,
                ktorArgs = sanitized.toTypedArray(),
                profileOverride = profile,
                forceCliConfig = forceCli
            )
        }
    }

    /**
     * Apply side-effects required during bootstrap (currently profile override).
     */
    fun applyProfileOverride() {
        profileOverride?.takeIf { it.isNotBlank() }?.let { profile ->
            System.setProperty("katalyst.profile", profile)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BootstrapArgs

        if (forceCliConfig != other.forceCliConfig) return false
        if (!rawArgs.contentEquals(other.rawArgs)) return false
        if (!ktorArgs.contentEquals(other.ktorArgs)) return false
        if (profileOverride != other.profileOverride) return false

        return true
    }

    override fun hashCode(): Int {
        var result = forceCliConfig.hashCode()
        result = 31 * result + rawArgs.contentHashCode()
        result = 31 * result + ktorArgs.contentHashCode()
        result = 31 * result + (profileOverride?.hashCode() ?: 0)
        return result
    }
}
