package com.ead.katalyst.services

import com.ead.katalyst.components.Component
import com.ead.katalyst.database.DatabaseTransactionManager
import org.koin.core.component.KoinComponent

interface Service : Component, KoinComponent {
    val transactionManager: DatabaseTransactionManager
        get() = getKoin().get()
}
