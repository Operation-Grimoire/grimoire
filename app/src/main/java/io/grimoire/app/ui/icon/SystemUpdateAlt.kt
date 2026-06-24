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
public val AppIcons.SystemUpdateAlt: ImageVector
  get() {
    if (_SystemUpdateAlt != null) {
      return _SystemUpdateAlt!!
    }
    _SystemUpdateAlt =
      ImageVector.Builder(
          name = "system_update_alt",
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
            moveTo(4f, 20f)
            quadTo(3.18f, 20f, 2.59f, 19.41f)
            reflectiveQuadTo(2f, 18f)
            verticalLineTo(6f)
            quadTo(2f, 5.18f, 2.59f, 4.59f)
            reflectiveQuadTo(4f, 4f)
            horizontalLineTo(9f)
            verticalLineTo(6f)
            horizontalLineTo(4f)
            verticalLineTo(18f)
            horizontalLineTo(20f)
            verticalLineTo(6f)
            horizontalLineTo(15f)
            verticalLineTo(4f)
            horizontalLineToRelative(5f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            quadTo(22f, 5.18f, 22f, 6f)
            verticalLineTo(18f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(20f, 20f)
            horizontalLineTo(4f)
            close()
            moveToRelative(8f, -4.6f)
            lineToRelative(-5f, -5f)
            lineTo(8.4f, 9f)
            lineTo(11f, 11.6f)
            verticalLineTo(4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(7.6f)
            lineTo(15.6f, 9f)
            lineTo(17f, 10.4f)
            lineToRelative(-5f, 5f)
            close()
          }
        }
        .build()
    return _SystemUpdateAlt!!
  }

private var _SystemUpdateAlt: ImageVector? = null
