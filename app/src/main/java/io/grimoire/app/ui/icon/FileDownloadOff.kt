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
public val AppIcons.FileDownloadOff: ImageVector
  get() {
    if (_FileDownloadOff != null) {
      return _FileDownloadOff!!
    }
    _FileDownloadOff =
      ImageVector.Builder(
          name = "file_download_off",
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
            moveTo(19.78f, 22.63f)
            lineTo(17.15f, 20f)
            horizontalLineTo(6f)
            quadTo(5.18f, 20f, 4.59f, 19.41f)
            reflectiveQuadTo(4f, 18f)
            verticalLineTo(15f)
            horizontalLineTo(6f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(9.15f)
            lineTo(12.58f, 15.43f)
            lineTo(12f, 16f)
            lineTo(7f, 11f)
            lineTo(7.58f, 10.43f)
            lineTo(1.38f, 4.22f)
            lineTo(2.8f, 2.8f)
            lineTo(21.2f, 21.2f)
            lineToRelative(-1.43f, 1.43f)
            close()
            moveTo(15.43f, 12.58f)
            lineTo(14f, 11.15f)
            lineToRelative(1.6f, -1.6f)
            lineTo(17f, 11f)
            lineToRelative(-1.57f, 1.57f)
            close()
            moveTo(13f, 10.15f)
            lineToRelative(-2f, -2f)
            verticalLineTo(4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6.15f)
            close()
            moveToRelative(7f, 7f)
            lineToRelative(-2f, -2f)
            verticalLineTo(15f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2.15f)
            close()
          }
        }
        .build()
    return _FileDownloadOff!!
  }

private var _FileDownloadOff: ImageVector? = null
