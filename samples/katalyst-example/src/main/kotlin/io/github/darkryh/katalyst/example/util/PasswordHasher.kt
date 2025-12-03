package io.github.darkryh.katalyst.example.util

import io.github.darkryh.katalyst.core.component.Component
import java.security.MessageDigest
import java.util.Base64

class PasswordHasher : Component {
    private val salt = System.getenv("AUTH_SALT") ?: "katalyst-sample-salt"
    private val digest = MessageDigest.getInstance("SHA-256")

    fun hash(password: String): String {
        val salted = "$salt:$password"
        val hash = digest.digest(salted.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    fun verify(raw: String, hashed: String): Boolean =
        hash(raw) == hashed
}
