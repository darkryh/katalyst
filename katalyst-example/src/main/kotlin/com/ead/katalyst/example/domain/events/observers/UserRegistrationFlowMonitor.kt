package com.ead.katalyst.example.domain.events.observers

import com.ead.katalyst.core.component.Component
import com.ead.katalyst.events.bus.EventBus
import com.ead.katalyst.events.bus.eventsOf
import com.ead.katalyst.example.domain.events.UserRegisteredEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Lightweight projection that demonstrates Flow-based observation of the local EventBus.
 *
 * Handlers continue to work as before, while this component consumes the same events
 * reactively without registering as an EventHandler.
 */
class UserRegistrationFlowMonitor(
    private val eventBus: EventBus
) : Component {
    private val logger = LoggerFactory.getLogger(UserRegistrationFlowMonitor::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            eventBus.eventsOf<UserRegisteredEvent>()
                .collect { event ->
                    logger.info(
                        "Flow observer spotted UserRegisteredEvent -> account={}, email={}",
                        event.accountId,
                        event.email
                    )
                }
        }
    }
}
