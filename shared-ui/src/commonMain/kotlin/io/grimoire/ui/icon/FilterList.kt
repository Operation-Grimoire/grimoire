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
public val AppIcons.FilterList: ImageVector
  get() {
    if (_FilterList != null) {
      return _FilterList!!
    }
    _FilterList =
      ImageVector.Builder(
          name = "filter_list",
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
            moveTo(10f, 18f)
            verticalLineTo(16f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(2f)
            horizontalLineTo(10f)
            close()
            moveTo(6f, 13f)
            verticalLineTo(11f)
            horizontalLineTo(18f)
            verticalLineToRelative(2f)
            horizontalLineTo(6f)
            close()
            moveTo(3f, 8f)
            verticalLineTo(6f)
            horizontalLineTo(21f)
            verticalLineTo(8f)
            horizontalLineTo(3f)
            close()
          }
        }
        .build()
    return _FilterList!!
  }

private var _FilterList: ImageVector? = null
