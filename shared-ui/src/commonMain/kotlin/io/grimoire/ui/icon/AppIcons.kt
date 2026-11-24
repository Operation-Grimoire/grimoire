package io.grimoire.app.ui.icon

/**
 * Central registry of the app's icons. Each icon is an extension `val` on this object,
 * one per file in this package, sourced from Google's Material Symbols (Outlined) render
 * endpoint as a ready Compose [androidx.compose.ui.graphics.vector.ImageVector].
 *
 * Reference as `AppIcons.Search`, etc. The app does not depend on `material-icons-extended`;
 * to add an icon, fetch its Material Symbol into a new `AppIcons.<Name>.kt` (mirror an
 * existing file) and reference `AppIcons.<Name>`.
 */
object AppIcons
