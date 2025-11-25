package com.example.ircclient

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the IRC socket alive even when the UI is backgrounded.
 */
class ConnectionService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP_SESSION = "com.example.ircclient.action.STOP_SESSION"

        fun start(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ConnectionService = this@ConnectionService
    }

    private val binder = LocalBinder()
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isForeground = false
    private var boundClients = 0
    private var currentConfig: IrcClient.Config? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive
    val client: IrcClient by lazy { IrcClient(serviceScope) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.init(this)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        registerNetworkCallback()
        serviceScope.launch {
            client.connected.collect { connected ->
                if (_sessionActive.value) updateNotification(connected)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SESSION) {
            stopSession(userRequested = false)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        boundClients += 1
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        boundClients = (boundClients - 1).coerceAtLeast(0)
        maybeStopSelf()
        return super.onUnbind(intent)
    }

    fun startSession(config: IrcClient.Config) {
        currentConfig = config
        _sessionActive.value = true
        ensureForeground()
        client.connect(config)
    }

    fun stopSession(userRequested: Boolean = true) {
        currentConfig = null
        _sessionActive.value = false
        client.disconnect()
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        if (userRequested) {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        }
        maybeStopSelf()
    }

    private fun ensureForeground() {
        updateNotification(client.connected.value)
    }

    private fun updateNotification(isConnected: Boolean) {
        val cfg = currentConfig ?: return
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ConnectionService::class.java).apply { action = ACTION_STOP_SESSION },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val title = if (isConnected) {
            "Connected to ${cfg.server}:${cfg.port}"
        } else {
            "Connecting to ${cfg.server}:${cfg.port}"
        }
        val text = if (isConnected) {
            "Tap to return to chat"
        } else {
            "Negotiating session"
        }
        val notification = NotificationHelper.buildSessionNotification(
            context = this,
            title = title,
            text = text,
            stopIntent = stopIntent,
            contentIntent = contentIntent
        )
        if (!isForeground) {
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
        } else {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

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

    private fun maybeStopSelf() {
        if (!_sessionActive.value && boundClients == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
        client.disconnect()
        serviceScope.cancel()
    }
}
