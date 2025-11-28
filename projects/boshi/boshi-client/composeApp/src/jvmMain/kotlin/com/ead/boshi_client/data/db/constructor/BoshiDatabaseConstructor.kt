@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ead.boshi_client.data.db.constructor

import androidx.room.RoomDatabaseConstructor
import com.ead.boshi_client.data.db.BoshiDatabase

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object BoshiDatabaseConstructor: RoomDatabaseConstructor<BoshiDatabase> {
    override fun initialize(): BoshiDatabase
}