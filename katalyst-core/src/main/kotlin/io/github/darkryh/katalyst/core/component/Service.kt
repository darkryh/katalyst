package io.github.darkryh.katalyst.core.component

import io.github.darkryh.katalyst.core.transaction.DatabaseTransactionManager
import org.koin.core.component.KoinComponent

interface Service : Component, KoinComponent {
    val transactionManager: DatabaseTransactionManager
        get() = getKoin().get()
}