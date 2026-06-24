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
public val AppIcons.BarChart: ImageVector
  get() {
    if (_BarChart != null) {
      return _BarChart!!
    }
    _BarChart =
      ImageVector.Builder(
          name = "bar_chart",
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
            moveTo(16f, 20f)
            verticalLineTo(13f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(7f)
            horizontalLineTo(16f)
            close()
            moveToRelative(-6f, 0f)
            verticalLineTo(4f)
            horizontalLineToRelative(4f)
            verticalLineTo(20f)
            horizontalLineTo(10f)
            close()
            moveTo(4f, 20f)
            verticalLineTo(9f)
            horizontalLineTo(8f)
            verticalLineTo(20f)
            horizontalLineTo(4f)
            close()
          }
        }
        .build()
    return _BarChart!!
  }

private var _BarChart: ImageVector? = null
