package com.example.ircclient

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.InetSocketAddress
import android.net.Network
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class IrcClient(
    private val externalScope: CoroutineScope
) {
    private companion object {
        const val TAG = "IrcClient"
    }
    data class Config(
        val server: String,
        val port: Int = 6667,
        val useTls: Boolean = false,
        val nick: String,
        val user: String = "android",
        val realName: String = "Android IRC",
        val channel: String? = null,
        val requestCaps: Boolean = true,
        val saslAccount: String? = null,
        val saslPassword: String? = null,
    )

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var readJob: Job? = null
    private var autoJoinChannel: String? = null
    private var namesBuffer = mutableListOf<ChannelUser>()
    private val usersMap = mutableMapOf<String, Char?>()
    private var trackedChannel: String? = null
    private val defaultModeToSymbol = mapOf(
        'q' to '~', // owner
        'a' to '&', // admin
        'o' to '@', // op
        'h' to '%', // half-op
        'v' to '+'  // voice
    )
    private val modeToSymbol = mutableMapOf<Char, Char>().apply { putAll(defaultModeToSymbol) }
    private var wantCaps: MutableSet<String> = mutableSetOf()
    private var capsAcked: MutableSet<String> = mutableSetOf()
    private var negotiatingCaps = false
    private var saslPending = false

    private var lastConfig: Config? = null
    private var activeNetwork: Network? = null
    private var userInitiatedDisconnect = false
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val minBackoffMs = 1000L
    private val maxBackoffMs = 60000L
    private var networkAvailable = true
    private var nextForcedDelayMs: Long? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _echoEnabled = MutableStateFlow(false)
    val echoEnabled: StateFlow<Boolean> = _echoEnabled

    private val _users = MutableStateFlow<List<ChannelUser>>(emptyList())
    val users: StateFlow<List<ChannelUser>> = _users

    val incoming = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun connect(config: Config) {
        if (_connected.value) return
        userInitiatedDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        lastConfig = config
        readJob = externalScope.launch(Dispatchers.IO) {
            try {
                val connectMsg = "Connecting to ${config.server}:${config.port} TLS=${config.useTls}"
                Log.d(TAG, connectMsg)
                incoming.tryEmit(connectMsg)

                val connectTimeoutMs = 10000
                socket = if (config.useTls) {
                    val plain = Socket()
                    try { activeNetwork?.bindSocket(plain) } catch (_: Throwable) {}
                    plain.connect(InetSocketAddress(config.server, config.port), connectTimeoutMs)
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val ssl = factory.createSocket(plain, config.server, config.port, true) as SSLSocket
                    ssl.useClientMode = true
                    ssl.startHandshake()
                    ssl
                } else {
                    Socket().apply {
                        try { activeNetwork?.bindSocket(this) } catch (_: Throwable) {}
                        connect(InetSocketAddress(config.server, config.port), connectTimeoutMs)
                    }
                }
                Log.d(TAG, "Connected: local=${socket!!.localSocketAddress} remote=${socket!!.remoteSocketAddress}")
                writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

                negotiatingCaps = false
                capsAcked.clear()
                wantCaps.clear()
                _echoEnabled.value = false
                saslPending = false

                if (config.requestCaps) {
                    sendRaw("CAP LS 302")
                    negotiatingCaps = true
                }
                sendRaw("NICK ${config.nick}")
                sendRaw("USER ${config.user} 0 * :${config.realName}")

                autoJoinChannel = config.channel?.takeIf { it.isNotBlank() }
                trackedChannel = autoJoinChannel

                var line: String?
                while (true) {
                    line = reader?.readLine() ?: break
                    val l = line!!
                    Log.v(TAG, "<-- ${l}")
                    incoming.tryEmit(l)

                    // Detect common rate-limit messages and apply a long cooldown
                    val lower = l.lowercase()
                    if (lower.contains("too many connections") || lower.contains("excess flood")) {
                        val cool = 10 * 60 * 1000L // 10 minutes
                        nextForcedDelayMs = cool
                        incoming.tryEmit("Server rate-limited connections; retrying in 10m")
                        try { socket?.close() } catch (_: Throwable) {}
                        break
                    }

                    // Structured handling for CAP/SASL/PING
                    run {
                        val msg = IrcParser.parse(l) ?: return@run
                        when (msg.command.uppercase()) {
                            "PING" -> {
                                val token = msg.trailing ?: msg.params.firstOrNull() ?: ""
                                sendRaw("PONG ${token}")
                                // handled; skip the rest of loop body
                                return@run
                            }
                            // Consider connection established on welcome
                            "001" -> { _connected.value = true }
                            // RPL_ISUPPORT
                            "005" -> handleIsupport(msg)
                            // Channel/user mode changes
                            "MODE" -> handleMode(msg)
                            // Nick change
                            "NICK" -> handleNick(msg)
                            // KICK
                            "KICK" -> handleKick(msg)
                            "CAP" -> handleCap(msg, config)
                            "AUTHENTICATE" -> handleAuthenticate(msg, config)
                            // SASL result numerics
                            "903" -> { // success
                                saslPending = false
                                endCapIfReady()
                            }
                            "904", "905", "906", "907" -> {
                                saslPending = false
                                endCapIfReady()
                            }
                        }
                    }
                    // Parse basic NAMES list (353)
                    if (l.contains(" 353 ")) {
                        // Example: ":server 353 nick = #chan :nick1 nick2 +nick3 @nick4"
                        val names = l.substringAfter(" :", "").trim()
                        if (names.isNotEmpty()) {
                            names.split(' ').forEach { n ->
                                if (n.isNotBlank()) {
                                    val t = n.trim()
                                    val first = t.firstOrNull()
                                    val mode = if (first != null && first in charArrayOf('~','&','@','%','+')) first else null
                                    val nick = if (mode != null) t.drop(1) else t
                                    if (nick.isNotBlank()) namesBuffer.add(ChannelUser(nick, mode))
                                }
                            }
                        }
                    }
                    if (l.contains(" 366 ")) {
                        // End of NAMES list
                        usersMap.clear()
                        // Keep highest-privilege mode per nick
                        namesBuffer.forEach { cu ->
                            val existing = usersMap[cu.nick]
                            if (existing == null || modeRank(cu.mode) > modeRank(existing)) {
                                usersMap[cu.nick] = cu.mode
                            }
                        }
                        _users.value = usersMap.entries
                            .map { ChannelUser(it.key, it.value) }
                            .sortedWith(compareByDescending<ChannelUser> { modeRank(it.mode) }.thenBy { it.nick.lowercase() })
                        namesBuffer.clear()
                    }
                    // Track JOIN/PART/QUIT to keep user list fresh
                    if (l.contains(" JOIN ")) {
                        val nick = l.substringAfter(":").substringBefore("!").ifBlank { null }
                        if (nick != null) {
                            usersMap.putIfAbsent(nick, null)
                            _users.value = usersMap.entries
                                .map { ChannelUser(it.key, it.value) }
                                .sortedWith(compareByDescending<ChannelUser> { modeRank(it.mode) }.thenBy { it.nick.lowercase() })
                        }
                    }
                    if (l.contains(" PART ") || l.contains(" QUIT ")) {
                        val nick = l.substringAfter(":").substringBefore("!").ifBlank { null }
                        if (nick != null) {
                            usersMap.remove(nick)
                            _users.value = usersMap.entries
                                .map { ChannelUser(it.key, it.value) }
                                .sortedWith(compareByDescending<ChannelUser> { modeRank(it.mode) }.thenBy { it.nick.lowercase() })
                        }
                    }
                    // Join after welcome (001) or after end of MOTD (376), increases success rate
                    if ((l.contains(" 001 ") || l.contains(" 376 ")) && autoJoinChannel != null) {
                        val ch = autoJoinChannel
                        autoJoinChannel = null
                        if (ch != null) {
                            sendRaw("JOIN ${ch}")
                            trackedChannel = ch
                            sendRaw("NAMES ${ch}")
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Connection error", t)
                incoming.tryEmit("ERROR: ${t.message}")
            } finally {
                _connected.value = false
                try { reader?.close() } catch (_: Throwable) {}
                try { writer?.close() } catch (_: Throwable) {}
                try { socket?.close() } catch (_: Throwable) {}
                Log.d(TAG, "Disconnected")
                if (!userInitiatedDisconnect) {
                    val forced = nextForcedDelayMs
                    nextForcedDelayMs = null
                    scheduleReconnect(forcedDelayMs = forced)
                }
            }
        }
    }

    private fun handleIsupport(msg: IrcMessage) {
        // 005 <me> TOKEN... :are supported
        val tokens = (msg.params.drop(1) + listOfNotNull(msg.trailing)).flatMap { it.split(' ') }
        for (tok in tokens) {
            if (tok.startsWith("PREFIX=") || tok.startsWith("PREFIX")) {
                // Formats: PREFIX=(qaohv)~&@%+
                val spec = tok.substringAfter("PREFIX=").ifBlank { tok.substringAfter("PREFIX") }
                val modes = spec.substringAfter('(').substringBefore(')')
                val symbols = spec.substringAfter(')').takeIf { it.isNotEmpty() }
                if (modes.isNotEmpty() && symbols != null && modes.length == symbols.length) {
                    modeToSymbol.clear()
                    for (i in modes.indices) {
                        modeToSymbol[modes[i]] = symbols[i]
                    }
                }
            }
        }
    }

    private fun handleMode(msg: IrcMessage) {
        val target = msg.params.getOrNull(0) ?: return
        val ourChan = trackedChannel
        if (ourChan == null || !target.equals(ourChan, ignoreCase = true)) return

        val modes = msg.params.getOrNull(1) ?: return
        var adding = true
        var argIndex = 2
        for (ch in modes) {
            when (ch) {
                '+' -> adding = true
                '-' -> adding = false
                else -> {
                    // Status modes that affect users require a nickname argument
                    if (modeToSymbol.containsKey(ch)) {
                        val nick = msg.params.getOrNull(argIndex) ?: continue
                        argIndex++
                        applyUserModeChange(nick, ch, adding)
                    } else {
                        // Channel modes without args or with args we ignore here
                        // If they require args, they may shift argIndex; ignore for now
                    }
                }
            }
        }
        publishUsers()
    }

    private fun handleNick(msg: IrcMessage) {
        val oldNick = IrcParser.nickFromPrefix(msg.prefix) ?: return
        val newNick = msg.trailing ?: msg.params.getOrNull(0) ?: return
        if (usersMap.containsKey(oldNick)) {
            val sym = usersMap.remove(oldNick)
            usersMap[newNick] = sym
            publishUsers()
        }
    }

    private fun handleKick(msg: IrcMessage) {
        val chan = msg.params.getOrNull(0) ?: return
        val victim = msg.params.getOrNull(1) ?: return
        val ourChan = trackedChannel
        if (ourChan != null && chan.equals(ourChan, ignoreCase = true)) {
            if (usersMap.remove(victim) != null) publishUsers()
        }
    }

    private fun applyUserModeChange(nick: String, mode: Char, adding: Boolean) {
        val symbol = modeToSymbol[mode]
        if (adding) {
            if (symbol != null) {
                val current = usersMap[nick]
                if (current == null || modeRank(symbol) > modeRank(current)) {
                    usersMap[nick] = symbol
                }
            } else {
                usersMap.putIfAbsent(nick, null)
            }
        } else {
            // Removing; if we believe the current symbol matches, drop it
            val current = usersMap[nick]
            if (current != null && symbol != null && current == symbol) {
                usersMap[nick] = null
            }
        }
    }

    private fun publishUsers() {
        _users.value = usersMap.entries
            .map { ChannelUser(it.key, it.value) }
            .sortedWith(compareByDescending<ChannelUser> { modeRank(it.mode) }.thenBy { it.nick.lowercase() })
    }

    private fun handleCap(msg: IrcMessage, config: Config) {
        // CAP subcommands: LS, NEW, ACK, NAK, END
        val sub = msg.params.getOrNull(1)?.uppercase() ?: return
        when (sub) {
            "LS", "NEW" -> {
                val capList = (msg.trailing ?: "").split(' ').filter { it.isNotBlank() }
                if (capList.isEmpty()) return
                val offered = capList.map { it.substringBefore('=') }.toSet()
                // STS handling
                val stsToken = capList.firstOrNull { it.startsWith("sts=") }
                if (stsToken != null && !config.useTls) {
                    val port = parseStsPort(stsToken)
                    if (port != null && port in 1..65535) {
                        val newCfg = config.copy(port = port, useTls = true)
                        lastConfig = newCfg
                        // Force reconnect to apply TLS/port upgrade
                        try { socket?.close() } catch (_: Throwable) {}
                        return
                    }
                }
                val req = mutableListOf<String>()
                if ("echo-message" in offered) req += "echo-message"
                if ("server-time" in offered) req += "server-time"
                if (config.saslAccount != null && config.saslPassword != null && "sasl" in offered) req += "sasl"
                // Only request what we don't already have
                val toReq = req.filter { it !in capsAcked }
                if (toReq.isNotEmpty()) {
                    wantCaps.addAll(toReq)
                    sendRaw("CAP REQ :${toReq.joinToString(" ")}")
                } else {
                    endCapIfReady()
                }
            }
            "ACK" -> {
                val acked = (msg.trailing ?: "").split(' ').filter { it.isNotBlank() }
                capsAcked.addAll(acked)
                if (acked.any { it.equals("echo-message", true) }) {
                    _echoEnabled.value = true
                }
                if (acked.any { it.equals("sasl", true) }) {
                    // Start SASL PLAIN
                    if (config.saslAccount != null && config.saslPassword != null) {
                        saslPending = true
                        sendRaw("AUTHENTICATE PLAIN")
                    } else {
                        // If no creds, just end
                        endCapIfReady()
                    }
                } else {
                    endCapIfReady()
                }
            }
            "NAK" -> {
                endCapIfReady()
            }
        }
    }

    private fun parseStsPort(stsToken: String): Int? {
        // sts=duration=...,port=NNNN
        val params = stsToken.substringAfter("sts=")
        val items = params.split(',')
        for (item in items) {
            val kv = item.split('=')
            if (kv.size == 2 && kv[0] == "port") return kv[1].toIntOrNull()
        }
        return null
    }

    private fun handleAuthenticate(msg: IrcMessage, config: Config) {
        // Server prompts with '+'
        if (msg.trailing == "+" && saslPending && config.saslAccount != null && config.saslPassword != null) {
            val authzid = config.saslAccount
            val authcid = config.saslAccount
            val pass = config.saslPassword
            val payload = "$authzid\u0000$authcid\u0000$pass"
            val b64 = android.util.Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            sendRaw("AUTHENTICATE ${b64}")
        }
    }

    private fun endCapIfReady() {
        if (!negotiatingCaps) return
        // If we requested SASL, wait for its completion
        if (saslPending) return
        negotiatingCaps = false
        sendRaw("CAP END")
    }

    fun sendRaw(raw: String) {
        externalScope.launch(Dispatchers.IO) {
            try {
                Log.v(TAG, "--> ${raw}")
                writer?.apply {
                    write(raw)
                    write("\r\n")
                    flush()
                }
            } catch (_: Throwable) {}
        }
    }

    fun sendMessage(target: String, text: String) {
        sendRaw("PRIVMSG ${target} :${text}")
    }

    fun join(channel: String) = sendRaw("JOIN ${channel}")
    fun part(channel: String) = sendRaw("PART ${channel}")

    fun requestNames(channel: String) = sendRaw("NAMES ${channel}")

    fun quit(message: String = "Bye") {
        sendRaw("QUIT :${message}")
        disconnect()
    }

    fun disconnect() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            socket?.close()
        } catch (_: Throwable) {}
        try {
            reader?.close()
        } catch (_: Throwable) {}
        try {
            writer?.close()
        } catch (_: Throwable) {}
        readJob?.cancel()
        _connected.value = false
    }

    fun reconnectNow() {
        userInitiatedDisconnect = false
        reconnectAttempts = 0
        scheduleReconnect(immediate = true)
    }

    private fun scheduleReconnect(immediate: Boolean = false, forcedDelayMs: Long? = null) {
        val cfg = lastConfig ?: return
        if (userInitiatedDisconnect) return
        if (_connected.value) return
        reconnectJob?.cancel()
        reconnectJob = externalScope.launch(Dispatchers.IO) {
            val attempt = reconnectAttempts
            val baseDelay = (minBackoffMs shl attempt).coerceAtMost(maxBackoffMs)
            val jitter = (0..500).random().toLong()
            val computed = if (immediate) 0L else baseDelay + jitter
            val delayMs = forcedDelayMs ?: computed
            val msg = if (forcedDelayMs != null) "Reconnecting in ${delayMs / 1000}s (cooldown)…" else "Reconnecting in ${delayMs / 1000}s (attempt ${attempt + 1})…"
            incoming.tryEmit(msg)
            kotlinx.coroutines.delay(delayMs)
            if (userInitiatedDisconnect) return@launch
            if (!networkAvailable) {
                // Wait until network becomes available; poll with short delay
                while (!networkAvailable && !userInitiatedDisconnect) {
                    kotlinx.coroutines.delay(1000)
                }
                if (userInitiatedDisconnect) return@launch
            }
            reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(10)
            connect(cfg)
        }
    }

    fun onNetworkAvailable(available: Boolean) {
        networkAvailable = available
        if (available && !_connected.value) {
            // Reset backoff and try immediately
            reconnectAttempts = 0
            scheduleReconnect(immediate = true)
        }
    }

    fun setActiveNetwork(network: Network?) {
        activeNetwork = network
    }
}
