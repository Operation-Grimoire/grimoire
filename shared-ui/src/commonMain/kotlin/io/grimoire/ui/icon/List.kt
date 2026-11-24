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
public val AppIcons.List: ImageVector
  get() {
    if (_List != null) {
      return _List!!
    }
    _List =
      ImageVector.Builder(
          name = "list",
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
            moveTo(7f, 9f)
            verticalLineTo(7f)
            horizontalLineTo(21f)
            verticalLineTo(9f)
            horizontalLineTo(7f)
            close()
            moveToRelative(0f, 4f)
            verticalLineTo(11f)
            horizontalLineTo(21f)
            verticalLineToRelative(2f)
            horizontalLineTo(7f)
            close()
            moveToRelative(0f, 4f)
            verticalLineTo(15f)
            horizontalLineTo(21f)
            verticalLineToRelative(2f)
            horizontalLineTo(7f)
            close()
            moveTo(4f, 9f)
            quadTo(3.58f, 9f, 3.29f, 8.71f)
            reflectiveQuadTo(3f, 8f)
            quadTo(3f, 7.57f, 3.29f, 7.29f)
            reflectiveQuadTo(4f, 7f)
            reflectiveQuadTo(4.71f, 7.29f)
            reflectiveQuadTo(5f, 8f)
            quadTo(5f, 8.42f, 4.71f, 8.71f)
            reflectiveQuadTo(4f, 9f)
            close()
            moveToRelative(0f, 4f)
            quadTo(3.58f, 13f, 3.29f, 12.71f)
            quadTo(3f, 12.43f, 3f, 12f)
            reflectiveQuadTo(3.29f, 11.29f)
            reflectiveQuadTo(4f, 11f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(5f, 12f)
            reflectiveQuadTo(4.71f, 12.71f)
            reflectiveQuadTo(4f, 13f)
            close()
            moveToRelative(0f, 4f)
            quadTo(3.58f, 17f, 3.29f, 16.71f)
            quadTo(3f, 16.43f, 3f, 16f)
            reflectiveQuadTo(3.29f, 15.29f)
            reflectiveQuadTo(4f, 15f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(5f, 16f)
            reflectiveQuadTo(4.71f, 16.71f)
            reflectiveQuadTo(4f, 17f)
            close()
          }
        }
        .build()
    return _List!!
  }

private var _List: ImageVector? = null
