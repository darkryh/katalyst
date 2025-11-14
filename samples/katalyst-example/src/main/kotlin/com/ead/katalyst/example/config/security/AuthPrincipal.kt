package com.ead.katalyst.example.config.security

import io.ktor.server.auth.*

data class AuthPrincipal(
    val accountId: Long,
    val email: String
) : Principal
