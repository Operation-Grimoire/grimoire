package io.grimoire.app.ui.screen.novelupdates

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.grimoire.app.R

@Composable
internal fun localizedNuLanguage(name: String): String = when (name) {
    "Chinese" -> stringResource(R.string.nu_language_chinese)
    "Filipino" -> stringResource(R.string.nu_language_filipino)
    "Indonesian" -> stringResource(R.string.nu_language_indonesian)
    "Japanese" -> stringResource(R.string.nu_language_japanese)
    "Khmer" -> stringResource(R.string.nu_language_khmer)
    "Korean" -> stringResource(R.string.nu_language_korean)
    "Malaysian" -> stringResource(R.string.nu_language_malaysian)
    "Thai" -> stringResource(R.string.nu_language_thai)
    "Vietnamese" -> stringResource(R.string.nu_language_vietnamese)
    else -> name
}

@Composable
internal fun localizedNuGenre(name: String): String = when (name) {
    "Action" -> stringResource(R.string.nu_genre_action)
    "Adult" -> stringResource(R.string.nu_genre_adult)
    "Adventure" -> stringResource(R.string.nu_genre_adventure)
    "Comedy" -> stringResource(R.string.nu_genre_comedy)
    "Drama" -> stringResource(R.string.nu_genre_drama)
    "Ecchi" -> stringResource(R.string.nu_genre_ecchi)
    "Fantasy" -> stringResource(R.string.nu_genre_fantasy)
    "Gender Bender" -> stringResource(R.string.nu_genre_gender_bender)
    "Harem" -> stringResource(R.string.nu_genre_harem)
    "Historical" -> stringResource(R.string.nu_genre_historical)
    "Horror" -> stringResource(R.string.nu_genre_horror)
    "Josei" -> stringResource(R.string.nu_genre_josei)
    "Martial Arts" -> stringResource(R.string.nu_genre_martial_arts)
    "Mature" -> stringResource(R.string.nu_genre_mature)
    "Mecha" -> stringResource(R.string.nu_genre_mecha)
    "Mystery" -> stringResource(R.string.nu_genre_mystery)
    "Psychological" -> stringResource(R.string.nu_genre_psychological)
    "Romance" -> stringResource(R.string.nu_genre_romance)
    "School Life" -> stringResource(R.string.nu_genre_school_life)
    "Sci-fi" -> stringResource(R.string.nu_genre_scifi)
    "Seinen" -> stringResource(R.string.nu_genre_seinen)
    "Shoujo" -> stringResource(R.string.nu_genre_shoujo)
    "Shoujo Ai" -> stringResource(R.string.nu_genre_shoujo_ai)
    "Shounen" -> stringResource(R.string.nu_genre_shounen)
    "Shounen Ai" -> stringResource(R.string.nu_genre_shounen_ai)
    "Slice of Life" -> stringResource(R.string.nu_genre_slice_of_life)
    "Smut" -> stringResource(R.string.nu_genre_smut)
    "Sports" -> stringResource(R.string.nu_genre_sports)
    "Supernatural" -> stringResource(R.string.nu_genre_supernatural)
    "Tragedy" -> stringResource(R.string.nu_genre_tragedy)
    "Wuxia" -> stringResource(R.string.nu_genre_wuxia)
    "Xianxia" -> stringResource(R.string.nu_genre_xianxia)
    "Xuanhuan" -> stringResource(R.string.nu_genre_xuanhuan)
    "Yaoi" -> stringResource(R.string.nu_genre_yaoi)
    "Yuri" -> stringResource(R.string.nu_genre_yuri)
    else -> name
}
