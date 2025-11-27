package com.ead.boshi_client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ead.boshi_client.navigation.Navigator
import com.ead.boshi_client.navigation.defaultPopTransition
import com.ead.boshi_client.navigation.defaultPredictivePopTransition
import com.ead.boshi_client.navigation.defaultTransition
import com.ead.boshi_client.ui.dashboard.DashboardCompositionLayoutState
import com.ead.boshi_client.ui.dashboard.DashboardLayoutState
import com.ead.boshi_client.ui.dashboard.components.Dashboard
import com.ead.boshi_client.ui.dashboard.components.DashboardSideBar
import com.ead.boshi_client.ui.dashboard.components.FloatingComposeWindow
import com.ead.boshi_client.ui.theme.BoshiTheme
import com.ead.boshi_client.ui.util.Stubs
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
@Preview
fun BoshiApp() {
    BoshiTheme {
        CompositionLocalProvider(value = DashboardCompositionLayoutState provides DashboardLayoutState()) {
            val navigator: Navigator = koinInject()
            val entryProvider = koinEntryProvider()

            var selectedAccount by remember { mutableStateOf(Stubs.accounts.first()) }
            var isComposeVisible by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxSize()) {
                Dashboard(modifier = Modifier.fillMaxSize()) {
                    DashboardSideBar(
                        modifier = Modifier
                            .fillMaxHeight(),
                        navigator = navigator,
                        selectedAccount = selectedAccount,
                        onAccountSelected = { selectedAccount = it }
                    )

                    NavDisplay(
                        backStack = navigator.backStack,
                        onBack = { navigator.goBack() },
                        entryProvider = entryProvider,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        transitionSpec = defaultTransition(),
                        popTransitionSpec = defaultPopTransition(),
                        predictivePopTransitionSpec = defaultPredictivePopTransition()
                    )
                }

                // Floating compose window
                FloatingComposeWindow(
                    isVisible = isComposeVisible,
                    onDismiss = { isComposeVisible = false }
                )

                // Floating Action Button
                FloatingActionButton(
                    onClick = { isComposeVisible = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Compose Email",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}