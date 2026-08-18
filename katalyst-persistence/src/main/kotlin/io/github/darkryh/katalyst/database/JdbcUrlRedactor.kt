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
 *
 * **Why the userinfo rule is greedy.** A regex like `//[^/@\s]*@` cannot cross `/`, `@` or a
 * space, so any password containing one of those characters — all of which PostgreSQL and MySQL
 * accept — fell straight through unredacted and was logged in full. Userinfo is therefore located
 * by scanning to the *last* `@` in the authority region instead of by excluding characters from
 * it. When the input is ambiguous this deliberately removes too much rather than too little: a
 * log line that lost a hostname is a nuisance, a log line that leaked a password is an incident.
 */
internal fun sanitizeJdbcUrl(url: String): String {
    return try {
        redactUserInfo(url).replace(
            // Redact credential-bearing query/attribute parameters wherever they appear,
            // e.g. ?password=secret, ;password=secret, &pwd=secret, &token=abc
            Regex("(?i)(password|pwd|secret|token)=[^&;]*"),
        ) { match -> "${match.groupValues[1]}=***" }
    } catch (_: Exception) {
        "<redacted: unparsable JDBC URL>"
    }
}

/**
 * Drop `user:password@` from the authority of a JDBC URL.
 *
 * The search for the credential terminator is bounded by the first `?` or `;` so that an `@` in a
 * query value — an email-shaped `user=a@b.com`, say — is never mistaken for the end of userinfo.
 * Within that bound the LAST `@` wins, because a password may itself contain `@`.
 */
private fun redactUserInfo(url: String): String {
    val schemeEnd = url.indexOf("//")
    if (schemeEnd < 0) return url
    val authorityStart = schemeEnd + 2

    // Credentials only ever precede the query/attribute section; bound the search there.
    val paramStart = url.indexOfFirst(authorityStart) { it == '?' || it == ';' }
    val searchEnd = if (paramStart < 0) url.length else paramStart

    val at = url.lastIndexOf('@', startIndex = searchEnd - 1)
    if (at < authorityStart) return url

    return url.substring(0, authorityStart) + url.substring(at + 1)
}

/** Index of the first character at or after [from] matching [predicate], or -1. */
private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
    for (i in from until length) if (predicate(this[i])) return i
    return -1
}
