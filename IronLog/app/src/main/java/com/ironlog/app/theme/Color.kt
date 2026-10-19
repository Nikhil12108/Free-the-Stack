package com.ironlog.app.theme

import androidx.compose.ui.graphics.Color

// IronLog palette: near-black "void", one ember accent, white type. The accent is reserved for
// live state (workout running, rest timer), completed sets and records; everything else is mono.
val Ember = Color(0xFFFF4500)
val EmberDeep = Color(0xFFD63A00) // accent as text/icons on a light page (4.7:1 on white)
val Blush = Color(0xFFFFE0E0)

val Void = Color(0xFF050505)
val Card = Color(0xFF111111)
val Raised = Color(0xFF161616)
val Cell = Color(0xFF1E1E1E)
val Hairline = Color(0xFF242424) // ≈ white/10 over the void
val Rule = Color(0xFF3A3A3A) // ≈ white/20: outlined pills and fields
val Gray400 = Color(0xFF9CA3AF)
val Gray500 = Color(0xFF6B7280)

val Paper = Color(0xFFF6F4F1)
val PaperCell = Color(0xFFEFECE8)
val PaperRule = Color(0xFFCFCAC4)
val PaperHairline = Color(0xFFE4E0DB)
val Graphite = Color(0xFF52525B)
val Ink = Color(0xFF0A0A0A)

val RoseDark = Color(0xFFFF4D6D)
val RoseLight = Color(0xFFC81E45)
val PeriwinkleDark = Color(0xFFA5B4FC)
val PeriwinkleLight = Color(0xFF4F46E5)
val GoldDark = Color(0xFFF2C14E)
val GoldLight = Color(0xFF9A6A00)

/** Superset stripes, cycled by group number. No orange: that hue belongs to the accent. */
val SupersetColors = listOf(Color(0xFFA78BFA), Color(0xFF38BDF8), Color(0xFFF472B6), Color(0xFFA3E635), Color(0xFF2DD4BF))
