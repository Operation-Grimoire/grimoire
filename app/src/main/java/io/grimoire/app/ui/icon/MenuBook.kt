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
public val AppIcons.MenuBook: ImageVector
  get() {
    if (_MenuBook != null) {
      return _MenuBook!!
    }
    _MenuBook =
      ImageVector.Builder(
          name = "menu_book",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
          autoMirror = true,
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
            moveTo(14f, 9.9f)
            verticalLineTo(8.2f)
            quadTo(14.83f, 7.85f, 15.69f, 7.68f)
            reflectiveQuadTo(17.5f, 7.5f)
            quadToRelative(0.65f, 0f, 1.28f, 0.1f)
            reflectiveQuadTo(20f, 7.85f)
            verticalLineToRelative(1.6f)
            quadTo(19.4f, 9.23f, 18.79f, 9.11f)
            reflectiveQuadTo(17.5f, 9f)
            quadTo(16.55f, 9f, 15.68f, 9.24f)
            quadTo(14.8f, 9.48f, 14f, 9.9f)
            close()
            moveToRelative(0f, 5.5f)
            verticalLineTo(13.7f)
            quadToRelative(0.83f, -0.35f, 1.69f, -0.53f)
            reflectiveQuadTo(17.5f, 13f)
            quadToRelative(0.65f, 0f, 1.28f, 0.1f)
            reflectiveQuadTo(20f, 13.35f)
            verticalLineToRelative(1.6f)
            quadTo(19.4f, 14.73f, 18.79f, 14.61f)
            reflectiveQuadTo(17.5f, 14.5f)
            quadToRelative(-0.95f, 0f, -1.82f, 0.22f)
            reflectiveQuadTo(14f, 15.4f)
            close()
            moveToRelative(0f, -2.75f)
            verticalLineToRelative(-1.7f)
            quadToRelative(0.83f, -0.35f, 1.69f, -0.53f)
            reflectiveQuadTo(17.5f, 10.25f)
            quadToRelative(0.65f, 0f, 1.28f, 0.1f)
            reflectiveQuadTo(20f, 10.6f)
            verticalLineToRelative(1.6f)
            quadTo(19.4f, 11.98f, 18.79f, 11.86f)
            reflectiveQuadTo(17.5f, 11.75f)
            quadToRelative(-0.95f, 0f, -1.82f, 0.24f)
            quadTo(14.8f, 12.23f, 14f, 12.65f)
            close()
            moveTo(6.5f, 16f)
            quadToRelative(1.18f, 0f, 2.29f, 0.26f)
            reflectiveQuadTo(11f, 17.05f)
            verticalLineTo(7.2f)
            quadTo(9.98f, 6.6f, 8.83f, 6.3f)
            reflectiveQuadTo(6.5f, 6f)
            quadTo(5.6f, 6f, 4.71f, 6.18f)
            reflectiveQuadTo(3f, 6.7f)
            verticalLineToRelative(9.9f)
            quadTo(3.88f, 16.3f, 4.74f, 16.15f)
            reflectiveQuadTo(6.5f, 16f)
            close()
            moveTo(13f, 17.05f)
            quadToRelative(1.1f, -0.53f, 2.21f, -0.79f)
            reflectiveQuadTo(17.5f, 16f)
            quadToRelative(0.9f, 0f, 1.76f, 0.15f)
            reflectiveQuadTo(21f, 16.6f)
            verticalLineTo(6.7f)
            quadTo(20.18f, 6.35f, 19.29f, 6.18f)
            reflectiveQuadTo(17.5f, 6f)
            quadTo(16.33f, 6f, 15.18f, 6.3f)
            reflectiveQuadTo(13f, 7.2f)
            verticalLineToRelative(9.85f)
            close()
            moveTo(12f, 20f)
            quadTo(10.8f, 19.05f, 9.4f, 18.52f)
            reflectiveQuadTo(6.5f, 18f)
            quadTo(5.45f, 18f, 4.44f, 18.27f)
            reflectiveQuadTo(2.5f, 19.05f)
            quadTo(1.98f, 19.33f, 1.49f, 19.02f)
            quadTo(1f, 18.73f, 1f, 18.15f)
            verticalLineTo(6.1f)
            quadTo(1f, 5.82f, 1.14f, 5.57f)
            quadTo(1.28f, 5.32f, 1.55f, 5.2f)
            quadTo(2.7f, 4.6f, 3.95f, 4.3f)
            reflectiveQuadTo(6.5f, 4f)
            quadTo(7.95f, 4f, 9.34f, 4.38f)
            reflectiveQuadTo(12f, 5.5f)
            quadTo(13.28f, 4.75f, 14.66f, 4.38f)
            reflectiveQuadTo(17.5f, 4f)
            quadToRelative(1.3f, 0f, 2.55f, 0.3f)
            reflectiveQuadToRelative(2.4f, 0.9f)
            quadToRelative(0.27f, 0.13f, 0.41f, 0.38f)
            reflectiveQuadTo(23f, 6.1f)
            verticalLineTo(18.15f)
            quadToRelative(0f, 0.58f, -0.49f, 0.88f)
            quadToRelative(-0.49f, 0.3f, -1.01f, 0.03f)
            quadToRelative(-0.92f, -0.5f, -1.94f, -0.78f)
            reflectiveQuadTo(17.5f, 18f)
            quadTo(16f, 18f, 14.6f, 18.52f)
            reflectiveQuadTo(12f, 20f)
            close()
            moveTo(7f, 11.65f)
            close()
          }
        }
        .build()
    return _MenuBook!!
  }

private var _MenuBook: ImageVector? = null
