package com.example.ircclient.ui

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConnectedTopBar(
    nick: String,
    currentChannel: String?,
    onUsers: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
    mentionsCount: Int,
    onMentions: () -> Unit,
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
            IconButton(onClick = onMentions) {
                BadgedBox(badge = {
                    if (mentionsCount > 0) {
                        Badge { Text(mentionsCount.coerceAtMost(99).toString()) }
                    }
                }) {
                    Icon(Icons.Filled.AlternateEmail, contentDescription = "Mentions")
                }
            }
            TextButton(onClick = onUsers) { Text("Users") }
            TextButton(onClick = onSettings) { Text("Settings") }
            TextButton(onClick = onDisconnect) { Text("Disconnect") }
        }
    )
}
