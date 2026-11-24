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
public val AppIcons.WifiNotification: ImageVector
  get() {
    if (_WifiNotification != null) {
      return _WifiNotification!!
    }
    _WifiNotification =
      ImageVector.Builder(
          name = "wifi_notification",
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
            moveTo(12f, 22f)
            quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
            reflectiveQuadTo(10f, 20f)
            horizontalLineToRelative(4f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(12f, 22f)
            close()
            moveTo(4f, 19f)
            verticalLineTo(17f)
            horizontalLineTo(6f)
            verticalLineTo(10f)
            quadTo(6f, 7.93f, 7.25f, 6.31f)
            reflectiveQuadTo(10.5f, 4.2f)
            verticalLineTo(3.5f)
            quadToRelative(0f, -0.63f, 0.44f, -1.06f)
            reflectiveQuadTo(12f, 2f)
            reflectiveQuadToRelative(1.06f, 0.44f)
            reflectiveQuadTo(13.5f, 3.5f)
            verticalLineTo(4.2f)
            quadToRelative(0.58f, 0.13f, 1.09f, 0.38f)
            reflectiveQuadToRelative(0.99f, 0.6f)
            quadToRelative(-0.65f, 0.2f, -1.29f, 0.44f)
            reflectiveQuadTo(13.05f, 6.15f)
            quadTo(12.78f, 6.07f, 12.53f, 6.04f)
            reflectiveQuadTo(12f, 6f)
            quadTo(10.35f, 6f, 9.18f, 7.18f)
            reflectiveQuadTo(8f, 10f)
            verticalLineToRelative(7f)
            horizontalLineTo(20f)
            verticalLineToRelative(2f)
            horizontalLineTo(4f)
            close()
            moveToRelative(8.25f, -7.5f)
            close()
            moveToRelative(0.5f, -1.1f)
            lineTo(11.68f, 9.35f)
            quadToRelative(1.1f, -1.1f, 2.56f, -1.72f)
            reflectiveQuadTo(17.35f, 7f)
            reflectiveQuadToRelative(3.1f, 0.63f)
            reflectiveQuadTo(23f, 9.35f)
            lineTo(21.93f, 10.4f)
            quadTo(21.05f, 9.52f, 19.86f, 9.01f)
            reflectiveQuadTo(17.33f, 8.5f)
            quadToRelative(-1.35f, 0f, -2.52f, 0.51f)
            reflectiveQuadTo(12.75f, 10.4f)
            close()
            moveToRelative(2.13f, 2.13f)
            lineTo(13.8f, 11.45f)
            quadToRelative(0.68f, -0.68f, 1.57f, -1.06f)
            quadTo(16.28f, 10f, 17.33f, 10f)
            quadToRelative(1.05f, 0f, 1.96f, 0.39f)
            reflectiveQuadToRelative(1.59f, 1.06f)
            lineTo(19.8f, 12.52f)
            quadToRelative(-0.47f, -0.5f, -1.1f, -0.76f)
            quadTo(18.08f, 11.5f, 17.35f, 11.5f)
            reflectiveQuadToRelative(-1.36f, 0.27f)
            reflectiveQuadToRelative(-1.11f, 0.75f)
            close()
            moveTo(17.35f, 15f)
            lineTo(15.93f, 13.6f)
            quadToRelative(0.27f, -0.28f, 0.64f, -0.44f)
            reflectiveQuadTo(17.35f, 13f)
            reflectiveQuadToRelative(0.77f, 0.16f)
            quadToRelative(0.35f, 0.16f, 0.63f, 0.44f)
            lineTo(17.35f, 15f)
            close()
          }
        }
        .build()
    return _WifiNotification!!
  }

private var _WifiNotification: ImageVector? = null
