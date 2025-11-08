package com.ead.katalyst.messaging

interface MessagingClientFactory {
    fun createProducer(): Producer
    fun createConsumer(): Consumer
}
