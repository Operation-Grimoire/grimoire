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
public val AppIcons.TrendingUp: ImageVector
  get() {
    if (_TrendingUp != null) {
      return _TrendingUp!!
    }
    _TrendingUp =
      ImageVector.Builder(
          name = "trending_up",
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
            moveTo(3.4f, 18f)
            lineTo(2f, 16.6f)
            lineTo(9.4f, 9.15f)
            lineToRelative(4f, 4f)
            lineTo(18.6f, 8f)
            horizontalLineTo(16f)
            verticalLineTo(6f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(6f)
            horizontalLineTo(20f)
            verticalLineTo(9.4f)
            lineTo(13.4f, 16f)
            lineToRelative(-4f, -4f)
            lineToRelative(-6f, 6f)
            close()
          }
        }
        .build()
    return _TrendingUp!!
  }

private var _TrendingUp: ImageVector? = null
