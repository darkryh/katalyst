package io.github.darkryh.katalyst.initializr.generate

import io.github.darkryh.katalyst.initializr.model.ProjectConfig
import io.github.darkryh.katalyst.initializr.template.StarterTemplate
import io.github.darkryh.katalyst.initializr.template.TemplateFile

/**
 * Turns a validated [ProjectConfig] into the concrete files of a downloadable project.
 *
 * Two concerns, cleanly split: [StarterTemplate] already did *feature composition* (which files, which
 * dependency lines, which `enable*()` calls, and the observability-gated `run.sh`) based on
 * [ProjectConfig.selection]; this step does *identity substitution*, replacing every `{{...}}` token —
 * in both file contents and file paths — with the user's coordinates.
 *
 * Pure and deterministic: same config in, same files out. That is what makes it unit-testable on the
 * JVM and keeps the browser layer a thin shell around it.
 */
object ProjectGenerator {
    fun generate(config: ProjectConfig): List<TemplateFile> {
        val replacements =
            listOf(
                // `{{D}}` first so a literal '$' it introduces can never be re-matched by another token.
                "{{D}}" to "$",
                "{{PROJECT_NAME}}" to config.projectName,
                "{{GROUP_ID}}" to config.groupId,
                "{{ARTIFACT_ID}}" to config.artifactId,
                "{{PACKAGE_PATH}}" to config.packagePath,
                "{{PACKAGE}}" to config.packageName,
                "{{APP_VERSION}}" to config.appVersion,
                "{{KATALYST_VERSION}}" to StarterTemplate.KATALYST_VERSION,
                "{{KOTLIN_VERSION}}" to StarterTemplate.KOTLIN_VERSION,
                "{{GRADLE_VERSION}}" to StarterTemplate.GRADLE_VERSION,
                "{{JVM}}" to StarterTemplate.JVM_TARGET,
            )

        fun substitute(text: String): String {
            var result = text
            for ((token, value) in replacements) {
                result = result.replace(token, value)
            }
            return result
        }

        return StarterTemplate.files(config).map { file ->
            TemplateFile(
                path = substitute(file.path),
                content = substitute(file.content),
                executable = file.executable,
            )
        }
    }

    /** Conventional download name for the generated archive, derived from the artifact id. */
    fun zipName(config: ProjectConfig): String = "${config.artifactId}.zip"
}
