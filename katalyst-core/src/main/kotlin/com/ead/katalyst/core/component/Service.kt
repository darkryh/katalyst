package com.ead.katalyst.core.component

import com.ead.katalyst.core.transaction.DatabaseTransactionManager
import org.koin.core.component.KoinComponent

interface Service : Component, KoinComponent {
    val transactionManager: DatabaseTransactionManager
        get() = getKoin().get()
}