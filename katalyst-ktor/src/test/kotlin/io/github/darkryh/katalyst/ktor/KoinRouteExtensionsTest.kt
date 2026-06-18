package io.github.darkryh.katalyst.ktor

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.ktor.extension.katalystContainer
import io.github.darkryh.katalyst.ktor.extension.ktInject
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

class KatalystRouteExtensionsTest {

    @AfterTest
    fun tearDown() {
        KatalystContainerProvider.reset()
    }

    @Test
    fun `route ktInject resolves dependency from active container`() {
        KatalystContainerProvider.set(containerWith(SampleDependency("route")))

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
    }

    @Test
    fun `application call ktInject resolves dependency`() {
        KatalystContainerProvider.set(containerWith(SampleDependency("call")))

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
    }

    @Test
    fun `application container accessor returns active container`() {
        val container = containerWith(SampleDependency("app"))
        KatalystContainerProvider.set(container)

        testApplication {
            application {
                assertSame(container, katalystContainer())
            }
        }
    }

    private fun containerWith(dependency: SampleDependency) =
        TestKatalystContainer(mapOf(TestKatalystContainer.Key(SampleDependency::class) to dependency))

    private data class SampleDependency(val id: String)
}
