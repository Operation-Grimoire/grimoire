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
public val AppIcons.Vibration: ImageVector
  get() {
    if (_Vibration != null) {
      return _Vibration!!
    }
    _Vibration =
      ImageVector.Builder(
          name = "vibration",
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
            moveTo(8f, 21f)
            quadTo(7.18f, 21f, 6.59f, 20.41f)
            reflectiveQuadTo(6f, 19f)
            verticalLineTo(5f)
            quadTo(6f, 4.17f, 6.59f, 3.59f)
            reflectiveQuadTo(8f, 3f)
            horizontalLineToRelative(8f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(18f, 5f)
            verticalLineTo(19f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(16f, 21f)
            horizontalLineTo(8f)
            close()
            moveToRelative(8f, -2f)
            verticalLineTo(5f)
            horizontalLineTo(8f)
            verticalLineTo(19f)
            horizontalLineToRelative(8f)
            close()
            moveTo(12.71f, 7.71f)
            quadTo(13f, 7.43f, 13f, 7f)
            reflectiveQuadTo(12.71f, 6.29f)
            reflectiveQuadTo(12f, 6f)
            reflectiveQuadTo(11.29f, 6.29f)
            reflectiveQuadTo(11f, 7f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(12f, 8f)
            reflectiveQuadTo(12.71f, 7.71f)
            close()
            moveTo(0f, 15f)
            verticalLineTo(9f)
            horizontalLineTo(2f)
            verticalLineToRelative(6f)
            horizontalLineTo(0f)
            close()
            moveToRelative(3f, 2f)
            verticalLineTo(7f)
            horizontalLineTo(5f)
            verticalLineTo(17f)
            horizontalLineTo(3f)
            close()
            moveTo(22f, 15f)
            verticalLineTo(9f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6f)
            horizontalLineTo(22f)
            close()
            moveToRelative(-3f, 2f)
            verticalLineTo(7f)
            horizontalLineToRelative(2f)
            verticalLineTo(17f)
            horizontalLineTo(19f)
            close()
            moveTo(8f, 19f)
            verticalLineTo(5f)
            verticalLineTo(19f)
            close()
          }
        }
        .build()
    return _Vibration!!
  }

private var _Vibration: ImageVector? = null
