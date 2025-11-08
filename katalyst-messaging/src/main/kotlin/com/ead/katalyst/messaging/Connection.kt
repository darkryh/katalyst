package com.ead.katalyst.messaging

/**
 * Represents a low-level connection to a messaging broker.
 */
interface MessagingConnection {
    suspend fun connect()
    suspend fun disconnect()
    val isConnected: Boolean
}
