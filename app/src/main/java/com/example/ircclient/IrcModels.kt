package com.example.ircclient

sealed class UiEvent(open val time: Long) {
    data class Chat(val nick: String, val target: String, val text: String, override val time: Long): UiEvent(time)
    data class Notice(val nick: String?, val target: String?, val text: String, override val time: Long): UiEvent(time)
    data class Join(val nick: String, val channel: String, override val time: Long): UiEvent(time)
    data class Part(val nick: String, val channel: String, val reason: String?, override val time: Long): UiEvent(time)
    data class Quit(val nick: String, val reason: String?, override val time: Long): UiEvent(time)
    data class Kick(val by: String?, val channel: String, val target: String, val reason: String?, override val time: Long): UiEvent(time)
    data class Nick(val oldNick: String, val newNick: String, override val time: Long): UiEvent(time)
    data class Topic(val channel: String, val text: String, val setter: String? = null, val setAtEpoch: Long? = null, override val time: Long): UiEvent(time)
    data class System(val text: String, val target: String? = null, override val time: Long): UiEvent(time)
}

data class ChannelUser(val nick: String, val mode: Char?)

fun modeRank(mode: Char?): Int = when (mode) {
    '~' -> 5
    '&' -> 4
    '@' -> 3
    '%' -> 2
    '+' -> 1
    else -> 0
}

private val privmsg = Regex("^:([^! ]+)!.* PRIVMSG ([^ ]+) :(.*)$")
private val join = Regex("^:([^! ]+)!.* JOIN :?(.*)$")
private val part = Regex("^:([^! ]+)!.* PART ([^ ]+)(?: :(.*))?$")
private val quit = Regex("^:([^! ]+)!.* QUIT :?(.*)$")
private val notice = Regex("^:([^! ]+)!?[^ ]* NOTICE ([^ ]+) :(.*)$")
private val kick = Regex("^:([^! ]+)!.* KICK ([^ ]+) ([^ ]+)(?: :(.*))?$")
private val topic = Regex("^:([^! ]+)!.* TOPIC ([^ ]+) :(.*)$")
private val nickChange = Regex("^:([^! ]+)!.* NICK :(.*)$")
private val numeric = Regex("^:[^ ]+ (\\d{3}) .*$")

fun parseIrcLine(line: String): UiEvent? {
    if (line.startsWith("PING ")) return null
    if (line.contains(" 353 ") || line.contains(" 366 ")) return null

    // Prefer structured parsing first
    IrcParser.parse(line)?.let { m ->
        val whenMs = IrcParser.serverTimeMillis(m)
        val nick = IrcParser.nickFromPrefix(m.prefix) ?: ""
        when (m.command.uppercase()) {
            "PRIVMSG" -> {
                val target = m.params.getOrNull(0) ?: ""
                var text = m.trailing ?: ""
                // CTCP ACTION
                if (text.startsWith("\u0001ACTION ") && text.endsWith("\u0001")) {
                    text = text.removePrefix("\u0001ACTION ").removeSuffix("\u0001")
                }
                return UiEvent.Chat(nick, target, text, whenMs)
            }
            "NOTICE" -> {
                val target = m.params.getOrNull(0)
                val text = m.trailing ?: ""
                return UiEvent.Notice(nick.ifBlank { null }, target, text, whenMs)
            }
            "JOIN" -> {
                val ch = m.params.getOrNull(0) ?: (m.trailing ?: "")
                if (ch.isNotBlank()) return UiEvent.Join(nick, ch, whenMs)
            }
            "PART" -> {
                val ch = m.params.getOrNull(0) ?: ""
                val reason = m.trailing?.ifBlank { null }
                if (ch.isNotBlank()) return UiEvent.Part(nick, ch, reason, whenMs)
            }
            "QUIT" -> {
                val reason = m.trailing?.ifBlank { null }
                return UiEvent.Quit(nick, reason, whenMs)
            }
            "KICK" -> {
                val ch = m.params.getOrNull(0)
                val target = m.params.getOrNull(1)
                if (!ch.isNullOrBlank() && !target.isNullOrBlank()) {
                    val reason = m.trailing?.ifBlank { null }
                    val by = nick.ifBlank { null }
                    return UiEvent.Kick(by, ch, target, reason, whenMs)
                }
            }
            "NICK" -> {
                val oldNick = IrcParser.nickFromPrefix(m.prefix)?.ifBlank { null }
                val newNick = (m.trailing ?: m.params.getOrNull(0)).orEmpty()
                if (!oldNick.isNullOrBlank() && newNick.isNotBlank()) {
                    return UiEvent.Nick(oldNick, newNick, whenMs)
                }
            }
            "TOPIC" -> {
                val ch = m.params.getOrNull(0)
                if (!ch.isNullOrBlank()) {
                    val text = m.trailing ?: ""
                    val setter = nick.ifBlank { null }
                    return UiEvent.Topic(ch, text, setter = setter, setAtEpoch = whenMs, time = whenMs)
                }
            }
        }
        // Numeric replies
        if (m.command.length == 3 && m.command.all { it.isDigit() }) {
            return when (m.command) {
                // RPL_WELCOME: show the server-provided message
                "001" -> UiEvent.System(m.trailing ?: "Welcome", time = whenMs)
                // MOTD start/line/end
                "375" -> UiEvent.System(m.trailing ?: "- Message of the day -", time = whenMs)
                "372" -> UiEvent.System(m.trailing ?: "", time = whenMs)
                "376" -> UiEvent.System(m.trailing ?: "End of MOTD", time = whenMs)
                // No MOTD
                "422" -> UiEvent.System(m.trailing ?: "MOTD missing", time = whenMs)
                "332" -> {
                    val ch = m.params.getOrNull(1)
                    if (!ch.isNullOrBlank()) UiEvent.Topic(ch, m.trailing ?: "", time = whenMs) else null
                }
                "333" -> {
                    val ch = m.params.getOrNull(1)
                    if (!ch.isNullOrBlank()) {
                        val setter = m.params.getOrNull(2)
                        val ts = m.params.getOrNull(3)?.toLongOrNull()?.let { it * 1000 }
                        UiEvent.Topic(ch, text = "", setter = setter, setAtEpoch = ts, time = whenMs)
                    } else null
                }
                else -> null
            }
        }
    }

    // Fallback regex-based handling to be safe
    val now = System.currentTimeMillis()
    privmsg.matchEntire(line)?.let { m ->
        val nick = m.groupValues[1]
        val target = m.groupValues[2]
        val text = m.groupValues[3]
        return UiEvent.Chat(nick, target, text, now)
    }
    join.matchEntire(line)?.let { m ->
        val nick = m.groupValues[1]
        val ch = m.groupValues[2]
        return UiEvent.Join(nick, ch, now)
    }
    part.matchEntire(line)?.let { m ->
        val nick = m.groupValues[1]
        val ch = m.groupValues[2]
        val reason = m.groupValues.getOrNull(3)?.ifBlank { null }
        return UiEvent.Part(nick, ch, reason, now)
    }
    quit.matchEntire(line)?.let { m ->
        val nick = m.groupValues[1]
        val reason = m.groupValues.getOrNull(2)?.ifBlank { null }
        return UiEvent.Quit(nick, reason, now)
    }
    kick.matchEntire(line)?.let { m ->
        val by = m.groupValues[1].ifBlank { null }
        val ch = m.groupValues[2]
        val target = m.groupValues[3]
        val reason = m.groupValues.getOrNull(4)?.ifBlank { null }
        return UiEvent.Kick(by, ch, target, reason, now)
    }
    topic.matchEntire(line)?.let { m ->
        val setter = m.groupValues[1].ifBlank { null }
        val ch = m.groupValues[2]
        val text = m.groupValues[3]
        return UiEvent.Topic(ch, text, setter = setter, setAtEpoch = now, time = now)
    }
    nickChange.matchEntire(line)?.let { m ->
        val oldNick = m.groupValues[1]
        val newNick = m.groupValues[2]
        if (oldNick.isNotBlank() && newNick.isNotBlank()) {
            return UiEvent.Nick(oldNick, newNick, now)
        }
    }
    notice.matchEntire(line)?.let { m ->
        val nick = m.groupValues[1].ifBlank { null }
        val target = m.groupValues[2].ifBlank { null }
        val text = m.groupValues[3]
        return UiEvent.Notice(nick, target, text, now)
    }
    numeric.matchEntire(line)?.let { m ->
        val code = m.groupValues[1]
        return when (code) {
            "001" -> UiEvent.System("Welcome", time = now)
            // WHOIS snippets
            "311", "312", "317", "319" -> UiEvent.System(line.substringAfter(" :", line), time = now)
            "318" -> UiEvent.System("End of WHOIS", time = now)
            "372" -> UiEvent.System(line.substringAfter(" :", ""), time = now)
            "375" -> UiEvent.System(line.substringAfter(" :", "- Message of the day -"), time = now)
            "376" -> UiEvent.System(line.substringAfter(" :", "End of MOTD"), time = now)
            "422" -> UiEvent.System(line.substringAfter(" :", "MOTD missing"), time = now)
            else -> null
        }
    }
    return UiEvent.System(line, time = now)
}
