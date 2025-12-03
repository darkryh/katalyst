package io.github.darkryh.katalyst.messaging

interface MessagingClientFactory {
    fun createProducer(): Producer
    fun createConsumer(): Consumer
}
