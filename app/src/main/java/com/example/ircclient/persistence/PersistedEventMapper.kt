package com.example.ircclient.persistence

import com.example.ircclient.UiEvent
import org.json.JSONObject

/**
 * Serializes UiEvent instances into BufferEventEntity rows and restores them later.
 */
object PersistedEventMapper {
    private const val TYPE_CHAT = "chat"
    private const val TYPE_NOTICE = "notice"
    private const val TYPE_JOIN = "join"
    private const val TYPE_PART = "part"
    private const val TYPE_QUIT = "quit"
    private const val TYPE_KICK = "kick"
    private const val TYPE_NICK = "nick"
    private const val TYPE_TOPIC = "topic"
    private const val TYPE_SYSTEM = "system"

    fun toEntity(bufferKey: String, event: UiEvent): BufferEventEntity {
        val (type, payload) = when (event) {
            is UiEvent.Chat -> TYPE_CHAT to jsonObject(
                "nick" to event.nick,
                "target" to event.target,
                "text" to event.text
            )
            is UiEvent.Notice -> TYPE_NOTICE to jsonObject(
                "nick" to event.nick,
                "target" to event.target,
                "text" to event.text
            )
            is UiEvent.Join -> TYPE_JOIN to jsonObject(
                "nick" to event.nick,
                "channel" to event.channel
            )
            is UiEvent.Part -> TYPE_PART to jsonObject(
                "nick" to event.nick,
                "channel" to event.channel,
                "reason" to event.reason
            )
            is UiEvent.Quit -> TYPE_QUIT to jsonObject(
                "nick" to event.nick,
                "reason" to event.reason
            )
            is UiEvent.Kick -> TYPE_KICK to jsonObject(
                "by" to event.by,
                "channel" to event.channel,
                "target" to event.target,
                "reason" to event.reason
            )
            is UiEvent.Nick -> TYPE_NICK to jsonObject(
                "old" to event.oldNick,
                "new" to event.newNick
            )
            is UiEvent.Topic -> TYPE_TOPIC to jsonObject(
                "channel" to event.channel,
                "text" to event.text,
                "setter" to event.setter,
                "setAt" to event.setAtEpoch
            )
            is UiEvent.System -> TYPE_SYSTEM to jsonObject(
                "text" to event.text,
                "target" to event.target
            )
        }
        return BufferEventEntity(
            bufferKey = bufferKey,
            eventType = type,
            payload = payload.toString(),
            time = event.time
        )
    }

    fun toUiEvent(entity: BufferEventEntity): UiEvent? = runCatching {
        val payload = JSONObject(entity.payload)
        when (entity.eventType) {
            TYPE_CHAT -> UiEvent.Chat(
                nick = payload.getString("nick"),
                target = payload.getString("target"),
                text = payload.getString("text"),
                time = entity.time
            )
            TYPE_NOTICE -> UiEvent.Notice(
                nick = payload.optStringOrNull("nick"),
                target = payload.optStringOrNull("target"),
                text = payload.getString("text"),
                time = entity.time
            )
            TYPE_JOIN -> UiEvent.Join(
                nick = payload.getString("nick"),
                channel = payload.getString("channel"),
                time = entity.time
            )
            TYPE_PART -> UiEvent.Part(
                nick = payload.getString("nick"),
                channel = payload.getString("channel"),
                reason = payload.optStringOrNull("reason"),
                time = entity.time
            )
            TYPE_QUIT -> UiEvent.Quit(
                nick = payload.getString("nick"),
                reason = payload.optStringOrNull("reason"),
                time = entity.time
            )
            TYPE_KICK -> UiEvent.Kick(
                by = payload.optStringOrNull("by"),
                channel = payload.getString("channel"),
                target = payload.getString("target"),
                reason = payload.optStringOrNull("reason"),
                time = entity.time
            )
            TYPE_NICK -> UiEvent.Nick(
                oldNick = payload.getString("old"),
                newNick = payload.getString("new"),
                time = entity.time
            )
            TYPE_TOPIC -> UiEvent.Topic(
                channel = payload.getString("channel"),
                text = payload.getString("text"),
                setter = payload.optStringOrNull("setter"),
                setAtEpoch = payload.optLongOrNull("setAt"),
                time = entity.time
            )
            TYPE_SYSTEM -> UiEvent.System(
                text = payload.getString("text"),
                target = payload.optStringOrNull("target"),
                time = entity.time
            )
            else -> null
        }
    }.getOrNull()

    private fun jsonObject(vararg pairs: Pair<String, Any?>): JSONObject {
        val obj = JSONObject()
        pairs.forEach { (key, value) ->
            if (value != null) obj.put(key, value)
        }
        return obj
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null
}
