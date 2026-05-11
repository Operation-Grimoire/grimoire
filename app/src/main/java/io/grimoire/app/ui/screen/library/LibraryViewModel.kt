package io.grimoire.app.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.preferences.LibraryDisplayMode
import io.grimoire.app.data.preferences.LibraryPreferences
import io.grimoire.app.data.preferences.LibrarySort
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val novelDao: NovelDao,
    private val categoryDao: CategoryDao,
    private val chapterDao: ChapterDao,
    private val extensionManager: ExtensionManager,
    private val libraryPreferences: LibraryPreferences,
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val novels: StateFlow<List<NovelEntity>?> = novelDao.getFavorites()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val chapterStats: StateFlow<Map<Long, NovelChapterStats>> = chapterDao.getStatsForAll()
        .map { list -> list.associateBy { it.novelId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val displayMode: StateFlow<LibraryDisplayMode> = libraryPreferences.displayMode.stateIn(viewModelScope)
    val gridColumns: StateFlow<Int> = libraryPreferences.gridColumns.stateIn(viewModelScope)
    val showAllTab: StateFlow<Boolean> = libraryPreferences.showAllTab.stateIn(viewModelScope)
    val sortOrder: StateFlow<LibrarySort> = libraryPreferences.sortOrder.stateIn(viewModelScope)
    val filterStatus: StateFlow<Int> = libraryPreferences.filterStatus.stateIn(viewModelScope)
    val filterUnreadOnly: StateFlow<Boolean> = libraryPreferences.filterUnreadOnly.stateIn(viewModelScope)
    val filterDownloadedOnly: StateFlow<Boolean> = libraryPreferences.filterDownloadedOnly.stateIn(viewModelScope)

    fun pkgForNovel(novel: NovelEntity): String =
        extensionManager.extensions.value
            .firstOrNull { it.source.id == novel.sourceId }
            ?.info?.packageName ?: ""

    fun addCategory(name: String) = viewModelScope.launch {
        categoryDao.upsert(CategoryEntity(name = name.trim(), order = categories.value.size))
    }

    fun renameCategory(category: CategoryEntity, name: String) = viewModelScope.launch {
        categoryDao.upsert(category.copy(name = name.trim()))
    }

    fun deleteCategory(category: CategoryEntity) = viewModelScope.launch {
        novelDao.clearCategory(category.id)
        categoryDao.delete(category)
    }

    fun moveNovel(novel: NovelEntity, categoryId: Long?) = viewModelScope.launch {
        novelDao.updateCategory(novel.id, categoryId)
    }

    fun removeFromLibrary(novel: NovelEntity) = viewModelScope.launch {
        novelDao.upsert(novel.copy(favorite = false))
    }

    fun setDisplayMode(mode: LibraryDisplayMode) = viewModelScope.launch {
        libraryPreferences.displayMode.set(mode)
    }

    fun setGridColumns(count: Int) = viewModelScope.launch {
        libraryPreferences.gridColumns.set(count.coerceIn(2, 5))
    }

    fun setSortOrder(sort: LibrarySort) = viewModelScope.launch {
        libraryPreferences.sortOrder.set(sort)
    }

    fun setFilterStatus(status: Int) = viewModelScope.launch {
        libraryPreferences.filterStatus.set(status)
    }

    fun setFilterUnreadOnly(value: Boolean) = viewModelScope.launch {
        libraryPreferences.filterUnreadOnly.set(value)
    }

    fun setFilterDownloadedOnly(value: Boolean) = viewModelScope.launch {
        libraryPreferences.filterDownloadedOnly.set(value)
    }
}
