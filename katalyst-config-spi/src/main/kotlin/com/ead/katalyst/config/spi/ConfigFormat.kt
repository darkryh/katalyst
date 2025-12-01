package com.ead.katalyst.config.spi

/**
 * Lightweight format identifier used to select a config loader.
 * Use simple names (e.g., "yaml", "hocon") so loaders can
 * register and match by either id or file extension.
 */
data class ConfigFormat(val id: String) {
    companion object {
        val YAML = ConfigFormat("yaml")
        val HOCON = ConfigFormat("hocon")

        fun fromExtension(ext: String): ConfigFormat? = when (ext.lowercase()) {
            "yaml", "yml" -> YAML
            "conf", "hocon" -> HOCON
            else -> null
        }
    }
}
