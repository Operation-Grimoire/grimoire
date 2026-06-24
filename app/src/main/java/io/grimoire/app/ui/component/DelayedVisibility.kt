package io.grimoire.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * True only after [active] has stayed true for [delayMillis]. Avoids loader flashes for
 * fast loads (e.g. chapters already in Room): if [active] clears before the delay, the
 * loader never shows.
 */
@Composable
fun rememberDelayedVisibility(active: Boolean, delayMillis: Long = 200L): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        visible = if (active) {
            delay(delayMillis)
            true
        } else {
            false
        }
    }
    return visible && active
}
