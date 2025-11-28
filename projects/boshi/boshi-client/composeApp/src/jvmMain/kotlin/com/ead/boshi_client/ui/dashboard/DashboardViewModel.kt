package com.ead.boshi_client.ui.dashboard

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ead.boshi_client.data.network.BoshiService
import com.ead.boshi_client.data.network.models.SendEmailRequest
import kotlinx.coroutines.launch
import java.io.File

class DashboardViewModel(
    private val boshiService: BoshiService
) : ViewModel() {

    // Form fields
    private val _to = mutableStateOf("")
    val to: State<String> = _to

    private val _subject = mutableStateOf("")
    val subject: State<String> = _subject

    private val _body = mutableStateOf("")
    val body: State<String> = _body

    private val _attachments = mutableStateOf(emptyList<File>())
    val attachments: State<List<File>> = _attachments

    // UI State
    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    private val _successMessage = mutableStateOf<String?>(null)
    val successMessage: State<String?> = _successMessage

    // Update form fields
    fun setTo(value: String) {
        _to.value = value
    }

    fun setSubject(value: String) {
        _subject.value = value
    }

    fun setBody(value: String) {
        _body.value = value
    }

    fun addAttachments(files: List<File>) {
        _attachments.value = _attachments.value + files
    }

    fun removeAttachment(file: File) {
        _attachments.value = _attachments.value.filter { it != file }
    }

    // Clear form
    fun clearForm() {
        _to.value = ""
        _subject.value = ""
        _body.value = ""
        _attachments.value = emptyList()
        _errorMessage.value = null
        _successMessage.value = null
    }

    // Clear messages
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    // Send email via HTTP API
    fun sendEmail() {
        if (!validateForm()) {
            return
        }

        _isLoading.value = true
        _errorMessage.value = null
        _successMessage.value = null

        viewModelScope.launch {
            try {
                val request = SendEmailRequest(
                    from = "boshi@ead.company",
                    to = _to.value,
                    subject = _subject.value,
                    html = _body.value,
                    tags = "client-sent",
                    metadata = mapOf(
                        "client_version" to "1.0.0",
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )

                val response = boshiService.sendEmail(request)

                _successMessage.value = "Email sent successfully! (ID: ${response.messageId})"
                clearForm()

            } catch (e: Exception) {
                _errorMessage.value = "Failed to send email: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Validate form fields
    private fun validateForm(): Boolean {
        when {
            _to.value.isBlank() -> {
                _errorMessage.value = "Recipient email is required"
                return false
            }
            _subject.value.isBlank() -> {
                _errorMessage.value = "Subject is required"
                return false
            }
            _body.value.isBlank() -> {
                _errorMessage.value = "Message body is required"
                return false
            }
            !isValidEmail(_to.value) -> {
                _errorMessage.value = "Invalid recipient email format"
                return false
            }
        }
        return true
    }

    // Simple email validation
    private fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
        )
        return emailRegex.matches(email.trim())
    }

}