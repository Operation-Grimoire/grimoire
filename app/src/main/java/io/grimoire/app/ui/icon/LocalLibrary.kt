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
public val AppIcons.LocalLibrary: ImageVector
  get() {
    if (_LocalLibrary != null) {
      return _LocalLibrary!!
    }
    _LocalLibrary =
      ImageVector.Builder(
          name = "local_library",
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
            moveTo(12f, 22.5f)
            quadTo(10.2f, 20.8f, 7.88f, 19.9f)
            reflectiveQuadTo(3f, 19f)
            verticalLineTo(8f)
            quadTo(5.53f, 8f, 7.85f, 8.91f)
            reflectiveQuadTo(12f, 11.55f)
            quadTo(13.83f, 9.82f, 16.15f, 8.91f)
            reflectiveQuadTo(21f, 8f)
            verticalLineTo(19f)
            quadToRelative(-2.57f, 0f, -4.89f, 0.9f)
            reflectiveQuadTo(12f, 22.5f)
            close()
            moveToRelative(0f, -2.6f)
            quadToRelative(1.58f, -1.17f, 3.35f, -1.88f)
            quadTo(17.13f, 17.33f, 19f, 17.1f)
            verticalLineTo(10.2f)
            quadToRelative(-1.82f, 0.32f, -3.59f, 1.31f)
            reflectiveQuadTo(12f, 14.15f)
            quadTo(10.35f, 12.5f, 8.59f, 11.51f)
            quadTo(6.83f, 10.52f, 5f, 10.2f)
            verticalLineToRelative(6.9f)
            quadToRelative(1.88f, 0.22f, 3.65f, 0.92f)
            quadToRelative(1.78f, 0.7f, 3.35f, 1.88f)
            close()
            moveTo(9.18f, 7.82f)
            quadTo(8f, 6.65f, 8f, 5f)
            reflectiveQuadTo(9.18f, 2.17f)
            reflectiveQuadTo(12f, 1f)
            reflectiveQuadToRelative(2.83f, 1.17f)
            reflectiveQuadTo(16f, 5f)
            reflectiveQuadTo(14.83f, 7.82f)
            reflectiveQuadTo(12f, 9f)
            reflectiveQuadTo(9.18f, 7.82f)
            close()
            moveTo(13.41f, 6.41f)
            quadTo(14f, 5.82f, 14f, 5f)
            quadTo(14f, 4.17f, 13.41f, 3.59f)
            reflectiveQuadTo(12f, 3f)
            reflectiveQuadTo(10.59f, 3.59f)
            reflectiveQuadTo(10f, 5f)
            quadToRelative(0f, 0.82f, 0.59f, 1.41f)
            reflectiveQuadTo(12f, 7f)
            reflectiveQuadTo(13.41f, 6.41f)
            close()
            moveTo(12f, 5f)
            close()
            moveToRelative(0f, 9.15f)
            close()
          }
        }
        .build()
    return _LocalLibrary!!
  }

private var _LocalLibrary: ImageVector? = null
