package io.github.darkryh.katalyst.initializr.template

/**
 * One file in the generated project: a repository-relative [path] and its full text [content].
 * Both the path and the content may contain `{{PLACEHOLDER}}` tokens that the generator substitutes
 * (the path matters because sources live under `src/main/kotlin/{{PACKAGE_PATH}}/`).
 *
 * [executable] requests the Unix executable bit in the generated archive — set it for shell scripts
 * like `run.sh` so they extract ready to launch (the ZIP writer records mode `0755` vs `0644`).
 */
data class TemplateFile(val path: String, val content: String, val executable: Boolean = false)
