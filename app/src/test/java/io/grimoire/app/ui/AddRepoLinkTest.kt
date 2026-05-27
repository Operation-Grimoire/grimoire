package io.grimoire.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddRepoLinkTest {

    @Test
    fun parses_https_app_link() {
        val result = parseAddRepoLink(
            scheme = "https",
            host = "grimoireapp.org",
            path = "/add-repo",
            urlParam = "https://example.com/index.json",
            nameParam = "Sample",
        )
        assertEquals(PendingAddRepo(name = "Sample", url = "https://example.com/index.json"), result)
    }

    @Test
    fun parses_custom_scheme_fallback() {
        val result = parseAddRepoLink(
            scheme = "grimoire",
            host = "add-repo",
            path = "",
            urlParam = "https://example.com/index.json",
            nameParam = "Sample",
        )
        assertEquals(PendingAddRepo(name = "Sample", url = "https://example.com/index.json"), result)
    }

    @Test
    fun name_is_optional() {
        val result = parseAddRepoLink(
            scheme = "https",
            host = "grimoireapp.org",
            path = "/add-repo",
            urlParam = "https://example.com/index.json",
            nameParam = null,
        )
        assertEquals(PendingAddRepo(name = null, url = "https://example.com/index.json"), result)
    }

    @Test
    fun blank_name_is_normalized_to_null() {
        val result = parseAddRepoLink(
            scheme = "https",
            host = "grimoireapp.org",
            path = "/add-repo",
            urlParam = "https://example.com/index.json",
            nameParam = "   ",
        )
        assertEquals(PendingAddRepo(name = null, url = "https://example.com/index.json"), result)
    }

    @Test
    fun rejects_missing_url() {
        val result = parseAddRepoLink(
            scheme = "https",
            host = "grimoireapp.org",
            path = "/add-repo",
            urlParam = null,
            nameParam = "Sample",
        )
        assertNull(result)
    }

    @Test
    fun rejects_non_json_url() {
        val result = parseAddRepoLink(
            scheme = "https",
            host = "grimoireapp.org",
            path = "/add-repo",
            urlParam = "https://example.com/index.txt",
            nameParam = "Sample",
        )
        assertNull(result)
    }

    @Test
    fun rejects_non_http_url() {
        val result = parseAddRepoLink(
            scheme = "https",
            host = "grimoireapp.org",
            path = "/add-repo",
            urlParam = "ftp://example.com/index.json",
            nameParam = "Sample",
        )
        assertNull(result)
    }

    @Test
    fun rejects_wrong_host() {
        val result = parseAddRepoLink(
            scheme = "https",
            host = "evil.com",
            path = "/add-repo",
            urlParam = "https://example.com/index.json",
            nameParam = "Sample",
        )
        assertNull(result)
    }

    @Test
    fun rejects_wrong_path() {
        val result = parseAddRepoLink(
            scheme = "https",
            host = "grimoireapp.org",
            path = "/something-else",
            urlParam = "https://example.com/index.json",
            nameParam = "Sample",
        )
        assertNull(result)
    }

    @Test
    fun accepts_http_url() {
        // The dialog accepts plain http too (only requires startsWith("http")).
        val result = parseAddRepoLink(
            scheme = "https",
            host = "grimoireapp.org",
            path = "/add-repo",
            urlParam = "http://example.com/index.json",
            nameParam = "Sample",
        )
        assertEquals(PendingAddRepo(name = "Sample", url = "http://example.com/index.json"), result)
    }
}
