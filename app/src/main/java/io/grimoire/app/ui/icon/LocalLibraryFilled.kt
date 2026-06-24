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
public val AppIcons.LocalLibraryFilled: ImageVector
  get() {
    if (_LocalLibraryFilled != null) {
      return _LocalLibraryFilled!!
    }
    _LocalLibraryFilled =
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
          }
        }
        .build()
    return _LocalLibraryFilled!!
  }

private var _LocalLibraryFilled: ImageVector? = null
