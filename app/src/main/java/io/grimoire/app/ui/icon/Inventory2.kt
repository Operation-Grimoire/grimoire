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
public val AppIcons.Inventory2: ImageVector
  get() {
    if (_Inventory2 != null) {
      return _Inventory2!!
    }
    _Inventory2 =
      ImageVector.Builder(
          name = "inventory_2",
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
            moveTo(5f, 22f)
            quadTo(4.18f, 22f, 3.59f, 21.41f)
            reflectiveQuadTo(3f, 20f)
            verticalLineTo(8.73f)
            quadTo(2.55f, 8.45f, 2.28f, 8.01f)
            reflectiveQuadTo(2f, 7f)
            verticalLineTo(4f)
            quadTo(2f, 3.17f, 2.59f, 2.59f)
            reflectiveQuadTo(4f, 2f)
            horizontalLineTo(20f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(22f, 4f)
            verticalLineTo(7f)
            quadToRelative(0f, 0.57f, -0.27f, 1.01f)
            reflectiveQuadTo(21f, 8.73f)
            verticalLineTo(20f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(19f, 22f)
            horizontalLineTo(5f)
            close()
            moveTo(5f, 9f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(9f)
            horizontalLineTo(5f)
            close()
            moveTo(4f, 7f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            horizontalLineTo(4f)
            verticalLineTo(7f)
            close()
            moveToRelative(5f, 7f)
            horizontalLineToRelative(6f)
            verticalLineTo(12f)
            horizontalLineTo(9f)
            verticalLineToRelative(2f)
            close()
            moveToRelative(3f, 0.5f)
            close()
          }
        }
        .build()
    return _Inventory2!!
  }

private var _Inventory2: ImageVector? = null
