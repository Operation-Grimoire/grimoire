package io.grimoire.app.ui.screen.novelupdates

import io.grimoire.app.ui.icon.*
import android.content.Intent
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.novelupdates.NovelUpdatesEndpoints
import io.grimoire.app.data.novelupdates.NuReview
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.ui.component.ExpandableText
import io.grimoire.app.ui.component.GenreChips
import io.grimoire.app.ui.component.ZoomableCoverImage
import io.grimoire.app.ui.screen.extensions.InstallState
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelUpdatesSeriesScreen(
    onNavigateBack: () -> Unit,
    onFindInSources: (title: String) -> Unit,
    onOpenSeries: (slug: String) -> Unit,
    onOpenWebView: (url: String) -> Unit,
    onOpenSource: (pkg: String, query: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NovelUpdatesSeriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sourceLinks by viewModel.sourceLinks.collectAsState()
    val installStates by viewModel.installStates.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()

    // Mirror the Extensions screen's install handoff: the VM downloads + verifies
    // the APK, then surfaces a File the screen hands to the system installer.
    val context = LocalContext.current
    val pendingInstall by viewModel.pendingInstall.collectAsState()
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onInstallResult() }
    LaunchedEffect(pendingInstall) {
        pendingInstall?.let { file ->
            viewModel.consumePendingInstall()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            installLauncher.launch(intent)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.nu_title)) },
                actions = {
                    val s = state
                    if (s is NuSeriesState.Loaded) {
                        val bookmarkLabel = stringResource(
                            if (isBookmarked) R.string.nu_remove_from_saved else R.string.nu_save,
                        )
                        PlainTooltipIconButton(onClick = viewModel::toggleBookmark, tooltip = bookmarkLabel) {
                            Icon(
                                if (isBookmarked) AppIcons.Inventory2
                                else AppIcons.Inventory2,
                                contentDescription = bookmarkLabel,
                            )
                        }
                        PlainTooltipIconButton(onClick = { onOpenWebView(s.series.url) }, tooltip = stringResource(R.string.action_open_in_webview)) {
                            Icon(
                                AppIcons.Language,
                                contentDescription = stringResource(R.string.action_open_in_webview),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is NuSeriesState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    Alignment.Center,
                ) { CircularProgressIndicator() }

                is NuSeriesState.Error -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { viewModel.retry() }) { Text(stringResource(R.string.action_retry)) }
                }

                is NuSeriesState.Loaded -> {
                    val series = s.series

                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ZoomableCoverImage(
                                model = series.coverUrl,
                                contentDescription = series.title,
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(156.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    series.title,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (series.authors.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        series.authors.joinToString(" · "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (series.artists.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        stringResource(R.string.nu_artists, series.artists.joinToString(" · ")),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val typeLine = listOfNotNull(
                                    series.type,
                                    series.language,
                                    series.year,
                                ).distinct().joinToString(" · ")
                                if (typeLine.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        typeLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.nu_listing_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        if (series.genres.isNotEmpty()) {
                            GenreChips(genres = series.genres.map { localizedNuGenre(it) })
                        }

                        series.description?.let { desc ->
                            Text(stringResource(R.string.nu_description), style = MaterialTheme.typography.titleSmall)
                            ExpandableText(text = desc)
                        }

                        Button(
                            onClick = { onFindInSources(series.title) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                AppIcons.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.nu_find_sources))
                        }

                        if (sourceLinks.isNotEmpty()) {
                            NuSourceLinks(
                                links = sourceLinks,
                                installStates = installStates,
                                onOpen = { pkg -> onOpenSource(pkg, series.title) },
                                onInstall = viewModel::install,
                            )
                        }

                        NovelUpdatesSeriesContent(
                            series = series,
                            onRecommendationClick = { url ->
                                onOpenSeries(NovelUpdatesEndpoints.slugFromUrl(url))
                            },
                        )

                        NuDetails(series)

                        if (series.releases.isNotEmpty()) {
                            NuReleases(
                                releases = series.releases,
                                onMore = { onOpenWebView(series.url) },
                            )
                        }

                        if (series.reviews.isNotEmpty()) {
                            NuReviews(
                                reviews = series.reviews,
                                reviewCount = series.reviewCount,
                                pageCount = series.reviewPageCount,
                                onMore = { onOpenWebView(series.url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sources whose declared NovelUpdates groups match this series' release groups
 * or English publisher. Installed ones offer "Open" (deep-links into the source
 * browser, pre-searching the series title); not-yet-installed ones offer a
 * one-tap "Install" reusing the extension installer.
 */
@Composable
private fun NuSourceLinks(
    links: List<ExtensionItem>,
    installStates: Map<String, InstallState>,
    onOpen: (pkg: String) -> Unit,
    onInstall: (ExtensionItem.Available) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.nu_read_with), style = MaterialTheme.typography.titleSmall)
        links.forEach { item ->
            val install = installStates[item.packageName]
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                        val isError = install is InstallState.Error
                        val sub = when {
                            isError -> (install as InstallState.Error).message
                            item is ExtensionItem.Available -> stringResource(R.string.nu_tap_to_install)
                            else -> stringResource(R.string.nu_installed_open)
                        }
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when {
                        install is InstallState.Downloading ->
                            CircularProgressIndicator(
                                Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )

                        item is ExtensionItem.Available -> Button(onClick = { onInstall(item) }) {
                            Icon(
                                AppIcons.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (install is InstallState.Error) R.string.action_retry else R.string.action_install))
                        }

                        else -> Button(onClick = { onOpen(item.packageName) }) {
                            Icon(
                                AppIcons.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_open))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NuDetails(series: io.grimoire.app.data.novelupdates.NuSeries) {
    val rows = buildList {
        series.year?.let { add(stringResource(R.string.nu_year) to it) }
        series.releaseFrequency?.let { add(stringResource(R.string.nu_release_frequency) to it) }
        series.licensed?.let {
            add(stringResource(R.string.nu_licensed) to stringResource(if (it) R.string.nu_yes else R.string.nu_no))
        }
        series.completelyTranslated?.let {
            add(
                stringResource(R.string.nu_completely_translated) to
                    stringResource(if (it) R.string.nu_yes else R.string.nu_no),
            )
        }
        if (series.originalPublishers.isNotEmpty()) {
            add(stringResource(R.string.nu_original_publisher) to series.originalPublishers.joinToString(" · "))
        }
        if (series.englishPublishers.isNotEmpty()) {
            add(stringResource(R.string.nu_english_publisher) to series.englishPublishers.joinToString(" · "))
        }
        series.readingListCount?.let {
            add(stringResource(R.string.nu_reading_lists) to "%,d".format(it))
        }
    }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.nu_details), style = MaterialTheme.typography.titleSmall)
        rows.forEach { (label, value) ->
            Row {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(140.dp),
                )
                Text(value, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NuReleases(
    releases: List<io.grimoire.app.data.novelupdates.NuRelease>,
    onMore: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) releases else releases.take(5)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.nu_latest_releases), style = MaterialTheme.typography.titleSmall)
        shown.forEach { rel ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rel.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(84.dp),
                )
                Text(
                    rel.chapter,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    rel.group,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (releases.size > 5) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) stringResource(R.string.nu_show_less)
                    else stringResource(R.string.nu_show_all, releases.size),
                )
            }
        }
        TextButton(onClick = onMore) {
            Text(stringResource(R.string.nu_view_all))
        }
    }
}

@Composable
private fun NuReviews(
    reviews: List<NuReview>,
    reviewCount: Int?,
    pageCount: Int,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            reviewCount?.let { stringResource(R.string.nu_reviews_count, it) }
                ?: stringResource(R.string.nu_reviews),
            style = MaterialTheme.typography.titleSmall,
        )
        reviews.forEach { review ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        review.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    review.rating?.let { stars ->
                        Icon(
                            AppIcons.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("$stars/5", style = MaterialTheme.typography.bodySmall)
                    }
                }
                val meta = listOfNotNull(
                    review.date,
                    review.progress?.let { stringResource(R.string.nu_review_progress, it) },
                    review.likes?.let { pluralStringResource(R.plurals.nu_review_likes, it, it) },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpandableText(text = review.body, collapsedMaxLines = 4)
            }
            HorizontalDivider()
        }
        if (pageCount > 1) {
            TextButton(onClick = onMore) {
                Text(stringResource(R.string.nu_more_reviews))
            }
        }
    }
}
