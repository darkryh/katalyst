package io.github.darkryh.katalyst.messaging.stub

import io.github.darkryh.katalyst.messaging.Destination
import io.github.darkryh.katalyst.messaging.Message
import io.github.darkryh.katalyst.messaging.MessagingClientFactory
import io.github.darkryh.katalyst.messaging.Producer
import io.github.darkryh.katalyst.messaging.Consumer
import io.github.darkryh.katalyst.messaging.routing.RoutingConfig
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
