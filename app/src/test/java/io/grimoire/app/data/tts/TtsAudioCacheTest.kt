package io.grimoire.app.data.tts

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TtsAudioCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val config = TtsEngineConfig("voice", "en", 1f, 1f, "secret")

    @Test fun replaySurvivesCacheRecreation() {
        val directory = temporary.newFolder()
        val key = ttsAudioCacheKey(config, "voice", "paragraph")
        TtsAudioCache(directory).put(key, byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), TtsAudioCache(directory).get(key))
        assertFalse(directory.listFiles()!!.any { it.name.contains("secret") })
    }

    @Test fun leastRecentlyUsedAudioIsEvictedToByteBudget() {
        val cache = TtsAudioCache(temporary.newFolder(), maxBytes = 6)
        cache.put("a", byteArrayOf(1, 2, 3))
        cache.put("b", byteArrayOf(4, 5, 6))
        assertNotNull(cache.get("a"))
        cache.put("c", byteArrayOf(7, 8, 9))
        assertNull(cache.get("b"))
        assertNotNull(cache.get("a"))
        assertNotNull(cache.get("c"))
    }

    @Test fun entryBudgetAndOversizeLimitAreEnforced() {
        val cache = TtsAudioCache(temporary.newFolder(), maxBytes = 3, maxEntries = 1)
        cache.put("a", byteArrayOf(1))
        cache.put("b", byteArrayOf(2))
        cache.put("large", ByteArray(4))
        assertNull(cache.get("a"))
        assertNotNull(cache.get("b"))
        assertNull(cache.get("large"))
    }

    @Test fun synthesisIdentityIncludesTextVoiceAndSettingsButNoCredentials() {
        val key = ttsAudioCacheKey(config, "voice", "text")
        assertEquals(key, ttsAudioCacheKey(config.copy(elevenLabsApiKey = "another-secret"), "voice", "text"))
        assertNotEquals(key, ttsAudioCacheKey(config, "other-voice", "text"))
        assertNotEquals(key, ttsAudioCacheKey(config, "voice", "other-text"))
        listOf(config.copy(rate = 1.1f), config.copy(pitch = 1.1f),
            config.copy(localeTag = "fr"), config.copy(elevenLabsModel = "other-model")).forEach {
            assertNotEquals(key, ttsAudioCacheKey(it, "voice", "text"))
        }
    }
}
