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
public val AppIcons.ExtensionFilled: ImageVector
  get() {
    if (_ExtensionFilled != null) {
      return _ExtensionFilled!!
    }
    _ExtensionFilled =
      ImageVector.Builder(
          name = "extension",
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
            moveTo(5f, 21f)
            quadTo(4.15f, 21f, 3.58f, 20.43f)
            reflectiveQuadTo(3f, 19f)
            verticalLineTo(15.2f)
            quadToRelative(1.2f, 0f, 2.1f, -0.76f)
            reflectiveQuadTo(6f, 12.5f)
            quadTo(6f, 11.35f, 5.1f, 10.6f)
            reflectiveQuadTo(3f, 9.8f)
            verticalLineTo(6f)
            quadTo(3f, 5.18f, 3.59f, 4.59f)
            reflectiveQuadTo(5f, 4f)
            horizontalLineTo(8.05f)
            quadTo(8.23f, 2.72f, 9.2f, 1.86f)
            reflectiveQuadTo(11.5f, 1f)
            quadToRelative(1.3f, 0f, 2.28f, 0.86f)
            reflectiveQuadTo(14.95f, 4f)
            horizontalLineTo(18f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            quadTo(20f, 5.18f, 20f, 6f)
            verticalLineTo(9.35f)
            quadToRelative(0.9f, 0.45f, 1.45f, 1.3f)
            reflectiveQuadTo(22f, 12.5f)
            quadToRelative(0f, 1.02f, -0.55f, 1.88f)
            reflectiveQuadTo(20f, 15.65f)
            verticalLineTo(19f)
            quadToRelative(0f, 0.85f, -0.59f, 1.43f)
            reflectiveQuadTo(18f, 21f)
            horizontalLineTo(5f)
            close()
          }
        }
        .build()
    return _ExtensionFilled!!
  }

private var _ExtensionFilled: ImageVector? = null
