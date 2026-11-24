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
public val AppIcons.HideImage: ImageVector
  get() {
    if (_HideImage != null) {
      return _HideImage!!
    }
    _HideImage =
      ImageVector.Builder(
          name = "hide_image",
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
            moveTo(21f, 18.15f)
            lineToRelative(-2f, -2f)
            verticalLineTo(5f)
            horizontalLineTo(7.85f)
            lineToRelative(-2f, -2f)
            horizontalLineTo(19f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(21f, 5f)
            verticalLineTo(18.15f)
            close()
            moveTo(19.8f, 22.6f)
            lineTo(18.2f, 21f)
            horizontalLineTo(5f)
            quadTo(4.18f, 21f, 3.59f, 20.41f)
            reflectiveQuadTo(3f, 19f)
            verticalLineTo(5.8f)
            lineTo(1.4f, 4.2f)
            lineTo(2.8f, 2.8f)
            lineTo(21.2f, 21.2f)
            lineToRelative(-1.4f, 1.4f)
            close()
            moveTo(6f, 17f)
            lineTo(9f, 13f)
            lineToRelative(2.25f, 3f)
            lineToRelative(0.83f, -1.1f)
            lineTo(5f, 7.82f)
            verticalLineTo(19f)
            horizontalLineTo(16.18f)
            lineToRelative(-2f, -2f)
            horizontalLineTo(6f)
            close()
            moveToRelative(7.43f, -6.43f)
            close()
            moveTo(10.6f, 13.4f)
            close()
          }
        }
        .build()
    return _HideImage!!
  }

private var _HideImage: ImageVector? = null
