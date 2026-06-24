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
public val AppIcons.DeleteSweep: ImageVector
  get() {
    if (_DeleteSweep != null) {
      return _DeleteSweep!!
    }
    _DeleteSweep =
      ImageVector.Builder(
          name = "delete_sweep",
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
            moveTo(15f, 18f)
            verticalLineTo(16f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(2f)
            horizontalLineTo(15f)
            close()
            moveToRelative(0f, -8f)
            verticalLineTo(8f)
            horizontalLineToRelative(7f)
            verticalLineToRelative(2f)
            horizontalLineTo(15f)
            close()
            moveToRelative(0f, 4f)
            verticalLineTo(12f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(2f)
            horizontalLineTo(15f)
            close()
            moveTo(3f, 8f)
            horizontalLineTo(2f)
            verticalLineTo(6f)
            horizontalLineTo(6f)
            verticalLineTo(4.5f)
            horizontalLineToRelative(4f)
            verticalLineTo(6f)
            horizontalLineToRelative(4f)
            verticalLineTo(8f)
            horizontalLineTo(13f)
            verticalLineToRelative(9f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(11f, 19f)
            horizontalLineTo(5f)
            quadTo(4.18f, 19f, 3.59f, 18.41f)
            reflectiveQuadTo(3f, 17f)
            verticalLineTo(8f)
            close()
            moveTo(5f, 8f)
            verticalLineToRelative(9f)
            horizontalLineToRelative(6f)
            verticalLineTo(8f)
            horizontalLineTo(5f)
            close()
            moveTo(5f, 8f)
            verticalLineToRelative(9f)
            verticalLineTo(8f)
            close()
          }
        }
        .build()
    return _DeleteSweep!!
  }

private var _DeleteSweep: ImageVector? = null
