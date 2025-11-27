package com.ead.boshi_client.ui.dashboard.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.sharp.AccountCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ead.boshi_client.navigation.Navigator
import com.ead.boshi_client.navigation.Screen
import com.ead.boshi_client.ui.dashboard.DashboardCompositionLayoutState
import com.ead.boshi_client.ui.dashboard.models.EmailAccountNavItem
import com.ead.boshi_client.ui.util.Account
import com.ead.boshi_client.ui.util.Stubs


@Composable
fun DashboardSideBar(
    modifier: Modifier = Modifier,
    navigator: Navigator,
    selectedAccount: Account,
    onAccountSelected: (Account) -> Unit,
    emailAccountNavItems : List<EmailAccountNavItem> = emptyList()
) {
    val state = DashboardCompositionLayoutState.current
    var accountExpanded by remember { mutableStateOf(false) }

    val expandSideBarWidth by animateDpAsState(
        if (state.isSidebarExpanded.value) 280.dp
        else 80.dp
    )

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(expandSideBarWidth),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Account Selector
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Surface(
                        onClick = { accountExpanded = true },
                        shape = MaterialTheme.shapes.medium,
                        color = if (accountExpanded) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (state.isSidebarExpanded.value) Arrangement.Start else Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Sharp.AccountCircle,
                                contentDescription = "Account Icon",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            if (state.isSidebarExpanded.value) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = selectedAccount.email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = accountExpanded,
                        onDismissRequest = { accountExpanded = false },
                        offset = androidx.compose.ui.unit.DpOffset(0.dp, 8.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Stubs.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.email) },
                                onClick = {
                                    onAccountSelected(account)
                                    accountExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Items
                NavigationDrawerItem(
                    label = { Text("Inbox") },
                    icon = { Icon(Icons.Default.Email, contentDescription = null) },
                    selected = false, // TODO: Track selection
                    onClick = { navigator.goTo(Screen.EmailInbox) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Sent") },
                    icon = { Icon(Icons.Default.Send, contentDescription = null) },
                    selected = false, // TODO: Track selection
                    onClick = { navigator.goTo(Screen.EmailSent) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Statistics") },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    selected = false, // TODO: Track selection
                    onClick = { navigator.goTo(Screen.Statistics) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    selected = false, // TODO: Track selection
                    onClick = { navigator.goTo(Screen.Settings) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            VerticalDivider(thickness = 1.dp)
        }
    }
}