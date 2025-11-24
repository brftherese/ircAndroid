package com.example.ircclient

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ircclient.ui.ConnectedTopBar
import com.example.ircclient.ui.theme.AppTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var client: IrcClient
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = IrcClient(lifecycleScope)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        NotificationHelper.init(this)
        requestNotificationPermissionIfNeeded()
        registerNetworkCallback()

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(client = client)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppForeground.isForeground = true
    }

    override fun onStop() {
        AppForeground.isForeground = false
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
        client.disconnect()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                client.setActiveNetwork(network)
                client.onNetworkAvailable(true)
            }

            override fun onLost(network: Network) {
                client.onNetworkAvailable(false)
            }
        }
        connectivityManager.registerNetworkCallback(request, callback)
        networkCallback = callback
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        networkCallback = null
    }
}

@Composable
private fun MainScreen(client: IrcClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected by client.connected.collectAsState(initial = false)
    val echoEnabled by client.echoEnabled.collectAsState(initial = false)
    val users by client.users.collectAsState(initial = emptyList())

    var server by remember { mutableStateOf("irc.libera.chat") }
    var portInput by remember { mutableStateOf("6697") }
    var useTls by remember { mutableStateOf(true) }
    var nick by remember { mutableStateOf("AndroidUser") }
    var user by remember { mutableStateOf("android") }
    var realName by remember { mutableStateOf("Android IRC") }
    var primaryChannel by remember { mutableStateOf("#android") }
    var extraChannels by remember { mutableStateOf("") }
    var highlights by remember { mutableStateOf("") }
    var highlightExceptions by remember { mutableStateOf("") }
    var ignoreNicks by remember { mutableStateOf("") }
    var saslAccount by remember { mutableStateOf("") }
    var saslPassword by remember { mutableStateOf("") }
    var stripColors by remember { mutableStateOf(true) }
    var allowBackgrounds by remember { mutableStateOf(false) }
    var fontScalePercent by remember { mutableStateOf(100) }
    var quietHoursEnabled by remember { mutableStateOf(false) }
    var quietHoursStart by remember { mutableStateOf(23) }
    var quietHoursEnd by remember { mutableStateOf(7) }

    var sessionActive by remember { mutableStateOf(false) }
    var currentChannel by remember { mutableStateOf<String?>(null) }
    var compactMode by remember { mutableStateOf(false) }
    var outgoing by remember { mutableStateOf(TextFieldValue("")) }
    var showJoin by remember { mutableStateOf(false) }
    var showUsers by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearchResults by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    val channelEvents = remember { mutableStateMapOf<String, SnapshotStateList<UiEvent>>() }
    val buffers = remember { mutableStateMapOf<String, BufferMeta>() }
    val joinedChannels = remember { mutableStateListOf<String>() }
    val queries = remember { mutableStateListOf<String>() }
    val muted = remember { mutableStateMapOf<String, Boolean>() }
    val lastRead = remember { mutableStateMapOf<String, Long>() }
    val inputHistories = remember { mutableStateMapOf<String, MutableList<String>>() }
    val inputHistoryPos = remember { mutableStateMapOf<String, Int>() }
    val searchResults = remember { mutableStateListOf<SearchHit>() }
    val suggestions = remember { mutableStateListOf<String>() }
    val ignoredNicksSet = remember(ignoreNicks) { parseIgnoreList(ignoreNicks) }
    val channelUsersMap = remember(users) { users.associateBy { it.nick.lowercase() } }
    val selfModeSymbol = remember(channelUsersMap, nick) { channelUsersMap[nick.lowercase()]?.mode }
    var pendingModerationTarget by remember { mutableStateOf<ModerationTarget?>(null) }

    val listState = rememberLazyListState()
    val bufferScroll = rememberScrollState()
    val moderationEnabled = currentChannel?.startsWith("#") == true
    val actionEnabled = remember(selfModeSymbol) { { action: ModerationAction -> canPerformModeration(action, selfModeSymbol) } }

    fun persistConfig() {
        val cfg = SavedConfig(
            server = server,
            port = portInput.toIntOrNull() ?: if (useTls) 6697 else 6667,
            tls = useTls,
            nick = nick,
            user = user,
            realName = realName,
            channel = primaryChannel,
            channels = extraChannels,
            highlights = highlights,
            highlightExceptions = highlightExceptions,
            ignoreNicks = ignoreNicks,
            saslAccount = saslAccount,
            saslPassword = saslPassword,
            stripColors = stripColors,
            allowBackgrounds = allowBackgrounds,
            fontScalePercent = fontScalePercent,
            quietHoursEnabled = quietHoursEnabled,
            quietHoursStart = quietHoursStart,
            quietHoursEnd = quietHoursEnd,
        )
        scope.launch { saveConfig(context, cfg) }
    }

    fun resetSessionState() {
        joinedChannels.clear()
        queries.clear()
        muted.clear()
        lastRead.clear()
        inputHistories.clear()
        inputHistoryPos.clear()
        channelEvents.clear()
        buffers.clear()
        channelEvents[STATUS_CHANNEL_KEY] = mutableStateListOf()
        buffers[STATUS_CHANNEL_KEY] = BufferMeta("Status", BufferType.STATUS)
        currentChannel = null
    }

    fun appendEvent(event: UiEvent) {
        if (shouldIgnoreEvent(event, ignoredNicksSet)) return
        val channelName = eventChannelName(event, nick)
        val bufferType = when {
            channelName == null -> BufferType.STATUS
            channelName.startsWith("#") -> BufferType.CHANNEL
            else -> BufferType.QUERY
        }
        val key = channelKeyOrStatus(channelName)
        val list = channelEvents.getOrPut(key) { mutableStateListOf() }
        val meta = ensureBufferMeta(buffers, channelName, bufferType)
        list.add(event)

        val activeKey = channelKeyOrStatus(currentChannel)
        if (activeKey != key) {
            meta.unread += 1
            if (event is UiEvent.Chat && shouldHighlight(event, bufferType, nick, highlights, highlightExceptions)) {
                meta.highlight += 1
                maybeNotify(
                    context = context,
                    event = event,
                    selfNick = nick,
                    highlights = highlights,
                    highlightExceptions = highlightExceptions,
                    mutedChannels = muted.filterValues { it }.keys,
                    ignoredNicks = ignoredNicksSet,
                    quietHoursEnabled = quietHoursEnabled,
                    quietHoursStart = quietHoursStart,
                    quietHoursEnd = quietHoursEnd,
                )
            }
            if (meta.firstUnreadTime == null) meta.firstUnreadTime = event.time
        }

        when (event) {
            is UiEvent.Join -> if (event.nick.equals(nick, ignoreCase = true)) {
                if (joinedChannels.none { it.equals(event.channel, ignoreCase = true) }) {
                    joinedChannels.add(event.channel)
                    joinedChannels.sortBy { it.lowercase() }
                }
            }
            is UiEvent.Part -> if (event.nick.equals(nick, ignoreCase = true)) {
                joinedChannels.removeAll { it.equals(event.channel, ignoreCase = true) }
                if (currentChannel?.equals(event.channel, ignoreCase = true) == true) currentChannel = null
            }
            is UiEvent.Kick -> if (event.target.equals(nick, ignoreCase = true)) {
                handleSelfKick(event, joinedChannels, muted, inputHistories, inputHistoryPos, currentChannel) { currentChannel = it }
            }
            is UiEvent.Nick -> handleNickRename(
                event = event,
                activeChannel = currentChannel,
                keyProvider = ::channelKeyOrStatus,
                queries = queries,
                channelEvents = channelEvents,
                buffers = buffers,
                lastRead = lastRead,
                muted = muted,
                inputHistories = inputHistories,
                inputHistoryPos = inputHistoryPos,
                onActiveChange = { currentChannel = it }
            )
            else -> Unit
        }

        if (bufferType == BufferType.QUERY && channelName != null) {
            if (queries.none { it.equals(channelName, ignoreCase = true) }) {
                queries.add(channelName)
                queries.sortBy { it.lowercase() }
            }
        }
    }

    fun emitStatus(message: String) {
        val status = channelEvents.getOrPut(STATUS_CHANNEL_KEY) { mutableStateListOf() }
        status.add(UiEvent.System(message, time = System.currentTimeMillis()))
    }

    fun handleModerationAction(target: ModerationTarget, action: ModerationAction, selfMode: Char?, selfNick: String) {
        val channel = currentChannel?.takeIf { it.startsWith("#") }
        if (channel.isNullOrBlank()) {
            emitStatus("Select a channel to use moderation tools.")
            return
        }
        if (target.nick.equals(selfNick, ignoreCase = true)) {
            emitStatus("You can't moderate yourself.")
            return
        }
        if (!canPerformModeration(action, selfMode)) {
            emitStatus("Insufficient privileges to ${moderationActionLabel(action)}.")
            return
        }
        val targetNick = target.nick
        when (action) {
            ModerationAction.OP -> {
                client.sendRaw("MODE ${channel} +o ${targetNick}")
                emitStatus("Requested +o for ${targetNick} in ${channel}")
            }
            ModerationAction.DEOP -> {
                client.sendRaw("MODE ${channel} -o ${targetNick}")
                emitStatus("Requested -o for ${targetNick} in ${channel}")
            }
            ModerationAction.VOICE -> {
                client.sendRaw("MODE ${channel} +v ${targetNick}")
                emitStatus("Requested +v for ${targetNick} in ${channel}")
            }
            ModerationAction.DEVOICE -> {
                client.sendRaw("MODE ${channel} -v ${targetNick}")
                emitStatus("Requested -v for ${targetNick} in ${channel}")
            }
            ModerationAction.KICK -> {
                client.sendRaw("KICK ${channel} ${targetNick} :Requested from Android")
                emitStatus("Requested kick for ${targetNick} in ${channel}")
            }
            ModerationAction.BAN -> {
                val mask = "${targetNick}!*@*"
                client.sendRaw("MODE ${channel} +b ${mask}")
                emitStatus("Requested ban ${mask} in ${channel}")
            }
        }
    }

    fun performSearch(query: String) {
        val key = channelKeyOrStatus(currentChannel)
        val events = channelEvents[key] ?: return
        searchResults.clear()
        if (query.isBlank()) return
        events.filterIsInstance<UiEvent.Chat>()
            .filter { it.text.contains(query, ignoreCase = true) }
            .forEach { hit ->
                val label = buffers[key]?.name ?: (currentChannel ?: "Status")
                searchResults.add(SearchHit(channel = label, nick = hit.nick, text = hit.text, time = hit.time))
            }
        searchQuery = query
        showSearchResults = true
    }

    fun sendCurrentMessage() {
        val raw = outgoing.text
        val trimmed = raw.trim()
        if (trimmed.startsWith("/search ")) {
            performSearch(trimmed.removePrefix("/search ").trim())
            outgoing = TextFieldValue("")
            return
        }
        sendMessageOrCommand(
            client = client,
            text = raw,
            activeChannel = currentChannel ?: "",
            nick = nick,
            echoEnabled = echoEnabled,
            channelEvents = channelEvents,
            buffers = buffers,
            queries = queries,
            inputHistories = inputHistories,
            inputHistoryPos = inputHistoryPos,
            ignoreNicks = ignoreNicks,
            onIgnoreChange = { updated ->
                ignoreNicks = updated
                persistConfig()
            },
            onOpenQuery = { target ->
                if (target.isNotBlank()) currentChannel = target
            }
        )
        outgoing = TextFieldValue("")
    }

    LaunchedEffect(Unit) {
        if (!channelEvents.containsKey(STATUS_CHANNEL_KEY)) {
            channelEvents[STATUS_CHANNEL_KEY] = mutableStateListOf()
            buffers[STATUS_CHANNEL_KEY] = BufferMeta("Status", BufferType.STATUS)
        }
        val saved = loadSavedConfig(context)
        server = saved.server
        portInput = saved.port.toString()
        useTls = saved.tls
        nick = saved.nick
        user = saved.user
        realName = saved.realName
        primaryChannel = saved.channel
        extraChannels = saved.channels
        highlights = saved.highlights
        highlightExceptions = saved.highlightExceptions
        ignoreNicks = saved.ignoreNicks
        saslAccount = saved.saslAccount
        saslPassword = saved.saslPassword
        stripColors = saved.stripColors
        allowBackgrounds = saved.allowBackgrounds
        fontScalePercent = saved.fontScalePercent
        quietHoursEnabled = saved.quietHoursEnabled
        quietHoursStart = saved.quietHoursStart
        quietHoursEnd = saved.quietHoursEnd
    }

    LaunchedEffect(client) {
        client.incoming.collectLatest { raw ->
            val event = parseIrcLine(raw) ?: UiEvent.System(raw, time = System.currentTimeMillis())
            appendEvent(event)
        }
    }

    val activeKey = channelKeyOrStatus(currentChannel)
    val activeEvents = channelEvents[activeKey] ?: emptyList()

    LaunchedEffect(activeKey, activeEvents.size) {
        if (activeEvents.isNotEmpty()) {
            listState.animateScrollToItem(activeEvents.lastIndex)
        }
        buffers[activeKey]?.resetCounts()
    }

    LaunchedEffect(outgoing.text, outgoing.selection, users) {
        val caret = outgoing.selection.start.coerceIn(0, outgoing.text.length)
        val prefix = outgoing.text.take(caret)
        val token = prefix.substringAfterLast(' ').substringAfterLast('\n')
        val next = buildSuggestions(token, users)
        suggestions.clear()
        suggestions.addAll(next)
        showSuggestions = next.isNotEmpty()
    }

    val autoJoinTargets = remember(primaryChannel, extraChannels) {
        buildList {
            if (primaryChannel.isNotBlank()) add(primaryChannel.trim())
            extraChannels.split(',', ';').map { it.trim() }.filter { it.startsWith("#") && it.isNotEmpty() }.forEach { add(it) }
        }
    }

    AutoJoinOnConnect(connected = connected, channels = autoJoinTargets, client = client)

    DisposableEffect(Unit) {
        onDispose { showSearchResults = false }
    }

    val fontScale = fontScalePercent / 100f
    val helpText = commandHelpText(outgoing.text, currentChannel != null)

    if (!sessionActive) {
        ConnectionForm(
            server = server,
            onServerChange = { server = it },
            port = portInput,
            onPortChange = { value -> portInput = value.filter { ch -> ch.isDigit() }.take(5) },
            nick = nick,
            onNickChange = { nick = it.take(32) },
            primaryChannel = primaryChannel,
            onPrimaryChange = { primaryChannel = it.take(64) },
            extraChannels = extraChannels,
            onExtrasChange = { extraChannels = it },
            useTls = useTls,
            onUseTlsChange = { useTls = it },
            onConnect = {
                val port = portInput.toIntOrNull() ?: if (useTls) 6697 else 6667
                if (server.isBlank() || nick.isBlank()) {
                    Toast.makeText(context, "Server and nick required", Toast.LENGTH_SHORT).show()
                    return@ConnectionForm
                }
                resetSessionState()
                persistConfig()
                sessionActive = true
                client.connect(
                    IrcClient.Config(
                        server = server.trim(),
                        port = port,
                        useTls = useTls,
                        nick = nick.trim(),
                        user = user.ifBlank { "android" },
                        realName = realName.ifBlank { nick },
                        channel = primaryChannel.takeIf { it.isNotBlank() },
                        saslAccount = saslAccount.takeIf { it.isNotBlank() },
                        saslPassword = saslPassword.takeIf { it.isNotBlank() },
                    )
                )
            },
            onShowSettings = { showSettings = true }
        )
    } else {
        SessionScreen(
            connected = connected,
            nick = nick,
            compactMode = compactMode,
            onToggleCompact = { compactMode = !compactMode },
            quietHoursEnabled = quietHoursEnabled,
            quietHoursStart = quietHoursStart,
            quietHoursEnd = quietHoursEnd,
            onQuietToggle = { quietHoursEnabled = it; persistConfig() },
            buffers = buffers,
            joinedChannels = joinedChannels,
            queries = queries,
            listState = listState,
            bufferScroll = bufferScroll,
            currentChannel = currentChannel,
            onChannelChange = { channel -> currentChannel = channel },
            events = activeEvents,
            fontScale = fontScale,
            stripColors = stripColors,
            allowBackgrounds = allowBackgrounds,
            outgoing = outgoing,
            onOutgoingChange = { outgoing = it },
            onSend = { sendCurrentMessage() },
            showSuggestions = showSuggestions,
            suggestions = suggestions,
            onSuggestionSelected = { suggestion ->
                val before = outgoing.text.substring(0, outgoing.selection.start)
                val after = outgoing.text.substring(outgoing.selection.end)
                val parts = before.split(' ', '\n', '\t').toMutableList()
                if (parts.isNotEmpty()) {
                    parts[parts.lastIndex] = suggestion
                } else {
                    parts.add(suggestion)
                }
                val prefix = parts.joinToString(" ").trimEnd()
                val newText = if (after.startsWith(" ") || after.isEmpty()) "$prefix$after" else "$prefix $after"
                outgoing = TextFieldValue(newText, selection = TextRange(prefix.length))
                showSuggestions = false
            },
            onJoinRequest = { showJoin = true },
            onDisconnect = {
                sessionActive = false
                client.disconnect()
                resetSessionState()
            },
            helpText = helpText,
            onShowUsers = { showUsers = true },
            onShowSettings = { showSettings = true },
            onChatLongPress = onChatLongPress@{ chat ->
                if (!moderationEnabled) return@onChatLongPress
                if (chat.nick.equals(nick, ignoreCase = true)) return@onChatLongPress
                val match = channelUsersMap[chat.nick.lowercase()]
                pendingModerationTarget = ModerationTarget(chat.nick, match?.mode)
            }
        )
    }

    JoinChannelDialog(show = showJoin, onDismiss = { showJoin = false }, onJoin = { channel ->
        val normalized = if (channel.startsWith("#")) channel else "#${channel}"
        client.join(normalized)
        showJoin = false
    })

    UsersDialog(
        show = showUsers,
        onDismiss = { showUsers = false },
        users = users,
        currentChannel = currentChannel,
        selfNick = nick,
        moderationEnabled = moderationEnabled,
        isActionEnabled = actionEnabled,
        onModerationAction = { target, action -> handleModerationAction(target, action, selfModeSymbol, nick) }
    )

    SearchResultsDialog(
        show = showSearchResults,
        query = searchQuery,
        results = searchResults,
        onDismiss = { showSearchResults = false }
    )

    SettingsDialog(
        show = showSettings,
        onDismiss = { showSettings = false },
        onSave = {
            persistConfig()
            showSettings = false
        },
        user = user,
        onUserChange = { user = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(32) },
        realName = realName,
        onRealNameChange = { realName = it.take(64) },
        highlights = highlights,
        onHighlightsChange = { highlights = it },
        highlightExceptions = highlightExceptions,
        onHighlightsExceptionsChange = { highlightExceptions = it },
        ignoreNicks = ignoreNicks,
        onIgnoreChange = { ignoreNicks = it },
        saslAccount = saslAccount,
        onSaslAccountChange = { saslAccount = it.take(64) },
        saslPassword = saslPassword,
        onSaslPasswordChange = { saslPassword = it.take(128) },
        stripColors = stripColors,
        onStripColorsChange = { stripColors = it },
        allowBackgrounds = allowBackgrounds,
        onAllowBackgroundChange = { allowBackgrounds = it },
        useTls = useTls,
        onUseTlsChange = { useTls = it },
        quietHoursEnabled = quietHoursEnabled,
        quietHoursStart = quietHoursStart,
        quietHoursEnd = quietHoursEnd,
        onQuietEnabledChange = { quietHoursEnabled = it },
        onQuietStartChange = { quietHoursStart = it },
        onQuietEndChange = { quietHoursEnd = it },
        fontScalePercent = fontScalePercent,
        onFontScaleChange = { fontScalePercent = it },
        client = client
    )

    ModerationActionDialog(
        target = pendingModerationTarget,
        onDismiss = { pendingModerationTarget = null },
        isActionEnabled = actionEnabled,
        onAction = { action ->
            val target = pendingModerationTarget ?: return@ModerationActionDialog
            handleModerationAction(target, action, selfModeSymbol, nick)
            pendingModerationTarget = null
        }
    )
}

@Composable
private fun ConnectionForm(
    server: String,
    onServerChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    nick: String,
    onNickChange: (String) -> Unit,
    primaryChannel: String,
    onPrimaryChange: (String) -> Unit,
    extraChannels: String,
    onExtrasChange: (String) -> Unit,
    useTls: Boolean,
    onUseTlsChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onShowSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Connect to IRC", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = server, onValueChange = onServerChange, label = { Text("Server") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(value = nick, onValueChange = onNickChange, label = { Text("Nick") }, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(checked = useTls, onCheckedChange = onUseTlsChange)
            Text("Use TLS")
        }
        OutlinedTextField(value = primaryChannel, onValueChange = onPrimaryChange, label = { Text("Primary channel") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = extraChannels,
            onValueChange = onExtrasChange,
            label = { Text("Auto-join channels (comma separated)") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onConnect) { Text("Connect") }
            OutlinedButton(onClick = onShowSettings) { Text("Advanced settings") }
        }
    }
}

@Composable
private fun SessionScreen(
    connected: Boolean,
    nick: String,
    compactMode: Boolean,
    onToggleCompact: () -> Unit,
    quietHoursEnabled: Boolean,
    quietHoursStart: Int,
    quietHoursEnd: Int,
    onQuietToggle: (Boolean) -> Unit,
    buffers: Map<String, BufferMeta>,
    joinedChannels: List<String>,
    queries: List<String>,
    listState: LazyListState,
    bufferScroll: ScrollState,
    currentChannel: String?,
    onChannelChange: (String?) -> Unit,
    events: List<UiEvent>,
    fontScale: Float,
    stripColors: Boolean,
    allowBackgrounds: Boolean,
    outgoing: TextFieldValue,
    onOutgoingChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    showSuggestions: Boolean,
    suggestions: List<String>,
    onSuggestionSelected: (String) -> Unit,
    onJoinRequest: () -> Unit,
    onDisconnect: () -> Unit,
    helpText: String?,
    onShowUsers: () -> Unit,
    onShowSettings: () -> Unit,
    onChatLongPress: (UiEvent.Chat) -> Unit,
) {
    Scaffold(
        topBar = {
            ConnectedTopBar(
                nick = nick,
                currentChannel = currentChannel,
                onUsers = onShowUsers,
                onSettings = onShowSettings,
                onDisconnect = onDisconnect
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!connected) {
                Text(
                    "Connecting…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Row(
                modifier = Modifier
                    .horizontalScroll(bufferScroll)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val statusUnread = buffers[STATUS_CHANNEL_KEY]?.unread ?: 0
                BufferChip(label = "Status", unread = statusUnread, selected = currentChannel == null) { onChannelChange(null) }
                joinedChannels.forEach { channel ->
                    val key = channelKeyOrStatus(channel)
                    BufferChip(
                        label = channel,
                        unread = buffers[key]?.unread ?: 0,
                        selected = currentChannel?.equals(channel, ignoreCase = true) == true
                    ) { onChannelChange(channel) }
                }
                queries.forEach { dm ->
                    val key = channelKeyOrStatus(dm)
                    BufferChip(
                        label = dm,
                        unread = buffers[key]?.unread ?: 0,
                        selected = currentChannel?.equals(dm, ignoreCase = true) == true
                    ) { onChannelChange(dm) }
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onQuietToggle(!quietHoursEnabled) },
                    label = { Text(quietHoursChipLabel(quietHoursEnabled, quietHoursStart, quietHoursEnd)) }
                )
                AssistChip(
                    onClick = onToggleCompact,
                    label = { Text(if (compactMode) "Compact" else "Comfort") }
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                itemsIndexed(events) { index, event ->
                    val previous = events.getOrNull(index - 1)
                    if (index == 0 || previous == null || !isSameDay(previous.time, event.time)) {
                        DayDivider(event.time)
                    }
                    val firstUnread = buffers[channelKeyOrStatus(currentChannel)]?.firstUnreadTime
                    if (firstUnread != null && (previous == null || previous.time < firstUnread) && event.time >= firstUnread) {
                        NewMessagesDivider()
                    }
                    when (event) {
                        is UiEvent.Chat -> ChatRow(event, nick, stripColors, allowBackgrounds, fontScale, compactMode) { onChatLongPress(event) }
                        is UiEvent.Notice -> MetaRow("Notice from ${event.nick ?: "server"}: ${event.text}", stripColors, allowBackgrounds, fontScale, compactMode)
                        is UiEvent.Join -> MetaRow("${event.nick} joined ${event.channel}", fontScale = fontScale, compact = compactMode)
                        is UiEvent.Part -> MetaRow("${event.nick} left ${event.channel}${event.reason?.let { ": $it" } ?: ""}", fontScale = fontScale, compact = compactMode)
                        is UiEvent.Quit -> MetaRow("${event.nick} quit${event.reason?.let { ": $it" } ?: ""}", fontScale = fontScale, compact = compactMode)
                        is UiEvent.Kick -> MetaRow("${event.target} was kicked from ${event.channel}${event.reason?.let { ": $it" } ?: ""}", fontScale = fontScale, compact = compactMode)
                        is UiEvent.Nick -> MetaRow("${event.oldNick} is now known as ${event.newNick}", fontScale = fontScale, compact = compactMode)
                        is UiEvent.Topic -> MetaRow("Topic for ${event.channel}: ${event.text}", fontScale = fontScale, compact = compactMode)
                        is UiEvent.System -> MetaRow(event.text, fontScale = fontScale, compact = compactMode)
                    }
                }
            }
            if (showSuggestions && suggestions.isNotEmpty()) {
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        suggestions.take(6).forEach { suggestion ->
                            Text(
                                suggestion,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSuggestionSelected(suggestion) }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                if (!helpText.isNullOrBlank()) {
                    Text(helpText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                TextField(
                    value = outgoing,
                    onValueChange = onOutgoingChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Message or command") },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onJoinRequest) { Text("Join") }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onSend, enabled = connected || outgoing.text.startsWith("/")) { Text("Send") }
                }
            }
        }
    }
}

@Composable
private fun BufferChip(label: String, unread: Int, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(if (unread > 0) "$label ($unread)" else label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun JoinChannelDialog(show: Boolean, onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    if (!show) return
    var toJoin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val channel = toJoin.trim().takeIf { it.isNotEmpty() }
                if (channel != null) onJoin(channel)
            }) { Text("Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Join a channel") },
        text = { OutlinedTextField(value = toJoin, onValueChange = { toJoin = it }, label = { Text("#channel") }) }
    )
}

@Composable
private fun UsersDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    users: List<ChannelUser>,
    currentChannel: String?,
    selfNick: String,
    moderationEnabled: Boolean,
    isActionEnabled: (ModerationAction) -> Boolean = { true },
    onModerationAction: (ModerationTarget, ModerationAction) -> Unit = { _, _ -> }
) {
    if (!show) return
    val canModerate = moderationEnabled && currentChannel?.startsWith("#") == true
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Users in ${currentChannel ?: "status"}") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Members: ${users.size}", style = MaterialTheme.typography.labelMedium)
                if (!canModerate) {
                    Text(
                        "Join a channel to access moderation actions.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(min = 200.dp, max = 400.dp)) {
                    itemsIndexed(users) { _, user ->
                        UserRow(
                            user = user,
                            canModerate = canModerate && !user.nick.equals(selfNick, ignoreCase = true),
                            isActionEnabled = isActionEnabled,
                            onModerationAction = { action -> onModerationAction(ModerationTarget(user.nick, user.mode), action) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ModerationActionDialog(
    target: ModerationTarget?,
    onDismiss: () -> Unit,
    isActionEnabled: (ModerationAction) -> Boolean,
    onAction: (ModerationAction) -> Unit,
) {
    if (target == null) return
    val anyEnabled = moderationMenuOrder.any(isActionEnabled)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Moderate ${target.nick}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                moderationMenuOrder.forEach { action ->
                    TextButton(
                        onClick = { onAction(action) },
                        enabled = isActionEnabled(action),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(moderationActionLabel(action)) }
                }
                if (!anyEnabled) {
                    Text(
                        "You don't have permission to perform moderation actions.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    )
}

@Composable
private fun SearchResultsDialog(show: Boolean, query: String, results: List<SearchHit>, onDismiss: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Search: \"$query\"") },
        text = {
            if (results.isEmpty()) {
                Text("No matches in this buffer.")
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(results) { _, hit ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text("${hit.channel} — ${hit.nick}", fontWeight = FontWeight.SemiBold)
                            Text(hit.text)
                            Text(
                                DateFormat.format("MMM d, yyyy HH:mm", hit.time).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SettingsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    user: String,
    onUserChange: (String) -> Unit,
    realName: String,
    onRealNameChange: (String) -> Unit,
    highlights: String,
    onHighlightsChange: (String) -> Unit,
    highlightExceptions: String,
    onHighlightsExceptionsChange: (String) -> Unit,
    ignoreNicks: String,
    onIgnoreChange: (String) -> Unit,
    saslAccount: String,
    onSaslAccountChange: (String) -> Unit,
    saslPassword: String,
    onSaslPasswordChange: (String) -> Unit,
    stripColors: Boolean,
    onStripColorsChange: (Boolean) -> Unit,
    allowBackgrounds: Boolean,
    onAllowBackgroundChange: (Boolean) -> Unit,
    useTls: Boolean,
    onUseTlsChange: (Boolean) -> Unit,
    quietHoursEnabled: Boolean,
    quietHoursStart: Int,
    quietHoursEnd: Int,
    onQuietEnabledChange: (Boolean) -> Unit,
    onQuietStartChange: (Int) -> Unit,
    onQuietEndChange: (Int) -> Unit,
    fontScalePercent: Int,
    onFontScaleChange: (Int) -> Unit,
    client: IrcClient,
) {
    if (!show) return
    var showReg by remember { mutableStateOf(false) }
    var regEmail by remember { mutableStateOf("") }
    var regPass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onSave) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = user, onValueChange = onUserChange, label = { Text("Username (USER)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = realName, onValueChange = onRealNameChange, label = { Text("Real name") }, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = highlights, onValueChange = onHighlightsChange, label = { Text("Highlights") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = highlightExceptions, onValueChange = onHighlightsExceptionsChange, label = { Text("Exceptions") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = ignoreNicks, onValueChange = onIgnoreChange, label = { Text("Ignore nicks") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = saslAccount, onValueChange = onSaslAccountChange, label = { Text("SASL account") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = saslPassword, onValueChange = onSaslPasswordChange, label = { Text("SASL password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val account = saslAccount.trim()
                        val pwd = saslPassword
                        if (account.isNotEmpty() && pwd.isNotEmpty()) {
                            client.sendRaw("PRIVMSG NickServ :IDENTIFY ${'$'}account ${'$'}pwd")
                        }
                    }) { Text("NickServ Identify") }
                    OutlinedButton(onClick = { showReg = true }) { Text("NickServ Register") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = useTls, onCheckedChange = onUseTlsChange); Text("Use TLS") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = stripColors, onCheckedChange = onStripColorsChange); Text("Monochrome") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = allowBackgrounds, onCheckedChange = onAllowBackgroundChange); Text("Allow bg") }
                }
                QuietHoursSection(
                    enabled = quietHoursEnabled,
                    startHour = quietHoursStart,
                    endHour = quietHoursEnd,
                    onEnabledChange = onQuietEnabledChange,
                    onStartChange = onQuietStartChange,
                    onEndChange = onQuietEndChange
                )
                Text("Text size: ${fontScalePercent}%")
                Slider(value = fontScalePercent / 100f, onValueChange = { value -> onFontScaleChange((value.coerceIn(0.85f, 1.5f) * 100).toInt()) }, valueRange = 0.85f..1.5f, steps = 5)
            }
        }
    )
    if (showReg) {
        AlertDialog(
            onDismissRequest = { showReg = false },
            confirmButton = {
                TextButton(onClick = {
                    val email = regEmail.trim()
                    val pwd = regPass
                    if (email.isNotEmpty() && pwd.isNotEmpty()) {
                        client.sendRaw("PRIVMSG NickServ :REGISTER ${'$'}pwd ${'$'}email")
                        showReg = false
                    }
                }) { Text("Register") }
            },
            dismissButton = { TextButton(onClick = { showReg = false }) { Text("Cancel") } },
            title = { Text("Register Nick with NickServ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = regEmail, onValueChange = { regEmail = it }, label = { Text("Email") })
                    OutlinedTextField(value = regPass, onValueChange = { regPass = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())
                    Text("This sends: PRIVMSG NickServ :REGISTER <password> <email>")
                }
            }
        )
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun AutoJoinOnConnect(connected: Boolean, channels: List<String>, client: IrcClient) {
    LaunchedEffect(connected) {
        if (connected) {
            channels.filter { it.startsWith("#") && it.isNotBlank() }.forEach { channel -> client.join(channel) }
        }
    }
}

private fun sendMessageOrCommand(
    client: IrcClient,
    text: String,
    activeChannel: String,
    nick: String,
    echoEnabled: Boolean,
    channelEvents: MutableMap<String, SnapshotStateList<UiEvent>>,
    buffers: MutableMap<String, BufferMeta>,
    queries: SnapshotStateList<String>,
    inputHistories: MutableMap<String, MutableList<String>> = mutableMapOf(),
    inputHistoryPos: MutableMap<String, Int> = mutableMapOf(),
    ignoreNicks: String,
    onIgnoreChange: (String) -> Unit,
    onOpenQuery: (String) -> Unit,
) {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return
    fun emitStatusMessage(message: String) {
        val status = channelEvents.getOrPut(STATUS_CHANNEL_KEY) { mutableStateListOf() }
        status.add(UiEvent.System(message, time = System.currentTimeMillis()))
    }
    val histKey = activeChannel.ifBlank { "global" }
    val history = inputHistories.getOrPut(histKey) { mutableListOf("") }
    if (history.isEmpty()) history.add("")
    if (history.getOrNull(1) != trimmed) history.add(1, trimmed)
    if (history.size > 101) history.removeLast()
    inputHistoryPos[histKey] = 0
    if (trimmed.startsWith("/raw ")) {
        client.sendRaw(trimmed.removePrefix("/raw "))
        return
    }
    if (trimmed.startsWith("/whois ")) {
        val nickArg = trimmed.removePrefix("/whois ").trim()
        if (nickArg.isNotEmpty()) {
            client.sendRaw("WHOIS ${nickArg}")
            emitStatusMessage("Requesting WHOIS for ${nickArg}")
        } else {
            emitStatusMessage("Usage: /whois <nick>")
        }
        return
    }
    if (trimmed.startsWith("/join ")) {
        val ch = trimmed.removePrefix("/join ").trim().ifBlank { null }
        ch?.let { client.join(if (it.startsWith("#")) it else "#${it}") }
        return
    }
    if (trimmed.startsWith("/part")) {
        val ch = trimmed.removePrefix("/part").trim().ifBlank { activeChannel }
        if (ch.isNotBlank()) client.part(ch)
        return
    }
    if (trimmed.startsWith("/me ")) {
        val action = trimmed.removePrefix("/me ").trim()
        val target = activeChannel.takeIf { it.isNotBlank() }
        if (target != null) {
            if (!target.startsWith("#") && queries.none { it.equals(target, ignoreCase = true) }) {
                queries.add(target)
                ensureBufferMeta(buffers, target, BufferType.QUERY)
            }
            client.sendRaw("PRIVMSG ${target} :\u0001ACTION ${action}\u0001")
            if (!echoEnabled) {
                val key = channelKeyOrStatus(target)
                channelEvents.getOrPut(key) { mutableStateListOf() }
                    .add(UiEvent.Chat(nick, target, "\u0001ACTION ${action}\u0001", System.currentTimeMillis()))
            }
        }
        return
    }
    if (trimmed.startsWith("/msg ")) {
        val rest = trimmed.removePrefix("/msg ").trim()
        val target = rest.substringBefore(' ').ifBlank { null }
        val body = rest.substringAfter(' ', missingDelimiterValue = "").trim()
        if (target != null && body.isNotEmpty()) {
            client.sendMessage(target, body)
            if (!target.startsWith("#") && queries.none { it.equals(target, ignoreCase = true) }) {
                queries.add(target)
                ensureBufferMeta(buffers, target, BufferType.QUERY)
            }
            if (!echoEnabled) {
                val key = channelKeyOrStatus(target)
                channelEvents.getOrPut(key) { mutableStateListOf() }
                    .add(UiEvent.Chat(nick, target, body, System.currentTimeMillis()))
            }
        }
        return
    }
    if (trimmed.startsWith("/query")) {
        val rest = trimmed.removePrefix("/query").trim()
        if (rest.isBlank()) {
            emitStatusMessage("Usage: /query <nick> [message]")
            return
        }
        val parts = rest.split(' ', limit = 2)
        val target = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: run {
            emitStatusMessage("Usage: /query <nick> [message]")
            return
        }
        val initialMessage = parts.getOrNull(1)?.trim().orEmpty()
        if (queries.none { it.equals(target, ignoreCase = true) }) {
            queries.add(target)
            queries.sortBy { it.lowercase() }
        }
        ensureBufferMeta(buffers, target, BufferType.QUERY)
        onOpenQuery(target)
        if (initialMessage.isNotEmpty()) {
            client.sendMessage(target, initialMessage)
            if (!echoEnabled) {
                val key = channelKeyOrStatus(target)
                channelEvents.getOrPut(key) { mutableStateListOf() }
                    .add(UiEvent.Chat(nick, target, initialMessage, System.currentTimeMillis()))
            }
        }
        return
    }
    if (trimmed.startsWith("/ignore")) {
        val rest = trimmed.removePrefix("/ignore").trim()
        val current = parseIgnoreList(ignoreNicks).toMutableSet()
        fun saveIgnores() {
            onIgnoreChange(formatIgnoreList(current))
        }
        if (rest.isBlank() || rest.equals("list", ignoreCase = true)) {
            val summary = if (current.isEmpty()) "Ignore list is empty" else "Ignoring: ${current.sorted().joinToString(", ")}"
            emitStatusMessage(summary)
        } else {
            fun addNick(rawNick: String) {
                val normalized = normalizeNick(rawNick)
                if (normalized.isEmpty()) {
                    emitStatusMessage("Usage: /ignore +nick")
                    return
                }
                val added = current.add(normalized)
                if (added) {
                    saveIgnores()
                    emitStatusMessage("Ignoring ${normalized}")
                } else {
                    emitStatusMessage("${normalized} already ignored")
                }
            }
            fun removeNick(rawNick: String) {
                val normalized = normalizeNick(rawNick)
                if (normalized.isEmpty()) {
                    emitStatusMessage("Usage: /ignore -nick")
                    return
                }
                val removed = current.remove(normalized)
                if (removed) {
                    saveIgnores()
                    emitStatusMessage("No longer ignoring ${normalized}")
                } else {
                    emitStatusMessage("${normalized} was not ignored")
                }
            }
            when {
                rest.startsWith("+" ) -> addNick(rest.drop(1))
                rest.startsWith("-") -> removeNick(rest.drop(1))
                rest.lowercase().startsWith("add ") -> addNick(rest.substringAfter(' '))
                rest.lowercase().startsWith("remove ") -> removeNick(rest.substringAfter(' '))
                else -> {
                    val normalized = normalizeNick(rest)
                    if (normalized.isEmpty()) {
                        emitStatusMessage("Usage: /ignore [+|-]nick | list")
                    } else if (current.contains(normalized)) {
                        current.remove(normalized)
                        saveIgnores()
                        emitStatusMessage("No longer ignoring ${normalized}")
                    } else {
                        current.add(normalized)
                        saveIgnores()
                        emitStatusMessage("Ignoring ${normalized}")
                    }
                }
            }
        }
        return
    }
    if (trimmed.startsWith("/notice ")) {
        val rest = trimmed.removePrefix("/notice ").trim()
        val parts = rest.split(' ', limit = 2)
        val target = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val body = parts.getOrNull(1)?.trim().orEmpty()
        if (target == null || body.isEmpty()) {
            emitStatusMessage("Usage: /notice <target> <text>")
        } else {
            client.sendRaw("NOTICE ${target} :${body}")
            if (!target.startsWith("#") && queries.none { it.equals(target, ignoreCase = true) }) {
                queries.add(target)
                ensureBufferMeta(buffers, target, BufferType.QUERY)
            }
            if (!echoEnabled) {
                val key = channelKeyOrStatus(target)
                channelEvents.getOrPut(key) { mutableStateListOf() }
                    .add(UiEvent.Notice(nick, target, body, System.currentTimeMillis()))
            }
        }
        return
    }
    if (trimmed.startsWith("/invite")) {
        val rest = trimmed.removePrefix("/invite").trim()
        val parts = rest.split(' ', limit = 2)
        val targetNick = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val explicitChannel = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        val candidateChannel = explicitChannel ?: activeChannel.takeIf { it.startsWith("#") }
        if (targetNick == null || candidateChannel.isNullOrBlank()) {
            emitStatusMessage("Usage: /invite <nick> [#channel]")
        } else {
            client.sendRaw("INVITE ${targetNick} ${candidateChannel}")
            emitStatusMessage("Invited ${targetNick} to ${candidateChannel}")
        }
        return
    }
    if (trimmed.startsWith("/away")) {
        val message = trimmed.removePrefix("/away").trim()
        if (message.isEmpty()) {
            client.sendRaw("AWAY")
            emitStatusMessage("Cleared away status")
        } else {
            client.sendRaw("AWAY :${message}")
            emitStatusMessage("Set away: ${message}")
        }
        return
    }
    if (trimmed.startsWith("/topic")) {
        val rest = trimmed.removePrefix("/topic").trim()
        var channelArg: String? = null
        var topicText: String? = null
        if (rest.startsWith("#") || rest.startsWith("&")) {
            val parts = rest.split(' ', limit = 2)
            channelArg = parts.getOrNull(0)
            topicText = parts.getOrNull(1)?.trim()
        } else if (rest.isNotBlank()) {
            topicText = rest
        }
        val resolvedChannel = channelArg?.takeIf { it.isNotBlank() } ?: activeChannel.takeIf { it.startsWith("#") }
        if (resolvedChannel.isNullOrBlank()) {
            emitStatusMessage("Join a channel or specify /topic #channel <text>")
            return
        }
        if (topicText.isNullOrBlank()) {
            client.sendRaw("TOPIC ${resolvedChannel}")
            emitStatusMessage("Requested topic for ${resolvedChannel}")
        } else {
            client.sendRaw("TOPIC ${resolvedChannel} :${topicText}")
            emitStatusMessage("Setting topic for ${resolvedChannel}")
        }
        return
    }
    if (trimmed.startsWith("/search ")) {
        return
    }
    if (activeChannel.startsWith("#")) {
        val channel = activeChannel
        client.sendMessage(channel, trimmed)
        if (!echoEnabled) {
            val key = channelKeyOrStatus(channel)
            channelEvents.getOrPut(key) { mutableStateListOf() }
                .add(UiEvent.Chat(nick, channel, trimmed, System.currentTimeMillis()))
        }
    } else if (activeChannel.isNotBlank()) {
        client.sendMessage(activeChannel, trimmed)
        if (queries.none { it.equals(activeChannel, ignoreCase = true) }) {
            queries.add(activeChannel)
            ensureBufferMeta(buffers, activeChannel, BufferType.QUERY)
        }
        if (!echoEnabled) {
            val key = channelKeyOrStatus(activeChannel)
            channelEvents.getOrPut(key) { mutableStateListOf() }
                .add(UiEvent.Chat(nick, activeChannel, trimmed, System.currentTimeMillis()))
        }
    } else {
        client.sendRaw(trimmed)
    }
}

@Composable
private fun QuietHoursSection(
    enabled: Boolean,
    startHour: Int,
    endHour: Int,
    onEnabledChange: (Boolean) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = enabled, onCheckedChange = onEnabledChange)
            Column {
                Text("Quiet hours")
                Text(
                    quietHoursChipLabel(enabled, startHour, endHour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        Text("Start: ${startHour.coerceIn(0, 23)}:00", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = startHour.coerceIn(0, 23).toFloat(),
            onValueChange = { onStartChange(it.roundToInt().coerceIn(0, 23)) },
            valueRange = 0f..23f,
            steps = 22,
            enabled = enabled
        )
        Text("End: ${endHour.coerceIn(0, 23)}:00", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = endHour.coerceIn(0, 23).toFloat(),
            onValueChange = { onEndChange(it.roundToInt().coerceIn(0, 23)) },
            valueRange = 0f..23f,
            steps = 22,
            enabled = enabled
        )
    }
}

@Composable
private fun ChatRow(
    ev: UiEvent.Chat,
    selfNick: String,
    stripColors: Boolean,
    allowBackgrounds: Boolean,
    fontScale: Float,
    compact: Boolean,
    onLongPress: (() -> Unit)? = null,
) {
    val isSelf = ev.nick.equals(selfNick, ignoreCase = true)
    val nickColor = remember(ev.nick) { colorForNick(ev.nick) }
    val time = remember(ev.time) { DateFormat.format("HH:mm", ev.time).toString() }
    val nickWidth = if (compact) 48.dp else 64.dp
    val baseStyle = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    val nickStyle = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    var rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = if (compact) 0.dp else 2.dp)
    if (onLongPress != null) {
        rowModifier = rowModifier.pointerInput(ev.nick, ev.text) {
            detectTapGestures(onLongPress = { onLongPress() })
        }
    }
    Row(rowModifier) {
        Text(
            ev.nick,
            color = if (isSelf) MaterialTheme.colorScheme.primary else nickColor,
            fontWeight = FontWeight.SemiBold,
            style = nickStyle,
            modifier = Modifier.widthIn(min = nickWidth).padding(end = 8.dp)
        )
        Column(Modifier.weight(1f)) {
            val annotated = remember(ev.text, stripColors, allowBackgrounds) { formatIrcAnnotated(ev.text, stripColors = stripColors, allowBackgrounds = allowBackgrounds) }
            LinkableAnnotatedText(annotated = annotated, style = baseStyle.copy(fontSize = baseStyle.fontSize * fontScale))
        }
        Text(time, style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MetaRow(
    text: String,
    stripColors: Boolean = true,
    allowBackgrounds: Boolean = false,
    fontScale: Float = 1f,
    compact: Boolean = false,
) {
    val annotated = remember(text, stripColors, allowBackgrounds) { formatIrcAnnotated(text, stripColors = stripColors, allowBackgrounds = allowBackgrounds) }
    val baseStyle = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall
    LinkableAnnotatedText(
        annotated = annotated,
        style = baseStyle.copy(fontSize = baseStyle.fontSize * fontScale, color = Color.Gray)
    )
}

@Composable
private fun LinkableAnnotatedText(
    annotated: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var layout by remember(annotated) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = style,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { pos ->
                val layoutResult = layout ?: return@detectTapGestures
                val offset = layoutResult.getOffsetForPosition(pos)
                val link = annotated.getLinkAnnotations(offset, offset).firstOrNull()?.item
                val url = when (link) {
                    is LinkAnnotation.Url -> link.url
                    else -> null
                }
                if (url != null) {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        },
        onTextLayout = { layout = it }
    )
}

@Composable
private fun DayDivider(time: Long) {
    val label = remember(time) { DateFormat.format("EEE, MMM d, yyyy", time).toString() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp))
        }
    }
}

@Composable
private fun NewMessagesDivider() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 0.dp) {
            Text("New messages", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(vertical = 2.dp, horizontal = 8.dp))
        }
    }
}

@Composable
private fun SidebarSectionHeader(label: String) {
    Text(label, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.secondary), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun SidebarRow(
    label: String,
    count: Int,
    selected: Boolean,
    isMuted: Boolean = false,
    onClick: () -> Unit,
    onToggleMute: (() -> Unit)? = null,
    onClearUnread: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    var showMenu by remember { mutableStateOf(false) }
    val containerPadding = if (compact) 1.dp else 2.dp
    val rowPadding = if (compact) 4.dp else 6.dp
    val labelStyle = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val mutedStyle = MaterialTheme.typography.labelSmall
    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = containerPadding),
            shape = MaterialTheme.shapes.small,
            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
            tonalElevation = if (selected) 1.dp else 0.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = rowPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = labelStyle)
                    if (isMuted) {
                        Text("Muted", style = mutedStyle, color = MaterialTheme.colorScheme.outline)
                    }
                }
                if (count > 0) {
                    Surface(color = MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.small) {
                        Text("$count", color = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (onToggleMute != null || onClearUnread != null) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Buffer actions")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            onToggleMute?.let { toggle ->
                                DropdownMenuItem(
                                    text = { Text(if (isMuted) "Unmute" else "Mute") },
                                    onClick = {
                                        toggle()
                                        showMenu = false
                                    }
                                )
                            }
                            onClearUnread?.let { clear ->
                                DropdownMenuItem(
                                    text = { Text("Mark read") },
                                    onClick = {
                                        clear()
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (selected) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        }
    }
}

private fun colorForNick(nick: String): Color {
    val palette = listOf(
        Color(0xFFe91e63), Color(0xFF9c27b0), Color(0xFF3f51b5), Color(0xFF03a9f4),
        Color(0xFF009688), Color(0xFF4caf50), Color(0xFF8bc34a), Color(0xFFffc107),
        Color(0xFFff9800), Color(0xFF795548)
    )
    val idx = (nick.lowercase().hashCode().toUInt().toInt() and Int.MAX_VALUE) % palette.size
    return palette[idx]
}

@Composable
private fun UserRow(
    user: ChannelUser,
    canModerate: Boolean,
    isActionEnabled: (ModerationAction) -> Boolean,
    onModerationAction: (ModerationAction) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val allowActions = canModerate && moderationMenuOrder.any { isActionEnabled(it) }
    val badgeColor = when (user.mode) {
        '~' -> Color(0xFFE91E63)
        '&' -> Color(0xFF9C27B0)
        '@' -> Color(0xFF3F51B5)
        '%' -> Color(0xFF009688)
        '+' -> Color(0xFF4CAF50)
        else -> Color.Gray
    }
    val label = user.mode?.toString() ?: " "
    var rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)
    if (allowActions) {
        rowModifier = rowModifier.pointerInput(user.nick) {
            detectTapGestures(onLongPress = { showMenu = true })
        }
    }
    Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = badgeColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
            Text(label, color = badgeColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Text(user.nick, modifier = Modifier.weight(1f))
        if (allowActions) {
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Moderate ${user.nick}") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    moderationMenuOrder.forEach { action ->
                        val enabled = isActionEnabled(action)
                        DropdownMenuItem(
                            text = { Text(moderationActionLabel(action)) },
                            enabled = enabled,
                            onClick = {
                                onModerationAction(action)
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private enum class ModerationAction {
    OP,
    DEOP,
    VOICE,
    DEVOICE,
    KICK,
    BAN,
}

private data class ModerationTarget(val nick: String, val mode: Char?)

private val moderationMenuOrder = listOf(
    ModerationAction.OP,
    ModerationAction.DEOP,
    ModerationAction.VOICE,
    ModerationAction.DEVOICE,
    ModerationAction.KICK,
    ModerationAction.BAN,
)

private fun moderationPrivilegeRank(mode: Char?): Int = when (mode) {
    '~' -> 5
    '&' -> 4
    '@' -> 3
    '%' -> 2
    '+' -> 1
    else -> 0
}

private fun canPerformModeration(action: ModerationAction, selfMode: Char?): Boolean {
    val rank = moderationPrivilegeRank(selfMode)
    return when (action) {
        ModerationAction.OP,
        ModerationAction.DEOP,
        ModerationAction.KICK,
        ModerationAction.BAN -> rank >= 3
        ModerationAction.VOICE,
        ModerationAction.DEVOICE -> rank >= 2
    }
}

private fun moderationActionLabel(action: ModerationAction): String = when (action) {
    ModerationAction.OP -> "Op (+o)"
    ModerationAction.DEOP -> "Deop (-o)"
    ModerationAction.VOICE -> "Voice (+v)"
    ModerationAction.DEVOICE -> "Devoice (-v)"
    ModerationAction.KICK -> "Kick"
    ModerationAction.BAN -> "Ban (+b)"
}

private fun maybeNotify(
    context: Context,
    event: UiEvent,
    selfNick: String,
    highlights: String,
    highlightExceptions: String,
    mutedChannels: Set<String> = emptySet(),
    ignoredNicks: Set<String> = emptySet(),
    precomputedHighlight: Boolean? = null,
    quietHoursEnabled: Boolean,
    quietHoursStart: Int,
    quietHoursEnd: Int,
) {
    if (AppForeground.isForeground) return
    val now = System.currentTimeMillis()
    if (now - event.time > 15_000) return
    if (isInQuietHours(quietHoursEnabled, quietHoursStart, quietHoursEnd, now)) return
    when (event) {
        is UiEvent.Chat -> {
            if (event.nick.equals(selfNick, ignoreCase = true)) return
            if (event.target.startsWith("#") && event.target in mutedChannels) return
            if (ignoredNicks.contains(event.nick.lowercase())) return
            val isDm = !event.target.startsWith("#") && event.target.equals(selfNick, ignoreCase = true)
            val highlightHit = precomputedHighlight ?: if (isDm) {
                true
            } else {
                isHighlight(selfNick, event.text, highlights, highlightExceptions)
            }
            val shouldNotify = if (isDm) true else highlightHit
            if (shouldNotify) {
                val title = if (isDm) "Message from ${event.nick}" else "Highlight in ${event.target}"
                val body = "${event.nick}: ${event.text}".take(200)
                val id = (if (isDm) event.nick.lowercase() else event.target.lowercase()).hashCode()
                val kind = if (isDm) NotificationHelper.NotificationKind.DIRECT else NotificationHelper.NotificationKind.HIGHLIGHT
                NotificationHelper.notifyEvent(context, kind, title, body, id)
            }
        }
        else -> Unit
    }
}

private fun isInQuietHours(enabled: Boolean, startHour: Int, endHour: Int, now: Long): Boolean {
    if (!enabled) return false
    val start = startHour.coerceIn(0, 23)
    val end = endHour.coerceIn(0, 23)
    if (start == end) return false
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return if (start < end) {
        hour in start until end
    } else {
        hour >= start || hour < end
    }
}

private fun buildTerms(selfNick: String?, csv: String): List<String> {
    val parts = csv.split(',', ';', ' ', '\n', '\t').mapNotNull { it.trim().ifEmpty { null } }
    return if (selfNick != null) listOf(selfNick) + parts else parts
}

private fun parseIgnoreList(raw: String): Set<String> {
    if (raw.isBlank()) return emptySet()
    return raw.split(',', ';', ' ', '\n', '\t')
        .mapNotNull { normalizeNick(it).ifEmpty { null } }
        .toSet()
}

private fun formatIgnoreList(entries: Collection<String>): String =
    entries.filter { it.isNotBlank() }
        .map { normalizeNick(it) }
        .distinct()
        .sorted()
        .joinToString(",")

private fun normalizeNick(nick: String): String = nick.trim().lowercase()

private fun containsWord(text: String, needle: String): Boolean {
    val lower = text.lowercase()
    val n = needle.lowercase()
    var idx = lower.indexOf(n)
    while (idx >= 0) {
        val beforeOk = idx == 0 || (!lower[idx - 1].isLetterOrDigit() && lower[idx - 1] != '_')
        val afterIdx = idx + n.length
        val afterOk = afterIdx >= lower.length || (!lower[afterIdx].isLetterOrDigit() && lower[afterIdx] != '_')
        if (beforeOk && afterOk) return true
        idx = lower.indexOf(n, idx + 1)
    }
    return false
}

private fun isHighlight(selfNick: String, text: String, highlights: String, highlightExceptions: String): Boolean {
    val terms = buildTerms(selfNick, highlights)
    val except = buildTerms(null, highlightExceptions)
    return terms.any { term ->
        if (term.isBlank()) {
            false
        } else if (except.any { it.equals(term, ignoreCase = true) }) {
            false
        } else {
            containsWord(text, term)
        }
    }
}

private enum class BufferType { STATUS, CHANNEL, QUERY }

private class BufferMeta(
    name: String,
    val type: BufferType,
    unread: Int = 0,
    highlight: Int = 0,
    firstUnread: Long? = null,
) {
    var name by mutableStateOf(name)
    var unread by mutableStateOf(unread)
    var highlight by mutableStateOf(highlight)
    var firstUnreadTime by mutableStateOf(firstUnread)
}

private data class SearchHit(
    val channel: String,
    val nick: String,
    val text: String,
    val time: Long,
)

private fun ensureBufferMeta(
    buffers: MutableMap<String, BufferMeta>,
    name: String?,
    type: BufferType,
): BufferMeta {
    val key = channelKeyOrStatus(name)
    val label = name?.takeIf { it.isNotBlank() }
        ?: if (type == BufferType.STATUS) "Status" else key
    val meta = buffers.getOrPut(key) { BufferMeta(label, type) }
    if (!name.isNullOrBlank()) meta.name = name
    return meta
}

private fun BufferMeta.resetCounts() {
    if (unread != 0) unread = 0
    if (highlight != 0) highlight = 0
    firstUnreadTime = null
}

private fun shouldHighlight(
    event: UiEvent.Chat,
    bufferType: BufferType,
    selfNick: String,
    highlights: String,
    highlightExceptions: String,
): Boolean {
    if (event.nick.equals(selfNick, ignoreCase = true)) return false
    return when (bufferType) {
        BufferType.QUERY -> true
        else -> isHighlight(selfNick, event.text, highlights, highlightExceptions)
    }
}

private fun shouldIgnoreEvent(event: UiEvent, ignored: Set<String>): Boolean {
    if (ignored.isEmpty()) return false
    val actor = when (event) {
        is UiEvent.Chat -> event.nick
        is UiEvent.Notice -> event.nick
        else -> null
    } ?: return false
    return ignored.contains(actor.lowercase())
}

private fun buildSuggestions(token: String, users: List<ChannelUser>): List<String> {
    val trimmed = token.trim()
    if (trimmed.isBlank()) return emptyList()
    if (trimmed.startsWith("/")) {
        val commands = listOf("/join", "/part", "/me", "/msg", "/notice", "/invite", "/away", "/whois", "/raw", "/search", "/ignore", "/query", "/topic")
        val lowercase = trimmed.lowercase()
        return commands.filter { it.startsWith(lowercase) }
    }
    if (trimmed.startsWith("@")) {
        val needle = trimmed.removePrefix("@").lowercase()
        return users.map { it.nick }.filter { it.lowercase().startsWith(needle) }.map { "@${it}" }
    }
    return emptyList()
}

private fun commandHelpText(input: String, hasChannel: Boolean): String? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("/")) return null
    return when {
        trimmed.startsWith("/search") -> "Search current buffer: /search keyword"
        trimmed.startsWith("/ignore") -> "Toggle ignores: /ignore +nick or /ignore remove nick"
        trimmed.startsWith("/query") -> "Open a DM buffer: /query nick [message]"
        trimmed.startsWith("/whois") -> "Whois lookup: /whois nick"
        trimmed.startsWith("/notice") -> "Send a notice: /notice <target> <text>"
        trimmed.startsWith("/invite") && hasChannel -> "Invite someone: /invite nick (channel optional)"
        trimmed.startsWith("/invite") && !hasChannel -> "Invite someone: /invite nick #channel"
        trimmed.startsWith("/away") -> "Set away or return: /away [message]"
        trimmed.startsWith("/topic") && hasChannel -> "Set channel topic: /topic new text"
        trimmed.startsWith("/topic") && !hasChannel -> "Join a channel to use /topic"
        else -> null
    }
}

private fun quietHoursChipLabel(enabled: Boolean, start: Int, end: Int): String {
    return if (!enabled) {
        "Quiet hours off"
    } else {
        "Quiet hours ${quietHoursWindowLabel(start, end)}"
    }
}

private fun quietHoursWindowLabel(start: Int, end: Int): String {
    val startNorm = start.coerceIn(0, 23)
    val endNorm = end.coerceIn(0, 23)
    return String.format("%02d:00-%02d:00", startNorm, endNorm)
}

private const val STATUS_CHANNEL_KEY = "_status"

private fun channelKeyOrStatus(name: String?): String = name?.takeIf { it.isNotBlank() }?.lowercase() ?: STATUS_CHANNEL_KEY

private fun eventChannelName(event: UiEvent, selfNick: String): String? = when (event) {
    is UiEvent.Chat -> when {
        event.target.startsWith("#") -> event.target
        event.target.equals(selfNick, ignoreCase = true) -> event.nick
        else -> event.target
    }
    is UiEvent.Notice -> {
        val target = event.target
        when {
            target.isNullOrBlank() -> null
            target.startsWith("#") -> target
            target.equals(selfNick, ignoreCase = true) -> event.nick
            else -> target
        }
    }
    is UiEvent.Join -> event.channel
    is UiEvent.Part -> event.channel
    is UiEvent.Kick -> event.channel
    is UiEvent.Topic -> event.channel
    is UiEvent.System -> event.target
    else -> null
}

private fun handleNickRename(
    event: UiEvent.Nick,
    activeChannel: String?,
    keyProvider: (String?) -> String,
    queries: SnapshotStateList<String>,
    channelEvents: MutableMap<String, SnapshotStateList<UiEvent>>,
    buffers: MutableMap<String, BufferMeta>,
    lastRead: MutableMap<String, Long>,
    muted: MutableMap<String, Boolean>,
    inputHistories: MutableMap<String, MutableList<String>>,
    inputHistoryPos: MutableMap<String, Int>,
    onActiveChange: (String?) -> Unit,
) {
    val oldName = event.oldNick
    val newName = event.newNick
    if (oldName.equals(newName, ignoreCase = true)) return
    val oldIndex = queries.indexOfFirst { it.equals(oldName, ignoreCase = true) }
    if (oldIndex < 0) return
    queries[oldIndex] = newName

    val oldKey = keyProvider(oldName)
    val newKey = keyProvider(newName)
    if (oldKey == newKey) return

    channelEvents[oldKey]?.let { log ->
        if (!channelEvents.containsKey(newKey)) channelEvents[newKey] = log else channelEvents.getValue(newKey).addAll(log)
        channelEvents.remove(oldKey)
    }
    lastRead[oldName]?.let { value -> lastRead[newName] = value; lastRead.remove(oldName) }
    buffers[oldKey]?.let { meta ->
        if (buffers.containsKey(newKey)) {
            val dest = buffers.getValue(newKey)
            dest.unread += meta.unread
            dest.highlight += meta.highlight
            if (dest.firstUnreadTime == null || (meta.firstUnreadTime ?: Long.MAX_VALUE) < (dest.firstUnreadTime ?: Long.MAX_VALUE)) {
                dest.firstUnreadTime = meta.firstUnreadTime
            }
        } else {
            buffers[newKey] = meta.apply { name = newName }
        }
        buffers.remove(oldKey)
    }
    muted[oldName]?.let { value -> muted[newName] = value; muted.remove(oldName) }
    inputHistories[oldName]?.let { history -> inputHistories[newName] = history; inputHistories.remove(oldName) }
    inputHistoryPos[oldName]?.let { pos -> inputHistoryPos[newName] = pos; inputHistoryPos.remove(oldName) }
    if (activeChannel != null && activeChannel.equals(oldName, ignoreCase = true)) {
        onActiveChange(newName)
    }
}

private fun handleSelfKick(
    event: UiEvent.Kick,
    channels: SnapshotStateList<String>,
    muted: MutableMap<String, Boolean>,
    inputHistories: MutableMap<String, MutableList<String>>,
    inputHistoryPos: MutableMap<String, Int>,
    activeChannel: String?,
    onActiveChange: (String?) -> Unit,
) {
    val channelName = event.channel
    val removed = mutableListOf<String>()
    for (i in channels.lastIndex downTo 0) {
        val name = channels[i]
        if (name.equals(channelName, ignoreCase = true)) {
            channels.removeAt(i)
            removed += name
        }
    }
    if (removed.isNotEmpty()) {
        removed.forEach { name ->
            muted.remove(name)
            inputHistories.remove(name)
            inputHistoryPos.remove(name)
        }
        if (activeChannel?.equals(channelName, ignoreCase = true) == true) {
            onActiveChange(null)
        }
    }
}
