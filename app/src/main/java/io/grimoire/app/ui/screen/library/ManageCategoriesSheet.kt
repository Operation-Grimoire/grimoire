package io.grimoire.app.ui.screen.library

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.R
import io.grimoire.app.data.local.entity.CategoryEntity
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

@Composable
internal fun ManageCategoriesSheet(
    categories: List<CategoryEntity>,
    isUnlocked: Boolean,
    hasPin: Boolean,
    onAdd: (String) -> Unit,
    onRename: (CategoryEntity, String) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    onToggleHidden: (CategoryEntity, Boolean) -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onUnlockRequest: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var renamingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    Column(Modifier.padding(bottom = 32.dp)) {
        Text(
            stringResource(R.string.library_manage_categories_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        if (hasPin && !isUnlocked) {
            TextButton(
                onClick = onUnlockRequest,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(AppIcons.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library_unlock_to_manage_hidden))
            }
        }
        ReorderableColumn(
            list = categories,
            onSettle = { from, to -> onMove(from, to) },
            modifier = Modifier.fillMaxWidth(),
        ) { _, cat, isDragging ->
            key(cat.id) {
                ReorderableItem {
                    Surface(tonalElevation = if (isDragging) 4.dp else 0.dp) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(cat.name) },
                            leadingContent = {
                                Icon(
                                    AppIcons.DragHandle,
                                    contentDescription = stringResource(
                                        R.string.library_drag_to_reorder,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .draggableHandle()
                                        .size(40.dp)
                                        .padding(8.dp),
                                )
                            },
                            trailingContent = {
                                Row {
                                    if (isUnlocked && !cat.isDefault) {
                                        val visibilityLabel = if (cat.isHidden) {
                                            stringResource(R.string.library_unhide_category)
                                        } else {
                                            stringResource(R.string.library_hide_category)
                                        }
                                        PlainTooltipIconButton(
                                            onClick = { onToggleHidden(cat, !cat.isHidden) },
                                            tooltip = visibilityLabel,
                                        ) {
                                            Icon(
                                                if (cat.isHidden) AppIcons.VisibilityOff else AppIcons.Visibility,
                                                contentDescription = visibilityLabel,
                                            )
                                        }
                                    }
                                    PlainTooltipIconButton(
                                        onClick = { renamingCategory = cat },
                                        tooltip = stringResource(R.string.library_rename_category),
                                    ) {
                                        Icon(
                                            AppIcons.Edit,
                                            contentDescription = stringResource(
                                                R.string.library_rename_category,
                                            ),
                                        )
                                    }
                                    if (!cat.isDefault) {
                                        PlainTooltipIconButton(
                                            onClick = { onDelete(cat) },
                                            tooltip = stringResource(R.string.library_delete_category),
                                        ) {
                                            Icon(
                                                AppIcons.Delete,
                                                contentDescription = stringResource(
                                                    R.string.library_delete_category,
                                                ),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        TextButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(AppIcons.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.library_add_category))
        }
    }

    if (showAddDialog) {
        CategoryNameDialog(
            title = stringResource(R.string.library_add_category),
            onConfirm = { name -> onAdd(name); showAddDialog = false },
            onDismiss = { showAddDialog = false },
        )
    }

    renamingCategory?.let { cat ->
        CategoryNameDialog(
            title = stringResource(R.string.library_rename_category),
            initial = cat.name,
            onConfirm = { name -> onRename(cat, name); renamingCategory = null },
            onDismiss = { renamingCategory = null },
        )
    }
}

@Composable
private fun CategoryNameDialog(
    title: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.library_category_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.library_save_category)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
