package com.ironlog.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.ironlog.app.model.ThemeMode

// Primary is the mono CTA (white pill, black label). The accent lives in primaryContainer (fills:
// live bars, FAB, done sets) and secondary (accent text/icons); read it through the extensions below.
private val DarkColors =
  darkColorScheme(
    primary = Color.White,
    onPrimary = Void,
    primaryContainer = Ember,
    onPrimaryContainer = Void,
    inversePrimary = EmberDeep, // snackbar actions on the white inverse surface
    secondary = Ember,
    onSecondary = Void,
    // Tonal buttons, the nav indicator and segmented selections: a quiet "glass" grey.
    secondaryContainer = Cell,
    onSecondaryContainer = Color.White,
    tertiary = PeriwinkleDark,
    onTertiary = Void,
    error = RoseDark,
    onError = Void,
    background = Void,
    onBackground = Color.White,
    surface = Void,
    onSurface = Color.White,
    surfaceVariant = Raised,
    onSurfaceVariant = Gray400,
    surfaceTint = Void,
    surfaceDim = Void,
    surfaceBright = Cell,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Card, // cards
    surfaceContainer = Cell, // menus: a step above the cards they open from
    surfaceContainerHigh = Raised, // dialogs
    surfaceContainerHighest = Cell, // input cells inside cards
    inverseSurface = Color.White,
    inverseOnSurface = Void,
    outline = Rule,
    outlineVariant = Hairline,
    scrim = Color.Black,
  )

private val LightColors =
  lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Ember,
    onPrimaryContainer = Void,
    inversePrimary = Ember,
    secondary = EmberDeep,
    onSecondary = Color.White,
    secondaryContainer = PaperCell,
    onSecondaryContainer = Ink,
    tertiary = PeriwinkleLight,
    onTertiary = Color.White,
    error = RoseLight,
    onError = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperCell,
    onSurfaceVariant = Graphite,
    surfaceTint = Paper,
    surfaceDim = PaperHairline,
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = PaperCell,
    inverseSurface = Ink,
    inverseOnSurface = Color.White,
    outline = PaperRule,
    outlineVariant = PaperHairline,
    scrim = Color.Black,
  )

/** Accent fill (#FF4500 in both themes); content on it is near-black for contrast. */
val ColorScheme.accent: Color
  get() = primaryContainer

val ColorScheme.onAccent: Color
  get() = onPrimaryContainer

/** Accent for text and small icons sitting on the page or a card. */
val ColorScheme.accentText: Color
  get() = secondary

/** Warm-up set marker. */
val ColorScheme.warmup: Color
  get() = if (background.luminance() < 0.5f) GoldDark else GoldLight

/** Card edge: white/10 on dark, a soft rule on paper. */
val ColorScheme.cardBorder: Color
  get() = if (background.luminance() < 0.5f) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

val IronLogShapes =
  Shapes(
    extraSmall = RoundedCornerShape(12.dp), // text fields, menus, snackbars
    small = RoundedCornerShape(50), // chips are pills
    medium = RoundedCornerShape(24.dp), // cards
    large = RoundedCornerShape(50), // FAB: a disc
    extraLarge = RoundedCornerShape(28.dp), // dialogs, sheets
  )

@Composable
fun ThemeMode.isDark(): Boolean =
  when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }

@Composable
fun IronLogTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
  val colors = if (mode.isDark()) DarkColors else LightColors
  MaterialTheme(colorScheme = colors, typography = Typography, shapes = IronLogShapes) {
    val selection = TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.accent.copy(alpha = 0.35f))
    CompositionLocalProvider(LocalTextSelectionColors provides selection, content = content)
  }
}
