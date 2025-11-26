package com.example.ircclient

import android.Manifest
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.ircclient.MentionBufferKind
import com.example.ircclient.MentionEntry
import com.example.ircclient.NetworkProfile
import com.example.ircclient.loadMentions
import com.example.ircclient.loadNetworkProfiles
import com.example.ircclient.saveMentions
import com.example.ircclient.saveNetworkProfiles
import com.example.ircclient.persistence.ChatDatabase
import com.example.ircclient.persistence.ScrollbackStore
import com.example.ircclient.ui.ConnectedTopBar
import com.example.ircclient.ui.theme.AppTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var scrollbackStore: ScrollbackStore
    private val boundClientState = mutableStateOf<IrcClient?>(null)
    private val sessionFlowState = mutableStateOf<StateFlow<Boolean>?>(null)
    private var connectionService: ConnectionService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ConnectionService.LocalBinder ?: return
            val svc = binder.getService()
            connectionService = svc
            boundClientState.value = svc.client
            sessionFlowState.value = svc.sessionActive
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connectionService = null
            boundClientState.value = null
            sessionFlowState.value = null
            serviceBound = false
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scrollbackStore = ScrollbackStore(ChatDatabase.getInstance(applicationContext).bufferEventDao())
        NotificationHelper.init(this)
        requestNotificationPermissionIfNeeded()
        ConnectionService.start(this)
        bindService(Intent(this, ConnectionService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        client = boundClientState.value,
                        scrollbackStore = scrollbackStore,
                        sessionState = sessionFlowState.value,
                        onConnectRequest = { config -> connectionService?.startSession(config) },
                        onDisconnectRequest = { connectionService?.stopSession() }
                    )
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
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

}

@Composable
private fun MainScreen(
    client: IrcClient?,
    scrollbackStore: ScrollbackStore,
    sessionState: StateFlow<Boolean>?,
    onConnectRequest: (IrcClient.Config) -> Unit,
    onDisconnectRequest: () -> Unit,
) {
    if (client == null || sessionState == null) {
        ServiceLoading()
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected by client.connected.collectAsState(initial = false)
    val echoEnabled by client.echoEnabled.collectAsState(initial = false)
    val users by client.users.collectAsState(initial = emptyList())
    val chathistoryEnabled by client.chathistoryEnabled.collectAsState(initial = false)
    val sessionActive by sessionState.collectAsState(initial = sessionState.value)

    var server by rememberSaveable { mutableStateOf("irc.libera.chat") }
    var portInput by rememberSaveable { mutableStateOf("6697") }
    var useTls by rememberSaveable { mutableStateOf(true) }
    var nick by rememberSaveable { mutableStateOf("AndroidUser") }
    var user by rememberSaveable { mutableStateOf("android") }
    var realName by rememberSaveable { mutableStateOf("Android IRC") }
    var primaryChannel by rememberSaveable { mutableStateOf("#android") }
    var extraChannels by rememberSaveable { mutableStateOf("") }
    var highlights by rememberSaveable { mutableStateOf("") }
    var highlightExceptions by rememberSaveable { mutableStateOf("") }
    var ignoreNicks by rememberSaveable { mutableStateOf("") }
    var saslAccount by rememberSaveable { mutableStateOf("") }
    var saslPassword by rememberSaveable { mutableStateOf("") }
    var stripColors by rememberSaveable { mutableStateOf(true) }
    var allowBackgrounds by rememberSaveable { mutableStateOf(false) }
    var fontScalePercent by rememberSaveable { mutableStateOf(100) }
    var quietHoursEnabled by rememberSaveable { mutableStateOf(false) }
    var quietHoursStart by rememberSaveable { mutableStateOf(23) }
    var quietHoursEnd by rememberSaveable { mutableStateOf(7) }

    var currentChannel by rememberSaveable { mutableStateOf<String?>(null) }
    var compactMode by rememberSaveable { mutableStateOf(false) }
    var outgoing by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var showJoin by rememberSaveable { mutableStateOf(false) }
    var showUsers by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSearchResults by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSuggestions by rememberSaveable { mutableStateOf(false) }

    val channelEvents = remember { mutableStateMapOf<String, SnapshotStateList<UiEvent>>() }
    val buffers = remember { mutableStateMapOf<String, BufferMeta>() }
    val joinedChannels = rememberSaveable(saver = snapshotStateListSaver<String>()) { mutableStateListOf<String>() }
    val queries = rememberSaveable(saver = snapshotStateListSaver<String>()) { mutableStateListOf<String>() }
    val muted = remember { mutableStateMapOf<String, Boolean>() }
    val requestedHistory = remember { mutableStateMapOf<String, Boolean>() }
    val lastRead = remember { mutableStateMapOf<String, Long>() }
    val inputHistories = remember { mutableStateMapOf<String, MutableList<String>>() }
    val inputHistoryPos = remember { mutableStateMapOf<String, Int>() }
    val searchResults = remember { mutableStateListOf<SearchHit>() }
    val suggestions = remember { mutableStateListOf<String>() }
    val ignoredNicksSet = remember(ignoreNicks) { parseIgnoreList(ignoreNicks) }
    val channelUsersMap = remember(users) { users.associateBy { it.nick.lowercase() } }
    val selfModeSymbol = remember(channelUsersMap, nick) { channelUsersMap[nick.lowercase()]?.mode }
    var pendingModerationTarget by remember { mutableStateOf<ModerationTarget?>(null) }
    val linkPreviewStates = remember { mutableStateMapOf<String, LinkPreviewState>() }

    val listState = rememberLazyListState()
    val bufferScroll = rememberScrollState()
    val moderationEnabled = currentChannel?.startsWith("#") == true
    val actionEnabled = remember(selfModeSymbol) { { action: ModerationAction -> canPerformModeration(action, selfModeSymbol) } }
    val mentions = remember { mutableStateListOf<MentionEntry>() }
    var showMentions by remember { mutableStateOf(false) }
    var pendingScrollTime by remember { mutableStateOf<Long?>(null) }
    val networkProfiles = remember { mutableStateListOf<NetworkProfile>() }
    var activeProfileId by remember { mutableStateOf<String?>(null) }
    var showProfileManager by remember { mutableStateOf(false) }
    var profileEditorState by remember { mutableStateOf<ProfileEditorState?>(null) }
    var profilePendingDelete by remember { mutableStateOf<NetworkProfile?>(null) }
    val highlightCounts by remember {
        derivedStateOf {
            mentions
                .filter { !it.dismissed }
                .groupingBy { mentionBufferKey(it) }
                .eachCount()
        }
    }

    LaunchedEffect(scrollbackStore) {
        val cached = scrollbackStore.loadAll(SCROLLBACK_SEED_LIMIT)
        cached.forEach { (bufferKey, events) ->
            if (events.isEmpty()) return@forEach
            val list = channelEvents.getOrPut(bufferKey) { mutableStateListOf() }
            if (list.isNotEmpty()) return@forEach
            list.addAll(events)
            val inferredName = bufferKey.takeUnless { it == STATUS_CHANNEL_KEY }
            val type = bufferTypeForKey(bufferKey)
            ensureBufferMeta(buffers, inferredName, type)
            if (type == BufferType.QUERY && !inferredName.isNullOrBlank() && queries.none { it.equals(inferredName, ignoreCase = true) }) {
                queries.add(inferredName)
                queries.sortBy { it.lowercase() }
            }
        }
    }

    LaunchedEffect(connected) {
        if (!connected) requestedHistory.clear()
    }

    LaunchedEffect(connected, chathistoryEnabled, joinedChannels.toList()) {
        if (!connected || !chathistoryEnabled) return@LaunchedEffect
        joinedChannels
            .filter { it.startsWith("#") && it.isNotBlank() }
            .forEach { channel ->
                val key = channelKeyOrStatus(channel)
                if (requestedHistory.containsKey(key)) return@forEach
                requestedHistory[key] = true
                val stateLast = channelEvents[key]?.lastOrNull()?.time
                val lastTimestamp = stateLast ?: scrollbackStore.latestTimestamp(key)
                val cursor = lastTimestamp?.let(::formatAsChathistoryCursor)
                client.requestChatHistory(channel, cursor, CHATHISTORY_FETCH_LIMIT)
            }
    }

    fun currentConfig(): SavedConfig = SavedConfig(
        server = server,
        port = portInput.toIntOrNull() ?: (if (useTls) 6697 else 6667),
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

    fun applyConfig(cfg: SavedConfig) {
        server = cfg.server
        portInput = cfg.port.toString()
        useTls = cfg.tls
        nick = cfg.nick
        user = cfg.user
        realName = cfg.realName
        primaryChannel = cfg.channel
        extraChannels = cfg.channels
        highlights = cfg.highlights
        highlightExceptions = cfg.highlightExceptions
        ignoreNicks = cfg.ignoreNicks
        saslAccount = cfg.saslAccount
        saslPassword = cfg.saslPassword
        stripColors = cfg.stripColors
        allowBackgrounds = cfg.allowBackgrounds
        fontScalePercent = cfg.fontScalePercent
        quietHoursEnabled = cfg.quietHoursEnabled
        quietHoursStart = cfg.quietHoursStart
        quietHoursEnd = cfg.quietHoursEnd
    }

    fun persistProfilesState(activeId: String? = activeProfileId) {
        scope.launch {
            saveNetworkProfiles(context, networkProfiles.toList(), activeId)
        }
    }

    fun updateActiveProfileConfig(cfg: SavedConfig) {
        val idx = networkProfiles.indexOfFirst { it.id == activeProfileId }
        if (idx >= 0) {
            val existing = networkProfiles[idx]
            if (existing.config != cfg) {
                networkProfiles[idx] = existing.copy(config = cfg)
            }
        }
        if (networkProfiles.isNotEmpty()) {
            persistProfilesState()
        }
    }

    fun persistConfig() {
        val cfg = currentConfig()
        scope.launch { saveConfig(context, cfg) }
        updateActiveProfileConfig(cfg)
    }

    fun activateProfile(profileId: String) {
        if (profileId == activeProfileId) return
        updateActiveProfileConfig(currentConfig())
        val target = networkProfiles.firstOrNull { it.id == profileId } ?: return
        activeProfileId = profileId
        applyConfig(target.config)
        scope.launch { saveConfig(context, target.config) }
        persistProfilesState(profileId)
    }

    fun createProfile(name: String) {
        val sanitized = name.trim().ifBlank { "Profile ${networkProfiles.size + 1}" }
        val profile = NetworkProfile(name = sanitized, config = currentConfig())
        networkProfiles.add(profile)
        activeProfileId = profile.id
        scope.launch { saveConfig(context, profile.config) }
        persistProfilesState(profile.id)
    }

    fun renameProfile(profileId: String, newName: String) {
        val idx = networkProfiles.indexOfFirst { it.id == profileId }
        if (idx < 0) return
        val sanitized = newName.trim().ifBlank { networkProfiles[idx].name }
        networkProfiles[idx] = networkProfiles[idx].copy(name = sanitized)
        persistProfilesState()
    }

    fun deleteProfile(profileId: String) {
        if (networkProfiles.size <= 1) return
        val idx = networkProfiles.indexOfFirst { it.id == profileId }
        if (idx < 0) return
        val wasActive = networkProfiles[idx].id == activeProfileId
        networkProfiles.removeAt(idx)
        if (networkProfiles.isEmpty()) {
            activeProfileId = null
            persistProfilesState(null)
            return
        }
        if (wasActive) {
            val next = networkProfiles.first()
            activeProfileId = next.id
            applyConfig(next.config)
            scope.launch { saveConfig(context, next.config) }
        }
        persistProfilesState(activeProfileId)
    }
    
    fun persistMentionsState() {
        scope.launch { saveMentions(context, mentions.take(MAX_MENTIONS)) }
    }
    
    fun markBufferRead(channelName: String?) {
        val bufferKey = channelKeyOrStatus(channelName)
        val events = channelEvents[bufferKey]
        val readKey = channelName ?: STATUS_CHANNEL_KEY
        val lastTimestamp = events?.lastOrNull()?.time ?: System.currentTimeMillis()
        lastRead[readKey] = lastTimestamp
        buffers[bufferKey]?.resetCounts()
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
        val insertIndex = list.lastIndex + 1
        list.add(event)
        scope.launch { scrollbackStore.append(key, event) }

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
                val mentionTarget = if (bufferType == BufferType.QUERY) event.nick else channelName ?: STATUS_CHANNEL_KEY
                val entry = MentionEntry(
                    source = event.nick,
                    bufferName = mentionTarget,
                    bufferKind = bufferType.toMentionKind(),
                    text = event.text,
                    time = event.time,
                    dismissed = false,
                )
                mentions.add(0, entry)
                if (mentions.size > MAX_MENTIONS) mentions.removeLast()
                persistMentionsState()
            }
            if (meta.firstUnreadTime == null) meta.firstUnreadTime = event.time
            if (meta.markerIndex == null) meta.markerIndex = insertIndex
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
        appendEvent(UiEvent.System(message, time = System.currentTimeMillis()))
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
            },
            emitStatus = ::emitStatus
        )
        outgoing = TextFieldValue("")
    }

    fun closeQueryBuffer(name: String) {
        val idx = queries.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (idx < 0) return
        val entry = queries[idx]
        queries.removeAt(idx)
        val key = channelKeyOrStatus(entry)
        buffers[key]?.resetCounts()
        if (currentChannel?.equals(entry, ignoreCase = true) == true) {
            currentChannel = null
        }
    }

    LaunchedEffect(Unit) {
        if (!channelEvents.containsKey(STATUS_CHANNEL_KEY)) {
            channelEvents[STATUS_CHANNEL_KEY] = mutableStateListOf()
            buffers[STATUS_CHANNEL_KEY] = BufferMeta("Status", BufferType.STATUS)
        }
        val storedProfiles = loadNetworkProfiles(context)
        if (storedProfiles.profiles.isNotEmpty()) {
            networkProfiles.clear()
            networkProfiles.addAll(storedProfiles.profiles)
            val target = storedProfiles.profiles.firstOrNull { it.id == storedProfiles.activeId } ?: storedProfiles.profiles.first()
            activeProfileId = target.id
            applyConfig(target.config)
            saveConfig(context, target.config)
        } else {
            val saved = loadSavedConfig(context)
            applyConfig(saved)
            val defaultProfile = NetworkProfile(name = "Default", config = saved)
            networkProfiles.add(defaultProfile)
            activeProfileId = defaultProfile.id
            saveNetworkProfiles(context, networkProfiles.toList(), defaultProfile.id)
        }

        val storedMentions = loadMentions(context)
        mentions.clear()
        mentions.addAll(storedMentions.take(MAX_MENTIONS))
    }

    var lastSessionActive by remember { mutableStateOf(sessionActive) }
    LaunchedEffect(sessionActive) {
        if (!sessionActive && lastSessionActive) {
            resetSessionState()
        }
        lastSessionActive = sessionActive
    }

    LaunchedEffect(client) {
        client.incoming.collectLatest { raw ->
            val event = parseIrcLine(raw) ?: UiEvent.System(raw, time = System.currentTimeMillis())
            appendEvent(event)
        }
    }

    val activeKey = channelKeyOrStatus(currentChannel)
    val activeEvents = channelEvents[activeKey] ?: emptyList()

    LaunchedEffect(activeKey, activeEvents.size, pendingScrollTime) {
        val channelName = currentChannel
        if (activeEvents.isEmpty()) {
            markBufferRead(channelName)
            pendingScrollTime = null
            return@LaunchedEffect
        }
        val targetTime = pendingScrollTime
        if (targetTime != null) {
            val targetIndex = activeEvents.indexOfFirst { it.time == targetTime }
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex)
            } else {
                listState.animateScrollToItem(activeEvents.lastIndex)
            }
            pendingScrollTime = null
        } else {
            listState.animateScrollToItem(activeEvents.lastIndex)
        }
        markBufferRead(channelName)
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
            profiles = networkProfiles,
            activeProfileId = activeProfileId,
            onProfileSelect = { id -> activateProfile(id) },
            onManageProfiles = { showProfileManager = true },
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
                onConnectRequest(
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
            linkPreviewStates = linkPreviewStates,
            highlightCounts = highlightCounts,
            mentionsCount = mentions.count { !it.dismissed },
            onMentions = { showMentions = true },
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
            onCloseBuffer = { closeQueryBuffer(it) },
            onJoinRequest = { showJoin = true },
            onDisconnect = {
                onDisconnectRequest()
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

    MentionsDialog(
        show = showMentions,
        mentions = mentions,
        onDismiss = { showMentions = false },
        onClear = {
            mentions.clear()
            persistMentionsState()
            showMentions = false
        },
        onJump = { entry ->
            entry.dismissed = true
            pendingScrollTime = entry.time
            when (entry.bufferKind) {
                MentionBufferKind.CHANNEL -> currentChannel = entry.bufferName
                MentionBufferKind.QUERY -> currentChannel = entry.bufferName
                MentionBufferKind.STATUS -> currentChannel = null
            }
            persistMentionsState()
            showMentions = false
        }
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

    ProfileManagerDialog(
        show = showProfileManager,
        profiles = networkProfiles,
        activeProfileId = activeProfileId,
        onDismiss = { showProfileManager = false },
        onSelect = { id ->
            activateProfile(id)
            showProfileManager = false
        },
        onRequestCreate = {
            profileEditorState = ProfileEditorState(ProfileDialogMode.CREATE, null, "")
        },
        onRequestRename = { profile ->
            profileEditorState = ProfileEditorState(ProfileDialogMode.RENAME, profile.id, profile.name)
        },
        onRequestDelete = { profile ->
            if (networkProfiles.size > 1) profilePendingDelete = profile
        }
    )

    ProfileEditorDialog(
        state = profileEditorState,
        onDismiss = { profileEditorState = null },
        onConfirm = { state, name ->
            when (state.mode) {
                ProfileDialogMode.CREATE -> createProfile(name)
                ProfileDialogMode.RENAME -> state.profileId?.let { renameProfile(it, name) }
            }
            profileEditorState = null
        }
    )

    ProfileDeleteDialog(
        profile = profilePendingDelete,
        onDismiss = { profilePendingDelete = null },
        onConfirm = { profile ->
            deleteProfile(profile.id)
            profilePendingDelete = null
        }
    )
}

@Composable
private fun ServiceLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ConnectionForm(
    profiles: List<NetworkProfile>,
    activeProfileId: String?,
    onProfileSelect: (String) -> Unit,
    onManageProfiles: () -> Unit,
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
        ProfileSelector(
            profiles = profiles,
            activeProfileId = activeProfileId,
            onProfileSelect = onProfileSelect,
            onManageProfiles = onManageProfiles,
        )
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
private fun ProfileSelector(
    profiles: List<NetworkProfile>,
    activeProfileId: String?,
    onProfileSelect: (String) -> Unit,
    onManageProfiles: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val activeProfile = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
    val activeName = activeProfile?.name ?: "Default"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Network profile", style = MaterialTheme.typography.labelSmall)
        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text(activeName, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (profiles.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No profiles yet") },
                    enabled = false,
                    onClick = {}
                )
            } else {
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = profile.name,
                                fontWeight = if (profile.id == activeProfileId) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onProfileSelect(profile.id)
                        }
                    )
                }
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text("Manage profiles…") },
                onClick = {
                    menuOpen = false
                    onManageProfiles()
                }
            )
        }
    }
}

@Composable
private fun SessionScreen(
    connected: Boolean,
    nick: String,
    linkPreviewStates: SnapshotStateMap<String, LinkPreviewState>,
    highlightCounts: Map<String, Int>,
    mentionsCount: Int,
    onMentions: () -> Unit,
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
    onCloseBuffer: (String) -> Unit,
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
                onDisconnect = onDisconnect,
                mentionsCount = mentionsCount,
                onMentions = onMentions
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
                val statusHighlights = highlightCounts[STATUS_CHANNEL_KEY] ?: 0
                BufferChip(
                    label = "Status",
                    unread = statusUnread,
                    highlight = statusHighlights,
                    selected = currentChannel == null,
                    onClick = { onChannelChange(null) }
                )
                joinedChannels.forEach { channel ->
                    val key = channelKeyOrStatus(channel)
                    val highlights = highlightCounts[key] ?: 0
                    BufferChip(
                        label = channel,
                        unread = buffers[key]?.unread ?: 0,
                        highlight = highlights,
                        selected = currentChannel?.equals(channel, ignoreCase = true) == true,
                        onClick = { onChannelChange(channel) }
                    )
                }
                queries.forEach { dm ->
                    val key = channelKeyOrStatus(dm)
                    val highlights = highlightCounts[key] ?: 0
                    BufferChip(
                        label = dm,
                        unread = buffers[key]?.unread ?: 0,
                        highlight = highlights,
                        selected = currentChannel?.equals(dm, ignoreCase = true) == true,
                        onClick = { onChannelChange(dm) },
                        closable = true,
                        onClose = { onCloseBuffer(dm) }
                    )
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
                    val meta = buffers[channelKeyOrStatus(currentChannel)]
                    val firstUnread = meta?.firstUnreadTime
                    val markerIdx = meta?.markerIndex
                    val shouldShowMarker = when {
                        markerIdx != null -> index == markerIdx
                        firstUnread != null -> (previous == null || previous.time < firstUnread) && event.time >= firstUnread
                        else -> false
                    }
                    if (shouldShowMarker) {
                        NewMessagesDivider()
                    }
                    when (event) {
                        is UiEvent.Chat -> ChatRow(event, nick, stripColors, allowBackgrounds, fontScale, compactMode, linkPreviewStates) { onChatLongPress(event) }
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
private fun BufferChip(
    label: String,
    unread: Int,
    highlight: Int,
    selected: Boolean,
    onClick: () -> Unit,
    closable: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label)
                if (unread > 0) {
                    CountBubble(
                        value = unread,
                        background = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (highlight > 0) {
                    CountBubble(
                        value = highlight,
                        background = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (closable && onClose != null) {
                    val interaction = remember { MutableInteractionSource() }
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close ${label}",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(
                                interactionSource = interaction,
                                indication = null
                            ) { onClose() }
                    )
                }
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun CountBubble(value: Int, background: Color, content: Color) {
    Surface(color = background, shape = MaterialTheme.shapes.small) {
        Text(
            value.coerceAtMost(99).toString(),
            color = content,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
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
private fun MentionsDialog(
    show: Boolean,
    mentions: List<MentionEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onJump: (MentionEntry) -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = {
                onClear()
            }) { Text("Clear") }
        },
        title = { Text("Mentions (${mentions.size})") },
        text = {
            if (mentions.isEmpty()) {
                Text("No highlights yet.")
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    itemsIndexed(mentions) { _, mention ->
                        MentionRow(entry = mention, onJump = onJump)
                    }
                }
            }
        }
    )
}

@Composable
private fun MentionRow(entry: MentionEntry, onJump: (MentionEntry) -> Unit) {
    val targetLabel = when (entry.bufferKind) {
        MentionBufferKind.STATUS -> "Status"
        else -> entry.bufferName
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onJump(entry) }
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = "${entry.source} in ${targetLabel}",
            style = MaterialTheme.typography.labelMedium
        )
        Text(entry.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            DateFormat.format("MMM d, HH:mm", entry.time).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
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
@OptIn(ExperimentalLayoutApi::class)
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
    val scrollState = rememberScrollState()
    val canIdentify = saslAccount.isNotBlank() && saslPassword.isNotBlank()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = onSave) { Text("Save") }
                }

                SettingsSection(
                    title = "Identity",
                    description = "These values are sent during login and do not change your visible nick.",
                ) {
                    OutlinedTextField(
                        value = user,
                        onValueChange = onUserChange,
                        label = { Text("Username (USER)") },
                        singleLine = true,
                        supportingText = { Text("Alphanumeric plus underscore, max 32 characters.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = realName,
                        onValueChange = onRealNameChange,
                        label = { Text("Real name") },
                        singleLine = true,
                        supportingText = { Text("Shown in WHOIS responses.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SettingsSection(
                    title = "Highlights & ignore list",
                    description = "Highlight rules drive notifications and the Mentions list.",
                ) {
                    OutlinedTextField(
                        value = highlights,
                        onValueChange = onHighlightsChange,
                        label = { Text("Highlight terms") },
                        placeholder = { Text("nick, release, urgent") },
                        supportingText = { Text("Comma/space separated words. Matches whole words, case-insensitive. Your nick is always included.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = highlightExceptions,
                        onValueChange = onHighlightsExceptionsChange,
                        label = { Text("Exceptions") },
                        supportingText = { Text("Use the same separators to mute matches (handy for bots).") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ignoreNicks,
                        onValueChange = onIgnoreChange,
                        label = { Text("Ignored nicks") },
                        supportingText = { Text("We skip highlights and notifications from these users. Separate with commas or spaces.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SettingsSection(
                    title = "NickServ & SASL",
                    description = "Stored locally and only sent when you tap Identify/Register.",
                ) {
                    OutlinedTextField(
                        value = saslAccount,
                        onValueChange = onSaslAccountChange,
                        label = { Text("Account name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = saslPassword,
                        onValueChange = onSaslPasswordChange,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val account = saslAccount.trim()
                                val pwd = saslPassword
                                if (account.isNotEmpty() && pwd.isNotEmpty()) {
                                    client.sendRaw("PRIVMSG NickServ :IDENTIFY ${'$'}account ${'$'}pwd")
                                }
                            },
                            enabled = canIdentify
                        ) { Text("Identify with NickServ") }
                        OutlinedButton(onClick = { showReg = true }) { Text("Register with NickServ") }
                    }
                    Text(
                        "We send the exact NickServ commands for you so there are no surprises.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SettingsSection(
                    title = "Connection & appearance",
                    description = "Tweak how incoming messages are rendered and whether TLS is required.",
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsToggle(
                            label = "Use TLS",
                            caption = "Encrypt the server connection when supported.",
                            checked = useTls,
                            onCheckedChange = onUseTlsChange
                        )
                        SettingsToggle(
                            label = "Strip IRC colors",
                            caption = "Great for readability-only themes.",
                            checked = stripColors,
                            onCheckedChange = onStripColorsChange
                        )
                        SettingsToggle(
                            label = "Allow background colors",
                            caption = "Render IRC background color codes.",
                            checked = allowBackgrounds,
                            onCheckedChange = onAllowBackgroundChange
                        )
                    }
                }

                SettingsSection(
                    title = "Quiet hours",
                    description = "Mute highlight notifications for a predictable window.",
                ) {
                    QuietHoursSection(
                        enabled = quietHoursEnabled,
                        startHour = quietHoursStart,
                        endHour = quietHoursEnd,
                        onEnabledChange = onQuietEnabledChange,
                        onStartChange = onQuietStartChange,
                        onEndChange = onQuietEndChange
                    )
                }

                SettingsSection(title = "Text size") {
                    Text("Chat text ${'$'}{fontScalePercent}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        modifier = Modifier.fillMaxWidth(),
                        value = fontScalePercent / 100f,
                        onValueChange = { value ->
                            onFontScaleChange((value.coerceIn(0.85f, 1.5f) * 100).toInt())
                        },
                        valueRange = 0.85f..1.5f,
                        steps = 5
                    )
                }
            }
        }
    }
    if (showReg) {
        val canRegister = regEmail.isNotBlank() && regPass.isNotBlank()
        AlertDialog(
            onDismissRequest = { showReg = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val email = regEmail.trim()
                        val pwd = regPass
                        if (email.isNotEmpty() && pwd.isNotEmpty()) {
                            client.sendRaw("PRIVMSG NickServ :REGISTER ${'$'}pwd ${'$'}email")
                            showReg = false
                        }
                    },
                    enabled = canRegister
                ) { Text("Register") }
            },
            dismissButton = { TextButton(onClick = { showReg = false }) { Text("Cancel") } },
            title = { Text("Register this nick") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = regEmail,
                        onValueChange = { regEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = regPass,
                        onValueChange = { regPass = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "We send: PRIVMSG NickServ :REGISTER <password> <email>.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    caption: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (!caption.isNullOrBlank()) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun <T> snapshotStateListSaver() = listSaver<SnapshotStateList<T>, T>(
    save = { it.toList() },
    restore = { restored -> mutableStateListOf<T>().apply { addAll(restored) } }
)

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun formatAsChathistoryCursor(epochMillis: Long): String =
    CHATHISTORY_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

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
    emitStatus: (String) -> Unit,
) {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return
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
            emitStatus("Requesting WHOIS for ${nickArg}")
        } else {
            emitStatus("Usage: /whois <nick>")
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
            emitStatus("Usage: /query <nick> [message]")
            return
        }
        val parts = rest.split(' ', limit = 2)
        val target = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: run {
            emitStatus("Usage: /query <nick> [message]")
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
            emitStatus(summary)
        } else {
            fun addNick(rawNick: String) {
                val normalized = normalizeNick(rawNick)
                if (normalized.isEmpty()) {
                    emitStatus("Usage: /ignore +nick")
                    return
                }
                val added = current.add(normalized)
                if (added) {
                    saveIgnores()
                    emitStatus("Ignoring ${normalized}")
                } else {
                    emitStatus("${normalized} already ignored")
                }
            }
            fun removeNick(rawNick: String) {
                val normalized = normalizeNick(rawNick)
                if (normalized.isEmpty()) {
                    emitStatus("Usage: /ignore -nick")
                    return
                }
                val removed = current.remove(normalized)
                if (removed) {
                    saveIgnores()
                    emitStatus("No longer ignoring ${normalized}")
                } else {
                    emitStatus("${normalized} was not ignored")
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
                        emitStatus("Usage: /ignore [+|-]nick | list")
                    } else if (current.contains(normalized)) {
                        current.remove(normalized)
                        saveIgnores()
                        emitStatus("No longer ignoring ${normalized}")
                    } else {
                        current.add(normalized)
                        saveIgnores()
                        emitStatus("Ignoring ${normalized}")
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
            emitStatus("Usage: /notice <target> <text>")
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
            emitStatus("Usage: /invite <nick> [#channel]")
        } else {
            client.sendRaw("INVITE ${targetNick} ${candidateChannel}")
            emitStatus("Invited ${targetNick} to ${candidateChannel}")
        }
        return
    }
    if (trimmed.startsWith("/away")) {
        val message = trimmed.removePrefix("/away").trim()
        if (message.isEmpty()) {
            client.sendRaw("AWAY")
            emitStatus("Cleared away status")
        } else {
            client.sendRaw("AWAY :${message}")
            emitStatus("Set away: ${message}")
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
            emitStatus("Join a channel or specify /topic #channel <text>")
            return
        }
        if (topicText.isNullOrBlank()) {
            client.sendRaw("TOPIC ${resolvedChannel}")
            emitStatus("Requested topic for ${resolvedChannel}")
        } else {
            client.sendRaw("TOPIC ${resolvedChannel} :${topicText}")
            emitStatus("Setting topic for ${resolvedChannel}")
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
    linkPreviewStates: SnapshotStateMap<String, LinkPreviewState>,
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
            val firstUrl = remember(ev.text) { extractFirstUrl(ev.text) }
            if (firstUrl != null) {
                LinkPreviewCard(firstUrl, linkPreviewStates)
            }
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
private fun ProfileManagerDialog(
    show: Boolean,
    profiles: List<NetworkProfile>,
    activeProfileId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRequestCreate: () -> Unit,
    onRequestRename: (NetworkProfile) -> Unit,
    onRequestDelete: (NetworkProfile) -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onRequestCreate) { Text("Add profile") } },
        title = { Text("Network profiles") },
        text = {
            if (profiles.isEmpty()) {
                Text("No saved profiles yet. Add one to store your server credentials and channel list.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    profiles.forEach { profile ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            tonalElevation = if (profile.id == activeProfileId) 1.dp else 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(profile.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        profile.name,
                                        fontWeight = if (profile.id == activeProfileId) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    if (profile.id == activeProfileId) {
                                        Text("Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(onClick = { onRequestRename(profile) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename profile")
                                }
                                IconButton(
                                    onClick = { onRequestDelete(profile) },
                                    enabled = profiles.size > 1
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete profile")
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ProfileEditorDialog(
    state: ProfileEditorState?,
    onDismiss: () -> Unit,
    onConfirm: (ProfileEditorState, String) -> Unit,
) {
    if (state == null) return
    var name by remember(state) { mutableStateOf(state.initialName) }
    val title = if (state.mode == ProfileDialogMode.CREATE) "Add profile" else "Rename profile"
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                if (trimmed.isNotEmpty()) onConfirm(state, trimmed)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Profile name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
private fun ProfileDeleteDialog(
    profile: NetworkProfile?,
    onDismiss: () -> Unit,
    onConfirm: (NetworkProfile) -> Unit,
) {
    if (profile == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(profile) }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Delete profile") },
        text = { Text("Remove \"${profile.name}\"? This only deletes the saved configuration.") }
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
private fun LinkPreviewCard(url: String, states: SnapshotStateMap<String, LinkPreviewState>) {
    val state = states[url]
    val status = state?.status ?: PreviewStatus.Idle

    LaunchedEffect(url, status) {
        val now = System.currentTimeMillis()
        val current = states[url]
        val shouldFetch = when {
            current == null -> true
            current.status == PreviewStatus.Idle -> true
            current.status == PreviewStatus.Failed && now - current.lastAttempt > PREVIEW_RETRY_MS -> true
            else -> false
        }
        if (shouldFetch) {
            states[url] = LinkPreviewState(status = PreviewStatus.Loading, lastAttempt = now)
            val preview = fetchLinkPreview(url)
            val refreshed = if (preview != null) {
                LinkPreviewState(status = PreviewStatus.Success, preview = preview, lastAttempt = System.currentTimeMillis())
            } else {
                LinkPreviewState(status = PreviewStatus.Failed, preview = null, lastAttempt = System.currentTimeMillis())
            }
            states[url] = refreshed
        }
    }

    when (status) {
        PreviewStatus.Success -> state?.preview?.let { PreviewContentCard(it) }
        PreviewStatus.Loading -> PreviewLoadingCard()
        PreviewStatus.Failed -> PreviewErrorCard { states[url] = LinkPreviewState(status = PreviewStatus.Idle) }
        PreviewStatus.Idle -> Unit
    }
}

@Composable
private fun PreviewContentCard(preview: LinkPreview) {
    val context = LocalContext.current
    val host = remember(preview.url) { Uri.parse(preview.url).host ?: preview.url }
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(preview.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            preview.title?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            preview.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            preview.siteName?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun PreviewLoadingCard() {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LinearProgressIndicator(modifier = Modifier.weight(1f))
            Text("Fetching preview…", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PreviewErrorCard(onRetry: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Preview unavailable", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
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

private data class LinkPreviewState(
    val status: PreviewStatus = PreviewStatus.Idle,
    val preview: LinkPreview? = null,
    val lastAttempt: Long = 0L,
)

private enum class PreviewStatus { Idle, Loading, Success, Failed }

private const val PREVIEW_RETRY_MS = 5 * 60 * 1000L

private val URL_REGEX = Regex("https?://[\\w._~:/?#@!$&'()*+,;=%-]+", RegexOption.IGNORE_CASE)
private val URL_TRAILING_CHARS = setOf('.', ',', ')', ']', '}', '>', '"', '\'', ':', ';', '…')

private data class ProfileEditorState(
    val mode: ProfileDialogMode,
    val profileId: String?,
    val initialName: String,
)

private enum class ProfileDialogMode { CREATE, RENAME }

private fun extractFirstUrl(text: String): String? {
    val match = URL_REGEX.find(text) ?: return null
    val sanitized = sanitizeUrlCandidate(match.value)
    return sanitized.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
}

private fun sanitizeUrlCandidate(candidate: String): String {
    var end = candidate.length
    while (end > 0 && URL_TRAILING_CHARS.contains(candidate[end - 1])) {
        end -= 1
    }
    return candidate.substring(0, end)
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

private fun BufferType.toMentionKind(): MentionBufferKind = when (this) {
    BufferType.STATUS -> MentionBufferKind.STATUS
    BufferType.CHANNEL -> MentionBufferKind.CHANNEL
    BufferType.QUERY -> MentionBufferKind.QUERY
}

private class BufferMeta(
    name: String,
    val type: BufferType,
    unread: Int = 0,
    highlight: Int = 0,
    firstUnread: Long? = null,
    markerEventIdx: Int? = null,
) {
    var name by mutableStateOf(name)
    var unread by mutableStateOf(unread)
    var highlight by mutableStateOf(highlight)
    var firstUnreadTime by mutableStateOf(firstUnread)
    var markerIndex by mutableStateOf(markerEventIdx)
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
    markerIndex = null
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
private const val MAX_MENTIONS = 100
private const val SCROLLBACK_SEED_LIMIT = 500
private const val CHATHISTORY_FETCH_LIMIT = 200

private val CHATHISTORY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

private fun channelKeyOrStatus(name: String?): String = name?.takeIf { it.isNotBlank() }?.lowercase() ?: STATUS_CHANNEL_KEY

private fun mentionBufferKey(entry: MentionEntry): String = when (entry.bufferKind) {
    MentionBufferKind.STATUS -> STATUS_CHANNEL_KEY
    MentionBufferKind.CHANNEL, MentionBufferKind.QUERY -> channelKeyOrStatus(entry.bufferName)
}

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

private fun bufferTypeForKey(key: String): BufferType = when {
    key == STATUS_CHANNEL_KEY -> BufferType.STATUS
    key.startsWith("#") -> BufferType.CHANNEL
    else -> BufferType.QUERY
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
