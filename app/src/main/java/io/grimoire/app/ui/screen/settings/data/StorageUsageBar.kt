package io.grimoire.app.ui.screen.settings.data

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.storage.StorageBreakdown
import io.grimoire.app.R

private data class StorageSlice(val label: String, val bytes: Long, val color: Color)

/**
 * A horizontal stacked bar of the app's byte-valued storage buckets plus a legend.
 * Library / browse rows are counts (not on-disk bytes), so they stay as info rows on
 * the screen and are deliberately not represented here. Renders nothing when empty.
 */
@Composable
internal fun StorageUsageBar(
    breakdown: StorageBreakdown,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val slices = listOf(
        StorageSlice(stringResource(R.string.data_chapter_text), breakdown.downloadedTextBytes, scheme.primary),
        StorageSlice(stringResource(R.string.data_chapter_images), breakdown.downloadedImageBytes, scheme.tertiary),
        StorageSlice(stringResource(R.string.data_cover_cache), breakdown.coverCacheBytes, scheme.secondary),
        StorageSlice(stringResource(R.string.data_database), breakdown.databaseBytes, scheme.error),
        StorageSlice(stringResource(R.string.data_installer_files), breakdown.installerBytes, scheme.outline),
    ).filter { it.bytes > 0L }
    val total = slices.sumOf { it.bytes }
    if (total <= 0L) return

    val context = LocalContext.current
    fun bytes(value: Long) = Formatter.formatShortFileSize(context, value)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant),
        ) {
            slices.forEach { slice ->
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(slice.bytes.toFloat())
                        .background(slice.color),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { slice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(slice.color),
                    )
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        bytes(slice.bytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
