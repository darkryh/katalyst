package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.telemetry.model.ConfigEntry
import io.github.darkryh.katalyst.telemetry.model.ConfigSnapshot
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore

/**
 * Taps the configuration subsystem and reports the effective merged config — the secret-masked
 * key/value browser the framework already computes at boot and then only holds behind the injected
 * [ConfigProvider] bean.
 *
 * The provider is read-only and side-effect-free:
 * - It resolves the [ConfigProvider] bean lazily at capture time via the active container. Before
 *   boot registers it (Phase 3), or when no configuration source was installed, that bean is absent
 *   and this capturer reports `null` (the whole section is simply unavailable).
 * - When present it reads the bean's PUBLIC surface only: [ConfigProvider.getAllKeys] for the dotted
 *   key set and [ConfigProvider.get] for each effective value. Intermediate tree nodes (whose value
 *   is a nested map) are skipped so only leaf key/value pairs are reported.
 *
 * SECRET MASKING happens here, at capture time, BEFORE any value leaves the process: any key whose
 * name matches (case-insensitive) `secret|password|passwd|token|apikey|api_key|key|credential` is
 * reported with `masked = true` and `maskedValue = "****"`; the raw value is never read into the
 * snapshot for those keys. The store never transmits an unmasked secret.
 *
 * The active profile and its source are a best-effort read of the same inputs the YAML loader uses
 * to select a profile (the `katalyst.profile` system property, then the `KATALYST_PROFILE` env var);
 * both stay `null` when no profile is selected.
 *
 * The value list is bounded ([MAX_ENTRIES]) so a pathologically large config can never produce an
 * unbounded section; `totalKeys` still reflects the full effective leaf count.
 *
 * Deferred (only reachable through `internal`/`private` framework API, left at model defaults):
 * - `filesLoaded`: the per-file load ledger lives in `YamlProfileLoader.loadConfiguration`, which
 *   merges files privately and discards which ones were found.
 * - `requiredKeys`: the required-key checklist is a `private` list inside `validateRequiredKeys`.
 * - `envLedger` / `unresolvedEnvVars`: `${VAR:default}` outcomes are computed in the (private)
 *   `EnvironmentVariableSubstitutor` pass and discarded after the merged map is built.
 * - `bindingActive` / `bindingErrors`: `ConfigBinder` is a stateless `object` that fails fast at
 *   boot; there is no retained public binding-ledger bean to read.
 * - [ConfigEntry.provenance]: the provider merges env + file into one flat map with no per-key
 *   origin exposed publicly.
 */
class ConfigCapturer : SubsystemCapturer {

    override val id: String = "config"

    override fun install(store: TelemetryStore) {
        store.configProvider = ::snapshot
    }

    private fun snapshot(): ConfigSnapshot? = runCatching {
        val container = KatalystContainerProvider.currentOrNull()
            ?: return@runCatching null

        // Absent before the config feature registers the source, or when none was installed.
        val provider = container.getOrNull<ConfigProvider>()
            ?: return@runCatching null

        var totalLeafKeys = 0
        val entries = ArrayList<ConfigEntry>()

        // Sorted for a stable, deterministic section; bounded by the config file's key count.
        for (key in provider.getAllKeys().sorted()) {
            val raw = runCatching { provider.get<Any>(key) }.getOrNull()
            // A map here is an intermediate dotted-path node (e.g. "database"), not a value.
            if (raw is Map<*, *>) continue

            totalLeafKeys++
            if (entries.size < MAX_ENTRIES) {
                val secret = SECRET_KEY.containsMatchIn(key)
                entries.add(
                    ConfigEntry(
                        key = key,
                        // Mask BEFORE the value leaves the process: never read the raw secret in.
                        maskedValue = if (secret) MASKED_VALUE else raw?.toString().orEmpty(),
                        masked = secret,
                    ),
                )
            }
        }

        val (activeProfile, profileSource) = resolveActiveProfile()

        ConfigSnapshot(
            activeProfile = activeProfile,
            profileSource = profileSource,
            totalKeys = totalLeafKeys,
            entries = entries,
        )
    }.getOrNull()

    /**
     * Best-effort mirror of the YAML loader's default profile selection: the `katalyst.profile`
     * system property wins over the `KATALYST_PROFILE` env var. Returns `(null, null)` when neither
     * is set (or when a security manager forbids the read).
     */
    private fun resolveActiveProfile(): Pair<String?, String?> = runCatching {
        System.getProperty(PROFILE_PROPERTY)?.takeIf { it.isNotBlank() }?.let {
            return@runCatching it to "system-property:$PROFILE_PROPERTY"
        }
        System.getenv(PROFILE_ENV_VAR)?.takeIf { it.isNotBlank() }?.let {
            return@runCatching it to "env:$PROFILE_ENV_VAR"
        }
        null to null
    }.getOrElse { null to null }

    private companion object {
        private const val MASKED_VALUE = "****"

        /** Upper bound on reported entries; `totalKeys` still counts the full effective leaf set. */
        private const val MAX_ENTRIES = 512

        private const val PROFILE_PROPERTY = "katalyst.profile"
        private const val PROFILE_ENV_VAR = "KATALYST_PROFILE"

        /** Case-insensitive secret-key matcher; a substring hit masks the value. */
        private val SECRET_KEY = Regex(
            "secret|password|passwd|token|apikey|api_key|key|credential",
            RegexOption.IGNORE_CASE,
        )
    }
}
