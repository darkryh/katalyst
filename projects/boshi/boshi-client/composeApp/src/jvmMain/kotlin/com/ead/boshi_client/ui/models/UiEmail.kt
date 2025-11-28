package com.ead.boshi_client.ui.models

import com.ead.boshi_client.domain.models.EmailStatus
import com.ead.boshi_client.domain.models.EmailType

data class UiEmail(
    val id: Long,
    val sender: String,
    val subject: String,
    val preview: String,
    val status: EmailStatus,
    val type: EmailType,
    val date: String, // Formatted date
    val initial: String // Sender initial
)
