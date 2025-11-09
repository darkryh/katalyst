package com.ead.katalyst.messaging.error

import com.ead.katalyst.messaging.Message

fun interface ErrorHandler {
    suspend fun onError(message: Message, exception: Throwable)
}
