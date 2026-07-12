package io.github.darkryh.katalyst.migrations.util

import java.security.MessageDigest

/**
 * Computes the SHA-256 checksum of a migration's statements.
 *
 * A fresh [MessageDigest] is allocated on every call. `MessageDigest` is not thread-safe, so
 * sharing one instance across callers would force every caller through a single lock (or risk
 * corrupting concurrent digests without one). Allocation is cheap relative to hashing migration
 * SQL, so there is no benefit to pooling/sharing instances here.
 */
internal fun hashStatements(statements: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    statements.forEach { digest.update(it.toByteArray()) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
