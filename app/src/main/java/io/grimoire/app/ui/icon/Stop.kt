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
public val AppIcons.Stop: ImageVector
  get() {
    if (_Stop != null) {
      return _Stop!!
    }
    _Stop =
      ImageVector.Builder(
          name = "stop",
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
            moveTo(8f, 8f)
            verticalLineToRelative(8f)
            verticalLineTo(8f)
            close()
            moveTo(6f, 18f)
            verticalLineTo(6f)
            horizontalLineTo(18f)
            verticalLineTo(18f)
            horizontalLineTo(6f)
            close()
            moveTo(8f, 16f)
            horizontalLineToRelative(8f)
            verticalLineTo(8f)
            horizontalLineTo(8f)
            verticalLineToRelative(8f)
            close()
          }
        }
        .build()
    return _Stop!!
  }

private var _Stop: ImageVector? = null
