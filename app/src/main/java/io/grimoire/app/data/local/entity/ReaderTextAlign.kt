package io.grimoire.app.data.local.entity

/**
 * Per-novel reader text alignment, stored on [NovelEntity.readerTextAlign] by
 * ordinal. [AUTO] (the default) derives direction + alignment from the novel's
 * language — RTL scripts read right-aligned, everything else left. The rest
 * are explicit user overrides, remembered per novel.
 *
 * Append-only: ordinals are persisted.
 */
enum class ReaderTextAlign {
    AUTO,
    LEFT,
    RIGHT,
    CENTER,
    JUSTIFY,
}
