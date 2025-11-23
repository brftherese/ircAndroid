package com.example.ircclient.ui

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConnectedTopBar(
    nick: String,
    currentChannel: String?,
    onUsers: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = currentChannel?.let { " in $it" } ?: ""
    CenterAlignedTopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
        title = {
            Text(
                text = "Connected as $nick$subtitle",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            TextButton(onClick = onUsers) { Text("Users") }
            TextButton(onClick = onSettings) { Text("Settings") }
            TextButton(onClick = onDisconnect) { Text("Disconnect") }
        }
    )
}
