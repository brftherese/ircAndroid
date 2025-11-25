package com.example.ircclient

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.networkProfilesStore by preferencesDataStore("network_profiles")

private object NetworkProfilesPrefs {
    val ENTRIES = stringPreferencesKey("entries")
    val ACTIVE = stringPreferencesKey("active")
}

data class NetworkProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val config: SavedConfig,
)

data class NetworkProfilesState(
    val profiles: List<NetworkProfile> = emptyList(),
    val activeId: String? = null,
)

suspend fun loadNetworkProfiles(context: Context): NetworkProfilesState {
    val prefs = context.networkProfilesStore.data.firstOrNullSafe()
    if (prefs == null) return NetworkProfilesState()
    val raw = prefs[NetworkProfilesPrefs.ENTRIES] ?: return NetworkProfilesState()
    val profiles = parseProfiles(raw)
    val activeId = prefs[NetworkProfilesPrefs.ACTIVE]
    return NetworkProfilesState(profiles, activeId)
}

suspend fun saveNetworkProfiles(context: Context, profiles: List<NetworkProfile>, activeId: String?) {
    context.networkProfilesStore.edit { prefs ->
        if (profiles.isEmpty()) {
            prefs.remove(NetworkProfilesPrefs.ENTRIES)
            prefs.remove(NetworkProfilesPrefs.ACTIVE)
        } else {
            val arr = JSONArray()
            profiles.forEach { arr.put(it.toJson()) }
            prefs[NetworkProfilesPrefs.ENTRIES] = arr.toString()
            if (activeId != null) {
                prefs[NetworkProfilesPrefs.ACTIVE] = activeId
            } else {
                prefs.remove(NetworkProfilesPrefs.ACTIVE)
            }
        }
    }
}

private fun parseProfiles(raw: String): List<NetworkProfile> {
    val list = mutableListOf<NetworkProfile>()
    runCatching {
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val profile = obj.toProfile() ?: continue
            list += profile
        }
    }
    return list
}

private fun NetworkProfile.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("config", config.toJson())
}

private fun JSONObject.toProfile(): NetworkProfile? {
    val name = optString("name")
    if (name.isNullOrBlank()) return null
    val cfgObj = optJSONObject("config") ?: return null
    return NetworkProfile(
        id = optString("id").takeIf { !it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
        name = name,
        config = cfgObj.toConfig()
    )
}

private fun SavedConfig.toJson(): JSONObject = JSONObject().apply {
    put("server", server)
    put("port", port)
    put("tls", tls)
    put("nick", nick)
    put("user", user)
    put("realName", realName)
    put("channel", channel)
    put("channels", channels)
    put("highlights", highlights)
    put("highlightExceptions", highlightExceptions)
    put("ignoreNicks", ignoreNicks)
    put("saslAccount", saslAccount)
    put("saslPassword", saslPassword)
    put("stripColors", stripColors)
    put("allowBackgrounds", allowBackgrounds)
    put("fontScalePercent", fontScalePercent)
    put("quietHoursEnabled", quietHoursEnabled)
    put("quietHoursStart", quietHoursStart)
    put("quietHoursEnd", quietHoursEnd)
}

private fun JSONObject.toConfig(): SavedConfig = SavedConfig(
    server = optString("server", "irc.libera.chat"),
    port = optInt("port", 6697),
    tls = optBoolean("tls", true),
    nick = optString("nick", "AndroidUser"),
    user = optString("user", "AndroidUser"),
    realName = optString("realName", "Android IRC"),
    channel = optString("channel", "#android"),
    channels = optString("channels", ""),
    highlights = optString("highlights", ""),
    highlightExceptions = optString("highlightExceptions", ""),
    ignoreNicks = optString("ignoreNicks", ""),
    saslAccount = optString("saslAccount", ""),
    saslPassword = optString("saslPassword", ""),
    stripColors = optBoolean("stripColors", true),
    allowBackgrounds = optBoolean("allowBackgrounds", false),
    fontScalePercent = optInt("fontScalePercent", 100),
    quietHoursEnabled = optBoolean("quietHoursEnabled", false),
    quietHoursStart = optInt("quietHoursStart", 23),
    quietHoursEnd = optInt("quietHoursEnd", 7),
)

private suspend fun <T> Flow<T>.firstOrNullSafe(): T? = try {
    this.firstOrNull()
} catch (_: Throwable) {
    null
}
