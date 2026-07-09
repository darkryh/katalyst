package io.github.darkryh.katalyst.initializr.model

/** The individual form fields, so the UI can attach an error message to the right input. */
enum class ConfigField { PROJECT_NAME, GROUP_ID, ARTIFACT_ID, PACKAGE_NAME, APP_VERSION }

/** A single validation problem: which field is wrong and a human-readable reason. */
data class FieldError(val field: ConfigField, val message: String)

/**
 * Pure, side-effect-free validation of a [ProjectConfig]. The form calls this on every keystroke to
 * decide whether to enable the "Generate" button and which messages to show — the same fail-fast
 * discipline Katalyst applies at boot, moved to configure-time. Rules are intentionally strict
 * enough that the generated Gradle project is guaranteed to have a legal package, artifact id, and
 * coordinates — so the download always compiles.
 */
object ProjectConfigValidator {
    // A Kotlin package, tightened to LOWERCASE letter-leading segments. Lowercase-enforcing the
    // package guarantees deriveGroupId() always yields a value that passes GROUP_REGEX, so derived
    // coordinates can never be invalid — group/artifact errors are only possible from manual overrides.
    private val PACKAGE_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$")

    // Maven groupId: same shape as a package but conventionally lowercase.
    private val GROUP_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$")

    // Maven artifactId: lowercase, digits and single dashes (kebab-case).
    private val ARTIFACT_REGEX = Regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$")

    // A lenient semantic-version-ish string: 1, 1.0, 1.0.0, 0.1.0-SNAPSHOT, 1.2.3-rc1, etc.
    private val VERSION_REGEX = Regex("^[0-9]+(\\.[0-9]+){0,3}(-[A-Za-z0-9.]+)?$")

    // Reserved words that cannot appear as a package segment.
    private val KOTLIN_KEYWORDS =
        setOf(
            "package", "as", "typealias", "class", "this", "super", "val", "var", "fun", "for",
            "null", "true", "false", "is", "in", "throw", "return", "break", "continue", "object",
            "if", "try", "else", "while", "do", "when", "interface", "typeof", "internal", "private",
        )

    fun validate(config: ProjectConfig): List<FieldError> =
        buildList {
            if (config.projectName.isBlank()) {
                add(FieldError(ConfigField.PROJECT_NAME, "Project name can't be empty."))
            }

            if (config.groupId.isBlank()) {
                add(FieldError(ConfigField.GROUP_ID, "Group id can't be empty."))
            } else if (!GROUP_REGEX.matches(config.groupId)) {
                add(FieldError(ConfigField.GROUP_ID, "Use lowercase, dot-separated segments, e.g. com.example."))
            }

            if (config.artifactId.isBlank()) {
                add(FieldError(ConfigField.ARTIFACT_ID, "Artifact id can't be empty."))
            } else if (!ARTIFACT_REGEX.matches(config.artifactId)) {
                add(FieldError(ConfigField.ARTIFACT_ID, "Use lowercase kebab-case, e.g. my-katalyst-app."))
            }

            when {
                config.packageName.isBlank() ->
                    add(FieldError(ConfigField.PACKAGE_NAME, "Package can't be empty."))
                !PACKAGE_REGEX.matches(config.packageName) ->
                    add(FieldError(ConfigField.PACKAGE_NAME, "Use a lowercase package, e.g. com.example.app."))
                config.packageName.split('.').any { it in KOTLIN_KEYWORDS } ->
                    add(FieldError(ConfigField.PACKAGE_NAME, "A package segment is a reserved Kotlin keyword."))
            }

            if (config.appVersion.isBlank()) {
                add(FieldError(ConfigField.APP_VERSION, "Version can't be empty."))
            } else if (!VERSION_REGEX.matches(config.appVersion)) {
                add(FieldError(ConfigField.APP_VERSION, "Use a version like 0.1.0 or 1.0.0-SNAPSHOT."))
            }
        }

    /** Convenience for the UI: the project is generatable only when there are no errors. */
    fun isValid(config: ProjectConfig): Boolean = validate(config).isEmpty()

    /**
     * Validate the reduced [FormState] by deriving the full config and validating that — but hiding
     * `groupId`/`artifactId` errors while those values are still derived (they cannot fail), so only
     * a manual override can surface them on the advanced inputs.
     */
    fun validate(form: FormState): List<FieldError> =
        validate(form.toProjectConfig()).filterNot {
            (it.field == ConfigField.GROUP_ID && form.groupIdOverride == null) ||
                (it.field == ConfigField.ARTIFACT_ID && form.artifactIdOverride == null)
        }

    fun isValid(form: FormState): Boolean = validate(form).isEmpty()
}
