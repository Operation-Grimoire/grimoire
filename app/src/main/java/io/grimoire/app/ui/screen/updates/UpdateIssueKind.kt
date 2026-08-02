package io.grimoire.app.ui.screen.updates

/**
 * What a stored update issue is about, derived from its message — the entity
 * predates any kind column, and the messages are already stable strings the
 * screen matches on for localization. Drives which action a row offers.
 */
internal enum class UpdateIssueKind {
    /** The novel's extension isn't installed — fix is installing it. */
    SOURCE_MISSING,

    /** A Cloudflare challenge blocked the refresh — fix is solving it in the WebView. */
    CLOUDFLARE,

    /** Anything else (transient network/parse trouble) — fix is retrying. */
    OTHER,
}

internal fun issueKindFor(message: String): UpdateIssueKind = when {
    message == "Source not installed — skipped" -> UpdateIssueKind.SOURCE_MISSING
    // describeError() stores "<ExceptionSimpleName>: <message>"; both
    // CloudflareException and CloudflareBypassException share the prefix.
    message.startsWith("Cloudflare") -> UpdateIssueKind.CLOUDFLARE
    else -> UpdateIssueKind.OTHER
}
