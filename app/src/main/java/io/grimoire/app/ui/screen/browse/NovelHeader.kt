package io.grimoire.app.ui.screen.browse

import io.grimoire.ui.icon.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import io.grimoire.app.ui.component.ImageAction
import io.grimoire.app.ui.component.rememberShimmerAlpha
import io.grimoire.app.ui.component.ShimmerBox
import io.grimoire.app.ui.component.ZoomableCoverImage
import io.grimoire.ui.component.localizedDisplayName
import io.grimoire.ui.component.icon
import java.util.Locale
import io.grimoire.app.R
import io.grimoire.ui.novel.HeaderField
import io.grimoire.ui.novel.NovelHeaderContent

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovelHeader(
    novel: Novel,
    overrides: NovelOverrides,
    coverModel: Any?,
    sourceName: String = "",
    isLocal: Boolean = false,
    sourceMissing: Boolean = false,
    missingSourceName: String? = null,
    onSourceMissingClick: () -> Unit = {},
    onEditField: (EditableField) -> Unit = {},
    onSetCoverUri: (Uri) -> Unit = {},
    onSetCoverUrl: (String) -> Unit = {},
    onResetCover: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showRatingInfo by remember { mutableStateOf(false) }
    var showCoverUrlDialog by remember { mutableStateOf(false) }

    if (showRatingInfo) {
        AlertDialog(
            onDismissRequest = { showRatingInfo = false },
            title = { Text(stringResource(R.string.novel_rating_info_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.novel_rating_info_description,
                        sourceName.ifBlank { stringResource(R.string.novel_rating_source_fallback) },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { showRatingInfo = false }) { Text(stringResource(R.string.novel_rating_got_it)) }
            },
        )
    }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onSetCoverUri) }

    if (showCoverUrlDialog) {
        var url by remember { mutableStateOf(overrides.coverUrl.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showCoverUrlDialog = false },
            title = { Text(stringResource(R.string.novel_cover_url_title)) },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://…") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = { onSetCoverUrl(url); showCoverUrlDialog = false },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCoverUrlDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    val hasCoverOverride = overrides.coverPath != null || overrides.coverUrl != null
    val coverActions = buildList {
        add(ImageAction(AppIcons.Image, stringResource(R.string.novel_cover_replace_image)) {
            coverPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        })
        add(ImageAction(AppIcons.Link, stringResource(R.string.novel_cover_replace_url)) { showCoverUrlDialog = true })
        if (hasCoverOverride) {
            add(ImageAction(AppIcons.Restore, stringResource(R.string.novel_cover_reset)) { onResetCover() })
        }
    }

    NovelHeaderContent(
        novel = novel,
        cover = { coverModifier ->
                ZoomableCoverImage(
                    model = coverModel,
                    contentDescription = novel.title,
                    saveBaseName = novel.title,
                    extraActions = coverActions,
                    modifier = coverModifier,
                )
        },
        sourceName = sourceName,
        isLocal = isLocal,
        sourceMissing = sourceMissing,
        missingSourceName = missingSourceName,
        onSourceMissingClick = onSourceMissingClick,
        onRatingClick = { showRatingInfo = true },
        overridden = { field ->
            when (field) {
                HeaderField.TITLE -> overrides.title != null
                HeaderField.AUTHOR -> overrides.author != null
                HeaderField.STATUS -> overrides.status != null
            }
        },
        onEditField = { field ->
            onEditField(when (field) {
                HeaderField.TITLE -> EditableField.TITLE
                HeaderField.AUTHOR -> EditableField.AUTHOR
                HeaderField.STATUS -> EditableField.STATUS
            })
        },
        modifier = modifier,
    )
}

@Composable
internal fun NovelHeaderSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberShimmerAlpha()
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShimmerBox(modifier = Modifier.width(140.dp).aspectRatio(2f / 3f), shape = RoundedCornerShape(8.dp), alpha = alpha)
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(20.dp), alpha = alpha)
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp), alpha = alpha)
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(12.dp), alpha = alpha)
        }
    }
}

@Composable
internal fun ChapterSkeletonItem(alpha: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.65f).height(15.dp), alpha = alpha)
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(11.dp), alpha = alpha)
        }
        ShimmerBox(modifier = Modifier.size(20.dp), shape = CircleShape, alpha = alpha)
    }
}
