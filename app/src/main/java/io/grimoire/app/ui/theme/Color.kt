package io.grimoire.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val DefaultLight = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

val DefaultDark = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

// Grimoire: ghostly phosphor green accents on near-black, evoking spectral light.
val GrimoireDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF7CFFB2),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF005232),
    onPrimaryContainer = Color(0xFFB6FFD0),
    secondary = Color(0xFFA8F0C6),
    onSecondary = Color(0xFF0E3B23),
    secondaryContainer = Color(0xFF1F5238),
    onSecondaryContainer = Color(0xFFC8FBDE),
    tertiary = Color(0xFFC5E8B7),
    onTertiary = Color(0xFF1A361A),
    tertiaryContainer = Color(0xFF324E2F),
    onTertiaryContainer = Color(0xFFE1F5D2),
    background = Color(0xFF07100B),
    onBackground = Color(0xFFD7E8DB),
    surface = Color(0xFF0B1610),
    onSurface = Color(0xFFD7E8DB),
    surfaceVariant = Color(0xFF1A2620),
    onSurfaceVariant = Color(0xFFBFCFC4),
    outline = Color(0xFF6A8F7A),
)

val GrimoireLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF006C44),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8AF8B8),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF3C6A52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBFF1D2),
    onSecondaryContainer = Color(0xFF002113),
    tertiary = Color(0xFF496D34),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCBF2AB),
    onTertiaryContainer = Color(0xFF0C2000),
    background = Color(0xFFF4FBF5),
    onBackground = Color(0xFF161D17),
    surface = Color(0xFFF4FBF5),
    onSurface = Color(0xFF161D17),
)

// Ocean: cool deep blues.
val OceanLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF00658E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4E7FF),
    onPrimaryContainer = Color(0xFF001E2D),
    secondary = Color(0xFF4F616E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E5F5),
    tertiary = Color(0xFF625A7C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE7DEFF),
)

val OceanDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF82CFFF),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004C6C),
    onPrimaryContainer = Color(0xFFC4E7FF),
    secondary = Color(0xFFB6C9D8),
    onSecondary = Color(0xFF21333E),
    secondaryContainer = Color(0xFF374955),
    tertiary = Color(0xFFCBC1EA),
    onTertiary = Color(0xFF332D4C),
)

// Sunset: warm orange and crimson.
val SunsetLight: ColorScheme = lightColorScheme(
    primary = Color(0xFFB1400D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCD),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF77574A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCD),
    tertiary = Color(0xFF6B5E2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E2A6),
)

val SunsetDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB59A),
    onPrimary = Color(0xFF5C1900),
    primaryContainer = Color(0xFF872800),
    onPrimaryContainer = Color(0xFFFFDBCD),
    secondary = Color(0xFFE7BEAC),
    onSecondary = Color(0xFF442A1E),
    secondaryContainer = Color(0xFF5D4033),
    tertiary = Color(0xFFD9C68B),
    onTertiary = Color(0xFF3A3005),
)

// Forest: deep evergreen and moss.
val ForestLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF276A2A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFAAF2A1),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF53634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E8CE),
    tertiary = Color(0xFF386569),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBEF),
)

val ForestDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF8FD587),
    onPrimary = Color(0xFF003A05),
    primaryContainer = Color(0xFF075213),
    onPrimaryContainer = Color(0xFFAAF2A1),
    secondary = Color(0xFFBACBB3),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3B4B39),
    tertiary = Color(0xFFA1CED3),
    onTertiary = Color(0xFF00363B),
)

// Rose: soft warm pinks.
val RoseLight: ColorScheme = lightColorScheme(
    primary = Color(0xFFB3265D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001C),
    secondary = Color(0xFF74565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFF7C5635),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
)

val RoseDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB1C5),
    onPrimary = Color(0xFF640030),
    primaryContainer = Color(0xFF8C0846),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE3BDC6),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5A3F47),
    tertiary = Color(0xFFEFBD94),
    onTertiary = Color(0xFF48290C),
)

// Midnight: deep indigo with violet accents.
val MidnightLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF3C4ABA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00115B),
    secondary = Color(0xFF5C5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFF7A5266),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E9),
)

val MidnightDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFBBC3FF),
    onPrimary = Color(0xFF0A1A8B),
    primaryContainer = Color(0xFF2333A1),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC4C4DC),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF444559),
    tertiary = Color(0xFFEBB8CD),
    onTertiary = Color(0xFF482536),
)
