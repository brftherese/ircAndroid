package com.example.ircclient

import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

private const val CACHE_MAX_ENTRIES = 64
private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(10)

data class CachedLinkPreview(
    val preview: LinkPreview?,
    val successful: Boolean,
    val timestamp: Long,
)

object LinkPreviewCache {
    private val cache = object : LinkedHashMap<String, CachedLinkPreview>(CACHE_MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLinkPreview>?): Boolean {
            return size > CACHE_MAX_ENTRIES
        }
    }

    @Synchronized
    fun putSuccess(url: String, preview: LinkPreview) {
        cache[url] = CachedLinkPreview(preview = preview, successful = true, timestamp = now())
    }

    @Synchronized
    fun putFailure(url: String) {
        cache[url] = CachedLinkPreview(preview = null, successful = false, timestamp = now())
    }

    @Synchronized
    fun get(url: String): CachedLinkPreview? {
        val entry = cache[url] ?: return null
        if (now() - entry.timestamp > CACHE_TTL_MS) {
            cache.remove(url)
            return null
        }
        return entry
    }

    @Synchronized
    fun invalidate(url: String) {
        cache.remove(url)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    private fun now(): Long = System.currentTimeMillis()
}
