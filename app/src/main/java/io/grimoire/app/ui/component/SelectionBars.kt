package io.grimoire.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = { Text("$count selected") },
        navigationIcon = {
            PlainTooltipIconButton(onClick = onClear, tooltip = "Clear selection") {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
        },
        actions = {
            PlainTooltipIconButton(onClick = onSelectAll, tooltip = "Select all") {
                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
            }
        },
    )
}

/**
 * Bottom action bar that hosts [TooltipIconButton]s in a full-width row, so each
 * action can grow on long-press while siblings make room. Visibility animates
 * via [enter]/[exit] (callers can override to give each screen a distinct
 * slide), and [containerColor]/[contentColor] let the bar adopt non-default
 * theming (e.g. the reader's themed background).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipBottomBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
    exit: ExitTransition = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
    containerColor: Color = BottomAppBarDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable RowScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier,
    ) {
        BottomAppBar(
            containerColor = containerColor,
            contentColor = contentColor,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}
