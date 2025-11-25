package com.example.ircclient

import android.net.Uri
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.Integer.min
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.Locale

private const val PREVIEW_MAX_BYTES = 150_000
private const val PREVIEW_TIMEOUT_MS = 5_000
private val PREVIEW_ACCEPT_TYPES = listOf("text/html", "application/xhtml")

data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val siteName: String?,
    val imageUrl: String?,
)

suspend fun fetchLinkPreview(url: String): LinkPreview? = withContext(Dispatchers.IO) {
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri?.scheme?.lowercase(Locale.US) !in listOf("http", "https")) return@withContext null
    runCatching {
        val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return@withContext null
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = PREVIEW_TIMEOUT_MS
            connection.readTimeout = PREVIEW_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "ircAndroid/preview (https://github.com/brftherese/ircAndroid)")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            val code = connection.responseCode
            if (code !in 200..299) return@withContext null
            val contentType = connection.contentType?.lowercase(Locale.US) ?: ""
            if (contentType.isNotBlank() && PREVIEW_ACCEPT_TYPES.none { contentType.startsWith(it) }) {
                return@withContext null
            }
            val charsetName = contentType.substringAfter("charset=", "utf-8").substringBefore(';').trim().ifBlank { "utf-8" }
            val payload = connection.inputStream.use { readLimited(it, PREVIEW_MAX_BYTES) }
            val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
            val html = String(payload, charset)
            parsePreview(html, url)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private fun readLimited(stream: InputStream, limit: Int): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(4096)
    var total = 0
    while (total < limit) {
        val maxRead = min(chunk.size, limit - total)
        val read = stream.read(chunk, 0, maxRead)
        if (read == -1) break
        buffer.write(chunk, 0, read)
        total += read
    }
    return buffer.toByteArray()
}

private fun parsePreview(html: String, baseUrl: String): LinkPreview? {
    val cleanHtml = html.take(20000)
    val title = decodeHtml(
        findMeta(cleanHtml, "og:title")
            ?: findMeta(cleanHtml, "twitter:title")
            ?: TITLE_REGEX.find(cleanHtml)?.groupValues?.getOrNull(1)
    )
    val description = decodeHtml(
        findMeta(cleanHtml, "og:description")
            ?: findMeta(cleanHtml, "description")
            ?: findMeta(cleanHtml, "twitter:description")
    )
    val image = findMeta(cleanHtml, "og:image")?.let { absolutize(baseUrl, it) }
    val site = decodeHtml(findMeta(cleanHtml, "og:site_name")) ?: Uri.parse(baseUrl).host
    if (title.isNullOrBlank() && description.isNullOrBlank()) return null
    return LinkPreview(
        url = baseUrl,
        title = title,
        description = description,
        siteName = site,
        imageUrl = image,
    )
}

private fun findMeta(html: String, key: String): String? {
    val pattern = "(?is)<meta[^>]+(?:name|property)\\s*=\\s*['\"]${Regex.escape(key)}['\"][^>]*content\\s*=\\s*['\"](.*?)['\"][^>]*>"
    return Regex(pattern).find(html)?.groupValues?.getOrNull(1)
}

private fun decodeHtml(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val text = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    return text.replace("\\s+".toRegex(), " ").trim()
}

private fun absolutize(base: String, candidate: String): String {
    return runCatching { URL(URL(base), candidate).toString() }.getOrDefault(candidate)
}

private val TITLE_REGEX = Regex("(?is)<title[^>]*>(.*?)</title>")
