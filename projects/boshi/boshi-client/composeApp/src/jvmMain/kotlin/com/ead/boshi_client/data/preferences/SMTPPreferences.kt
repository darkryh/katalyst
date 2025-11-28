package com.ead.boshi_client.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SMTPPreferences(private val dataStore: DataStore<Preferences>) {
    companion object {
        private val SMTP_TESTING_ENABLED = booleanPreferencesKey("smtp_testing_enabled")
        private val SERVER_HOST = stringPreferencesKey("server_host")
        private val SERVER_PORT = intPreferencesKey("server_port")
        private val SERVER_JAR_PATH = stringPreferencesKey("server_jar_path")
        private val SERVER_AUTO_START = booleanPreferencesKey("server_auto_start")
    }

    val isSmtpTestingEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SMTP_TESTING_ENABLED] ?: false
    }

    val serverHost: Flow<String> = dataStore.data.map { preferences ->
        preferences[SERVER_HOST] ?: "127.0.0.1"
    }

    val serverPort: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SERVER_PORT] ?: 8080
    }

    val serverJarPath: Flow<String?> = dataStore.data.map { preferences ->
        preferences[SERVER_JAR_PATH]
    }

    val serverAutoStart: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SERVER_AUTO_START] ?: false
    }

    suspend fun setSmtpTestingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SMTP_TESTING_ENABLED] = enabled
        }
    }

    suspend fun setServerHost(host: String) {
        dataStore.edit { preferences ->
            preferences[SERVER_HOST] = host
        }
    }

    suspend fun setServerPort(port: Int) {
        dataStore.edit { preferences ->
            preferences[SERVER_PORT] = port
        }
    }

    suspend fun setServerJarPath(path: String?) {
        dataStore.edit { preferences ->
            if (path != null) {
                preferences[SERVER_JAR_PATH] = path
            } else {
                preferences.remove(SERVER_JAR_PATH)
            }
        }
    }

    suspend fun setServerAutoStart(autoStart: Boolean) {
        dataStore.edit { preferences ->
            preferences[SERVER_AUTO_START] = autoStart
        }
    }
}
