package com.ead.katalyst.example

import com.ead.katalyst.di.katalystApplication
import com.ead.katalyst.events.di.enableEvents
import com.ead.katalyst.example.infra.config.DatabaseConfigFactory
import com.ead.katalyst.migrations.extensions.enableMigrations
import com.ead.katalyst.scheduler.enableScheduler
import com.ead.katalyst.websockets.enableWebSockets
import io.ktor.server.application.Application

fun main(args: Array<String>) = katalystApplication(args) {
    database(DatabaseConfigFactory.config())
    scanPackages("com.ead.katalyst.example")
    enableEvents{
        withBus(true)
    }
    enableMigrations()
    enableScheduler()
    enableWebSockets()
}

fun Application.module() {/*unused*/}