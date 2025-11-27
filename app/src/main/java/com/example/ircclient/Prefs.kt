package com.example.ircclient

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

private val Context.dataStore by preferencesDataStore("settings")

object PrefsKeys {
    val SERVER = stringPreferencesKey("server")
    val PORT = intPreferencesKey("port")
    val TLS = booleanPreferencesKey("tls")
    val NICK = stringPreferencesKey("nick")
    val USER = stringPreferencesKey("user")
    val REALNAME = stringPreferencesKey("realname")
    val CHANNEL = stringPreferencesKey("channel")
    val CHANNELS = stringPreferencesKey("channels")
    val HIGHLIGHTS = stringPreferencesKey("highlights")
    val HIGHLIGHT_EXCEPTIONS = stringPreferencesKey("highlight_exceptions")
    val IGNORE_NICKS = stringPreferencesKey("ignore_nicks")
    val SASL_ACCOUNT = stringPreferencesKey("sasl_account")
    val SASL_PASSWORD = stringPreferencesKey("sasl_password")
    val STRIP_COLORS = booleanPreferencesKey("strip_colors")
    val ALLOW_BACKGROUNDS = booleanPreferencesKey("allow_backgrounds")
    val LINK_PREVIEWS_ENABLED = booleanPreferencesKey("link_previews_enabled")
    val FONT_SCALE = intPreferencesKey("font_scale_percent")
    val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
    val QUIET_HOURS_START = intPreferencesKey("quiet_hours_start")
    val QUIET_HOURS_END = intPreferencesKey("quiet_hours_end")
    val FORCE_LIGHT_THEME = booleanPreferencesKey("force_light_theme")
}

data class SavedConfig(
    val server: String = "irc.libera.chat",
    val port: Int = 6697,
    val tls: Boolean = true,
    val nick: String = "AndroidUser",
    val user: String = "AndroidUser",
    val realName: String = "Android IRC",
    val channel: String = "#android",
    val channels: String = "",
    val highlights: String = "",
    val highlightExceptions: String = "",
    val ignoreNicks: String = "",
    val saslAccount: String = "",
    val saslPassword: String = "",
    val stripColors: Boolean = true,
    val allowBackgrounds: Boolean = false,
    val linkPreviewsEnabled: Boolean = true,
    val fontScalePercent: Int = 100,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 23,
    val quietHoursEnd: Int = 7,
    val forceLightTheme: Boolean = false,
)

suspend fun loadSavedConfig(context: Context): SavedConfig {
    val prefs = context.dataStore.data.firstOrNullSafe()
    if (prefs == null) return SavedConfig()
    return SavedConfig(
        server = prefs[PrefsKeys.SERVER] ?: "irc.libera.chat",
        port = prefs[PrefsKeys.PORT] ?: 6697,
        tls = prefs[PrefsKeys.TLS] ?: true,
        nick = prefs[PrefsKeys.NICK] ?: "AndroidUser",
        user = prefs[PrefsKeys.USER] ?: "AndroidUser",
        realName = prefs[PrefsKeys.REALNAME] ?: "Android IRC",
        channel = prefs[PrefsKeys.CHANNEL] ?: "#android",
        channels = prefs[PrefsKeys.CHANNELS] ?: "",
        highlights = prefs[PrefsKeys.HIGHLIGHTS] ?: "",
        highlightExceptions = prefs[PrefsKeys.HIGHLIGHT_EXCEPTIONS] ?: "",
        ignoreNicks = prefs[PrefsKeys.IGNORE_NICKS] ?: "",
        saslAccount = prefs[PrefsKeys.SASL_ACCOUNT] ?: "",
        saslPassword = prefs[PrefsKeys.SASL_PASSWORD] ?: "",
        stripColors = prefs[PrefsKeys.STRIP_COLORS] ?: true,
        allowBackgrounds = prefs[PrefsKeys.ALLOW_BACKGROUNDS] ?: false,
        linkPreviewsEnabled = prefs[PrefsKeys.LINK_PREVIEWS_ENABLED] ?: true,
        fontScalePercent = prefs[PrefsKeys.FONT_SCALE] ?: 100,
        quietHoursEnabled = prefs[PrefsKeys.QUIET_HOURS_ENABLED] ?: false,
        quietHoursStart = prefs[PrefsKeys.QUIET_HOURS_START] ?: 23,
        quietHoursEnd = prefs[PrefsKeys.QUIET_HOURS_END] ?: 7,
        forceLightTheme = prefs[PrefsKeys.FORCE_LIGHT_THEME] ?: false,
    )
}

suspend fun saveConfig(context: Context, cfg: SavedConfig) {
    context.dataStore.edit { e ->
        e[PrefsKeys.SERVER] = cfg.server
        e[PrefsKeys.PORT] = cfg.port
        e[PrefsKeys.TLS] = cfg.tls
        e[PrefsKeys.NICK] = cfg.nick
        e[PrefsKeys.USER] = cfg.user
        e[PrefsKeys.REALNAME] = cfg.realName
        e[PrefsKeys.CHANNEL] = cfg.channel
        e[PrefsKeys.CHANNELS] = cfg.channels
        e[PrefsKeys.HIGHLIGHTS] = cfg.highlights
        e[PrefsKeys.HIGHLIGHT_EXCEPTIONS] = cfg.highlightExceptions
        e[PrefsKeys.IGNORE_NICKS] = cfg.ignoreNicks
        e[PrefsKeys.SASL_ACCOUNT] = cfg.saslAccount
        e[PrefsKeys.SASL_PASSWORD] = cfg.saslPassword
        e[PrefsKeys.STRIP_COLORS] = cfg.stripColors
        e[PrefsKeys.ALLOW_BACKGROUNDS] = cfg.allowBackgrounds
        e[PrefsKeys.LINK_PREVIEWS_ENABLED] = cfg.linkPreviewsEnabled
        e[PrefsKeys.FONT_SCALE] = cfg.fontScalePercent
        e[PrefsKeys.QUIET_HOURS_ENABLED] = cfg.quietHoursEnabled
        e[PrefsKeys.QUIET_HOURS_START] = cfg.quietHoursStart
        e[PrefsKeys.QUIET_HOURS_END] = cfg.quietHoursEnd
        e[PrefsKeys.FORCE_LIGHT_THEME] = cfg.forceLightTheme
    }
}

// small helper to avoid requiring kotlinx-coroutines reactive import here
private suspend fun <T> Flow<T>.firstOrNullSafe(): T? =
    try { this.firstOrNull() } catch (_: Throwable) { null }
