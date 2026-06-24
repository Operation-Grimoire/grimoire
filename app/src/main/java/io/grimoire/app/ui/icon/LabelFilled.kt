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
public val AppIcons.LabelFilled: ImageVector
  get() {
    if (_LabelFilled != null) {
      return _LabelFilled!!
    }
    _LabelFilled =
      ImageVector.Builder(
          name = "label",
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
            moveTo(4f, 20f)
            quadTo(3.18f, 20f, 2.59f, 19.41f)
            reflectiveQuadTo(2f, 18f)
            verticalLineTo(6f)
            quadTo(2f, 5.18f, 2.59f, 4.59f)
            reflectiveQuadTo(4f, 4f)
            horizontalLineTo(15f)
            quadToRelative(0.48f, 0f, 0.9f, 0.21f)
            reflectiveQuadTo(16.6f, 4.8f)
            lineTo(22f, 12f)
            lineToRelative(-5.4f, 7.2f)
            quadToRelative(-0.28f, 0.38f, -0.7f, 0.59f)
            reflectiveQuadTo(15f, 20f)
            horizontalLineTo(4f)
            close()
          }
        }
        .build()
    return _LabelFilled!!
  }

private var _LabelFilled: ImageVector? = null
