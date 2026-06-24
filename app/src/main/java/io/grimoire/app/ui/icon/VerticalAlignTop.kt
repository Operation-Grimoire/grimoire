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
public val AppIcons.VerticalAlignTop: ImageVector
  get() {
    if (_VerticalAlignTop != null) {
      return _VerticalAlignTop!!
    }
    _VerticalAlignTop =
      ImageVector.Builder(
          name = "vertical_align_top",
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
            moveTo(4f, 5f)
            verticalLineTo(3f)
            horizontalLineTo(20f)
            verticalLineTo(5f)
            horizontalLineTo(4f)
            close()
            moveToRelative(7f, 16f)
            verticalLineTo(10.8f)
            lineTo(8.4f, 13.4f)
            lineTo(7f, 12f)
            lineTo(12f, 7f)
            lineToRelative(5f, 5f)
            lineToRelative(-1.4f, 1.4f)
            lineTo(13f, 10.8f)
            verticalLineTo(21f)
            horizontalLineTo(11f)
            close()
          }
        }
        .build()
    return _VerticalAlignTop!!
  }

private var _VerticalAlignTop: ImageVector? = null
