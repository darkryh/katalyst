package com.ead.boshi_client.di

import com.ead.boshi_client.ui.email.EmailViewModel
import com.ead.boshi_client.ui.settings.SettingsViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mainModule = module {
    viewModel { EmailViewModel() }
    viewModel { SettingsViewModel() }
}

fun initModules() {
    startKoin {
        modules(mainModule,navigationModule)
    }
}