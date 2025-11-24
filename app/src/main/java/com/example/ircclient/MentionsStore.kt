package com.example.ircclient

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject

private val Context.mentionsStore by preferencesDataStore("mentions_store")

private object MentionPrefs {
    val ENTRIES = stringPreferencesKey("entries")
}

enum class MentionBufferKind { STATUS, CHANNEL, QUERY }

data class MentionEntry(
    val source: String,
    val bufferName: String,
    val bufferKind: MentionBufferKind,
    val text: String,
    val time: Long,
    var dismissed: Boolean = false,
)

suspend fun loadMentions(context: Context): List<MentionEntry> {
    val prefs = context.mentionsStore.data.firstOrNullSafe() ?: return emptyList()
    val blob = prefs[MentionPrefs.ENTRIES] ?: return emptyList()
    return parseMentions(blob)
}

suspend fun saveMentions(context: Context, entries: List<MentionEntry>) {
    context.mentionsStore.edit { prefs ->
        if (entries.isEmpty()) {
            prefs.remove(MentionPrefs.ENTRIES)
        } else {
            val arr = JSONArray()
            entries.forEach { entry -> arr.put(entry.toJson()) }
            prefs[MentionPrefs.ENTRIES] = arr.toString()
        }
    }
}

private fun MentionEntry.toJson(): JSONObject = JSONObject().apply {
    put("source", source)
    put("bufferName", bufferName)
    put("bufferKind", bufferKind.name)
    put("text", text)
    put("time", time)
    put("dismissed", dismissed)
}

private fun parseMentions(raw: String): List<MentionEntry> {
    val results = mutableListOf<MentionEntry>()
    runCatching {
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val source = obj.optString("source")
            val bufferName = obj.optString("bufferName")
            val kindName = obj.optString("bufferKind")
            val text = obj.optString("text")
            val time = obj.optLong("time")
            val kind = runCatching { MentionBufferKind.valueOf(kindName) }.getOrDefault(MentionBufferKind.CHANNEL)
            val dismissed = obj.optBoolean("dismissed", false)
            if (source.isNotEmpty() && bufferName.isNotEmpty() && text.isNotEmpty()) {
                results.add(MentionEntry(source, bufferName, kind, text, time, dismissed))
            }
        }
    }
    return results
}

private suspend fun <T> Flow<T>.firstOrNullSafe(): T? = try {
    this.firstOrNull()
} catch (_: Throwable) {
    null
}
