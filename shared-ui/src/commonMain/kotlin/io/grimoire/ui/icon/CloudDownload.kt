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
public val AppIcons.CloudDownload: ImageVector
  get() {
    if (_CloudDownload != null) {
      return _CloudDownload!!
    }
    _CloudDownload =
      ImageVector.Builder(
          name = "cloud_download",
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
            moveTo(6.5f, 20f)
            quadTo(4.23f, 20f, 2.61f, 18.43f)
            reflectiveQuadTo(1f, 14.58f)
            quadTo(1f, 12.63f, 2.18f, 11.1f)
            reflectiveQuadTo(5.25f, 9.15f)
            quadTo(5.68f, 7.35f, 7.38f, 5.72f)
            quadTo(9.08f, 4.1f, 11f, 4.1f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            quadTo(13f, 5.27f, 13f, 6.1f)
            verticalLineToRelative(6.05f)
            lineTo(14.6f, 10.6f)
            lineTo(16f, 12f)
            lineToRelative(-4f, 4f)
            lineTo(8f, 12f)
            lineTo(9.4f, 10.6f)
            lineTo(11f, 12.15f)
            verticalLineTo(6.1f)
            quadTo(9.1f, 6.45f, 8.05f, 7.94f)
            quadTo(7f, 9.42f, 7f, 11f)
            horizontalLineTo(6.5f)
            quadTo(5.05f, 11f, 4.03f, 12.02f)
            reflectiveQuadTo(3f, 14.5f)
            reflectiveQuadToRelative(1.03f, 2.48f)
            reflectiveQuadTo(6.5f, 18f)
            horizontalLineToRelative(12f)
            quadToRelative(1.05f, 0f, 1.78f, -0.73f)
            reflectiveQuadTo(21f, 15.5f)
            reflectiveQuadTo(20.28f, 13.73f)
            reflectiveQuadTo(18.5f, 13f)
            horizontalLineTo(17f)
            verticalLineTo(11f)
            quadTo(17f, 9.8f, 16.45f, 8.76f)
            quadTo(15.9f, 7.72f, 15f, 7f)
            verticalLineTo(4.67f)
            quadToRelative(1.85f, 0.88f, 2.93f, 2.59f)
            quadTo(19f, 8.98f, 19f, 11f)
            quadToRelative(1.73f, 0.2f, 2.86f, 1.49f)
            reflectiveQuadTo(23f, 15.5f)
            quadToRelative(0f, 1.88f, -1.31f, 3.19f)
            reflectiveQuadTo(18.5f, 20f)
            horizontalLineTo(6.5f)
            close()
            moveTo(12f, 11.05f)
            close()
          }
        }
        .build()
    return _CloudDownload!!
  }

private var _CloudDownload: ImageVector? = null
