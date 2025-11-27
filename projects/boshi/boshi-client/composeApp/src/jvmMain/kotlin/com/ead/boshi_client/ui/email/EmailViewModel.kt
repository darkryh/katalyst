package com.ead.boshi_client.ui.email

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ead.boshi_client.ui.util.Email

class EmailViewModel : ViewModel() {

    private val _selectedEmail = mutableStateOf<Email?>(null)
    val selectedEmail : State<Email?> = _selectedEmail

    fun setSelectedEmail(email: Email) {
        _selectedEmail.value = email
    }
}