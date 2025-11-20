package com.ead.katalyst.ktor

import com.ead.katalyst.ktor.extension.koin
import com.ead.katalyst.ktor.extension.ktInject
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class KoinRouteExtensionsTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `route ktInject resolves dependency from global context`() {
        stopKoin()
        val koinApp = startKoin {
            modules(
                module {
                    single { SampleDependency("route") }
                }
            )
        }

        try {
            testApplication {
                application {
                    routing {
                        val dependency by ktInject<SampleDependency>()
                        get("/route-inject") {
                            call.respondText(dependency.id)
                        }
                    }
                }

                val response = client.get("/route-inject")
                assertEquals("route", response.bodyAsText())
            }
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `application call ktInject resolves dependency`() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single { SampleDependency("call") }
                }
            )
        }

        try {
            testApplication {
                application {
                    routing {
                        get("/dep") {
                            val dependency by call.ktInject<SampleDependency>()
                            call.respondText(dependency.id)
                        }
                    }
                }

                val response = client.get("/dep")
                assertEquals("call", response.bodyAsText())
            }
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `application koin accessor returns active instance`() {
        stopKoin()
        val koinApp = startKoin {
            modules(
                module { single { SampleDependency("app") } }
            )
        }

        try {
            testApplication {
                application {
                    assertSame(koinApp.koin, koin())
                }
            }
        } finally {
            stopKoin()
        }
    }

    private data class SampleDependency(val id: String)
}
