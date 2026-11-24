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
public val AppIcons.NavigateBefore: ImageVector
  get() {
    if (_NavigateBefore != null) {
      return _NavigateBefore!!
    }
    _NavigateBefore =
      ImageVector.Builder(
          name = "navigate_before",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
          autoMirror = true,
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
            moveTo(14f, 18f)
            lineTo(8f, 12f)
            lineTo(14f, 6f)
            lineToRelative(1.4f, 1.4f)
            lineTo(10.8f, 12f)
            lineToRelative(4.6f, 4.6f)
            lineTo(14f, 18f)
            close()
          }
        }
        .build()
    return _NavigateBefore!!
  }

private var _NavigateBefore: ImageVector? = null
