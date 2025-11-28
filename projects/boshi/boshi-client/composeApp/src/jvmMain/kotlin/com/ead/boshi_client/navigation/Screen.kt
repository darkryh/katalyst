package com.ead.boshi_client.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {
    @Serializable
    data object EmailInbox : Screen()

    @Serializable
    data object EmailSent : Screen()

    @Serializable
    data object Statistics : Screen()

    @Serializable
    data object SmtpTesting : Screen()

    @Serializable
    data object Settings : Screen()
}