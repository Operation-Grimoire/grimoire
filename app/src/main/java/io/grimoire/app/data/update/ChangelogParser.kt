package io.grimoire.app.data.update

enum class ChangelogCategory(val displayName: String) {
    FEATURES("Features"),
    BUG_FIXES("Bug fixes"),
    SOURCES("Sources"),
    DOCUMENTATION("Documentation"),
    OTHER("Other changes"),
    CHANGES("Changes"),
}

data class ChangelogItem(
    val text: String,
    val prNumber: Int? = null,
    val prUrl: String? = null,
    val author: String? = null,
)

data class ChangelogSection(
    val category: ChangelogCategory,
    val items: List<ChangelogItem>,
)

object ChangelogParser {
    private val itemLine = Regex("""^[*\-]\s+(.+?)\s*$""")
    private val headingLine = Regex("""^#{2,4}\s+(.+?)\s*$""")
    private val prUrl = Regex("""https?://github\.com/[^/\s]+/[^/\s]+/pull/(\d+)""")
    private val prShort = Regex("""\(#(\d+)\)|(?<![\w/])#(\d+)\b""")
    private val author = Regex("""by @([\w-]+)""")
    private val byInTail = Regex("""\s+by\s+@[\w-]+(?:\s+in\s+\S+)?\s*$""")
    private val parenPr = Regex("""\s*\(#\d+\)\s*$""")

    fun parse(raw: String): List<ChangelogSection> {
        val buckets = linkedMapOf<ChangelogCategory, MutableList<ChangelogItem>>()
        var current: ChangelogCategory? = null

        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("**Full Changelog")) continue

            headingLine.matchEntire(line)?.let { match ->
                current = classify(match.groupValues[1])
                return@let
            }?.let { continue }

            itemLine.matchEntire(line)?.let { match ->
                val target = current ?: ChangelogCategory.CHANGES
                buckets.getOrPut(target) { mutableListOf() }.add(parseItem(match.groupValues[1]))
            }
        }

        return buckets.map { (category, items) -> ChangelogSection(category, items) }
    }

    private fun classify(heading: String): ChangelogCategory? {
        val h = heading.lowercase()
        return when {
            // GitHub's outer wrapper and our own version separators — ignore so
            // items beneath them attribute to the next real subsection (or
            // fall back to CHANGES if there is none).
            "what's changed" in h || "whats changed" in h -> null
            h.startsWith("v") && h.length > 1 && h[1].isDigit() -> null
            "feature" in h || "enhancement" in h -> ChangelogCategory.FEATURES
            "bug" in h || "fix" in h -> ChangelogCategory.BUG_FIXES
            "source" in h || "extension" in h -> ChangelogCategory.SOURCES
            "doc" in h -> ChangelogCategory.DOCUMENTATION
            "other" in h -> ChangelogCategory.OTHER
            else -> ChangelogCategory.CHANGES
        }
    }

    private fun parseItem(raw: String): ChangelogItem {
        val urlMatch = prUrl.find(raw)
        val shortMatch = prShort.find(raw)
        val prNum = urlMatch?.groupValues?.get(1)?.toIntOrNull()
            ?: shortMatch?.let {
                it.groupValues.drop(1).firstOrNull { v -> v.isNotEmpty() }?.toIntOrNull()
            }
        val url = urlMatch?.value
        val by = author.find(raw)?.groupValues?.get(1)

        val cleaned = raw
            .replace(byInTail, "")
            .replace(parenPr, "")
            .trim()
            .trimEnd(',')

        return ChangelogItem(
            text = cleaned.ifEmpty { raw },
            prNumber = prNum,
            prUrl = url,
            author = by,
        )
    }
}
