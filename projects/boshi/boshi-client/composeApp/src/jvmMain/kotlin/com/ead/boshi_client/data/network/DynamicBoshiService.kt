package com.ead.boshi_client.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dynamic API wrapper that allows runtime updates to the API endpoint.
 * Delegates all API calls to the current BoshiApi instance.
 */
class DynamicBoshiService(
    initialBaseUrl: String = "http://localhost:8080"
) : BoshiService {

    private var currentApi: BoshiService = BoshiClient.create(initialBaseUrl)
    private val _currentBaseUrl = MutableStateFlow(initialBaseUrl)
    val currentBaseUrl = _currentBaseUrl.asStateFlow()

    /**
     * Update the API endpoint URL.
     * Creates a new BoshiApi instance with the new base URL.
     */
    fun updateEndpoint(newBaseUrl: String) {
        if (_currentBaseUrl.value != newBaseUrl) {
            currentApi = BoshiClient.create(newBaseUrl)
            _currentBaseUrl.value = newBaseUrl
        }
    }

    /**
     * Get the current base URL.
     */
    fun getBaseUrl(): String = _currentBaseUrl.value

    // Delegate all API calls to the current instance
    override suspend fun sendEmail(request: com.ead.boshi_client.data.network.models.SendEmailRequest) =
        currentApi.sendEmail(request)

    override suspend fun getEmailStatus(messageId: String) =
        currentApi.getEmailStatus(messageId)

    override suspend fun getEmails(page: Int, limit: Int) =
        currentApi.getEmails(page, limit)
}
