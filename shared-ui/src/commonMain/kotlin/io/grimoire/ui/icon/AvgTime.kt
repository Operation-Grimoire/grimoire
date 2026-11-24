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
public val AppIcons.AvgTime: ImageVector
  get() {
    if (_AvgTime != null) {
      return _AvgTime!!
    }
    _AvgTime =
      ImageVector.Builder(
          name = "avg_time",
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
            moveTo(5.08f, 12f)
            horizontalLineTo(8f)
            quadToRelative(0.28f, 0f, 0.53f, 0.14f)
            reflectiveQuadTo(8.9f, 12.55f)
            lineToRelative(1.1f, 2.2f)
            lineToRelative(3.1f, -6.2f)
            quadTo(13.38f, 7.97f, 14f, 7.97f)
            reflectiveQuadToRelative(0.9f, 0.58f)
            lineTo(16.63f, 12f)
            horizontalLineToRelative(2.3f)
            quadTo(18.55f, 9.45f, 16.6f, 7.72f)
            reflectiveQuadTo(12f, 6f)
            reflectiveQuadTo(7.4f, 7.72f)
            reflectiveQuadTo(5.08f, 12f)
            close()
            moveTo(16.6f, 18.27f)
            quadTo(18.55f, 16.55f, 18.93f, 14f)
            horizontalLineTo(16f)
            quadToRelative(-0.27f, 0f, -0.52f, -0.14f)
            quadTo(15.23f, 13.73f, 15.1f, 13.45f)
            lineTo(14f, 11.25f)
            lineToRelative(-3.1f, 6.2f)
            quadTo(10.63f, 18.02f, 10f, 18.02f)
            reflectiveQuadTo(9.1f, 17.45f)
            lineTo(7.38f, 14f)
            horizontalLineTo(5.08f)
            quadToRelative(0.38f, 2.55f, 2.33f, 4.27f)
            reflectiveQuadTo(12f, 20f)
            reflectiveQuadToRelative(4.6f, -1.73f)
            close()
            moveTo(8.51f, 21.29f)
            quadTo(6.88f, 20.58f, 5.65f, 19.35f)
            reflectiveQuadTo(3.71f, 16.49f)
            reflectiveQuadTo(3f, 13f)
            horizontalLineTo(5f)
            quadToRelative(0f, 2.9f, 2.05f, 4.95f)
            reflectiveQuadTo(12f, 20f)
            reflectiveQuadToRelative(4.95f, -2.05f)
            reflectiveQuadTo(19f, 13f)
            horizontalLineToRelative(2f)
            quadToRelative(0f, 1.85f, -0.71f, 3.49f)
            reflectiveQuadToRelative(-1.94f, 2.86f)
            reflectiveQuadToRelative(-2.86f, 1.94f)
            reflectiveQuadTo(12f, 22f)
            reflectiveQuadTo(8.51f, 21.29f)
            close()
            moveTo(3f, 13f)
            quadTo(3f, 11.15f, 3.71f, 9.51f)
            reflectiveQuadTo(5.65f, 6.65f)
            quadTo(6.88f, 5.43f, 8.51f, 4.71f)
            reflectiveQuadTo(12f, 4f)
            quadToRelative(1.55f, 0f, 2.98f, 0.5f)
            reflectiveQuadToRelative(2.68f, 1.45f)
            lineToRelative(1.4f, -1.4f)
            lineToRelative(1.4f, 1.4f)
            lineToRelative(-1.4f, 1.4f)
            quadTo(20f, 8.6f, 20.5f, 10.02f)
            reflectiveQuadTo(21f, 13f)
            horizontalLineTo(19f)
            quadTo(19f, 10.1f, 16.95f, 8.05f)
            reflectiveQuadTo(12f, 6f)
            reflectiveQuadTo(7.05f, 8.05f)
            reflectiveQuadTo(5f, 13f)
            horizontalLineTo(3f)
            close()
            moveTo(9f, 3f)
            verticalLineTo(1f)
            horizontalLineToRelative(6f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            close()
            moveTo(7.05f, 17.95f)
            quadTo(5f, 15.9f, 5f, 13f)
            reflectiveQuadTo(7.05f, 8.05f)
            reflectiveQuadTo(12f, 6f)
            reflectiveQuadToRelative(4.95f, 2.05f)
            reflectiveQuadTo(19f, 13f)
            reflectiveQuadToRelative(-2.05f, 4.95f)
            reflectiveQuadTo(12f, 20f)
            reflectiveQuadTo(7.05f, 17.95f)
            close()
            moveTo(12f, 13f)
            close()
          }
        }
        .build()
    return _AvgTime!!
  }

private var _AvgTime: ImageVector? = null
