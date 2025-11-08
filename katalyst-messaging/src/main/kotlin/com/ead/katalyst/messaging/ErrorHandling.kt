package com.ead.katalyst.messaging

fun interface ErrorHandler {
    suspend fun onError(message: Message, exception: Throwable)
}
