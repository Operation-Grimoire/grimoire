package io.grimoire.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Which Material 3 tab row to draw above the pager. Mirrors the three flavours
 * the app already used by hand: full-width primary tabs (most screens), the
 * scrollable variant for an unbounded count (library categories), and the
 * lighter secondary style for tabs nested inside a sheet.
 */
enum class SwipeTabStyle { Primary, PrimaryScrollable, Secondary }

/**
 * A tab row wired to a [HorizontalPager] so every tabbed surface in the app
 * swaps tabs on a left/right swipe as well as a tap. Tapping a tab animates the
 * pager to that page; swiping the page moves the selection — the two stay in
 * sync because both read [pagerState].
 *
 * Use it anywhere tabs appear (full screens *and* bottom sheets). For a sheet,
 * pass [fillHeight] = `false` so the pager wraps its content height instead of
 * expanding the sheet to fill the screen.
 *
 * The default [pagerState] derives its page count from [tabs]; pass your own
 * when the selection needs to persist or be restored (e.g. the library remembers
 * the last-viewed category).
 *
 * @param fillHeight when true the row fills its parent and the pager takes the
 *   remaining height (full-screen use); when false both wrap their content
 *   (bottom-sheet use).
 * @param hideTabRowForSingleTab when true a lone tab renders no tab row — the
 *   page still shows, so a screen that only sometimes has multiple tabs needn't
 *   branch its layout.
 * @param badges optional per-tab badge text (index-aligned with [tabs]); null
 *   entries render no badge. Used for counts, e.g. sources with results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeTabRow(
    tabs: List<String>,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState(pageCount = { tabs.size }),
    style: SwipeTabStyle = SwipeTabStyle.Primary,
    fillHeight: Boolean = true,
    userScrollEnabled: Boolean = true,
    hideTabRowForSingleTab: Boolean = false,
    badges: List<String?>? = null,
    pageContent: @Composable (page: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tabCount = tabs.size.coerceAtLeast(1)
    val selected = pagerState.currentPage.coerceIn(0, tabCount - 1)

    Column(modifier = if (fillHeight) modifier.fillMaxSize() else modifier) {
        val showRow = tabs.isNotEmpty() && (tabs.size > 1 || !hideTabRowForSingleTab)
        if (showRow) {
            val tabSlots: @Composable () -> Unit = {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selected == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            val badge = badges?.getOrNull(index)
                            if (badge == null) {
                                Text(title)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(title)
                                    Badge { Text(badge) }
                                }
                            }
                        },
                    )
                }
            }
            when (style) {
                SwipeTabStyle.Primary ->
                    PrimaryTabRow(selectedTabIndex = selected) { tabSlots() }
                SwipeTabStyle.PrimaryScrollable ->
                    PrimaryScrollableTabRow(selectedTabIndex = selected) { tabSlots() }
                SwipeTabStyle.Secondary ->
                    SecondaryTabRow(selectedTabIndex = selected) { tabSlots() }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = if (fillHeight) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                Modifier.fillMaxWidth()
            },
            userScrollEnabled = userScrollEnabled,
            // Pages can differ in height (notably in sheets); pin them to the top
            // so the tab row doesn't appear to jump while a shorter page settles.
            verticalAlignment = Alignment.Top,
        ) { page ->
            pageContent(page)
        }
    }
}
