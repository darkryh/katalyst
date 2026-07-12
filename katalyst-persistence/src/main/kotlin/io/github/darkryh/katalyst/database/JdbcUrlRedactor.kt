package io.github.darkryh.katalyst.database

/**
 * Produces a credential-safe version of a JDBC URL for logging.
 *
 * JDBC URLs can carry secrets either as URL userinfo (`//user:pass@host/db`) or as
 * query-string / attribute parameters (`?password=...`, `;pwd=...`). This strips both
 * forms so operators can still see which database/host was targeted without leaking
 * credentials into log output.
 *
 * Kept as a small, pure, directly-testable function so the redaction behavior can be
 * verified without needing a real database connection.
 */
internal fun sanitizeJdbcUrl(url: String): String {
    return try {
        // Strip credentials embedded as URL userinfo, e.g. jdbc:postgresql://user:pass@host/db
        val withoutUserInfo = url.replace(Regex("//[^/@\\s]*@"), "//")

        // Redact credential-bearing query/attribute parameters wherever they appear,
        // e.g. ?password=secret, ;password=secret, &pwd=secret, &token=abc
        withoutUserInfo.replace(
            Regex("(?i)(password|pwd|secret|token)=[^&;]*")
        ) { match -> "${match.groupValues[1]}=***" }
    } catch (_: Exception) {
        "<redacted: unparsable JDBC URL>"
    }
}
