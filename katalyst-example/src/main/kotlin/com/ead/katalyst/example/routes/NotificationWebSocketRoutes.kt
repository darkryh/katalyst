package com.ead.katalyst.example.routes

import com.ead.katalyst.example.service.UserService
import com.ead.katalyst.routes.inject
import com.ead.katalyst.routes.katalystWebSockets
import io.ktor.server.routing.*
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.*
import kotlinx.serialization.json.*

/**
 * Notification WebSocket Routes
 *
 * Automatically discovered and installed by Katalyst framework.
 * Demonstrates WebSocket support using the semantic katalystWebSockets DSL.
 *
 * **Features:**
 * - Semantic WebSocket DSL (katalystWebSockets) for clean syntax
 * - Frame-based message handling (text, binary, close)
 * - Proper connection lifecycle management
 * - Real-time bidirectional communication
 *
 * **Auto-Discovery:**
 * - Functions with katalystWebSockets are automatically discovered
 * - No manual registration needed
 * - Framework handles lifecycle management
 *
 * **Difference from REST Routes:**
 * - Persistent bidirectional connection (vs. request/response)
 * - Frame-based messaging (vs. HTTP messages)
 * - WebSocket protocol (vs. HTTP)
 * - Real-time, low-latency communication
 *
 * **Client Usage:**
 * ```javascript
 * const ws = new WebSocket('ws://localhost:8080/notifications')
 * ws.onmessage = (event) => console.log(event.data)
 * ws.send(JSON.stringify({action: 'subscribe', channel: 'updates'}))
 * ```
 */
@Suppress("unused")
fun Route.notificationWebSocketRoutes() = katalystWebSockets {
    val user = inject<UserService>()
    webSocket("notifications") {
        //val user = inject<UserService>()

        try {
            // Send welcome message to client
            send(
                Frame.Text(
                    buildJsonObject {
                        put("type", "welcome")
                        put("message", "Connected to notification WebSocket")
                        put("timestamp", System.currentTimeMillis())
                    }.toString()
                )
            )

            println("✓ WebSocket client connected to /notifications")

            // Process incoming frames from client
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        println("📨 Received frame: $text")

                        try {
                            val json = Json.parseToJsonElement(text)
                            val action = json.jsonObject["action"]?.jsonPrimitive?.content

                            when (action) {
                                "subscribe" -> {
                                    val channel = json.jsonObject["channel"]?.jsonPrimitive?.content
                                    send(Frame.Text(
                                        buildJsonObject {
                                            put("type", "subscribed")
                                            put("channel", channel)
                                            put("message", "Subscribed to $channel")
                                            put("timestamp", System.currentTimeMillis())
                                        }.toString()
                                    ))
                                    println("✓ Client subscribed to: $channel")
                                }

                                "unsubscribe" -> {
                                    val channel = json.jsonObject["channel"]?.jsonPrimitive?.content
                                    send(Frame.Text(
                                        buildJsonObject {
                                            put("type", "unsubscribed")
                                            put("channel", channel)
                                            put("message", "Unsubscribed from $channel")
                                            put("timestamp", System.currentTimeMillis())
                                        }.toString()
                                    ))
                                    println("✓ Client unsubscribed from: $channel")
                                }

                                "ping" -> {
                                    send(Frame.Text(
                                        buildJsonObject {
                                            put("type", "pong")
                                            put("timestamp", System.currentTimeMillis())
                                        }.toString()
                                    ))
                                }

                                else -> {
                                    send(Frame.Text(
                                        buildJsonObject {
                                            put("type", "error")
                                            put("message", "Unknown action: $action")
                                            put("timestamp", System.currentTimeMillis())
                                        }.toString()
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            send(Frame.Text(
                                buildJsonObject {
                                    put("type", "error")
                                    put("message", "Invalid JSON: ${e.message}")
                                    put("timestamp", System.currentTimeMillis())
                                }.toString()
                            ))
                        }
                    }

                    is Frame.Close -> {
                        println("✗ WebSocket client disconnected from /notifications")
                    }

                    else -> {
                        println("⚠ Received unsupported frame type: ${frame::class.simpleName}")
                    }
                }
            }
        } catch (e: Exception) {
            println("✗ WebSocket error in /notifications: ${e.message}")
        }
    }
}
