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
public val AppIcons.KeyboardDoubleArrowUp: ImageVector
  get() {
    if (_KeyboardDoubleArrowUp != null) {
      return _KeyboardDoubleArrowUp!!
    }
    _KeyboardDoubleArrowUp =
      ImageVector.Builder(
          name = "keyboard_double_arrow_up",
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
            moveTo(7.4f, 18.4f)
            lineTo(6f, 17f)
            lineToRelative(6f, -6f)
            lineToRelative(6f, 6f)
            lineToRelative(-1.4f, 1.4f)
            lineTo(12f, 13.83f)
            lineTo(7.4f, 18.4f)
            close()
            moveToRelative(0f, -6f)
            lineTo(6f, 11f)
            lineTo(12f, 5f)
            lineToRelative(6f, 6f)
            lineToRelative(-1.4f, 1.4f)
            lineTo(12f, 7.82f)
            lineTo(7.4f, 12.4f)
            close()
          }
        }
        .build()
    return _KeyboardDoubleArrowUp!!
  }

private var _KeyboardDoubleArrowUp: ImageVector? = null
