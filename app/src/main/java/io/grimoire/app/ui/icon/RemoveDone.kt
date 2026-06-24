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
public val AppIcons.RemoveDone: ImageVector
  get() {
    if (_RemoveDone != null) {
      return _RemoveDone!!
    }
    _RemoveDone =
      ImageVector.Builder(
          name = "remove_done",
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
            moveTo(21.83f, 21.8f)
            lineToRelative(-6.6f, -6.6f)
            lineToRelative(-2.85f, 2.85f)
            lineTo(6.73f, 12.4f)
            lineToRelative(1.4f, -1.45f)
            lineToRelative(4.25f, 4.25f)
            lineToRelative(1.4f, -1.4f)
            lineTo(3.43f, 3.45f)
            lineTo(4.83f, 2f)
            lineToRelative(18.4f, 18.4f)
            lineToRelative(-1.4f, 1.4f)
            close()
            moveTo(6.73f, 18.05f)
            lineTo(1.08f, 12.4f)
            lineTo(2.48f, 11f)
            lineToRelative(4.25f, 4.25f)
            lineToRelative(1.4f, 1.4f)
            lineToRelative(-1.4f, 1.4f)
            close()
            moveTo(18.03f, 12.4f)
            lineTo(16.63f, 11f)
            lineToRelative(4.9f, -4.9f)
            lineToRelative(1.45f, 1.35f)
            lineTo(18.03f, 12.4f)
            close()
            moveTo(15.18f, 9.55f)
            lineToRelative(-1.4f, -1.4f)
            lineTo(15.93f, 6f)
            lineToRelative(1.4f, 1.4f)
            lineTo(15.18f, 9.55f)
            close()
          }
        }
        .build()
    return _RemoveDone!!
  }

private var _RemoveDone: ImageVector? = null
