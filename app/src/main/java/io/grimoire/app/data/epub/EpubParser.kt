package io.grimoire.app.data.epub

import io.grimoire.app.data.local.entity.CHAPTER_PAGE_SEPARATOR
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

/** One chapter extracted from an EPUB: a title and its content pre-joined with [CHAPTER_PAGE_SEPARATOR]. */
data class EpubChapter(val title: String, val content: String)

/** Cover image bytes plus the file extension to store it under. */
data class EpubCover(val bytes: ByteArray, val extension: String)

data class ParsedEpub(
    val title: String,
    val author: String?,
    val description: String?,
    val genres: List<String>,
    val cover: EpubCover?,
    val chapters: List<EpubChapter>,
)

/**
 * Minimal, dependency-free EPUB reader: an EPUB is a ZIP containing an OPF
 * package document. We resolve the OPF via META-INF/container.xml, read its
 * metadata/manifest/spine, name chapters from the TOC (EPUB3 nav or EPUB2 ncx),
 * and extract each spine document's text. Jsoup (already on the classpath via
 * the extensions API) parses both the XML and the XHTML.
 */
object EpubParser {

    fun parse(file: File): ParsedEpub = ZipFile(file).use { zip ->
        val containerXml = zip.readText("META-INF/container.xml")
            ?: error("Not a valid EPUB: missing META-INF/container.xml")
        val opfPath = Jsoup.parse(containerXml, "", Parser.xmlParser())
            .selectFirst("rootfile")
            ?.attr("full-path")
            ?.takeIf { it.isNotBlank() }
            ?.let(::percentDecode)
            ?: error("Not a valid EPUB: no rootfile in container.xml")

        val opfDir = opfPath.substringBeforeLast('/', "")
        val opf = Jsoup.parse(
            zip.readText(opfPath) ?: error("Not a valid EPUB: missing OPF at $opfPath"),
            "",
            Parser.xmlParser(),
        )

        val title = opf.firstText("dc:title", "title").orEmpty().ifBlank { file.nameWithoutExtension }
        val author = opf.firstText("dc:creator", "creator")
        val description = opf.firstText("dc:description", "description")
        val genres = (opf.getElementsByTag("dc:subject") + opf.getElementsByTag("subject"))
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        // Manifest: item id -> (href, mediaType, properties)
        data class Item(val href: String, val mediaType: String, val properties: String)
        val manifest = opf.select("manifest > item").associate { el ->
            el.attr("id") to Item(
                href = el.attr("href"),
                mediaType = el.attr("media-type"),
                properties = el.attr("properties"),
            )
        }

        val spineEl = opf.selectFirst("spine")
        val spineRefs = spineEl?.select("itemref")
            ?.map { it.attr("idref") }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        // Build href -> chapter title map from the navigation document.
        val tocTitles = HashMap<String, String>()
        // EPUB3 nav document.
        manifest.values.firstOrNull { it.properties.contains("nav") }?.let { nav ->
            val navPath = resolvePath(opfDir, nav.href)
            zip.readText(navPath)?.let { html ->
                val navDir = navPath.substringBeforeLast('/', "")
                Jsoup.parse(html).select("nav a[href]").forEach { a ->
                    val href = resolvePath(navDir, a.attr("href")).substringBefore('#')
                    val t = a.text().trim()
                    if (href.isNotEmpty() && t.isNotEmpty()) tocTitles.putIfAbsent(href, t)
                }
            }
        }
        // EPUB2 NCX (referenced by spine `toc` attribute).
        if (tocTitles.isEmpty()) {
            val ncxId = spineEl?.attr("toc")?.takeIf { it.isNotBlank() }
            val ncxItem = ncxId?.let { manifest[it] }
                ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
            ncxItem?.let {
                val ncxPath = resolvePath(opfDir, it.href)
                zip.readText(ncxPath)?.let { xml ->
                    val ncxDir = ncxPath.substringBeforeLast('/', "")
                    Jsoup.parse(xml, "", Parser.xmlParser()).select("navPoint").forEach { np ->
                        val src = np.selectFirst("content")?.attr("src").orEmpty()
                        val href = resolvePath(ncxDir, src).substringBefore('#')
                        val t = np.selectFirst("navLabel > text")?.text()?.trim().orEmpty()
                        if (href.isNotEmpty() && t.isNotEmpty()) tocTitles.putIfAbsent(href, t)
                    }
                }
            }
        }

        val chapters = ArrayList<EpubChapter>()
        spineRefs.forEachIndexed { index, idref ->
            val item = manifest[idref] ?: return@forEachIndexed
            val path = resolvePath(opfDir, item.href)
            val xhtml = zip.readText(path) ?: return@forEachIndexed
            val doc = Jsoup.parse(xhtml)
            doc.select("script, style").remove()
            val pages = doc.body()?.let { body ->
                body.select("p, h1, h2, h3, h4, h5, h6, blockquote, li")
                    .map { it.text().trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty {
                        body.wholeText().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    }
            }.orEmpty()
            if (pages.isEmpty()) return@forEachIndexed
            val name = tocTitles[path]
                ?: doc.title().trim().takeIf { it.isNotEmpty() }
                ?: "Chapter ${index + 1}"
            chapters += EpubChapter(name, pages.joinToString(CHAPTER_PAGE_SEPARATOR))
        }
        require(chapters.isNotEmpty()) { "EPUB contains no readable chapters" }

        // Cover: EPUB3 properties="cover-image", else EPUB2 <meta name="cover">,
        // else any image whose id mentions "cover".
        val coverHref = manifest.values.firstOrNull { it.properties.contains("cover-image") }?.href
            ?: opf.select("meta[name=cover]").firstOrNull()?.attr("content")
                ?.let { manifest[it]?.href }
            ?: manifest.entries.firstOrNull {
                it.key.contains("cover", ignoreCase = true) && it.value.mediaType.startsWith("image/")
            }?.value?.href
        val cover = coverHref?.let { href ->
            zip.readBytes(resolvePath(opfDir, href))?.let { bytes ->
                EpubCover(bytes, href.substringAfterLast('.', "jpg").lowercase())
            }
        }

        ParsedEpub(
            title = title.trim(),
            author = author?.trim()?.takeIf { it.isNotEmpty() },
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            genres = genres,
            cover = cover,
            chapters = chapters,
        )
    }

    private fun ZipFile.entryIgnoreCase(name: String) =
        getEntry(name) ?: entries().toList().firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun ZipFile.readBytes(name: String): ByteArray? =
        entryIgnoreCase(name)?.let { getInputStream(it).use { s -> s.readBytes() } }

    private fun ZipFile.readText(name: String): String? = readBytes(name)?.toString(Charsets.UTF_8)

    private fun org.jsoup.nodes.Document.firstText(vararg tags: String): String? {
        for (t in tags) {
            val el = getElementsByTag(t).firstOrNull { it.text().isNotBlank() }
            if (el != null) return el.text()
        }
        return null
    }

    /**
     * Percent-decode [s] as UTF-8. OPF/NCX/nav hrefs are URIs, so producers
     * (Calibre, Sigil, …) escape spaces and non-ASCII (`Chapter%201.html`),
     * while the matching ZIP entry is the literal path (`Chapter 1.html`).
     * Unlike [java.net.URLDecoder], `+` is left intact (it is literal in a
     * URI path segment, not a space).
     */
    private fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val hi = if (c == '%' && i + 2 < s.length) Character.digit(s[i + 1], 16) else -1
            val lo = if (hi >= 0) Character.digit(s[i + 2], 16) else -1
            if (hi >= 0 && lo >= 0) {
                out.write((hi shl 4) or lo)
                i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** Resolve [href] (which may contain ../ segments) against [baseDir] into a normalized zip path. */
    private fun resolvePath(baseDir: String, href: String): String {
        val raw = percentDecode(href.substringBefore('#').trim())
        if (raw.isEmpty()) return raw
        val combined = if (baseDir.isEmpty()) raw else "$baseDir/$raw"
        val stack = ArrayDeque<String>()
        for (part in combined.split('/')) {
            when (part) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }
}
