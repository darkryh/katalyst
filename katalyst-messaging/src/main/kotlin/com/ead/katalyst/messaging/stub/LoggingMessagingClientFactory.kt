package com.ead.katalyst.messaging.stub

import com.ead.katalyst.messaging.Destination
import com.ead.katalyst.messaging.Message
import com.ead.katalyst.messaging.MessagingClientFactory
import com.ead.katalyst.messaging.Producer
import com.ead.katalyst.messaging.Consumer
import com.ead.katalyst.messaging.RoutingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Simple logging-based messaging client used for development/tests.
 */
class LoggingMessagingClientFactory(
    private val name: String = "katalyst-logging-client"
) : MessagingClientFactory {

    override fun createProducer(): Producer = LoggingProducer(name)
    override fun createConsumer(): Consumer = LoggingConsumer(name)
}

private class LoggingProducer(
    private val name: String
) : Producer {

    private val logger = LoggerFactory.getLogger(LoggingProducer::class.java)

    override suspend fun send(destination: Destination, message: Message, routing: RoutingConfig?) {
        logger.info(
            "[{}] -> destination={}, routing={}, messageKey={}, payload={} bytes",
            name,
            destination,
            routing,
            message.key,
            message.payload.size
        )
    }
}

private class LoggingConsumer(
    private val name: String
) : Consumer {

    private val logger = LoggerFactory.getLogger(LoggingConsumer::class.java)
    private val scope = CoroutineScope(Dispatchers.Default)

    override suspend fun consume(
        destination: Destination,
        routing: RoutingConfig?,
        handler: suspend (Message) -> Unit
    ) {
        logger.info("[{}] Listening on destination={} routing={}", name, destination, routing)
        // This stub does not pull real messages; it simply logs registration.
        scope.launch {
            logger.debug("[{}] No-op consumer active for {}", name, destination.name)
        }
    }
}
