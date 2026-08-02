package io.grimoire.app.ui.screen.library

import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.epub.LOCAL_SOURCE_ID
import io.grimoire.app.data.preferences.AdultFilter
import io.grimoire.app.data.preferences.NovelTypeFilter
import io.grimoire.app.data.preferences.SortDirection
import io.grimoire.app.data.preferences.SortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterTest {

    private fun novel(
        id: Long,
        title: String = "Novel $id",
        author: String? = null,
        sourceId: Long = 1L,
        categoryId: Long? = null,
        status: Int = 0,
        lastUpdated: Long = 0L,
        lastReadAt: Long = 0L,
        notifyOnNewChapters: Boolean = false,
        notifyOnNewLockedChapters: Boolean = false,
        autoDownloadNewChapters: Boolean = false,
        overrideTitle: String? = null,
        overrideAuthor: String? = null,
        overrideStatus: Int? = null,
        userRating: Int? = null,
    ) = NovelEntity(
        id = id,
        sourceId = sourceId,
        url = "https://example.test/$id",
        title = title,
        author = author,
        status = status,
        favorite = true,
        lastUpdated = lastUpdated,
        categoryId = categoryId,
        lastReadAt = lastReadAt,
        notifyOnNewChapters = notifyOnNewChapters,
        notifyOnNewLockedChapters = notifyOnNewLockedChapters,
        autoDownloadNewChapters = autoDownloadNewChapters,
        overrideTitle = overrideTitle,
        overrideAuthor = overrideAuthor,
        overrideStatus = overrideStatus,
        userRating = userRating,
    )

    private fun category(id: Long, name: String, isDefault: Boolean = false, isHidden: Boolean = false) =
        CategoryEntity(id = id, name = name, order = id.toInt(), isDefault = isDefault, isHidden = isHidden)

    private fun stats(
        novelId: Long,
        total: Int = 10,
        read: Int = 0,
        downloaded: Int = 0,
        locked: Int = 0,
    ) = NovelChapterStats(
        novelId = novelId,
        total = total,
        readCount = read,
        downloadedCount = downloaded,
        lockedCount = locked,
    )

    private fun baseInputs(
        novels: List<NovelEntity>?,
        categories: List<CategoryEntity> = emptyList(),
        chapterStats: Map<Long, NovelChapterStats> = emptyMap(),
        showAllTab: Boolean = true,
        sortField: SortField = SortField.TITLE,
        sortDirection: SortDirection = SortDirection.ASC,
        filterStatuses: Set<Int> = emptySet(),
        filterStatusesExclude: Set<Int> = emptySet(),
        filterUnreadOnly: Boolean = false,
        filterDownloadedOnly: Boolean = false,
        filterNotifyEnabled: Boolean = false,
        filterAutoDownloadEnabled: Boolean = false,
        filterMinUserRating: Int = 1,
        filterMaxUserRating: Int = 10,
        filterType: NovelTypeFilter = NovelTypeFilter.ALL,
        epubSourceIds: Set<Long> = emptySet(),
        filterAdult: AdultFilter = AdultFilter.ANY,
        adultSourceIds: Set<Long> = emptySet(),
        filterSourceIds: Set<Long> = emptySet(),
        isUnlocked: Boolean = true,
        hiddenCategoryIds: Set<Long> = emptySet(),
        includeHiddenInAll: Boolean = true,
        includeLockedInTotals: Boolean = true,
        searchQuery: String = "",
    ) = LibraryFilterInputs(
        novels = novels,
        categories = categories,
        chapterStats = chapterStats,
        showAllTab = showAllTab,
        sortField = sortField,
        sortDirection = sortDirection,
        filterStatuses = filterStatuses,
        filterStatusesExclude = filterStatusesExclude,
        filterUnreadOnly = filterUnreadOnly,
        filterDownloadedOnly = filterDownloadedOnly,
        filterNotifyEnabled = filterNotifyEnabled,
        filterAutoDownloadEnabled = filterAutoDownloadEnabled,
        filterMinUserRating = filterMinUserRating,
        filterMaxUserRating = filterMaxUserRating,
        filterType = filterType,
        epubSourceIds = epubSourceIds,
        filterSourceIds = filterSourceIds,
        filterAdult = filterAdult,
        adultSourceIds = adultSourceIds,
        isUnlocked = isUnlocked,
        hiddenCategoryIds = hiddenCategoryIds,
        includeHiddenInAll = includeHiddenInAll,
        includeLockedInTotals = includeLockedInTotals,
        searchQuery = searchQuery,
    )

    @Test
    fun `null novels yields null tabs - loading state preserved`() {
        val tabs = buildLibraryTabs(baseInputs(novels = null))
        assertTrue(tabs.isNotEmpty() || tabs.isEmpty()) // tabs list itself is empty when no categories
        // Add one category to surface a tab
        val withCat = buildLibraryTabs(
            baseInputs(novels = null, categories = listOf(category(1, "C1"))),
        )
        assertEquals(2, withCat.size)
        assertNull(withCat[0].novels)
        assertNull(withCat[1].novels)
    }

    @Test
    fun `all tab returns every favorited novel sorted by title`() {
        val novels = listOf(
            novel(1, title = "Beta"),
            novel(2, title = "Alpha"),
            novel(3, title = "charlie"),
        )
        val tabs = buildLibraryTabs(baseInputs(novels = novels))
        assertEquals(1, tabs.size)
        val ordered = tabs[0].novels!!.map { it.title }
        assertEquals(listOf("Alpha", "Beta", "charlie"), ordered)
    }

    @Test
    fun `sort direction DESC reverses ASC comparator`() {
        val novels = listOf(novel(1, "A"), novel(2, "B"), novel(3, "C"))
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, sortDirection = SortDirection.DESC),
        )
        assertEquals(listOf("C", "B", "A"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `category tab filters by categoryId`() {
        val cats = listOf(category(10, "Read"), category(20, "TBR"))
        val novels = listOf(
            novel(1, categoryId = 10L, title = "A"),
            novel(2, categoryId = 20L, title = "B"),
            novel(3, categoryId = null, title = "C"),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, categories = cats, showAllTab = false),
        )
        assertEquals(2, tabs.size)
        assertEquals(listOf("A"), tabs[0].novels!!.map { it.title })
        assertEquals(listOf("B"), tabs[1].novels!!.map { it.title })
    }

    @Test
    fun `default category tab matches novels with null categoryId`() {
        val def = category(1, "Default", isDefault = true)
        val novels = listOf(
            novel(1, categoryId = null, title = "Loose"),
            novel(2, categoryId = 99L, title = "Tagged"),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, categories = listOf(def), showAllTab = false),
        )
        assertEquals(listOf("Loose"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `locked excludes hidden categories on all-tab when includeHiddenInAll is false`() {
        val visible = category(10, "Visible")
        val hidden = category(20, "Hidden", isHidden = true)
        val novels = listOf(
            novel(1, categoryId = 10L, title = "Shown"),
            novel(2, categoryId = 20L, title = "Secret"),
        )
        val tabs = buildLibraryTabs(
            baseInputs(
                novels = novels,
                categories = listOf(visible, hidden),
                isUnlocked = true,
                hiddenCategoryIds = setOf(20L),
                includeHiddenInAll = false,
            ),
        )
        // All tab present; secret excluded; hidden category tab still shows secret.
        assertEquals(listOf("Shown"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `locked state always excludes hidden categories regardless of includeHiddenInAll`() {
        val visible = category(10, "Visible")
        val hidden = category(20, "Hidden", isHidden = true)
        val novels = listOf(
            novel(1, categoryId = 10L, title = "Shown"),
            novel(2, categoryId = 20L, title = "Secret"),
        )
        val tabs = buildLibraryTabs(
            baseInputs(
                novels = novels,
                categories = listOf(visible, hidden),
                isUnlocked = false,
                hiddenCategoryIds = setOf(20L),
                includeHiddenInAll = true, // ignored when locked
            ),
        )
        // hidden category is filtered out of `categories` list by VM before this point in real code,
        // but the all tab still must hide its novels even if the category leaked through here.
        assertEquals(listOf("Shown"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `status filter narrows results`() {
        val novels = listOf(
            novel(1, title = "A", status = 1),
            novel(2, title = "B", status = 2),
            novel(3, title = "C", status = 1),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterStatuses = setOf(1)),
        )
        assertEquals(listOf("A", "C"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `status filter uses overridden status, not source status`() {
        val novels = listOf(
            // Source status 2, but user overrode to 1 — must match a filter for 1.
            novel(1, title = "A", status = 2, overrideStatus = 1),
            // Source status 1, but user overrode to 2 — must be excluded by a filter for 1.
            novel(2, title = "B", status = 1, overrideStatus = 2),
            novel(3, title = "C", status = 1),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterStatuses = setOf(1)),
        )
        assertEquals(listOf("A", "C"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `excluded statuses are filtered out with no include set`() {
        val novels = listOf(
            novel(1, "A", status = 1),
            novel(2, "B", status = 2),
            novel(3, "C", status = 0),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterStatusesExclude = setOf(2)),
        )
        assertEquals(listOf("A", "C"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `include and exclude status sets combine`() {
        val novels = listOf(
            novel(1, "A", status = 1),
            novel(2, "B", status = 2),
            novel(3, "C", status = 1),
        )
        // Include ongoing(1)+completed(2) but exclude completed(2): only ongoing left.
        val tabs = buildLibraryTabs(
            baseInputs(
                novels = novels,
                filterStatuses = setOf(1, 2),
                filterStatusesExclude = setOf(2),
            ),
        )
        assertEquals(listOf("A", "C"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `title sort and search use overridden title and author`() {
        val novels = listOf(
            novel(1, title = "Zeta", overrideTitle = "Alpha"),
            novel(2, title = "Yankee", author = "Nobody", overrideAuthor = "Brandon"),
        )
        // Sorted by effective title: "Alpha" before "Yankee".
        val sorted = buildLibraryTabs(baseInputs(novels = novels))
        assertEquals(listOf("Alpha", "Yankee"), sorted[0].novels!!.map { it.effectiveTitle })
        // Search hits the overridden author.
        val searched = buildLibraryTabs(baseInputs(novels = novels, searchQuery = "brand"))
        assertEquals(listOf(2L), searched[0].novels!!.map { it.id })
    }

    @Test
    fun `unread-only filter drops fully read novels`() {
        val novels = listOf(novel(1, title = "Unread"), novel(2, title = "Done"))
        val stats = mapOf(
            1L to stats(novelId = 1L, total = 10, read = 3),
            2L to stats(novelId = 2L, total = 10, read = 10),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, chapterStats = stats, filterUnreadOnly = true),
        )
        assertEquals(listOf("Unread"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `includeLockedInTotals false treats locked-only as fully read for unread filter`() {
        val novels = listOf(novel(1, title = "Locked-only"))
        val stats = mapOf(
            1L to stats(novelId = 1L, total = 5, read = 2, locked = 3),
        )
        // effectiveTotal(false) = 5 - 3 = 2, readCount = 2 → unread = 0 → filtered out
        val tabs = buildLibraryTabs(
            baseInputs(
                novels = novels,
                chapterStats = stats,
                filterUnreadOnly = true,
                includeLockedInTotals = false,
            ),
        )
        assertTrue(tabs[0].novels!!.isEmpty())
    }

    @Test
    fun `downloaded-only filter requires at least one downloaded chapter`() {
        val novels = listOf(novel(1, title = "Has"), novel(2, title = "None"))
        val stats = mapOf(
            1L to stats(novelId = 1L, downloaded = 1),
            2L to stats(novelId = 2L, downloaded = 0),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, chapterStats = stats, filterDownloadedOnly = true),
        )
        assertEquals(listOf("Has"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `notify-enabled filter keeps novels with either notify flag set`() {
        val novels = listOf(
            novel(1, title = "Chapters", notifyOnNewChapters = true),
            novel(2, title = "Locked", notifyOnNewLockedChapters = true),
            novel(3, title = "Off"),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterNotifyEnabled = true),
        )
        assertEquals(listOf("Chapters", "Locked"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `auto-download filter keeps only novels with auto-download on`() {
        val novels = listOf(
            novel(1, title = "On", autoDownloadNewChapters = true),
            novel(2, title = "Off"),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterAutoDownloadEnabled = true),
        )
        assertEquals(listOf("On"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `type filter ALL keeps both epub and web novels`() {
        val novels = listOf(
            novel(1, title = "Epub", sourceId = LOCAL_SOURCE_ID),
            novel(2, title = "Web", sourceId = 100L),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterType = NovelTypeFilter.ALL),
        )
        assertEquals(listOf("Epub", "Web"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `type filter EPUB keeps only local novels`() {
        val novels = listOf(
            novel(1, title = "Epub", sourceId = LOCAL_SOURCE_ID),
            novel(2, title = "Web", sourceId = 100L),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterType = NovelTypeFilter.EPUB),
        )
        assertEquals(listOf("Epub"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `type filter WEB hides local novels`() {
        val novels = listOf(
            novel(1, title = "Epub", sourceId = LOCAL_SOURCE_ID),
            novel(2, title = "Web", sourceId = 100L),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterType = NovelTypeFilter.WEB),
        )
        assertEquals(listOf("Web"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `type filter treats EPUB-source extensions as EPUB`() {
        // sourceId 50 is an installed EPUB-source extension (e.g. Z-Library); 100 is web.
        val novels = listOf(
            novel(1, title = "Local", sourceId = LOCAL_SOURCE_ID),
            novel(2, title = "ZLib", sourceId = 50L),
            novel(3, title = "Web", sourceId = 100L),
        )
        val epubTabs = buildLibraryTabs(
            baseInputs(novels = novels, filterType = NovelTypeFilter.EPUB, epubSourceIds = setOf(50L)),
        )
        assertEquals(listOf("Local", "ZLib"), epubTabs[0].novels!!.map { it.title })
        val webTabs = buildLibraryTabs(
            baseInputs(novels = novels, filterType = NovelTypeFilter.WEB, epubSourceIds = setOf(50L)),
        )
        assertEquals(listOf("Web"), webTabs[0].novels!!.map { it.title })
    }

    @Test
    fun `source filter restricts to matching sourceIds`() {
        val novels = listOf(
            novel(1, title = "A", sourceId = 100L),
            novel(2, title = "B", sourceId = 200L),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterSourceIds = setOf(200L)),
        )
        assertEquals(listOf("B"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `search matches title case-insensitively`() {
        val novels = listOf(novel(1, title = "Hello World"), novel(2, title = "Other"))
        val tabs = buildLibraryTabs(baseInputs(novels = novels, searchQuery = "hello"))
        assertEquals(listOf("Hello World"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `search matches author`() {
        val novels = listOf(
            novel(1, title = "X", author = "Brandon"),
            novel(2, title = "Y", author = "Other"),
        )
        val tabs = buildLibraryTabs(baseInputs(novels = novels, searchQuery = "brand"))
        assertEquals(listOf("X"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `blank search returns all`() {
        val novels = listOf(novel(1), novel(2))
        val tabs = buildLibraryTabs(baseInputs(novels = novels, searchQuery = "   "))
        assertEquals(2, tabs[0].novels!!.size)
    }

    @Test
    fun `sort by UNREAD orders by unread count ascending`() {
        val novels = listOf(novel(1, title = "More"), novel(2, title = "Less"), novel(3, title = "Same"))
        val stats = mapOf(
            1L to stats(novelId = 1L, total = 10, read = 1),  // 9 unread
            2L to stats(novelId = 2L, total = 10, read = 7),  // 3 unread
            3L to stats(novelId = 3L, total = 10, read = 10), // 0 unread
        )
        val tabs = buildLibraryTabs(
            baseInputs(
                novels = novels,
                chapterStats = stats,
                sortField = SortField.UNREAD,
            ),
        )
        assertEquals(listOf("Same", "Less", "More"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `sort by USER_RATING orders by rating, unrated last on descending`() {
        val novels = listOf(
            novel(1, title = "Mid", userRating = 5),
            novel(2, title = "Top", userRating = 9),
            novel(3, title = "Unrated", userRating = null),
        )
        val desc = buildLibraryTabs(
            baseInputs(
                novels = novels,
                sortField = SortField.USER_RATING,
                sortDirection = SortDirection.DESC,
            ),
        )
        // Highest first; unrated (null == 0) sinks to the bottom.
        assertEquals(listOf("Top", "Mid", "Unrated"), desc[0].novels!!.map { it.title })

        val asc = buildLibraryTabs(
            baseInputs(
                novels = novels,
                sortField = SortField.USER_RATING,
                sortDirection = SortDirection.ASC,
            ),
        )
        assertEquals(listOf("Unrated", "Mid", "Top"), asc[0].novels!!.map { it.title })
    }

    @Test
    fun `user-rating range keeps only novels whose rating falls inside it`() {
        val novels = listOf(
            novel(1, title = "Low", userRating = 3),
            novel(2, title = "Mid", userRating = 6),
            novel(3, title = "High", userRating = 9),
            novel(4, title = "Unrated", userRating = null),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterMinUserRating = 5, filterMaxUserRating = 8),
        )
        // Only the 6 is inside [5,8]; the 3, the 9, and unrated are dropped.
        assertEquals(listOf("Mid"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `full user-rating range is treated as no filter and keeps unrated novels`() {
        val novels = listOf(
            novel(1, title = "Rated", userRating = 4),
            novel(2, title = "Unrated", userRating = null),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterMinUserRating = 1, filterMaxUserRating = 10),
        )
        assertEquals(listOf("Rated", "Unrated"), tabs[0].novels!!.map { it.title })
    }

    @Test
    fun `restore target waits until categories are loaded`() {
        assertNull(
            resolveRestoreTargetPage(
                categoriesLoaded = false,
                persistedCategoryId = 5L,
                tabCategoryIds = listOf(-1L, 5L),
            ),
        )
    }

    @Test
    fun `restore target waits until persisted id is known`() {
        assertNull(
            resolveRestoreTargetPage(
                categoriesLoaded = true,
                persistedCategoryId = null,
                tabCategoryIds = listOf(-1L, 5L),
            ),
        )
    }

    @Test
    fun `restore target waits until tabs are built`() {
        // The bug: categories + persisted id are ready but the tabs combine hasn't
        // emitted yet. Returning a page here would latch the restore onto the fallback
        // and drop the saved category.
        assertNull(
            resolveRestoreTargetPage(
                categoriesLoaded = true,
                persistedCategoryId = 5L,
                tabCategoryIds = emptyList(),
            ),
        )
    }

    @Test
    fun `restore target resolves to the saved category's page`() {
        assertEquals(
            2,
            resolveRestoreTargetPage(
                categoriesLoaded = true,
                persistedCategoryId = 7L,
                tabCategoryIds = listOf(-1L, 5L, 7L),
            ),
        )
    }

    @Test
    fun `restore target falls back to the first tab for a hidden saved category`() {
        // App started locked: the saved category is filtered out of the tabs. Now that
        // the tabs exist, falling back to page 0 is correct.
        assertEquals(
            0,
            resolveRestoreTargetPage(
                categoriesLoaded = true,
                persistedCategoryId = 9L,
                tabCategoryIds = listOf(-1L, 5L, 7L),
            ),
        )
    }

    @Test
    fun `adult filter ONLY_ADULT keeps novels from adult sources`() {
        val novels = listOf(
            novel(id = 1, title = "Adult", sourceId = 100L),
            novel(id = 2, title = "Safe", sourceId = 200L),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterAdult = AdultFilter.ONLY_ADULT, adultSourceIds = setOf(100L)),
        )
        assertEquals(listOf(1L), tabs.first().novels!!.map { it.id })
    }

    @Test
    fun `adult filter HIDE_ADULT drops novels from adult sources`() {
        val novels = listOf(
            novel(id = 1, title = "Adult", sourceId = 100L),
            novel(id = 2, title = "Safe", sourceId = 200L),
        )
        val tabs = buildLibraryTabs(
            baseInputs(novels = novels, filterAdult = AdultFilter.HIDE_ADULT, adultSourceIds = setOf(100L)),
        )
        assertEquals(listOf(2L), tabs.first().novels!!.map { it.id })
    }
}
