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
public val AppIcons.TouchApp: ImageVector
  get() {
    if (_TouchApp != null) {
      return _TouchApp!!
    }
    _TouchApp =
      ImageVector.Builder(
          name = "touch_app",
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
            moveTo(10.48f, 22f)
            quadTo(9.78f, 22f, 9.16f, 21.7f)
            reflectiveQuadTo(8.13f, 20.85f)
            lineTo(2.68f, 13.93f)
            lineToRelative(0.48f, -0.5f)
            quadTo(3.65f, 12.9f, 4.35f, 12.8f)
            reflectiveQuadToRelative(1.3f, 0.28f)
            lineTo(7.5f, 14.2f)
            verticalLineTo(6f)
            quadTo(7.5f, 5.57f, 7.79f, 5.29f)
            reflectiveQuadTo(8.5f, 5f)
            quadTo(8.93f, 5f, 9.23f, 5.29f)
            quadTo(9.53f, 5.57f, 9.53f, 6f)
            verticalLineTo(17.8f)
            lineTo(7.1f, 16.3f)
            lineToRelative(2.6f, 3.32f)
            quadToRelative(0.15f, 0.18f, 0.35f, 0.27f)
            reflectiveQuadTo(10.48f, 20f)
            horizontalLineTo(16f)
            quadToRelative(0.82f, 0f, 1.41f, -0.59f)
            reflectiveQuadTo(18f, 18f)
            verticalLineTo(14f)
            quadToRelative(0f, -0.43f, -0.29f, -0.71f)
            reflectiveQuadTo(17f, 13f)
            horizontalLineTo(11.53f)
            verticalLineTo(11f)
            horizontalLineTo(17f)
            quadToRelative(1.25f, 0f, 2.13f, 0.88f)
            reflectiveQuadTo(20f, 14f)
            verticalLineToRelative(4f)
            quadToRelative(0f, 1.65f, -1.18f, 2.82f)
            reflectiveQuadTo(16f, 22f)
            horizontalLineTo(10.48f)
            close()
            moveTo(4.18f, 8.5f)
            quadTo(3.85f, 7.95f, 3.68f, 7.31f)
            reflectiveQuadTo(3.5f, 6f)
            quadTo(3.5f, 3.92f, 4.96f, 2.46f)
            reflectiveQuadTo(8.5f, 1f)
            reflectiveQuadToRelative(3.54f, 1.46f)
            reflectiveQuadTo(13.5f, 6f)
            quadToRelative(0f, 0.68f, -0.17f, 1.31f)
            reflectiveQuadTo(12.83f, 8.5f)
            lineTo(11.1f, 7.5f)
            quadTo(11.3f, 7.15f, 11.4f, 6.79f)
            quadTo(11.5f, 6.43f, 11.5f, 6f)
            quadToRelative(0f, -1.25f, -0.88f, -2.13f)
            reflectiveQuadTo(8.5f, 3f)
            reflectiveQuadTo(6.38f, 3.88f)
            reflectiveQuadTo(5.5f, 6f)
            quadToRelative(0f, 0.43f, 0.1f, 0.79f)
            reflectiveQuadTo(5.9f, 7.5f)
            lineToRelative(-1.73f, 1f)
            close()
            moveToRelative(8.38f, 7f)
            close()
          }
        }
        .build()
    return _TouchApp!!
  }

private var _TouchApp: ImageVector? = null
