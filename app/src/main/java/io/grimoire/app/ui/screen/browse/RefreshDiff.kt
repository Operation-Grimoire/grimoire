package io.grimoire.app.ui.screen.browse

import io.grimoire.app.data.local.entity.ChapterEntity

/**
 * Chapters that changed across a user-triggered refresh: rows absent from [before],
 * plus rows that flipped locked -> unlocked (the user couldn't read them before and
 * now can). Same rule the background LibraryUpdater uses to detect updates.
 */
internal fun diffNewChapters(
    before: Map<String, ChapterEntity>,
    after: List<ChapterEntity>,
): List<ChapterEntity> = after.filter { ch ->
    val prev = before[ch.url]
    prev == null || (prev.locked && !ch.locked)
}

/**
 * The subset of [newChapters] the user asked to be told about: readable chapters are
 * gated by [notifyOnNewChapters], locked ones by [notifyOnNewLockedChapters] — the
 * same split the background sync notification applies per novel.
 */
internal fun filterNotifiableChapters(
    newChapters: List<ChapterEntity>,
    notifyOnNewChapters: Boolean,
    notifyOnNewLockedChapters: Boolean,
): List<ChapterEntity> = newChapters.filter { ch ->
    if (ch.locked) notifyOnNewLockedChapters else notifyOnNewChapters
}
