package io.grimoire.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val AppIcons.SaveAlt: ImageVector
  get() {
    _SaveAlt?.let { return it }
    _SaveAlt =
      ImageVector.Builder(
          name = "save_alt",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          addPath(
            pathData = PathParser().parsePathString(
              "M19 12v7H5v-7H3v7c0 1.1 0.9 2 2 2h14c1.1 0 2-0.9 2-2v-7h-2z" +
                "M13 12.67l2.59-2.58L17 11.5l-5 5-5-5 1.41-1.41L11 12.67V3h2v9.67z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
          )
        }
        .build()
    return _SaveAlt!!
  }

private var _SaveAlt: ImageVector? = null
