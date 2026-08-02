package io.grimoire.app.ui.screen.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateIssueKindTest {

    @Test
    fun `source missing message maps to SOURCE_MISSING`() {
        assertEquals(
            UpdateIssueKind.SOURCE_MISSING,
            issueKindFor("Source not installed — skipped"),
        )
    }

    @Test
    fun `cloudflare exception messages map to CLOUDFLARE`() {
        assertEquals(
            UpdateIssueKind.CLOUDFLARE,
            issueKindFor("CloudflareException: Cloudflare challenge detected for https://x"),
        )
        assertEquals(
            UpdateIssueKind.CLOUDFLARE,
            issueKindFor("CloudflareBypassException: Failed to bypass Cloudflare protection for https://x"),
        )
    }

    @Test
    fun `everything else maps to OTHER`() {
        assertEquals(UpdateIssueKind.OTHER, issueKindFor("SocketTimeoutException: timeout"))
        assertEquals(UpdateIssueKind.OTHER, issueKindFor("Source returned no chapters — kept the existing list"))
        assertEquals(UpdateIssueKind.OTHER, issueKindFor("Source returned incomplete data — kept the previous title/cover"))
    }
}
