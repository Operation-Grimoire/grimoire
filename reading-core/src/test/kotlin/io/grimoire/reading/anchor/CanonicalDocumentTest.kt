package io.grimoire.reading.anchor

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalDocumentTest {
    private val key = ChapterKey(NovelKey(17, "/novel"), "/chapter")

    @Test fun independentFrozenFramingVectorsMatchBothDocumentAndBlockDigests() {
        val vectors = fixtures().getValue("cases").jsonArray
        assertEquals(14, vectors.size)
        for (entry in vectors) {
            val vector = entry.jsonObject
            val pages = vector.getValue("blocks").jsonArray.mapIndexed { ordinal, value ->
                val block = value.jsonObject
                val text = block.getValue("units").jsonArray.map { it.jsonPrimitive.int.toChar() }.toCharArray().concatToString()
                NovelPage(ordinal + 77, when (block.getValue("tag").jsonPrimitive.int) {
                    0 -> PageContent.Text(text, "unrelated HTML")
                    1 -> PageContent.Separator()
                    2 -> PageContent.Image(text)
                    else -> error("Invalid frozen vector")
                })
            }
            val document = CanonicalDocument.fromPages(key, pages)
            val label = vector.getValue("label").jsonPrimitive.content
            assertEquals(label, vector.getValue("logicalRevision").jsonPrimitive.content, document.logicalRevision)
            val blocks = vector.getValue("blockFingerprints").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(label, blocks, document.blocks.indices.map(document::blockFingerprint))
        }
    }

    @Test fun digestChunksPreserveAPairSplitAcrossTheBufferBoundary() {
        val text = "a".repeat(2047) + "\uD83D\uDE00" + "z".repeat(4097)
        val document = CanonicalDocument.fromPages(key, listOf(NovelPage(0, PageContent.Text(text))))
        val expected = fixtures().getValue("chunkBoundary").jsonObject
        assertEquals(expected.getValue("logicalRevision").jsonPrimitive.content, document.logicalRevision)
        assertEquals(expected.getValue("blockFingerprints").jsonArray.single().jsonPrimitive.content, document.blockFingerprint(0))
        assertEquals(text, (document.blocks.single() as AnchorBlock.Text).text)
    }

    @Test fun decodedLegacyAndCurrentContentShareIdentityWithoutRewritingTheirPayloads() {
        val legacy = StoredChapterContent("A\u001c<b>A</b>\u001f\u001d\u001f\u001eimage", 1)
        val current = StoredChapterContent("""[["text","A",null],["separator"],["image","image"]]""", 2)
        val before = listOf(legacy.copy(), current.copy())
        val a = CanonicalDocument.fromPages(key, decodeChapterContent(legacy))
        val b = CanonicalDocument.fromPages(key, decodeChapterContent(current))
        assertEquals(a.blocks, b.blocks)
        assertEquals(a.logicalRevision, b.logicalRevision)
        assertEquals(before, listOf(legacy, current))
    }

    @Test fun indicesAndHtmlDoNotChangeIdentityButExactPlainContentAndOriginalLocatorsDo() {
        fun document(vararg content: PageContent) = CanonicalDocument.fromPages(key, content.mapIndexed { i, c -> NovelPage(i, c) })
        val base = document(PageContent.Text("A", "<b>A</b>"), PageContent.Image("remote"), PageContent.Separator())
        val formatted = CanonicalDocument.fromPages(key, listOf(NovelPage(999, PageContent.Text("A", "<i>A</i>")),
            NovelPage(-1, PageContent.Image("remote")), NovelPage(999, PageContent.Separator())))
        assertEquals(base.logicalRevision, formatted.logicalRevision)
        for (changed in listOf(
            document(PageContent.Text("a"), PageContent.Image("remote"), PageContent.Separator()),
            document(PageContent.Text("A "), PageContent.Image("remote"), PageContent.Separator()),
            document(PageContent.Text("A"), PageContent.Image("REMOTE"), PageContent.Separator()),
            document(PageContent.Text("A"), PageContent.Image("file:///cache/image"), PageContent.Separator()),
            document(PageContent.Image("remote"), PageContent.Text("A"), PageContent.Separator()),
            document(PageContent.Text("A"), PageContent.Text("remote"), PageContent.Separator()),
            document(PageContent.Text(""), PageContent.Text("A"), PageContent.Image("remote"), PageContent.Separator()),
        )) assertNotEquals(base.logicalRevision, changed.logicalRevision)
        assertNotEquals(document(PageContent.Text("\u00e9")).logicalRevision, document(PageContent.Text("e\u0301")).logicalRevision)
    }

    @Test fun blockFingerprintsAreTypedAndIndependentOfOrdinalWhileDocumentCountsArePreserved() {
        val a = CanonicalDocument.fromPages(key, listOf(NovelPage(0, PageContent.Text("same"))))
        val b = CanonicalDocument.fromPages(key, listOf(NovelPage(0, PageContent.Text("")),
            NovelPage(0, PageContent.Text("same")), NovelPage(0, PageContent.Text("same"))))
        assertEquals(a.blockFingerprint(0), b.blockFingerprint(1))
        assertEquals(b.blockFingerprint(1), b.blockFingerprint(2))
        assertNotEquals(a.logicalRevision, b.logicalRevision)
        val otherKey = key.copy(novel = NovelKey(18, key.novel.url))
        assertEquals(a.logicalRevision, CanonicalDocument.fromPages(otherKey, listOf(NovelPage(1, PageContent.Text("same")))).logicalRevision)
        assertNotEquals(a.key, otherKey) // Identical content is not cross-key authority.
    }

    @Test fun documentIsAnImmutableSnapshotBeforeFilteringAndUriProjection() {
        val pages = mutableListOf(NovelPage(100, PageContent.Text("")), NovelPage(100, PageContent.Text("   ")),
            NovelPage(2, PageContent.Text("The harbor bell rang.")), NovelPage(3, PageContent.Image("remote")),
            NovelPage(4, PageContent.Separator()), NovelPage(5, PageContent.Text("Mira followed the light.")))
        val original = pages.toList()
        val document = CanonicalDocument.fromPages(key, pages)
        val revision = document.logicalRevision
        val projected = pages.filter { (it.content as? PageContent.Text)?.text?.isNotBlank() != false }.map {
            if (it.content is PageContent.Image) it.copy(content = PageContent.Image("local")) else it
        }
        assertEquals(4, projected.size)
        pages.clear()
        assertEquals(6, document.blocks.size)
        assertEquals(AnchorBlock.Image("remote"), document.blocks[3])
        assertEquals(revision, document.logicalRevision)
        assertEquals(CanonicalDocument.fromPages(key, original).logicalRevision, document.logicalRevision)
        try { (document.blocks as MutableList<AnchorBlock>).clear(); fail("Mutable document") }
        catch (_: UnsupportedOperationException) { }
        assertEquals(6, document.blocks.size)
    }

    private fun fixtures(): JsonObject = javaClass.getResourceAsStream("/anchors/v1-framing.json")!!.use {
        Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject
    }
}
