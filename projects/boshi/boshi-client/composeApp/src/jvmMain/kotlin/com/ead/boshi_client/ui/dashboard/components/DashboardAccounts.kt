package com.ead.boshi_client.ui.dashboard.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ead.boshi_client.navigation.Navigator
import com.ead.boshi_client.navigation.Screen
import com.ead.boshi_client.ui.dashboard.models.EmailAccountNavItem

@Composable
fun DashboardAccounts(
    modifier: Modifier = Modifier,
    navigator: Navigator,
    emailAccountNavItems : List<EmailAccountNavItem>
) {
    LazyColumn(modifier = modifier) {
        items(emailAccountNavItems) { accountNavItem ->
            DashboardNavigationItem(
                navItem = accountNavItem.toNavItem(),
                onClick = { navigator.goTo(Screen) }
            )
        }
        item {
            DashboardAddAccount {

            }
        }
    }
}