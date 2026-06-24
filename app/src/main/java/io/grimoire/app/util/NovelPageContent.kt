package io.grimoire.app.util

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent

/**
 * Flat accessors bridging the API's sealed [PageContent] back to the plain
 * fields the app's reader / downloader / TTS pipeline consume. A text page
 * yields its text (empty for image/separator pages); [formattedText] is the
 * optional constrained-HTML payload; [imageUrl] and [isSeparator] expose the
 * other two variants.
 */
val NovelPage.text: String get() = (content as? PageContent.Text)?.text ?: ""
val NovelPage.formattedText: String? get() = (content as? PageContent.Text)?.html
val NovelPage.imageUrl: String? get() = (content as? PageContent.Image)?.url
val NovelPage.isSeparator: Boolean get() = content is PageContent.Separator
