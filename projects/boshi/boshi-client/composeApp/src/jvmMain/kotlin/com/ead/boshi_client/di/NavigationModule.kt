package com.ead.boshi_client.di

import com.ead.boshi_client.navigation.Navigator
import com.ead.boshi_client.navigation.Screen
import com.ead.boshi_client.ui.email.EmailViewModel
import com.ead.boshi_client.ui.email.inbox.EmailInboxScreen
import com.ead.boshi_client.ui.email.sent.EmailSentScreen
import com.ead.boshi_client.ui.settings.SettingsScreen
import com.ead.boshi_client.ui.smtptesting.SMTPTestingScreen
import com.ead.boshi_client.ui.stats.StatisticsScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

@OptIn(KoinExperimentalAPI::class)
val navigationModule = module {
    single { Navigator(startDestination = Screen.EmailInbox) }

    navigation<Screen.EmailInbox> {
        val viewModel = get<EmailViewModel>()

        EmailInboxScreen(viewModel = viewModel)
    }

    navigation<Screen.EmailSent> {
        val viewModel = get<EmailViewModel>()

        EmailSentScreen(viewModel = viewModel)
    }

    navigation<Screen.Statistics> { StatisticsScreen() }

    navigation<Screen.SmtpTesting> { SMTPTestingScreen() }

    navigation<Screen.Settings> { SettingsScreen() }
}