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
public val AppIcons.Star: ImageVector
  get() {
    if (_Star != null) {
      return _Star!!
    }
    _Star =
      ImageVector.Builder(
          name = "star",
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
            moveTo(5.83f, 21f)
            lineTo(7.45f, 13.98f)
            lineTo(2f, 9.25f)
            lineTo(9.2f, 8.63f)
            lineTo(12f, 2f)
            lineToRelative(2.8f, 6.63f)
            lineTo(22f, 9.25f)
            lineToRelative(-5.45f, 4.72f)
            lineTo(18.18f, 21f)
            lineTo(12f, 17.27f)
            lineTo(5.83f, 21f)
            close()
          }
        }
        .build()
    return _Star!!
  }

private var _Star: ImageVector? = null
