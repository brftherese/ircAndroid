package com.example.ircclient

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

// Parse IRC/mIRC formatting codes into an AnnotatedString with styles applied.
// Supports: bold(0x02), italic(0x1D), underline(0x1F), strikethrough(0x1E), inverse(0x16), reset(0x0F),
// colors via 0x03 (NN[,MM]) and hex colors via 0x04 (RRGGBB[,RRGGBB]).
fun formatIrcAnnotated(
    input: String,
    stripColors: Boolean = false,
    allowBackgrounds: Boolean = true,
): AnnotatedString {
    val b = AnnotatedString.Builder()

    data class State(
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var strike: Boolean = false,
        var inverse: Boolean = false,
        var fg: Color? = null,
        var bg: Color? = null,
    )

    val state = State()

    fun currentStyle(): SpanStyle {
        val fg = if (stripColors) Color.Unspecified else state.fg ?: Color.Unspecified
        val bgBase = if (stripColors || !allowBackgrounds) Color.Unspecified else state.bg ?: Color.Unspecified
        val bg = bgBase
        val color = if (state.inverse && bg != Color.Unspecified) bg else fg
        val background = if (!allowBackgrounds) Color.Unspecified else if (state.inverse && color != Color.Unspecified) (state.fg ?: Color.Unspecified) else bg
        return SpanStyle(
            color = color,
            background = background,
            fontWeight = if (state.bold) FontWeight.SemiBold else null,
            fontStyle = if (state.italic) FontStyle.Italic else null,
            textDecoration = when {
                state.underline && state.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                state.underline -> TextDecoration.Underline
                state.strike -> TextDecoration.LineThrough
                else -> null
            }
        )
    }

    var i = 0
    var openAt = 0
    var openStyle = currentStyle()
    fun flush(until: Int) {
        if (until > openAt) {
            b.pushStyle(openStyle)
            b.append(input.substring(openAt, until))
            b.pop()
        }
        openAt = until
        openStyle = currentStyle()
    }

    while (i < input.length) {
        val ch = input[i]
        when (ch) {
            '\u0002' -> { // bold
                flush(i)
                state.bold = !state.bold
                i++
                openAt = i
                openStyle = currentStyle()
            }
            '\u001D' -> { // italic
                flush(i)
                state.italic = !state.italic
                i++
                openAt = i
                openStyle = currentStyle()
            }
            '\u001F' -> { // underline
                flush(i)
                state.underline = !state.underline
                i++
                openAt = i
                openStyle = currentStyle()
            }
            '\u001E' -> { // strikethrough (rare)
                flush(i)
                state.strike = !state.strike
                i++
                openAt = i
                openStyle = currentStyle()
            }
            '\u0016' -> { // inverse
                flush(i)
                state.inverse = !state.inverse
                i++
                openAt = i
                openStyle = currentStyle()
            }
            '\u000F' -> { // reset
                flush(i)
                state.bold = false
                state.italic = false
                state.underline = false
                state.strike = false
                state.inverse = false
                state.fg = null
                state.bg = null
                i++
                openAt = i
                openStyle = currentStyle()
            }
            '\u0003' -> { // mIRC color NN[,MM]
                flush(i)
                i++
                // If no digits after ^C, reset colors
                fun readNumber(): Int? {
                    val d0 = if (i < input.length && input[i].isDigit()) input[i++] else return null
                    var value = d0 - '0'
                    if (i < input.length && input[i].isDigit()) {
                        value = value * 10 + (input[i] - '0')
                        i++
                    }
                    return value
                }
                val fgNum = readNumber()
                val hasComma = i < input.length && input[i] == ','
                val bgNum = if (hasComma) { i++; readNumber() } else null
                if (fgNum == null && !hasComma) {
                    // ^C with no args => reset colors
                    state.fg = null; state.bg = null
                } else {
                    if (!stripColors) {
                        state.fg = fgNum?.let { mircPalette(it) }
                        if (hasComma) state.bg = bgNum?.let { mircPalette(it) }
                    } else {
                        state.fg = null; state.bg = null
                    }
                }
                // Do not append the consumed digits
                openAt = i
                openStyle = currentStyle()
            }
            '\u0004' -> { // hex color RRGGBB[,RRGGBB]
                flush(i)
                i++
                fun readHex(n: Int): String? {
                    if (i + n > input.length) return null
                    val s = input.substring(i, i + n)
                    if (!s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
                    i += n
                    return s
                }
                val fgHex = readHex(6)
                val hasComma = i < input.length && input[i] == ','
                val bgHex = if (hasComma) { i++; readHex(6) } else null
                if (fgHex == null && !hasComma) {
                    state.fg = null; state.bg = null
                } else {
                    if (!stripColors) {
                        state.fg = fgHex?.let { Color(java.lang.Long.parseLong(it, 16).toInt() or 0xFF000000.toInt()) }
                        if (hasComma) state.bg = bgHex?.let { Color(java.lang.Long.parseLong(it, 16).toInt() or 0xFF000000.toInt()) }
                    } else {
                        state.fg = null; state.bg = null
                    }
                }
                openAt = i
                openStyle = currentStyle()
            }
            else -> {
                i++
            }
        }
    }
    // Append the rest
    flush(input.length)

    // Add structured link annotations for clickable links
    val text = b.toString()
    for (range in findLinks(text)) {
        val normalized = normalizeLinkUrl(range.url)
        b.addLink(
            LinkAnnotation.Url(normalized),
            range.start,
            range.end
        )
    }
    return b.toAnnotatedString()
}

private fun mircPalette(n: Int): Color {
    // mIRC 16-color palette
    val idx = ((n % 100) + 100) % 100 // guard
    val table = arrayOf(
        0xFFFFFF, // 0 white
        0x000000, // 1 black
        0x00007F, // 2 blue (navy)
        0x009300, // 3 green
        0xFF0000, // 4 red
        0x7F0000, // 5 brown (maroon)
        0x9C009C, // 6 purple
        0xFC7F00, // 7 orange (olive)
        0xFFFF00, // 8 yellow
        0x00FC00, // 9 light green (lime)
        0x009393, // 10 teal (cyan)
        0x00FFFF, // 11 light cyan (aqua)
        0x0000FC, // 12 light blue (royal)
        0xFF00FF, // 13 pink (light purple, fuchsia)
        0x7F7F7F, // 14 grey
        0xD2D2D2  // 15 light grey (silver)
    )
    val rgb = if (idx in 0..15) table[idx] else table[idx % 16]
    return Color((0xFF shl 24) or rgb)
}

private data class LinkRange(val start: Int, val end: Int, val url: String)

private fun findLinks(text: String): List<LinkRange> {
    // Simple, pragmatic URL matcher; handles http/https/irc/ircs/ftp/file/mailto and www.
    val pattern = Pattern.compile("""(?i)\b((?:https?://|irc://|ircs://|ftp://|file://|mailto:|www\.)[^\s<>]+)""")
    val m = pattern.matcher(text)
    val out = ArrayList<LinkRange>()
    while (m.find()) {
        var s = m.start(1)
        var e = m.end(1)
        var url = text.substring(s, e)
        // Trim trailing punctuation that often attaches to URLs
        while (e > s && ",.;:)]!?".indexOf(text[e - 1]) >= 0) {
            e--
            url = text.substring(s, e)
        }
        out.add(LinkRange(s, e, url))
    }
    return out
}

private fun normalizeLinkUrl(raw: String): String {
    return when {
        raw.startsWith("http", true) -> raw
        raw.startsWith("irc://", true) || raw.startsWith("ircs://", true) -> raw
        raw.startsWith("ftp://", true) || raw.startsWith("file://", true) || raw.startsWith("mailto:", true) -> raw
        raw.startsWith("www.", true) -> "http://${raw}"
        else -> raw
    }
}
