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
public val AppIcons.Celebration: ImageVector
  get() {
    if (_Celebration != null) {
      return _Celebration!!
    }
    _Celebration =
      ImageVector.Builder(
          name = "celebration",
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
            moveTo(2f, 22f)
            lineTo(7f, 8f)
            lineToRelative(9f, 9f)
            lineTo(2f, 22f)
            close()
            moveTo(5.3f, 18.7f)
            lineToRelative(7.05f, -2.5f)
            lineTo(7.8f, 11.65f)
            lineTo(5.3f, 18.7f)
            close()
            moveToRelative(9.25f, -6.15f)
            lineTo(13.5f, 11.5f)
            lineTo(19.1f, 5.9f)
            quadTo(19.9f, 5.1f, 21.03f, 5.1f)
            reflectiveQuadToRelative(1.93f, 0.8f)
            lineToRelative(0.6f, 0.6f)
            lineTo(22.5f, 7.55f)
            lineTo(21.9f, 6.95f)
            quadTo(21.55f, 6.6f, 21.03f, 6.6f)
            reflectiveQuadTo(20.15f, 6.95f)
            lineToRelative(-5.6f, 5.6f)
            close()
            moveToRelative(-4f, -4f)
            lineTo(9.5f, 7.5f)
            lineTo(10.1f, 6.9f)
            quadTo(10.45f, 6.55f, 10.45f, 6.05f)
            reflectiveQuadTo(10.1f, 5.2f)
            lineTo(9.45f, 4.55f)
            lineTo(10.5f, 3.5f)
            lineToRelative(0.65f, 0.65f)
            quadToRelative(0.8f, 0.8f, 0.8f, 1.9f)
            reflectiveQuadToRelative(-0.8f, 1.9f)
            lineToRelative(-0.6f, 0.6f)
            close()
            moveToRelative(2f, 2f)
            lineTo(11.5f, 9.5f)
            lineTo(15.1f, 5.9f)
            quadTo(15.45f, 5.55f, 15.45f, 5.02f)
            reflectiveQuadTo(15.1f, 4.15f)
            lineTo(13.5f, 2.55f)
            lineTo(14.55f, 1.5f)
            lineToRelative(1.6f, 1.6f)
            quadToRelative(0.8f, 0.8f, 0.8f, 1.93f)
            reflectiveQuadToRelative(-0.8f, 1.93f)
            lineToRelative(-3.6f, 3.6f)
            close()
            moveToRelative(4f, 4f)
            lineTo(15.5f, 13.5f)
            lineToRelative(1.6f, -1.6f)
            quadToRelative(0.8f, -0.8f, 1.93f, -0.8f)
            reflectiveQuadToRelative(1.93f, 0.8f)
            lineToRelative(1.6f, 1.6f)
            lineTo(21.5f, 14.55f)
            lineToRelative(-1.6f, -1.6f)
            quadTo(19.55f, 12.6f, 19.03f, 12.6f)
            reflectiveQuadToRelative(-0.88f, 0.35f)
            lineToRelative(-1.6f, 1.6f)
            close()
            moveTo(5.3f, 18.7f)
            close()
          }
        }
        .build()
    return _Celebration!!
  }

private var _Celebration: ImageVector? = null
