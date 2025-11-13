package com.ead.katalyst.migrations.util

import java.security.MessageDigest

private val encoder = MessageDigest.getInstance("SHA-256")

internal fun hashStatements(statements: List<String>): String =
    synchronized(encoder) {
        encoder.reset()
        statements.forEach { encoder.update(it.toByteArray()) }
        encoder.digest().joinToString("") { "%02x".format(it) }
    }
