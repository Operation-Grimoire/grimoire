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
public val AppIcons.NewReleases: ImageVector
  get() {
    if (_NewReleases != null) {
      return _NewReleases!!
    }
    _NewReleases =
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
            moveTo(9.45f, 19.95f)
            lineTo(12f, 18.85f)
            lineToRelative(2.6f, 1.1f)
            lineTo(16f, 17.55f)
            lineTo(18.75f, 16.9f)
            lineTo(18.5f, 14.1f)
            lineTo(20.35f, 12f)
            lineTo(18.5f, 9.85f)
            lineToRelative(0.25f, -2.8f)
            lineTo(16f, 6.45f)
            lineTo(14.55f, 4.05f)
            lineTo(12f, 5.15f)
            lineTo(9.4f, 4.05f)
            lineTo(8f, 6.45f)
            lineTo(5.25f, 7.05f)
            lineTo(5.5f, 9.85f)
            lineTo(3.65f, 12f)
            lineTo(5.5f, 14.1f)
            lineTo(5.25f, 16.95f)
            lineTo(8f, 17.55f)
            lineToRelative(1.45f, 2.4f)
            close()
            moveTo(12f, 12f)
            close()
            moveToRelative(-1.05f, 3.55f)
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
    return _NewReleases!!
  }

private var _NewReleases: ImageVector? = null
