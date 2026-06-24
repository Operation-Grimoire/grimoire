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
public val AppIcons.LibraryAdd: ImageVector
  get() {
    if (_LibraryAdd != null) {
      return _LibraryAdd!!
    }
    _LibraryAdd =
      ImageVector.Builder(
          name = "library_add",
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
            moveTo(13f, 14f)
            horizontalLineToRelative(2f)
            verticalLineTo(11f)
            horizontalLineToRelative(3f)
            verticalLineTo(9f)
            horizontalLineTo(15f)
            verticalLineTo(6f)
            horizontalLineTo(13f)
            verticalLineTo(9f)
            horizontalLineTo(10f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(3f)
            close()
            moveTo(8f, 18f)
            quadTo(7.18f, 18f, 6.59f, 17.41f)
            reflectiveQuadTo(6f, 16f)
            verticalLineTo(4f)
            quadTo(6f, 3.17f, 6.59f, 2.59f)
            reflectiveQuadTo(8f, 2f)
            horizontalLineTo(20f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(22f, 4f)
            verticalLineTo(16f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(20f, 18f)
            horizontalLineTo(8f)
            close()
            moveTo(8f, 16f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            horizontalLineTo(8f)
            verticalLineTo(16f)
            close()
            moveTo(4f, 22f)
            quadTo(3.18f, 22f, 2.59f, 21.41f)
            reflectiveQuadTo(2f, 20f)
            verticalLineTo(6f)
            horizontalLineTo(4f)
            verticalLineTo(20f)
            horizontalLineTo(18f)
            verticalLineToRelative(2f)
            horizontalLineTo(4f)
            close()
            moveTo(8f, 4f)
            verticalLineTo(16f)
            verticalLineTo(4f)
            close()
          }
        }
        .build()
    return _LibraryAdd!!
  }

private var _LibraryAdd: ImageVector? = null
