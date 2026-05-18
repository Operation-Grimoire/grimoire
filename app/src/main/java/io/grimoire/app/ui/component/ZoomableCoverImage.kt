package io.grimoire.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * A cover image that opens [ZoomableImageDialog] when tapped. Bundles the
 * "show zoom" state, the clickable affordance, and the dialog so screens
 * don't each re-implement it. Sizing/clipping is supplied via [modifier].
 */
@Composable
fun ZoomableCoverImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var zoomed by remember(model) { mutableStateOf(false) }
    val hasImage = (model as? String)?.isNotBlank() ?: (model != null)

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.clickable(enabled = hasImage) { zoomed = true },
    )

    if (zoomed && hasImage) {
        ZoomableImageDialog(
            model = model,
            contentDescription = contentDescription,
            onDismiss = { zoomed = false },
        )
    }
}
