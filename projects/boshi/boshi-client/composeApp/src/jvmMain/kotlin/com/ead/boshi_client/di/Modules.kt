package com.ead.boshi_client.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ead.boshi_client.data.db.BoshiDatabase
import com.ead.boshi_client.data.network.BoshiService
import com.ead.boshi_client.data.network.DynamicBoshiService
import com.ead.boshi_client.data.preferences.DataStoreFactory
import com.ead.boshi_client.data.preferences.SMTPPreferences
import com.ead.boshi_client.data.repository.EmailRepository
import com.ead.boshi_client.data.server.ServerManager
import com.ead.boshi_client.ui.email.EmailViewModel
import com.ead.boshi_client.ui.settings.SettingsViewModel
import com.ead.boshi_client.ui.settings.ServerSettingsViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Data layer DI module.
 * Provides database, API client, and repository instances.
 */
val dataModule = module {
    // DataStore for preferences (singleton)
    single<DataStore<Preferences>> {
        DataStoreFactory.createPreferencesDataStore()
    }

    // SMTP Preferences wrapper
    single { SMTPPreferences(get()) }

    // Database instance (singleton)
    single { BoshiDatabase.createDatabase() }

    // Email DAO from database
    single { get<BoshiDatabase>().emailDao() }

    // Dynamic API client (singleton) - supports runtime endpoint updates
    single { DynamicBoshiService(initialBaseUrl = "http://localhost:8080") }

    // Bind DynamicBoshiApi as BoshiApi interface
    single<BoshiService> { get<DynamicBoshiService>() }

    // Repository (singleton)
    single { EmailRepository(get(), get()) }

    // Server Manager for SMTP testing
    single { ServerManager() }
}

/**
 * ViewModel DI module.
 * Provides view model instances with dependency injection.
 */
val viewModelModule = module {
    // Email ViewModel
    viewModel { EmailViewModel(get()) }

    // Settings ViewModel
    viewModel { SettingsViewModel() }

    // Server Settings ViewModel
    viewModel { ServerSettingsViewModel(get(), get(), get<DynamicBoshiService>()) }
}

/**
 * Initialize all Koin modules.
 * Call this once on app startup.
 */
fun initModules() {
    startKoin {
        modules(dataModule, viewModelModule, navigationModule)
    }
}