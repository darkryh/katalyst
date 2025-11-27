package com.ead.boshi_client.ui.email.components.renderer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PlainTextEmailRenderer(
    textContent: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier.verticalScroll(scrollState)) {
        Text(
            text = textContent,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
    }
}