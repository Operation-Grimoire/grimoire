package io.grimoire.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val AppIcons.DownloadDone: ImageVector
  get() {
    if (_DownloadDone != null) {
      return _DownloadDone!!
    }
    _DownloadDone =
      ImageVector.Builder(
          name = "download_done",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(9.55f, 16f)
            lineTo(3.88f, 10.33f)
            lineTo(5.3f, 8.9f)
            lineToRelative(4.25f, 4.25f)
            lineTo(18.7f, 4f)
            lineToRelative(1.43f, 1.43f)
            lineTo(9.55f, 16f)
            close()
            moveTo(5f, 20f)
            verticalLineTo(18f)
            horizontalLineTo(19f)
            verticalLineToRelative(2f)
            horizontalLineTo(5f)
            close()
          }
        }
        .build()
    return _DownloadDone!!
  }

private var _DownloadDone: ImageVector? = null
