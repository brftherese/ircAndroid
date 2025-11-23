package com.example.ircclient

import java.time.Instant

data class IrcMessage(
    val raw: String,
    val tags: Map<String, String> = emptyMap(),
    val prefix: String? = null,
    val command: String = "",
    val params: List<String> = emptyList(),
    val trailing: String? = null,
)

object IrcParser {
    fun parse(line: String): IrcMessage? {
        var idx = 0
        val len = line.length
        var tags: Map<String, String> = emptyMap()
        var prefix: String? = null

        // IRCv3 message tags
        if (idx < len && line[idx] == '@') {
            val end = line.indexOf(' ', idx)
            if (end == -1) return null
            tags = parseTags(line.substring(idx + 1, end))
            idx = end + 1
        }

        // Optional prefix
        if (idx < len && line[idx] == ':') {
            val end = line.indexOf(' ', idx)
            if (end == -1) return null
            prefix = line.substring(idx + 1, end)
            idx = end + 1
        }

        // Skip extra spaces
        while (idx < len && line[idx] == ' ') idx++
        if (idx >= len) return null

        // Command
        val cmdStart = idx
        while (idx < len && line[idx] != ' ') idx++
        val command = line.substring(cmdStart, idx)

        // Params and trailing
        val params = mutableListOf<String>()
        var trailing: String? = null

        while (idx < len) {
            while (idx < len && line[idx] == ' ') idx++
            if (idx >= len) break
            if (line[idx] == ':') {
                trailing = line.substring(idx + 1)
                break
            }
            val pStart = idx
            while (idx < len && line[idx] != ' ') idx++
            params.add(line.substring(pStart, idx))
        }

        return IrcMessage(
            raw = line,
            tags = tags,
            prefix = prefix,
            command = command,
            params = params,
            trailing = trailing,
        )
    }

    private fun parseTags(tags: String): Map<String, String> {
        if (tags.isEmpty()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        val parts = tags.split(';')
        for (part in parts) {
            if (part.isEmpty()) continue
            val eq = part.indexOf('=')
            if (eq == -1) {
                map[part] = ""
            } else {
                val key = part.substring(0, eq)
                val value = unescapeTagValue(part.substring(eq + 1))
                map[key] = value
            }
        }
        return map
    }

    // https://ircv3.net/specs/extensions/message-tags.html#escaping-values
    private fun unescapeTagValue(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    ':' -> { sb.append(';'); i += 2; continue }
                    's' -> { sb.append(' '); i += 2; continue }
                    'r' -> { sb.append('\r'); i += 2; continue }
                    'n' -> { sb.append('\n'); i += 2; continue }
                    '\\' -> { sb.append('\\'); i += 2; continue }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    fun serverTimeMillis(msg: IrcMessage, defaultNow: Long = System.currentTimeMillis()): Long {
        val tag = msg.tags["time"] ?: return defaultNow
        return try { Instant.parse(tag).toEpochMilli() } catch (_: Throwable) { defaultNow }
    }

    fun nickFromPrefix(prefix: String?): String? {
        if (prefix == null) return null
        val bang = prefix.indexOf('!')
        return if (bang == -1) prefix else prefix.substring(0, bang)
    }
}
