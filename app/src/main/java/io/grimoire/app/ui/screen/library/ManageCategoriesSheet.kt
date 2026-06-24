package io.grimoire.app.ui.screen.library

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.local.entity.CategoryEntity

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
            "Manage Categories",
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
                Text("Unlock to manage hidden categories")
            }
        }
        categories.forEachIndexed { index, cat ->
            ListItem(
                headlineContent = { Text(cat.name) },
                leadingContent = {
                    Column {
                        PlainTooltipIconButton(
                            onClick = { onMove(index, index - 1) },
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp), tooltip = "Move up") {
                            Icon(
                                AppIcons.KeyboardArrowUp,
                                contentDescription = "Move up",
                            )
                        }
                        PlainTooltipIconButton(
                            onClick = { onMove(index, index + 1) },
                            enabled = index < categories.lastIndex,
                            modifier = Modifier.size(28.dp), tooltip = "Move down") {
                            Icon(
                                AppIcons.KeyboardArrowDown,
                                contentDescription = "Move down",
                            )
                        }
                    }
                },
                trailingContent = {
                    Row {
                        if (isUnlocked && !cat.isDefault) {
                            PlainTooltipIconButton(onClick = { onToggleHidden(cat, !cat.isHidden) }, tooltip = if (cat.isHidden) "Unhide" else "Hide") {
                                Icon(
                                    if (cat.isHidden) AppIcons.VisibilityOff else AppIcons.Visibility,
                                    contentDescription = if (cat.isHidden) "Unhide" else "Hide",
                                )
                            }
                        }
                        PlainTooltipIconButton(onClick = { renamingCategory = cat }, tooltip = "Rename") {
                            Icon(AppIcons.Edit, contentDescription = "Rename")
                        }
                        if (!cat.isDefault) {
                            PlainTooltipIconButton(onClick = { onDelete(cat) }, tooltip = "Delete") {
                                Icon(
                                    AppIcons.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        TextButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(AppIcons.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add category")
        }
    }

    if (showAddDialog) {
        CategoryNameDialog(
            title = "Add category",
            onConfirm = { name -> onAdd(name); showAddDialog = false },
            onDismiss = { showAddDialog = false },
        )
    }

    renamingCategory?.let { cat ->
        CategoryNameDialog(
            title = "Rename",
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
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
