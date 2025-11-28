package com.ead.boshi_client.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

object DataStoreFactory {
    fun createPreferencesDataStore(): DataStore<Preferences> {
        val dataStoreFile = File(System.getProperty("java.io.tmpdir"), "boshi_preferences.preferences_pb")
        return PreferenceDataStoreFactory.create {
            dataStoreFile
        }
    }
}
