package io.github.darkryh.katalyst.example

internal fun sampleJwtTestConfig(): Map<String, Any?> =
    mapOf(
        "jwt.secret" to "sample-test-secret-at-least-32-characters",
        "jwt.issuer" to "katalyst-example-test",
        "jwt.audience" to "katalyst-users-test",
        "jwt.realm" to "KatalystTestRealm",
        "jwt.expirationSeconds" to 120L,
    )
