package com.ironlog.app.data

import android.content.Context
import androidx.core.content.edit
import com.ironlog.app.data.db.FeedbackMode
import com.ironlog.app.model.DistanceUnit
import com.ironlog.app.model.ThemeMode
import com.ironlog.app.model.WeightUnit
import com.ironlog.app.progression.EquipmentProfile
import com.ironlog.app.progression.Experience
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

data class Settings(
  val weightUnit: WeightUnit = WeightUnit.KG,
  val distanceUnit: DistanceUnit = DistanceUnit.KM,
  val defaultRestSeconds: Int = 90,
  val includeWarmupsInStats: Boolean = false,
  val keepScreenOn: Boolean = true,
  val theme: ThemeMode = ThemeMode.DARK,
  val barWeightKg: Double = 20.0,
  /** Log reps in reserve per set; it sharpens the targets but is optional. */
  val rirEnabled: Boolean = true,
  /** Pre-fill each set with a target weight and reps from your history. */
  val smartTargets: Boolean = true,
  /** Reps in reserve routines aim for unless an exercise says otherwise. */
  val defaultRir: Int = 2,
  val experience: Experience = Experience.INTERMEDIATE,
  val feedbackMode: FeedbackMode = FeedbackMode.LIGHT,
  /** Plates, dumbbells and stacks in [weightUnit]: targets snap to these. */
  val equipment: EquipmentProfile = EquipmentProfile.defaultFor(weightUnit),
  /** Insert warm-up sets before the first exercise for a muscle in a session. */
  val smartWarmups: Boolean = false,
  /** Rest timer defaults by movement (heavy lower body longest, isolation shortest). */
  val restByMovement: Boolean = true,
)

class SettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true }
  private val _settings = MutableStateFlow(read())
  val settings: StateFlow<Settings> = _settings.asStateFlow()

  val current: Settings
    get() = _settings.value

  private fun read(): Settings {
    val d = Settings()
    val unit = enumOr(prefs.getString("weightUnit", null), d.weightUnit)
    val equipment =
      prefs.getString("equipment", null)?.let { runCatching { json.decodeFromString(EquipmentProfile.serializer(), it) }.getOrNull() }?.takeIf { it.unit == unit }
        ?: EquipmentProfile.defaultFor(unit)
    return Settings(
      weightUnit = unit,
      distanceUnit = enumOr(prefs.getString("distanceUnit", null), d.distanceUnit),
      defaultRestSeconds = prefs.getInt("defaultRestSeconds", d.defaultRestSeconds),
      includeWarmupsInStats = prefs.getBoolean("includeWarmupsInStats", d.includeWarmupsInStats),
      keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
      theme = enumOr(prefs.getString("theme", null), d.theme),
      barWeightKg = prefs.getFloat("barWeightKg", d.barWeightKg.toFloat()).toDouble(),
      rirEnabled = prefs.getBoolean("rirEnabled", d.rirEnabled),
      smartTargets = prefs.getBoolean("smartTargets", d.smartTargets),
      defaultRir = prefs.getInt("defaultRir", d.defaultRir),
      experience = enumOr(prefs.getString("experience", null), d.experience),
      feedbackMode = enumOr(prefs.getString("feedbackMode", null), d.feedbackMode),
      equipment = equipment,
      smartWarmups = prefs.getBoolean("smartWarmups", d.smartWarmups),
      restByMovement = prefs.getBoolean("restByMovement", d.restByMovement),
    )
  }

  fun update(f: (Settings) -> Settings) {
    val old = _settings.value
    var s = f(old)
    // Plates and dumbbells are counted in the gym's unit; switching units starts from that unit's defaults.
    if (s.weightUnit != old.weightUnit && s.equipment.unit != s.weightUnit) s = s.copy(equipment = EquipmentProfile.defaultFor(s.weightUnit))
    prefs.edit {
      putString("weightUnit", s.weightUnit.name)
      putString("distanceUnit", s.distanceUnit.name)
      putInt("defaultRestSeconds", s.defaultRestSeconds)
      putBoolean("includeWarmupsInStats", s.includeWarmupsInStats)
      putBoolean("keepScreenOn", s.keepScreenOn)
      putString("theme", s.theme.name)
      putFloat("barWeightKg", s.barWeightKg.toFloat())
      putBoolean("rirEnabled", s.rirEnabled)
      putBoolean("smartTargets", s.smartTargets)
      putInt("defaultRir", s.defaultRir)
      putString("experience", s.experience.name)
      putString("feedbackMode", s.feedbackMode.name)
      putString("equipment", json.encodeToString(EquipmentProfile.serializer(), s.equipment))
      putBoolean("smartWarmups", s.smartWarmups)
      putBoolean("restByMovement", s.restByMovement)
    }
    _settings.value = s
  }

  private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default
}
