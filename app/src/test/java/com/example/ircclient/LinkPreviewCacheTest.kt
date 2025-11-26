package com.example.ircclient

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LinkPreviewCacheTest {

    @Before
    @After
    fun resetCache() {
        LinkPreviewCache.clear()
    }

    @Test
    fun storesAndReturnsSuccessfulPreview() {
        val preview = LinkPreview(
            url = "https://example.com",
            title = "Example",
            description = "Desc",
            siteName = "Example",
            imageUrl = null,
        )

        LinkPreviewCache.putSuccess(preview.url, preview)

        val cached = LinkPreviewCache.get(preview.url)
        assertNotNull(cached)
        assertTrue(cached!!.successful)
        assertEquals(preview, cached.preview)
    }

    @Test
    fun storesFailureWithoutPreview() {
        val url = "https://example.com/missing"

        LinkPreviewCache.putFailure(url)

        val cached = LinkPreviewCache.get(url)
        assertNotNull(cached)
        assertFalse(cached!!.successful)
        assertNull(cached.preview)
    }

    @Test
    fun invalidateRemovesEntry() {
        val url = "https://example.com/one"
        LinkPreviewCache.putFailure(url)

        assertNotNull(LinkPreviewCache.get(url))

        LinkPreviewCache.invalidate(url)

        assertNull(LinkPreviewCache.get(url))
    }
}
