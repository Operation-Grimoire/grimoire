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
public val AppIcons.Pause: ImageVector
  get() {
    if (_Pause != null) {
      return _Pause!!
    }
    _Pause =
      ImageVector.Builder(
          name = "pause",
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
            moveTo(13f, 19f)
            verticalLineTo(5f)
            horizontalLineToRelative(6f)
            verticalLineTo(19f)
            horizontalLineTo(13f)
            close()
            moveTo(5f, 19f)
            verticalLineTo(5f)
            horizontalLineToRelative(6f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
            moveTo(15f, 17f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            horizontalLineTo(15f)
            verticalLineTo(17f)
            close()
            moveTo(7f, 17f)
            horizontalLineTo(9f)
            verticalLineTo(7f)
            horizontalLineTo(7f)
            verticalLineTo(17f)
            close()
            moveTo(7f, 7f)
            verticalLineTo(17f)
            verticalLineTo(7f)
            close()
            moveToRelative(8f, 0f)
            verticalLineTo(17f)
            verticalLineTo(7f)
            close()
          }
        }
        .build()
    return _Pause!!
  }

private var _Pause: ImageVector? = null
