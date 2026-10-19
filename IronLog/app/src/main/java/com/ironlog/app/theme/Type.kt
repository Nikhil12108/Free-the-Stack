package com.ironlog.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ironlog.app.R

// Both files are variable fonts (OFL, licences in assets/licenses); each Font() pins one weight.
val Inter =
  FontFamily(
    Font(R.font.inter, FontWeight.Light),
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
  )

val Playfair =
  FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(R.font.playfair_display, FontWeight.Medium),
    Font(R.font.playfair_display, FontWeight.SemiBold),
    Font(R.font.playfair_display, FontWeight.Bold),
    Font(R.font.playfair_display_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.playfair_display_italic, FontWeight.Medium, FontStyle.Italic),
  )

/** Ticking clocks: fixed-width digits so the numbers don't jitter every second. */
val Mono = FontFamily.Monospace

private fun serif(size: Int, line: Int, tracking: Double, weight: FontWeight = FontWeight.Medium) =
  TextStyle(fontFamily = Playfair, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

private fun sans(size: Int, line: Int, tracking: Double, weight: FontWeight = FontWeight.Normal) =
  TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

// Headings and titles are serif (tight tracking); UI text, numbers and labels are Inter.
val Typography =
  Typography(
    displayLarge = serif(56, 60, -1.2),
    displayMedium = serif(44, 48, -0.8),
    displaySmall = serif(36, 40, -0.5),
    headlineLarge = serif(32, 38, -0.5),
    headlineMedium = serif(28, 34, -0.4),
    headlineSmall = serif(24, 30, -0.2),
    titleLarge = serif(22, 28, -0.2, FontWeight.SemiBold),
    titleMedium = serif(18, 24, 0.0),
    titleSmall = sans(14, 20, 0.1, FontWeight.SemiBold),
    bodyLarge = sans(16, 24, 0.15),
    bodyMedium = sans(14, 20, 0.15),
    bodySmall = sans(12, 16, 0.2),
    labelLarge = sans(14, 20, 0.2, FontWeight.Medium),
    labelMedium = sans(12, 16, 0.6, FontWeight.Medium),
    labelSmall = sans(11, 16, 0.8, FontWeight.Medium),
  )

/** Small uppercase label with wide tracking (pass text already uppercased). */
val EyebrowStyle = sans(11, 16, 1.6, FontWeight.Medium)

val ClockStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
