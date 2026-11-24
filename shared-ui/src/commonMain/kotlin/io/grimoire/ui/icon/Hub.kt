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
public val AppIcons.Hub: ImageVector
  get() {
    if (_Hub != null) {
      return _Hub!!
    }
    _Hub =
      ImageVector.Builder(
          name = "hub",
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
            moveTo(3.88f, 22.13f)
            quadTo(3f, 21.25f, 3f, 20f)
            reflectiveQuadTo(3.88f, 17.88f)
            reflectiveQuadTo(6f, 17f)
            quadToRelative(0.35f, 0f, 0.65f, 0.07f)
            reflectiveQuadToRelative(0.58f, 0.2f)
            lineTo(8.65f, 15.5f)
            quadTo(7.95f, 14.73f, 7.68f, 13.75f)
            quadTo(7.4f, 12.77f, 7.55f, 11.8f)
            lineTo(5.53f, 11.13f)
            quadToRelative(-0.42f, 0.63f, -1.08f, 1f)
            reflectiveQuadTo(3f, 12.5f)
            quadToRelative(-1.25f, 0f, -2.13f, -0.88f)
            reflectiveQuadTo(0f, 9.5f)
            reflectiveQuadTo(0.88f, 7.38f)
            reflectiveQuadTo(3f, 6.5f)
            reflectiveQuadTo(5.13f, 7.38f)
            reflectiveQuadTo(6f, 9.5f)
            quadTo(6f, 9.55f, 6f, 9.6f)
            reflectiveQuadTo(6f, 9.7f)
            lineToRelative(2.03f, 0.7f)
            quadTo(8.53f, 9.5f, 9.36f, 8.88f)
            reflectiveQuadToRelative(1.89f, -0.8f)
            verticalLineTo(5.9f)
            quadTo(10.28f, 5.63f, 9.64f, 4.84f)
            reflectiveQuadTo(9f, 3f)
            quadTo(9f, 1.75f, 9.88f, 0.88f)
            reflectiveQuadTo(12f, 0f)
            reflectiveQuadToRelative(2.13f, 0.88f)
            reflectiveQuadTo(15f, 3f)
            quadToRelative(0f, 1.05f, -0.65f, 1.84f)
            reflectiveQuadTo(12.75f, 5.9f)
            verticalLineTo(8.07f)
            quadToRelative(1.05f, 0.18f, 1.89f, 0.8f)
            reflectiveQuadToRelative(1.34f, 1.52f)
            lineTo(18f, 9.7f)
            quadTo(18f, 9.65f, 18f, 9.6f)
            reflectiveQuadTo(18f, 9.5f)
            quadTo(18f, 8.25f, 18.88f, 7.38f)
            reflectiveQuadTo(21f, 6.5f)
            reflectiveQuadToRelative(2.13f, 0.88f)
            reflectiveQuadTo(24f, 9.5f)
            reflectiveQuadToRelative(-0.88f, 2.13f)
            reflectiveQuadTo(21f, 12.5f)
            quadToRelative(-0.8f, 0f, -1.46f, -0.38f)
            reflectiveQuadToRelative(-1.06f, -1f)
            lineTo(16.45f, 11.8f)
            quadToRelative(0.15f, 0.97f, -0.13f, 1.94f)
            reflectiveQuadTo(15.35f, 15.5f)
            lineToRelative(1.43f, 1.75f)
            quadToRelative(0.27f, -0.13f, 0.57f, -0.19f)
            reflectiveQuadTo(18f, 17f)
            quadToRelative(1.25f, 0f, 2.13f, 0.88f)
            reflectiveQuadTo(21f, 20f)
            reflectiveQuadToRelative(-0.88f, 2.13f)
            reflectiveQuadTo(18f, 23f)
            reflectiveQuadTo(15.88f, 22.13f)
            reflectiveQuadTo(15f, 20f)
            quadToRelative(0f, -0.5f, 0.16f, -0.96f)
            reflectiveQuadTo(15.6f, 18.2f)
            lineTo(14.18f, 16.43f)
            quadTo(13.15f, 17f, 11.99f, 17f)
            reflectiveQuadTo(9.8f, 16.43f)
            lineTo(8.4f, 18.2f)
            quadToRelative(0.28f, 0.38f, 0.44f, 0.84f)
            reflectiveQuadTo(9f, 20f)
            quadToRelative(0f, 1.25f, -0.88f, 2.13f)
            reflectiveQuadTo(6f, 23f)
            reflectiveQuadTo(3.88f, 22.13f)
            close()
            moveTo(3f, 10.5f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            reflectiveQuadTo(4f, 9.5f)
            quadTo(4f, 9.07f, 3.71f, 8.79f)
            reflectiveQuadTo(3f, 8.5f)
            reflectiveQuadTo(2.29f, 8.79f)
            reflectiveQuadTo(2f, 9.5f)
            quadToRelative(0f, 0.42f, 0.29f, 0.71f)
            reflectiveQuadTo(3f, 10.5f)
            close()
            moveTo(6.71f, 20.71f)
            quadTo(7f, 20.43f, 7f, 20f)
            reflectiveQuadTo(6.71f, 19.29f)
            reflectiveQuadTo(6f, 19f)
            quadTo(5.58f, 19f, 5.29f, 19.29f)
            reflectiveQuadTo(5f, 20f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(6f, 21f)
            reflectiveQuadTo(6.71f, 20.71f)
            close()
            moveToRelative(6f, -17f)
            quadTo(13f, 3.42f, 13f, 3f)
            quadTo(13f, 2.57f, 12.71f, 2.29f)
            reflectiveQuadTo(12f, 2f)
            reflectiveQuadTo(11.29f, 2.29f)
            reflectiveQuadTo(11f, 3f)
            quadToRelative(0f, 0.42f, 0.29f, 0.71f)
            reflectiveQuadTo(12f, 4f)
            reflectiveQuadTo(12.71f, 3.71f)
            close()
            moveTo(12f, 15f)
            quadToRelative(1.05f, 0f, 1.78f, -0.73f)
            reflectiveQuadTo(14.5f, 12.5f)
            reflectiveQuadTo(13.78f, 10.73f)
            reflectiveQuadTo(12f, 10f)
            reflectiveQuadToRelative(-1.77f, 0.72f)
            reflectiveQuadTo(9.5f, 12.5f)
            reflectiveQuadToRelative(0.73f, 1.77f)
            reflectiveQuadTo(12f, 15f)
            close()
            moveToRelative(6.71f, 5.71f)
            quadTo(19f, 20.43f, 19f, 20f)
            reflectiveQuadTo(18.71f, 19.29f)
            reflectiveQuadTo(18f, 19f)
            reflectiveQuadToRelative(-0.71f, 0.29f)
            reflectiveQuadTo(17f, 20f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(18f, 21f)
            reflectiveQuadToRelative(0.71f, -0.29f)
            close()
            moveToRelative(3f, -10.5f)
            quadTo(22f, 9.92f, 22f, 9.5f)
            quadTo(22f, 9.07f, 21.71f, 8.79f)
            reflectiveQuadTo(21f, 8.5f)
            reflectiveQuadTo(20.29f, 8.79f)
            reflectiveQuadTo(20f, 9.5f)
            quadToRelative(0f, 0.42f, 0.29f, 0.71f)
            reflectiveQuadTo(21f, 10.5f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            close()
            moveTo(12f, 3f)
            close()
            moveTo(3f, 9.5f)
            close()
            moveToRelative(9f, 3f)
            close()
            moveToRelative(9f, -3f)
            close()
            moveTo(6f, 20f)
            close()
            moveToRelative(12f, 0f)
            close()
          }
        }
        .build()
    return _Hub!!
  }

private var _Hub: ImageVector? = null
