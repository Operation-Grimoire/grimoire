package io.grimoire.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.local.entity.CategoryEntity

/**
 * Bottom-sheet picker for moving one or more novels to a category. Tap a row
 * to apply. [count] drives the subtitle ("1 novel" / "N novels"). When
 * [showCurrent] is true, the row matching [currentCategoryId] (or the default
 * category if it is null) is tinted primary with a trailing check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToCategorySheet(
    categories: List<CategoryEntity>,
    count: Int,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
    currentCategoryId: Long? = null,
    showCurrent: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("Move to category", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (count == 1) "1 novel" else "$count novels",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            categories.forEach { cat ->
                val targetId = if (cat.isDefault) null else cat.id
                val isCurrent = showCurrent && (
                    if (cat.isDefault) currentCategoryId == null
                    else currentCategoryId == cat.id
                )
                ListItem(
                    headlineContent = { Text(cat.name) },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.Label,
                            contentDescription = null,
                            tint = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    trailingContent = if (isCurrent) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Current category",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                    modifier = Modifier.clickable { onSelect(targetId) },
                )
            }
        }
    }
}
