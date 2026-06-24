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
public val AppIcons.BookmarkBorder: ImageVector
  get() {
    if (_BookmarkBorder != null) {
      return _BookmarkBorder!!
    }
    _BookmarkBorder =
      ImageVector.Builder(
          name = "bookmark",
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
            verticalLineTo(5f)
            quadTo(5f, 4.17f, 5.59f, 3.59f)
            reflectiveQuadTo(7f, 3f)
            horizontalLineTo(17f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(19f, 5f)
            verticalLineTo(21f)
            lineTo(12f, 18f)
            lineTo(5f, 21f)
            close()
            moveTo(7f, 17.95f)
            lineTo(12f, 15.8f)
            lineToRelative(5f, 2.15f)
            verticalLineTo(5f)
            horizontalLineTo(7f)
            verticalLineTo(17.95f)
            close()
            moveTo(7f, 5f)
            horizontalLineTo(17f)
            horizontalLineTo(12f)
            horizontalLineTo(7f)
            close()
          }
        }
        .build()
    return _BookmarkBorder!!
  }

private var _BookmarkBorder: ImageVector? = null
