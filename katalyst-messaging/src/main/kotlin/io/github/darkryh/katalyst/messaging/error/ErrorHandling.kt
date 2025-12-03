package io.github.darkryh.katalyst.messaging.error

import io.github.darkryh.katalyst.messaging.Message

fun interface ErrorHandler {
    suspend fun onError(message: Message, exception: Throwable)
}
