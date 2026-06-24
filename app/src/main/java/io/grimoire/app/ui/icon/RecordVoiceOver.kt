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
public val AppIcons.RecordVoiceOver: ImageVector
  get() {
    if (_RecordVoiceOver != null) {
      return _RecordVoiceOver!!
    }
    _RecordVoiceOver =
      ImageVector.Builder(
          name = "record_voice_over",
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
            moveTo(19.95f, 15.95f)
            lineTo(18.4f, 14.4f)
            quadToRelative(1.1f, -1.02f, 1.72f, -2.42f)
            reflectiveQuadTo(20.75f, 9f)
            reflectiveQuadTo(20.13f, 6.05f)
            quadTo(19.5f, 4.67f, 18.4f, 3.65f)
            lineToRelative(1.55f, -1.6f)
            quadToRelative(1.4f, 1.33f, 2.22f, 3.13f)
            quadTo(23f, 6.97f, 23f, 9f)
            quadToRelative(0f, 2.02f, -0.82f, 3.82f)
            reflectiveQuadToRelative(-2.22f, 3.13f)
            close()
            moveToRelative(-3.2f, -3.2f)
            lineToRelative(-1.6f, -1.6f)
            quadToRelative(0.45f, -0.42f, 0.72f, -0.96f)
            reflectiveQuadTo(16.15f, 9f)
            reflectiveQuadTo(15.88f, 7.81f)
            quadTo(15.6f, 7.27f, 15.15f, 6.85f)
            lineToRelative(1.6f, -1.6f)
            quadTo(17.55f, 5.97f, 18f, 6.94f)
            reflectiveQuadTo(18.45f, 9f)
            reflectiveQuadTo(18f, 11.06f)
            quadToRelative(-0.45f, 0.96f, -1.25f, 1.69f)
            close()
            moveTo(9f, 13f)
            quadTo(7.35f, 13f, 6.18f, 11.83f)
            reflectiveQuadTo(5f, 9f)
            reflectiveQuadTo(6.18f, 6.18f)
            reflectiveQuadTo(9f, 5f)
            reflectiveQuadToRelative(2.83f, 1.18f)
            reflectiveQuadTo(13f, 9f)
            reflectiveQuadToRelative(-1.17f, 2.82f)
            reflectiveQuadTo(9f, 13f)
            close()
            moveTo(1f, 21f)
            verticalLineTo(18.2f)
            quadTo(1f, 17.38f, 1.43f, 16.65f)
            quadTo(1.85f, 15.93f, 2.6f, 15.55f)
            quadTo(3.88f, 14.9f, 5.48f, 14.45f)
            reflectiveQuadTo(9f, 14f)
            reflectiveQuadToRelative(3.53f, 0.45f)
            reflectiveQuadToRelative(2.88f, 1.1f)
            quadToRelative(0.75f, 0.38f, 1.17f, 1.1f)
            reflectiveQuadTo(17f, 18.2f)
            verticalLineTo(21f)
            horizontalLineTo(1f)
            close()
            moveTo(3f, 19f)
            horizontalLineTo(15f)
            verticalLineTo(18.2f)
            quadToRelative(0f, -0.27f, -0.14f, -0.5f)
            quadTo(14.73f, 17.48f, 14.5f, 17.35f)
            quadTo(13.6f, 16.9f, 12.19f, 16.45f)
            quadTo(10.78f, 16f, 9f, 16f)
            reflectiveQuadTo(5.81f, 16.45f)
            reflectiveQuadTo(3.5f, 17.35f)
            quadTo(3.28f, 17.48f, 3.14f, 17.7f)
            quadTo(3f, 17.93f, 3f, 18.2f)
            verticalLineTo(19f)
            close()
            moveToRelative(7.41f, -8.59f)
            quadTo(11f, 9.82f, 11f, 9f)
            quadTo(11f, 8.17f, 10.41f, 7.59f)
            reflectiveQuadTo(9f, 7f)
            quadTo(8.18f, 7f, 7.59f, 7.59f)
            reflectiveQuadTo(7f, 9f)
            quadToRelative(0f, 0.82f, 0.59f, 1.41f)
            reflectiveQuadTo(9f, 11f)
            quadToRelative(0.83f, 0f, 1.41f, -0.59f)
            close()
            moveTo(9f, 9f)
            close()
            moveTo(9f, 19f)
            close()
          }
        }
        .build()
    return _RecordVoiceOver!!
  }

private var _RecordVoiceOver: ImageVector? = null
