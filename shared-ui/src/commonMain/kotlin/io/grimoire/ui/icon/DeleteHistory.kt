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
public val AppIcons.DeleteHistory: ImageVector
  get() {
    if (_DeleteHistory != null) {
      return _DeleteHistory!!
    }
    _DeleteHistory =
      ImageVector.Builder(
          name = "delete_history",
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
            moveTo(16.4f, 21f)
            lineTo(15f, 19.6f)
            lineToRelative(2.1f, -2.1f)
            lineTo(15f, 15.4f)
            lineTo(16.4f, 14f)
            lineToRelative(2.1f, 2.1f)
            lineTo(20.6f, 14f)
            lineTo(22f, 15.4f)
            lineToRelative(-2.07f, 2.1f)
            lineTo(22f, 19.6f)
            lineTo(20.6f, 21f)
            lineTo(18.5f, 18.93f)
            lineTo(16.4f, 21f)
            close()
            moveTo(12f, 21f)
            quadTo(8.55f, 21f, 5.99f, 18.71f)
            quadTo(3.43f, 16.43f, 3.05f, 13f)
            horizontalLineTo(5.1f)
            quadToRelative(0.35f, 2.6f, 2.31f, 4.3f)
            reflectiveQuadTo(12f, 19f)
            quadToRelative(0.28f, 0f, 0.51f, -0.01f)
            reflectiveQuadTo(13f, 18.93f)
            verticalLineToRelative(2.02f)
            quadToRelative(-0.25f, 0.03f, -0.49f, 0.04f)
            reflectiveQuadTo(12f, 21f)
            close()
            moveTo(3f, 10f)
            verticalLineTo(4f)
            horizontalLineTo(5f)
            verticalLineTo(6.35f)
            quadTo(6.28f, 4.75f, 8.11f, 3.88f)
            reflectiveQuadTo(12f, 3f)
            quadToRelative(3.75f, 0f, 6.38f, 2.63f)
            reflectiveQuadTo(21f, 12f)
            horizontalLineTo(19f)
            quadTo(19f, 9.07f, 16.96f, 7.04f)
            reflectiveQuadTo(12f, 5f)
            quadTo(10.28f, 5f, 8.78f, 5.8f)
            reflectiveQuadTo(6.25f, 8f)
            horizontalLineTo(9f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            close()
            moveToRelative(10.35f, 4.75f)
            lineTo(11f, 12.4f)
            verticalLineTo(7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(4.6f)
            lineTo(14.4f, 13f)
            lineToRelative(-1.05f, 1.75f)
            close()
          }
        }
        .build()
    return _DeleteHistory!!
  }

private var _DeleteHistory: ImageVector? = null
