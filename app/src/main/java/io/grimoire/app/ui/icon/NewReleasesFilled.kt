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
public val AppIcons.NewReleasesFilled: ImageVector
  get() {
    if (_NewReleasesFilled != null) {
      return _NewReleasesFilled!!
    }
    _NewReleasesFilled =
      ImageVector.Builder(
          name = "new_releases",
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
            moveTo(8.6f, 22.5f)
            lineTo(6.7f, 19.3f)
            lineTo(3.1f, 18.5f)
            lineTo(3.45f, 14.8f)
            lineTo(1f, 12f)
            lineTo(3.45f, 9.2f)
            lineTo(3.1f, 5.5f)
            lineTo(6.7f, 4.7f)
            lineTo(8.6f, 1.5f)
            lineTo(12f, 2.95f)
            lineTo(15.4f, 1.5f)
            lineToRelative(1.9f, 3.2f)
            lineToRelative(3.6f, 0.8f)
            lineTo(20.55f, 9.2f)
            lineTo(23f, 12f)
            lineToRelative(-2.45f, 2.8f)
            lineToRelative(0.35f, 3.7f)
            lineToRelative(-3.6f, 0.8f)
            lineToRelative(-1.9f, 3.2f)
            lineTo(12f, 21.05f)
            lineTo(8.6f, 22.5f)
            close()
            moveToRelative(2.35f, -6.95f)
            lineTo(16.6f, 9.9f)
            lineTo(15.2f, 8.45f)
            lineTo(10.95f, 12.7f)
            lineTo(8.8f, 10.6f)
            lineTo(7.4f, 12f)
            lineToRelative(3.55f, 3.55f)
            close()
          }
        }
        .build()
    return _NewReleasesFilled!!
  }

private var _NewReleasesFilled: ImageVector? = null
