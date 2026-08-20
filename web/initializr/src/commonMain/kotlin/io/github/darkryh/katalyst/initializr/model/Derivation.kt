package io.github.darkryh.katalyst.initializr.model

/**
 * The user-facing form model. Instead of asking for all five Maven/Gradle values, the form collects
 * the three that actually matter — [projectName], [packageName] (the single coordinate) and
 * [appVersion] — plus the [selection] of engine + features, and *derives* `groupId`/`artifactId`.
 * Power users can override the derived values via [groupIdOverride]/[artifactIdOverride] (the
 * "advanced" panel); `null` means "use the derived value". This keeps the common path short while
 * the generator still receives a full, unchanged [ProjectConfig] (see [toProjectConfig]).
 */
data class FormState(
    val projectName: String,
    val packageName: String,
    val appVersion: String,
    val selection: FeatureSelection = FeatureSelection.DEFAULT,
    val groupIdOverride: String? = null,
    val artifactIdOverride: String? = null,
    val advancedOpen: Boolean = false,
) {
    companion object {
        val DEFAULT =
            FormState(
                projectName = "My Katalyst App",
                packageName = "com.example.app",
                appVersion = "0.1.0",
            )
    }
}

/** Build the generator's [ProjectConfig] from the reduced form, applying derivation and overrides. */
fun FormState.toProjectConfig(): ProjectConfig =
    ProjectConfig(
        projectName = projectName,
        groupId = groupIdOverride ?: deriveGroupId(packageName),
        artifactId = artifactIdOverride ?: deriveArtifactId(projectName, packageName),
        packageName = packageName,
        appVersion = appVersion,
        selection = selection,
    )

/** The last `.`-separated segment of a package, e.g. `com.example.myapp` -> `myapp`. */
fun lastSegment(packageName: String): String = packageName.substringAfterLast('.')

/**
 * groupId = the package minus its last segment (`com.example.myapp` -> `com.example`). For a
 * single-segment package the whole thing is used. This yields the idiomatic `com.example:my-app`
 * rather than the redundant `com.example.myapp:my-app`.
 */
fun deriveGroupId(packageName: String): String =
    packageName.substringBeforeLast('.', missingDelimiterValue = packageName).lowercase()

/**
 * artifactId = kebab-case of the project name, with fallbacks so the result is always a legal
 * artifact id: project name -> last package segment -> `"app"`.
 */
fun deriveArtifactId(
    projectName: String,
    packageName: String,
): String {
    kebabCase(projectName).takeIf { it.isNotEmpty() }?.let { return it }
    kebabCase(lastSegment(packageName)).takeIf { it.isNotEmpty() }?.let { return it }
    return "app"
}

private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")
private val NON_KEBAB = Regex("[^a-z0-9]+")

// Common Latin-1 / Latin Extended-A letters folded to ASCII (no java.text.Normalizer in common code).
private val ASCII_FOLD =
    mapOf(
        'á' to "a", 'à' to "a", 'â' to "a", 'ä' to "a", 'ã' to "a", 'å' to "a", 'ā' to "a",
        'é' to "e", 'è' to "e", 'ê' to "e", 'ë' to "e", 'ē' to "e",
        'í' to "i", 'ì' to "i", 'î' to "i", 'ï' to "i", 'ī' to "i",
        'ó' to "o", 'ò' to "o", 'ô' to "o", 'ö' to "o", 'õ' to "o", 'ø' to "o", 'ō' to "o",
        'ú' to "u", 'ù' to "u", 'û' to "u", 'ü' to "u", 'ū' to "u",
        'ñ' to "n", 'ç' to "c", 'ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'ý' to "y", 'ÿ' to "y",
    )

private fun asciiFold(text: String): String =
    buildString {
        for (ch in text) {
            val lower = ch.lowercaseChar()
            val folded = ASCII_FOLD[lower]
            when {
                folded != null -> append(if (ch.isUpperCase()) folded.uppercase() else folded)
                else -> append(ch)
            }
        }
    }

/**
 * Convert an arbitrary display string to a kebab-case identifier suitable for a Maven artifactId:
 * split camelCase, ASCII-fold accents, lowercase, collapse non-alphanumerics to single dashes, and
 * ensure it starts with a letter. May return `""` (callers fall back).
 */
fun kebabCase(text: String): String {
    val spaced = text.replace(CAMEL_BOUNDARY, "$1 $2")
    val ascii = asciiFold(spaced)
    val kebab = ascii.lowercase().replace(NON_KEBAB, "-").trim('-')
    return kebab.dropWhile { !it.isLetter() }.trim('-')
}
