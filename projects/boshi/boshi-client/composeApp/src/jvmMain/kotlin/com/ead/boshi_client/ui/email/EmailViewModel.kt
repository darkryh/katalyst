package com.ead.boshi_client.ui.email

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ead.boshi_client.data.repository.EmailRepository
import com.ead.boshi_client.domain.models.Email
import com.ead.boshi_client.domain.models.EmailType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for email management.
 * Handles email display, sending, status tracking, and synchronization.
 *
 * Automatically syncs sent emails from backend on first access.
 * Provides reactive StateFlow bindings for UI.
 */
class EmailViewModel(
    private val emailRepository: EmailRepository
) : ViewModel() {

    // ==================== EMAIL LISTS (Reactive StateFlows) ====================

    /**
     * All sent emails from local database.
     * Updates automatically when emails are synced or sent.
     */
    val sentEmails: StateFlow<List<Email>> = emailRepository.getSentEmails()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    /**
     * All received emails from local database.
     * Currently local-only (for testing/UI validation).
     */
    val receivedEmails: StateFlow<List<Email>> = emailRepository.getReceivedEmails()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    // ==================== UI STATE ====================

    /**
     * Currently selected email for preview/details view.
     */
    private val _selectedEmail = mutableStateOf<Email?>(null)
    val selectedEmail: State<Email?> = _selectedEmail

    /**
     * Loading state for async operations (send, sync, etc.)
     */
    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    /**
     * Error message for display (null = no error).
     * Auto-clears after viewing.
     */
    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    /**
     * Success message for temporary display (e.g., "Email sent successfully").
     */
    private val _successMessage = mutableStateOf<String?>(null)
    val successMessage: State<String?> = _successMessage

    // ==================== UI ACTIONS ====================

    /**
     * Set the selected email for detail view.
     */
    fun setSelectedEmail(email: Email) {
        _selectedEmail.value = email
    }

    /**
     * Clear the selected email.
     */
    fun clearSelectedEmail() {
        _selectedEmail.value = null
    }

    /**
     * Clear error message after user has seen it.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Clear success message.
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    // ==================== EMAIL OPERATIONS ====================

    /**
     * Send an email via the backend SMTP service.
     *
     * Flow:
     * 1. Show loading state
     * 2. Call repository.sendEmail()
     * 3. On success: show message, store locally, refresh UI
     * 4. On error: show error message
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param body HTML or plain text email body
     * @param tags Optional comma-separated tags
     */
    fun sendEmail(
        to: String,
        subject: String,
        body: String,
        tags: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _successMessage.value = null

            emailRepository.sendEmail(to, subject, body, tags)
                .onSuccess { email ->
                    _isLoading.value = false
                    _successMessage.value = "Email sent successfully to $to"
                    _selectedEmail.value = email
                }
                .onFailure { exception ->
                    _isLoading.value = false
                    _error.value = exception.message ?: "Failed to send email"
                }
        }
    }

    /**
     * Sync sent emails from backend.
     * Typically called on app startup to populate initial data.
     *
     * @param page Page number (1-indexed)
     * @param limit Emails per page
     */
    fun syncSentEmails(page: Int = 1, limit: Int = 50) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            emailRepository.syncSentEmails(page, limit)
                .onSuccess {
                    _isLoading.value = false
                    _successMessage.value = "Synced emails from backend"
                }
                .onFailure { exception ->
                    _isLoading.value = false
                    _error.value = exception.message ?: "Failed to sync emails"
                }
        }
    }

    /**
     * Refresh delivery status of a specific sent email.
     * Queries backend for latest status and updates local database.
     *
     * @param messageId The backend message ID
     */
    fun refreshEmailStatus(messageId: String) {
        viewModelScope.launch {
            _error.value = null

            emailRepository.getEmailStatus(messageId)
                .onSuccess { updatedEmail ->
                    // Update selected email if it matches
                    if (_selectedEmail.value?.messageId == messageId) {
                        _selectedEmail.value = updatedEmail
                    }
                }
                .onFailure { exception ->
                    _error.value = exception.message ?: "Failed to refresh status"
                }
        }
    }

    /**
     * Verify that an email still exists on the backend.
     * Useful for checking if backend data was lost.
     *
     * @param messageId The backend message ID
     */
    fun verifyEmailOnBackend(messageId: String) {
        viewModelScope.launch {
            _error.value = null

            val exists = emailRepository.verifyEmailExistsOnBackend(messageId)
            if (!exists) {
                _error.value = "Email not found on backend - it may have expired"
            }
        }
    }

    /**
     * Clean up expired emails from local database.
     * Only deletes emails with explicit expiration timestamps.
     */
    fun cleanupExpiredEmails() {
        viewModelScope.launch {
            emailRepository.cleanupExpiredEmails()
        }
    }

    // ==================== QUERY OPERATIONS ====================

    /**
     * Get count of sent emails.
     * Non-blocking - returns immediately with flow.
     */
    fun getSentEmailsCount(): Long {
        var count = 0L
        viewModelScope.launch {
            count = emailRepository.getSentEmailsCount()
        }
        return count
    }

    /**
     * Get count of received emails.
     */
    fun getReceivedEmailsCount(): Long {
        var count = 0L
        viewModelScope.launch {
            count = emailRepository.getReceivedEmailsCount()
        }
        return count
    }
}