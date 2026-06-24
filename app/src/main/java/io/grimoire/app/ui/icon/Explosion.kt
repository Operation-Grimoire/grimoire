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
public val AppIcons.Explosion: ImageVector
  get() {
    if (_Explosion != null) {
      return _Explosion!!
    }
    _Explosion =
      ImageVector.Builder(
          name = "explosion",
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
            moveTo(12f, 16.98f)
            lineTo(13.48f, 15.5f)
            horizontalLineTo(15.5f)
            verticalLineTo(13.48f)
            lineTo(16.98f, 12f)
            lineTo(15.5f, 10.52f)
            verticalLineTo(8.5f)
            horizontalLineTo(13.48f)
            lineTo(12f, 7.02f)
            lineTo(10.53f, 8.5f)
            horizontalLineTo(8.5f)
            verticalLineToRelative(2.02f)
            lineTo(7.03f, 12f)
            lineTo(8.5f, 13.48f)
            verticalLineTo(15.5f)
            horizontalLineToRelative(2.03f)
            lineTo(12f, 16.98f)
            close()
            moveToRelative(0f, 6.32f)
            lineTo(8.65f, 20f)
            horizontalLineTo(4f)
            verticalLineTo(15.35f)
            lineTo(0.7f, 12f)
            lineTo(4f, 8.65f)
            verticalLineTo(4f)
            horizontalLineTo(8.65f)
            lineTo(12f, 0.7f)
            lineTo(15.35f, 4f)
            horizontalLineTo(20f)
            verticalLineTo(8.65f)
            lineTo(23.3f, 12f)
            lineTo(20f, 15.35f)
            verticalLineTo(20f)
            horizontalLineTo(15.35f)
            lineTo(12f, 23.3f)
            close()
            moveToRelative(0f, -2.8f)
            lineTo(14.5f, 18f)
            horizontalLineTo(18f)
            verticalLineTo(14.5f)
            lineTo(20.5f, 12f)
            lineTo(18f, 9.5f)
            verticalLineTo(6f)
            horizontalLineTo(14.5f)
            lineTo(12f, 3.5f)
            lineTo(9.5f, 6f)
            horizontalLineTo(6f)
            verticalLineTo(9.5f)
            lineTo(3.5f, 12f)
            lineTo(6f, 14.5f)
            verticalLineTo(18f)
            horizontalLineTo(9.5f)
            lineTo(12f, 20.5f)
            close()
            moveTo(12f, 12f)
            close()
          }
        }
        .build()
    return _Explosion!!
  }

private var _Explosion: ImageVector? = null
