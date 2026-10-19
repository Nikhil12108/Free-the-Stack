package com.ironlog.app.domain

import com.ironlog.app.model.DistanceUnit
import com.ironlog.app.model.WeightUnit
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Conversion between canonical storage units (kg, m, s) and what the user sees. */
object Units {
  const val LB_PER_KG = 2.20462262185
  const val M_PER_MILE = 1609.344

  private val symbols = DecimalFormatSymbols(Locale.US)
  private val upTo2 = DecimalFormat("0.##", symbols)
  private val grouped = DecimalFormat("#,##0", symbols)

  fun toDisplayWeight(kg: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) kg else kg * LB_PER_KG

  fun fromDisplayWeight(value: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) value else value / LB_PER_KG

  /** Long-distance values (cardio) are shown in km or miles. */
  fun toDisplayDistance(meters: Double, unit: DistanceUnit): Double = if (unit == DistanceUnit.KM) meters / 1000 else meters / M_PER_MILE

  fun fromDisplayDistance(value: Double, unit: DistanceUnit): Double = if (unit == DistanceUnit.KM) value * 1000 else value * M_PER_MILE

  /** Up to two decimals, trailing zeros stripped; snaps float noise like 99.99999 to 100. */
  fun number(value: Double): String {
    val snapped = if (abs(value - value.roundToLong()) < 0.005) value.roundToLong().toDouble() else value
    return upTo2.format(snapped)
  }

  fun weight(kg: Double, unit: WeightUnit): String = "${number(toDisplayWeight(kg, unit))} ${unit.label}"

  fun volume(kg: Double, unit: WeightUnit): String = "${grouped.format(toDisplayWeight(kg, unit))} ${unit.label}"

  fun distance(meters: Double, unit: DistanceUnit): String = "${number(toDisplayDistance(meters, unit))} ${unit.label}"

  fun shortDistance(meters: Double): String = "${number(meters)} m"

  /** 75 -> "1:15", 3725 -> "1:02:05". */
  fun clock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
  }

  /** 3725 -> "1h 2m", 300 -> "5m", 42 -> "42s". */
  fun durationWords(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
      h > 0 -> "${h}h ${m}m"
      m > 0 -> "${m}m"
      else -> "${s}s"
    }
  }

  /** Parses user input that may use a comma as decimal separator; blank or junk -> null. */
  fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

  fun parseInt(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 0 }

  /** "1:30" or "90" -> 90 seconds. */
  fun parseClock(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val parts = t.split(':')
    if (parts.any { it.toIntOrNull() == null }) return null
    return parts.fold(0) { acc, p -> acc * 60 + p.toInt() }.takeIf { it >= 0 }
  }
}
