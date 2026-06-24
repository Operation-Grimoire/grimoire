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
public val AppIcons.ExpandLess: ImageVector
  get() {
    if (_ExpandLess != null) {
      return _ExpandLess!!
    }
    _ExpandLess =
      ImageVector.Builder(
          name = "expand_less",
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
            moveTo(7.4f, 15.38f)
            lineTo(6f, 13.98f)
            lineToRelative(6f, -6f)
            lineToRelative(6f, 6f)
            lineToRelative(-1.4f, 1.4f)
            lineTo(12f, 10.77f)
            lineToRelative(-4.6f, 4.6f)
            close()
          }
        }
        .build()
    return _ExpandLess!!
  }

private var _ExpandLess: ImageVector? = null
