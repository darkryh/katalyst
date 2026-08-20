package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.Serializable

/** How an env-var placeholder `${VAR:default}` resolved. */
@Serializable
enum class EnvOutcome { FROM_ENV, USED_DEFAULT, USED_EMPTY }

/**
 * One effective config key. Values are ALWAYS masked on the backend before they reach the wire —
 * the store never transmits raw secrets.
 */
@Serializable
data class ConfigEntry(
    val key: String,
    val maskedValue: String,
    val provenance: String? = null,
    val masked: Boolean = false,
)

/** One required key's presence, replacing the fatal one-line ConfigException with a checklist. */
@Serializable
data class RequiredKey(
    val key: String,
    val present: Boolean,
)

/** One `${VAR:default}` resolution, flagged red when it fell through to an empty-string fallback. */
@Serializable
data class EnvLedgerEntry(
    val variable: String,
    val outcome: EnvOutcome,
    val maskedValue: String? = null,
)

/**
 * Configuration: browse the effective merged config (secret-masked), see the active profile and
 * where it came from, env-substitution outcomes, the required-key checklist, and per-property
 * binding decisions. All already-free at boot; masking is applied at capture time.
 */
@Serializable
data class ConfigSnapshot(
    val activeProfile: String? = null,
    val profileSource: String? = null,
    val filesLoaded: Map<String, Boolean> = emptyMap(),
    val totalKeys: Int = 0,
    val entries: List<ConfigEntry> = emptyList(),
    val requiredKeys: List<RequiredKey> = emptyList(),
    val envLedger: List<EnvLedgerEntry> = emptyList(),
    val unresolvedEnvVars: List<String> = emptyList(),
    val bindingActive: Boolean = false,
    val bindingErrors: List<String> = emptyList(),
)
