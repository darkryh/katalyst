package com.ead.boshi_client.data.network.models

import kotlinx.serialization.Serializable

@Serializable
data class EmailDto(
    val id: Long,
    val messageId: String,
    val sender: String,
    val recipient: String,
    val subject: String,
    val body: String,
    val status: String,
    val timestamp: Long,
    val tags: String? = null
)

@Serializable
data class SendEmailRequest(
    val from: String,
    val to: String,
    val subject: String,
    val html: String,
    val tags: String? = null,
    val metadata: Map<String, String>? = null
)

@Serializable
data class SendEmailResponse(
    val messageId: String,
    val status: String,
    val message: String
)

@Serializable
data class EmailStatusResponse(
    val messageId: String,
    val status: String,
    val attempts: Int,
    val errorMessage: String? = null,
    val deliveredAt: Long? = null
)
