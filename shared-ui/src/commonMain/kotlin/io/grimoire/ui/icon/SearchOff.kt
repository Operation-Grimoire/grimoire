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
public val AppIcons.SearchOff: ImageVector
  get() {
    if (_SearchOff != null) {
      return _SearchOff!!
    }
    _SearchOff =
      ImageVector.Builder(
          name = "search_off",
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
            moveTo(3.4625f, 20.5375f)
            quadTo(2f, 19.075f, 2f, 17f)
            reflectiveQuadToRelative(1.4625f, -3.5375f)
            quadTo(4.925f, 12f, 7f, 12f)
            reflectiveQuadToRelative(3.5375f, 1.4625f)
            quadTo(12f, 14.925f, 12f, 17f)
            reflectiveQuadToRelative(-1.4625f, 3.5375f)
            quadTo(9.075f, 22f, 7f, 22f)
            reflectiveQuadToRelative(-3.5375f, -1.4625f)
            close()
            moveTo(20.6f, 21f)
            lineTo(14.2f, 14.6f)
            quadToRelative(-0.3f, -0.325f, -0.6375f, -0.6625f)
            reflectiveQuadTo(12.9f, 13.3f)
            quadToRelative(0.95f, -0.6f, 1.525f, -1.6f)
            reflectiveQuadToRelative(0.575f, -2.2f)
            quadToRelative(0f, -1.875f, -1.3125f, -3.1875f)
            reflectiveQuadTo(10.5f, 5f)
            quadToRelative(-1.875f, 0f, -3.1875f, 1.3125f)
            reflectiveQuadTo(6f, 9.5f)
            quadToRelative(0f, 0.15f, 0.0125f, 0.2875f)
            reflectiveQuadTo(6.05f, 10.075f)
            quadToRelative(-0.45f, 0.05f, -0.9875f, 0.2f)
            reflectiveQuadTo(4.1f, 10.625f)
            quadToRelative(-0.05f, -0.275f, -0.075f, -0.55f)
            reflectiveQuadToRelative(-0.025f, -0.575f)
            quadToRelative(0f, -2.725f, 1.8875f, -4.6125f)
            reflectiveQuadTo(10.5f, 3f)
            quadToRelative(2.725f, 0f, 4.6125f, 1.8875f)
            reflectiveQuadTo(17f, 9.5f)
            quadToRelative(0f, 1.075f, -0.3375f, 2.0375f)
            reflectiveQuadTo(15.725f, 13.3f)
            lineToRelative(6.275f, 6.3f)
            lineToRelative(-1.4f, 1.4f)
            close()
            moveToRelative(-15.375f, -1.525f)
            lineToRelative(1.775f, -1.775f)
            lineToRelative(1.75f, 1.775f)
            lineToRelative(0.725f, -0.7f)
            lineToRelative(-1.775f, -1.775f)
            lineToRelative(1.775f, -1.775f)
            lineToRelative(-0.7f, -0.7f)
            lineToRelative(-1.775f, 1.775f)
            lineToRelative(-1.775f, -1.775f)
            lineToRelative(-0.7f, 0.7f)
            lineToRelative(1.775f, 1.775f)
            lineToRelative(-1.775f, 1.775f)
            lineToRelative(0.7f, 0.7f)
            close()
          }
        }
        .build()
    return _SearchOff!!
  }

private var _SearchOff: ImageVector? = null
