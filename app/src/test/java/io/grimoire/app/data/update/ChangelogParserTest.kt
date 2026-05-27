package io.grimoire.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogParserTest {

    @Test
    fun parse_categorisedStable_groupsByCategory() {
        val raw = """
            ## What's Changed
            ### Features
            * Add changelog popup by @alice in https://github.com/owner/repo/pull/59
            * Support beta channel by @bob in https://github.com/owner/repo/pull/58
            ### Bug fixes
            * Fix duplicate library badges by @carol in https://github.com/owner/repo/pull/57

            **Full Changelog**: https://github.com/owner/repo/compare/v1.0.0...v1.0.1
        """.trimIndent()

        val sections = ChangelogParser.parse(raw)

        assertEquals(2, sections.size)
        val features = sections[0]
        assertEquals(ChangelogCategory.FEATURES, features.category)
        assertEquals(2, features.items.size)
        assertEquals("Add changelog popup", features.items[0].text)
        assertEquals(59, features.items[0].prNumber)
        assertEquals("alice", features.items[0].author)
        assertEquals("https://github.com/owner/repo/pull/59", features.items[0].prUrl)

        val fixes = sections[1]
        assertEquals(ChangelogCategory.BUG_FIXES, fixes.category)
        assertEquals(1, fixes.items.size)
        assertEquals("Fix duplicate library badges", fixes.items[0].text)
        assertEquals(57, fixes.items[0].prNumber)
        assertEquals("carol", fixes.items[0].author)
    }

    @Test
    fun parse_flatStable_putsItemsUnderChanges() {
        // When PRs carry no recognised label, GitHub omits ### subsections.
        val raw = """
            ## What's Changed
            * First PR by @alice in https://github.com/owner/repo/pull/1
            * Second PR by @bob in https://github.com/owner/repo/pull/2

            **Full Changelog**: https://github.com/owner/repo/compare/v1.0.0...v1.0.1
        """.trimIndent()

        val sections = ChangelogParser.parse(raw)

        assertEquals(1, sections.size)
        assertEquals(ChangelogCategory.CHANGES, sections[0].category)
        assertEquals(2, sections[0].items.size)
        assertEquals("First PR", sections[0].items[0].text)
        assertEquals(1, sections[0].items[0].prNumber)
    }

    @Test
    fun parse_betaPrFormat_groupsByCategory() {
        // Body shape produced by .github/workflows/nightly.yml — framing
        // paragraphs above/below the /generate-notes output should be ignored.
        val raw = """
            Auto-built from `master` @ abc123def456.

            Version: `0.0.21-beta.58+abc123d`

            ## What's Changed
            ### Features
            * Aggregate beta changelog like stable by @alice in https://github.com/owner/repo/pull/60
            ### Bug fixes
            * Stop overwriting beta tag on every push by @bob in https://github.com/owner/repo/pull/59

            **Full Changelog**: https://github.com/owner/repo/compare/v0.0.21-beta.57...v0.0.21-beta.58
        """.trimIndent()

        val sections = ChangelogParser.parse(raw)

        assertEquals(2, sections.size)
        val features = sections[0]
        assertEquals(ChangelogCategory.FEATURES, features.category)
        assertEquals("Aggregate beta changelog like stable", features.items[0].text)
        assertEquals(60, features.items[0].prNumber)
        assertEquals("alice", features.items[0].author)
        val fixes = sections[1]
        assertEquals(ChangelogCategory.BUG_FIXES, fixes.category)
        assertEquals("Stop overwriting beta tag on every push", fixes.items[0].text)
        assertEquals(59, fixes.items[0].prNumber)
    }

    @Test
    fun parse_aggregatedStable_mergesAcrossVersionHeaders() {
        // Format produced by AppUpdateChecker.fetchStableNotesSince for skip-version upgrades.
        val raw = """
            ## v1.0.3

            ## What's Changed
            ### Features
            * Latest feature by @alice in https://github.com/owner/repo/pull/30

            **Full Changelog**: https://github.com/owner/repo/compare/v1.0.2...v1.0.3

            ## v1.0.2

            ## What's Changed
            ### Bug fixes
            * Older fix by @bob in https://github.com/owner/repo/pull/20

            **Full Changelog**: https://github.com/owner/repo/compare/v1.0.1...v1.0.2
        """.trimIndent()

        val sections = ChangelogParser.parse(raw)

        // Version headers (## v...) and "## What's Changed" wrappers are ignored;
        // items group under their nearest ### subsection.
        val features = sections.first { it.category == ChangelogCategory.FEATURES }
        val fixes = sections.first { it.category == ChangelogCategory.BUG_FIXES }
        assertEquals(1, features.items.size)
        assertEquals("Latest feature", features.items[0].text)
        assertEquals(1, fixes.items.size)
        assertEquals("Older fix", fixes.items[0].text)
    }

    @Test
    fun parse_emptyOrUnparseable_returnsEmptyList() {
        assertTrue(ChangelogParser.parse("").isEmpty())
        assertTrue(ChangelogParser.parse("Just a paragraph with no bullets.").isEmpty())
    }

    @Test
    fun parse_parenthesisPrSuffix_isStripped() {
        // Some changelog tools append (#123) rather than the GitHub URL form.
        val raw = """
            ## What's Changed
            * Tighten validation (#42)
        """.trimIndent()

        val item = ChangelogParser.parse(raw).single().items.single()
        assertEquals("Tighten validation", item.text)
        assertEquals(42, item.prNumber)
        assertNull(item.author)
    }

    @Test
    fun parse_categoryAliases_matchByKeyword() {
        val raw = """
            ### Enhancements
            * Smoother animations
            ### Sources & Extensions
            * Add NovGo support
            ### Documentation updates
            * README polish
            ### Other changes
            * Misc cleanup
        """.trimIndent()

        val categories = ChangelogParser.parse(raw).map { it.category }
        assertEquals(
            listOf(
                ChangelogCategory.FEATURES,
                ChangelogCategory.SOURCES,
                ChangelogCategory.DOCUMENTATION,
                ChangelogCategory.OTHER,
            ),
            categories,
        )
    }
}
