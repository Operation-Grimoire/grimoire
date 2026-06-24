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
public val AppIcons.Extension: ImageVector
  get() {
    if (_Extension != null) {
      return _Extension!!
    }
    _Extension =
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
            moveTo(5f, 19f)
            horizontalLineTo(18f)
            verticalLineTo(14.4f)
            lineToRelative(1.13f, -0.55f)
            quadToRelative(0.4f, -0.2f, 0.64f, -0.55f)
            reflectiveQuadTo(20f, 12.5f)
            quadToRelative(0f, -0.43f, -0.24f, -0.79f)
            reflectiveQuadTo(19.13f, 11.15f)
            lineTo(18f, 10.63f)
            verticalLineTo(6f)
            horizontalLineTo(13.2f)
            lineTo(12.95f, 4.3f)
            quadTo(12.88f, 3.75f, 12.46f, 3.38f)
            reflectiveQuadTo(11.5f, 3f)
            quadTo(10.93f, 3f, 10.51f, 3.38f)
            reflectiveQuadTo(10.03f, 4.3f)
            lineTo(9.78f, 6f)
            horizontalLineTo(5f)
            verticalLineTo(8.15f)
            quadToRelative(1.4f, 0.53f, 2.2f, 1.7f)
            quadTo(8f, 11.02f, 8f, 12.5f)
            quadTo(8f, 14f, 7.2f, 15.18f)
            reflectiveQuadTo(5f, 16.88f)
            verticalLineTo(19f)
            close()
            moveToRelative(0f, 2f)
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
            moveToRelative(7.5f, -8.5f)
            close()
          }
        }
        .build()
    return _Extension!!
  }

private var _Extension: ImageVector? = null
