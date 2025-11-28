package com.ead.boshi_client.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ead.boshi_client.data.db.dao.EmailDao
import com.ead.boshi_client.data.db.entities.EmailEntity
import kotlinx.coroutines.Dispatchers
import java.io.File

@Database(
    entities = [EmailEntity::class],
    version = 1,
)
abstract class BoshiDatabase : RoomDatabase() {
    abstract fun emailDao(): EmailDao

    companion object {
        private const val DB_NAME = "boshi.db"

        /**
         * Create a BoshiDatabase instance.
         * Uses a file-based SQLite database in the user's home directory.
         *
         * For JVM Desktop (Compose Multiplatform), uses Room's database builder
         * with proper context and driver configuration.
         */
        fun createDatabase(): BoshiDatabase {
            val dbFile = File(System.getProperty("java.io.tmpdir"), DB_NAME)

            // Room.databaseBuilder requires BOTH context and name parameters
            // context: the directory containing the database
            // name: the absolute path to the database file
            return Room.databaseBuilder<BoshiDatabase>(
                name = dbFile.absolutePath   // Database file path
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }
}