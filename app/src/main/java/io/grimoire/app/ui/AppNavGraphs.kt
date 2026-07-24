package io.grimoire.app.ui

import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import io.grimoire.app.ui.screen.browse.BrowseScreen
import io.grimoire.app.ui.screen.browse.BrowseViewModel
import io.grimoire.app.ui.screen.browse.GlobalSearchScreen
import io.grimoire.app.ui.screen.browse.NovelDetailScreen
import io.grimoire.app.ui.screen.browse.SourceBrowseScreen
import io.grimoire.app.ui.screen.crash.CrashReportScreen
import io.grimoire.app.ui.screen.downloads.DownloadsScreen
import io.grimoire.app.ui.screen.extensions.ExtensionsScreen
import io.grimoire.app.ui.screen.library.LibraryScreen
import io.grimoire.app.ui.screen.migrate.MigrateScreen
import io.grimoire.app.ui.screen.more.MoreScreen
import io.grimoire.app.ui.screen.tours.ToursScreen
import io.grimoire.app.ui.screen.more.statistics.StatisticsScreen
import io.grimoire.app.ui.screen.novelupdates.NovelUpdatesBookmarksScreen
import io.grimoire.app.ui.screen.novelupdates.NovelUpdatesBrowserScreen
import io.grimoire.app.ui.screen.novelupdates.NovelUpdatesSearchScreen
import io.grimoire.app.ui.screen.novelupdates.NovelUpdatesSeriesScreen
import io.grimoire.app.ui.screen.reader.ReaderScreen
import io.grimoire.app.ui.screen.settings.SettingsScreen
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.ui.screen.settings.about.AboutSettingsScreen
import io.grimoire.app.ui.screen.settings.appearance.AppearanceSettingsScreen
import io.grimoire.app.ui.screen.settings.backup.BackupSettingsScreen
import io.grimoire.app.ui.screen.settings.behavior.BehaviorSettingsScreen
import io.grimoire.app.ui.screen.settings.browse.BrowseLanguagesScreen
import io.grimoire.app.ui.screen.settings.browse.BrowseSettingsScreen
import io.grimoire.app.ui.screen.settings.connections.ConnectionsSettingsScreen
import io.grimoire.app.ui.screen.settings.data.DataSettingsScreen
import io.grimoire.app.ui.screen.settings.github.GitHubAuthScreen
import io.grimoire.app.ui.screen.settings.hidden.HiddenCategoriesSettingsScreen
import io.grimoire.app.ui.screen.settings.languages.LanguagesSettingsScreen
import io.grimoire.app.ui.screen.settings.library.LibrarySettingsScreen
import io.grimoire.app.ui.screen.settings.libraryupdate.LibraryUpdateSettingsScreen
import io.grimoire.app.ui.screen.settings.novelupdates.NovelUpdatesSettingsScreen
import io.grimoire.app.ui.screen.settings.reader.ReaderSettingsScreen
import io.grimoire.app.ui.screen.settings.source.SourceLanguagesScreen
import io.grimoire.app.ui.screen.settings.source.SourceSettingsScreen
import io.grimoire.app.ui.screen.settings.tts.TtsSettingsScreen
import io.grimoire.app.ui.screen.settings.tts.TtsVoicePickerScreen
import io.grimoire.app.ui.screen.tasks.TasksScreen
import io.grimoire.app.ui.screen.history.HistoryScreen
import io.grimoire.app.ui.screen.updates.LibraryUpdatesScreen
import io.grimoire.app.ui.screen.updates.UpdateIssuesScreen
import io.grimoire.app.ui.screen.webview.SOURCE_LOGIN_RESULT_KEY
import io.grimoire.app.ui.screen.webview.SourceLoginScreen
import io.grimoire.app.ui.screen.webview.WebViewScreen

internal fun NavGraphBuilder.libraryDestination(
    navController: NavHostController,
    onSelectionActiveChange: (Boolean) -> Unit,
    pendingEpubUri: StateFlow<Uri?>,
    onEpubUriHandled: () -> Unit,
) {
    composable(route = TopLevelDestination.Library.route) {
        LibraryScreen(
            onNovelClick = { pkg, url, sourceId ->
                navController.navigate(
                    "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(url)}&sourceId=$sourceId"
                )
            },
            onBrowse = {
                navController.navigate(TopLevelDestination.Browse.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onSelectionActiveChange = onSelectionActiveChange,
            pendingEpubUri = pendingEpubUri,
            onEpubUriHandled = onEpubUriHandled,
        )
    }
}

internal fun NavGraphBuilder.browseGraph(
    navController: NavHostController,
    onSelectionActiveChange: (Boolean) -> Unit = {},
) {
    navigation(
        startDestination = ROUTE_BROWSE_HOME,
        route = TopLevelDestination.Browse.route,
    ) {
        composable(route = ROUTE_BROWSE_HOME) { entry ->
            val graphEntry = remember(entry) {
                navController.getBackStackEntry(TopLevelDestination.Browse.route)
            }
            val vm: BrowseViewModel = hiltViewModel(graphEntry)
            BrowseScreen(
                onNavigateToManage = { navController.navigate("extensions") },
                onNavigateToSource = { pkg -> navController.navigate("browse/$pkg") },
                onNavigateToGlobalSearch = { navController.navigate(ROUTE_GLOBAL_SEARCH) },
                onNavigateToNovelUpdatesSearch = { navController.navigate(ROUTE_NU_SEARCH) },
                onNavigateToNovelUpdatesRankings = {
                    navController.navigate("nu_browser?mode=RANKINGS")
                },
                onNavigateToNovelUpdatesLatest = {
                    navController.navigate("nu_browser?mode=LATEST")
                },
                onNavigateToNovelUpdatesBookmarks = {
                    navController.navigate(ROUTE_NU_BOOKMARKS)
                },
                onOpenSourceSettings = { pkg ->
                    navController.navigate("settings/source/${Uri.encode(pkg)}")
                },
                onSelectionActiveChange = onSelectionActiveChange,
                viewModel = vm,
            )
        }
        composable(
            route = ROUTE_GLOBAL_SEARCH_ARG,
            arguments = listOf(
                navArgument("q") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
            ),
        ) { entry ->
            val graphEntry = remember(entry) {
                navController.getBackStackEntry(TopLevelDestination.Browse.route)
            }
            val vm: BrowseViewModel = hiltViewModel(graphEntry)
            val q = entry.arguments?.getString("q")
            LaunchedEffect(q) {
                if (!q.isNullOrBlank()) {
                    vm.setQuery(q)
                    vm.submitSearch()
                }
            }
            GlobalSearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNovelClick = { novel, pkg ->
                    navController.navigate(
                        "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(novel.url)}"
                    )
                },
                onChapterClick = { pkg, novelUrl, chapterUrl ->
                    navController.navigate(
                        "reader?pkg=${Uri.encode(pkg)}&novelUrl=${Uri.encode(novelUrl)}&chapterUrl=${Uri.encode(chapterUrl)}"
                    )
                },
                onNavigateToSourceSearch = { pkg, query ->
                    navController.navigate("browse/$pkg?q=${Uri.encode(query)}")
                },
                viewModel = vm,
            )
        }
        composable(
            route = ROUTE_NU_BROWSER,
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
            ),
        ) {
            NovelUpdatesBrowserScreen(
                onNavigateBack = { navController.popBackStack() },
                onSeriesClick = { slug ->
                    navController.navigate("nu_series?slug=${Uri.encode(slug)}")
                },
                onOpenWebView = { url ->
                    navController.navigate("webview?url=${Uri.encode(url)}")
                },
            )
        }
        composable(route = ROUTE_NU_BOOKMARKS) {
            NovelUpdatesBookmarksScreen(
                onNavigateBack = { navController.popBackStack() },
                onSeriesClick = { slug ->
                    navController.navigate("nu_series?slug=${Uri.encode(slug)}")
                },
            )
        }
        composable(route = ROUTE_NU_SEARCH) {
            NovelUpdatesSearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onSeriesClick = { slug ->
                    navController.navigate("nu_series?slug=${Uri.encode(slug)}")
                },
                onOpenWebView = { url ->
                    navController.navigate("webview?url=${Uri.encode(url)}")
                },
            )
        }
        composable(
            route = ROUTE_NU_SERIES,
            arguments = listOf(navArgument("slug") { type = NavType.StringType }),
        ) {
            NovelUpdatesSeriesScreen(
                onNavigateBack = { navController.popBackStack() },
                onFindInSources = { title ->
                    navController.navigate("global_search?q=${Uri.encode(title)}")
                },
                onOpenSeries = { slug ->
                    navController.navigate("nu_series?slug=${Uri.encode(slug)}")
                },
                onOpenWebView = { url ->
                    navController.navigate("webview?url=${Uri.encode(url)}")
                },
                onOpenSource = { pkg, query ->
                    navController.navigate("browse/${Uri.encode(pkg)}?q=${Uri.encode(query)}")
                },
            )
        }
    }
}

internal fun NavGraphBuilder.moreDestinations(navController: NavHostController) {
    composable(route = TopLevelDestination.More.route) {
        MoreScreen(
            onNavigateToTasks = { navController.navigate(ROUTE_TASKS) },
            onNavigateToUpdates = { navController.navigate(ROUTE_UPDATES) },
            onNavigateToHistory = { navController.navigate(ROUTE_HISTORY) },
            onNavigateToWarnings = { navController.navigate(ROUTE_UPDATE_ISSUES) },
            onNavigateToDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
            onNavigateToStatistics = { navController.navigate(ROUTE_STATISTICS) },
            onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS_ROOT) },
            onNavigateToTours = { navController.navigate(ROUTE_TOURS) },
            onNavigateToAbout = { navController.navigate(ROUTE_ABOUT) },
        )
    }
    composable(route = ROUTE_TOURS) {
        ToursScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(route = ROUTE_DOWNLOADS) {
        DownloadsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(route = ROUTE_STATISTICS) {
        StatisticsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(route = ROUTE_TASKS) {
        TasksScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(route = ROUTE_UPDATES) {
        LibraryUpdatesScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenReader = { pkg, novelUrl, chapterUrl ->
                navController.navigate(
                    "reader?pkg=${Uri.encode(pkg)}&novelUrl=${Uri.encode(novelUrl)}&chapterUrl=${Uri.encode(chapterUrl)}"
                )
            },
            onOpenNovel = { pkg, novelUrl ->
                navController.navigate(
                    "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(novelUrl)}"
                )
            },
        )
    }
    composable(route = ROUTE_HISTORY) {
        HistoryScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenReader = { pkg, novelUrl, chapterUrl ->
                navController.navigate(
                    "reader?pkg=${Uri.encode(pkg)}&novelUrl=${Uri.encode(novelUrl)}&chapterUrl=${Uri.encode(chapterUrl)}"
                )
            },
            onOpenNovel = { pkg, novelUrl ->
                navController.navigate(
                    "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(novelUrl)}"
                )
            },
        )
    }
    composable(route = ROUTE_UPDATE_ISSUES) {
        UpdateIssuesScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenNovel = { pkg, novelUrl ->
                navController.navigate(
                    "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(novelUrl)}"
                )
            },
        )
    }
    composable(route = ROUTE_ABOUT) {
        AboutSettingsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(route = ROUTE_CRASH_REPORT) {
        CrashReportScreen(onNavigateBack = { navController.popBackStack() })
    }
}

internal fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    navigation(
        startDestination = ROUTE_SETTINGS_ROOT,
        route = SETTINGS_GRAPH_ROUTE,
    ) {
        composable(route = ROUTE_SETTINGS_ROOT) { entry ->
            val graphEntry = remember(entry) { navController.getBackStackEntry(SETTINGS_GRAPH_ROUTE) }
            val vm: SettingsViewModel = hiltViewModel(graphEntry)
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAppearance = { navController.navigate(ROUTE_SETTINGS_APPEARANCE) },
                onNavigateToBehavior = { navController.navigate(ROUTE_SETTINGS_BEHAVIOR) },
                onNavigateToLibrary = { navController.navigate(ROUTE_SETTINGS_LIBRARY) },
                onNavigateToBrowse = { navController.navigate(ROUTE_SETTINGS_BROWSE) },
                onNavigateToLanguages = { navController.navigate(ROUTE_SETTINGS_LANGUAGES) },
                onNavigateToReader = { navController.navigate(ROUTE_SETTINGS_READER) },
                onNavigateToTts = { navController.navigate(ROUTE_SETTINGS_TTS) },
                onNavigateToLibraryUpdates = { navController.navigate(ROUTE_SETTINGS_LIBRARY_UPDATE) },
                onNavigateToBackup = { navController.navigate(ROUTE_SETTINGS_BACKUP) },
                onNavigateToData = { navController.navigate(ROUTE_SETTINGS_DATA) },
                onNavigateToNovelUpdates = { navController.navigate(ROUTE_SETTINGS_NOVELUPDATES) },
                onNavigateToConnections = { navController.navigate(ROUTE_SETTINGS_CONNECTIONS) },
            )
        }
        composable(route = ROUTE_SETTINGS_CONNECTIONS) {
            ConnectionsSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGitHub = { navController.navigate(ROUTE_SETTINGS_GITHUB) },
            )
        }
        composable(route = ROUTE_SETTINGS_GITHUB) {
            GitHubAuthScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(route = ROUTE_SETTINGS_APPEARANCE) { entry ->
            val graphEntry = remember(entry) { navController.getBackStackEntry(SETTINGS_GRAPH_ROUTE) }
            val vm: SettingsViewModel = hiltViewModel(graphEntry)
            AppearanceSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
        }
        composable(route = ROUTE_SETTINGS_BEHAVIOR) { entry ->
            val graphEntry = remember(entry) { navController.getBackStackEntry(SETTINGS_GRAPH_ROUTE) }
            val vm: SettingsViewModel = hiltViewModel(graphEntry)
            BehaviorSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
        }
        composable(route = ROUTE_SETTINGS_LIBRARY) { entry ->
            val graphEntry = remember(entry) { navController.getBackStackEntry(SETTINGS_GRAPH_ROUTE) }
            val vm: SettingsViewModel = hiltViewModel(graphEntry)
            LibrarySettingsScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHiddenCategories = { navController.navigate(ROUTE_SETTINGS_HIDDEN) },
            )
        }
        composable(route = ROUTE_SETTINGS_HIDDEN) {
            HiddenCategoriesSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(route = ROUTE_SETTINGS_BROWSE) { entry ->
            val graphEntry = remember(entry) { navController.getBackStackEntry(SETTINGS_GRAPH_ROUTE) }
            val vm: SettingsViewModel = hiltViewModel(graphEntry)
            BrowseSettingsScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(route = ROUTE_SETTINGS_LANGUAGES) {
            LanguagesSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSourceLanguages = {
                    navController.navigate(ROUTE_SETTINGS_CONTENT_LANGUAGES)
                },
            )
        }
        composable(route = ROUTE_SETTINGS_CONTENT_LANGUAGES) {
            BrowseLanguagesScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(route = ROUTE_SETTINGS_READER) { entry ->
            val graphEntry = remember(entry) { navController.getBackStackEntry(SETTINGS_GRAPH_ROUTE) }
            val vm: SettingsViewModel = hiltViewModel(graphEntry)
            ReaderSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
        }
        composable(route = ROUTE_SETTINGS_TTS) {
            TtsSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVoice = { lang ->
                    navController.navigate("settings/tts/voice?lang=${Uri.encode(lang)}")
                },
            )
        }
        composable(
            route = ROUTE_SETTINGS_TTS_VOICE,
            arguments = listOf(navArgument("lang") { type = NavType.StringType }),
        ) {
            TtsVoicePickerScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(route = ROUTE_SETTINGS_NOVELUPDATES) { entry ->
            val graphEntry = remember(entry) { navController.getBackStackEntry(SETTINGS_GRAPH_ROUTE) }
            val vm: SettingsViewModel = hiltViewModel(graphEntry)
            NovelUpdatesSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
        }
        composable(route = ROUTE_SETTINGS_BACKUP) {
            BackupSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(route = ROUTE_SETTINGS_DATA) {
            DataSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(route = ROUTE_SETTINGS_LIBRARY_UPDATE) {
            LibraryUpdateSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

internal fun NavGraphBuilder.sourceDestinations(
    navController: NavHostController,
    pendingAddRepo: StateFlow<PendingAddRepo?> = MutableStateFlow(null),
    onAddRepoHandled: () -> Unit = {},
) {
    composable(
        route = ROUTE_EXTENSION_MANAGE,
        arguments = listOf(
            navArgument("query") { type = NavType.StringType; defaultValue = "" },
        ),
    ) { entry ->
        ExtensionsScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenSourceSettings = { pkg ->
                navController.navigate("settings/source/${Uri.encode(pkg)}")
            },
            onConnectGitHub = { navController.navigate(ROUTE_SETTINGS_GITHUB) },
            pendingAddRepo = pendingAddRepo,
            onAddRepoHandled = onAddRepoHandled,
            prefillQuery = entry.arguments?.getString("query").orEmpty(),
        )
    }
    composable(
        route = ROUTE_SOURCE_BROWSE,
        arguments = listOf(
            navArgument("pkg") { type = NavType.StringType },
            navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { entry ->
        val pkg = entry.arguments?.getString("pkg") ?: ""
        SourceBrowseScreen(
            onNavigateBack = { navController.popBackStack() },
            onNovelClick = { novel ->
                navController.navigate(
                    "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(novel.url)}"
                )
            },
            onChapterClick = { p, novelUrl, chapterUrl ->
                navController.navigate(
                    "reader?pkg=${Uri.encode(p)}&novelUrl=${Uri.encode(novelUrl)}&chapterUrl=${Uri.encode(chapterUrl)}"
                )
            },
            onOpenWebView = { url ->
                navController.navigate("webview?url=${Uri.encode(url)}")
            },
            onOpenSourceSettings = {
                navController.navigate("settings/source/${Uri.encode(pkg)}")
            },
        )
    }
    composable(
        route = ROUTE_SOURCE_SETTINGS,
        arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
    ) { entry ->
        val pkg = entry.arguments?.getString("pkg") ?: ""
        SourceSettingsScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToContentLanguages = {
                navController.navigate("settings/source/${Uri.encode(pkg)}/languages")
            },
            onNavigateToLogin = {
                navController.navigate("login/${Uri.encode(pkg)}")
            },
        )
    }
    composable(
        route = ROUTE_SOURCE_LANGUAGES,
        arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
    ) {
        SourceLanguagesScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = ROUTE_SOURCE_LOGIN,
        arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
    ) {
        SourceLoginScreen(
            onNavigateBack = {
                // Tell the screen that opened login to re-check sign-in state.
                navController.previousBackStackEntry?.savedStateHandle
                    ?.set(SOURCE_LOGIN_RESULT_KEY, true)
                navController.popBackStack()
            },
        )
    }
}

internal fun NavGraphBuilder.novelDetailDestinations(navController: NavHostController) {
    composable(
        route = ROUTE_NOVEL_DETAIL,
        arguments = listOf(
            navArgument("pkg") { type = NavType.StringType },
            navArgument("url") { type = NavType.StringType },
            navArgument("migrateFrom") { type = NavType.LongType; defaultValue = -1L },
            // Fallback source identity for a novel whose extension is uninstalled
            // (so its pkg can't be resolved); 0 = derive from pkg as usual.
            navArgument("sourceId") { type = NavType.LongType; defaultValue = 0L },
        ),
    ) {
        NovelDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onChapterClick = { pkg, novelUrl, chapterUrl ->
                navController.navigate(
                    "reader?pkg=${Uri.encode(pkg)}&novelUrl=${Uri.encode(novelUrl)}&chapterUrl=${Uri.encode(chapterUrl)}"
                )
            },
            onOpenWebView = { url ->
                navController.navigate("webview?url=${Uri.encode(url)}")
            },
            onOpenNuSeries = { slug ->
                navController.navigate("nu_series?slug=${Uri.encode(slug)}")
            },
            onNavigateToLogin = { pkg ->
                navController.navigate("login/${Uri.encode(pkg)}")
            },
            onOpenSourceSettings = { pkg ->
                navController.navigate("settings/source/${Uri.encode(pkg)}")
            },
            onOpenExtensions = { query ->
                navController.navigate("extensions?query=${Uri.encode(query)}")
            },
            onMigrate = { novelId ->
                navController.navigate("migrate?novelId=$novelId")
            },
            onMigrationComplete = { pkg, url ->
                navController.navigate(
                    "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(url)}"
                ) {
                    popUpTo(TopLevelDestination.Library.route) { inclusive = false }
                }
            },
        )
    }
    composable(
        route = ROUTE_MIGRATE,
        arguments = listOf(navArgument("novelId") { type = NavType.LongType }),
    ) { entry ->
        val sourceId = entry.arguments?.getLong("novelId") ?: -1L
        MigrateScreen(
            onNavigateBack = { navController.popBackStack() },
            onPreviewNovel = { pkg, url ->
                navController.navigate(
                    "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(url)}&migrateFrom=$sourceId"
                )
            },
        )
    }
}

internal fun NavGraphBuilder.readerDestinations(navController: NavHostController) {
    composable(
        route = ROUTE_READER,
        arguments = listOf(
            navArgument("pkg") { type = NavType.StringType },
            navArgument("novelUrl") { type = NavType.StringType },
            navArgument("chapterUrl") { type = NavType.StringType },
        ),
    ) {
        ReaderScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenWebView = { url ->
                navController.navigate("webview?url=${Uri.encode(url)}")
            },
            onOpenTtsSettings = { navController.navigate(ROUTE_SETTINGS_TTS) },
        )
    }
    composable(
        route = ROUTE_WEBVIEW,
        arguments = listOf(navArgument("url") { type = NavType.StringType }),
    ) { entry ->
        val url = entry.arguments?.getString("url") ?: ""
        WebViewScreen(url = url, onNavigateBack = { navController.popBackStack() })
    }
}

