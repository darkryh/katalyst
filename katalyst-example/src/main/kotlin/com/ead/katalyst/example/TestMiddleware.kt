package com.ead.katalyst.example

import com.ead.katalyst.routes.inject
import com.ead.katalyst.routes.katalystMiddleware
import io.ktor.server.application.*

@Suppress("unused")
fun Application.testMiddleware(/*cannot be injected by params*/) = katalystMiddleware {
    val testComponent = inject<TestComponent>()
    testComponent.hi()
}