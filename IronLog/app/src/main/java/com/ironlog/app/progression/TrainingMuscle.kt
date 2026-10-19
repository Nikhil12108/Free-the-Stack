package com.ironlog.app.progression

import com.ironlog.app.model.MuscleGroup
import kotlinx.serialization.Serializable

enum class Recovery {
  SLOW,
  MODERATE,
  FAST,
}

/**
 * The muscles plans are built around. Weekly set landmarks are IronLog's starting values for an
 * intermediate lifter (direct sets): maintenance, minimum effective, and the most that is usually
 * recoverable at 2 / 3 / 4+ sessions a week. [sessionCap] is the most sets in one session.
 */
@Serializable
enum class TrainingMuscle(
  val label: String,
  val recovery: Recovery,
  val minSessions: Int,
  val maxSessions: Int,
  val maintenance: Int,
  val minimumEffective: Int,
  private val maxRecoverable: IntArray,
  val sessionCap: Int,
  val minor: Boolean = false,
) {
  CHEST("Chest", Recovery.SLOW, 2, 3, 4, 6, intArrayOf(18, 22, 26), 10),
  BACK("Back", Recovery.MODERATE, 2, 4, 6, 8, intArrayOf(20, 24, 28), 12),
  SHOULDERS("Shoulders", Recovery.FAST, 2, 6, 6, 8, intArrayOf(22, 26, 30), 12),
  BICEPS("Biceps", Recovery.FAST, 2, 6, 4, 8, intArrayOf(18, 22, 26), 10),
  TRICEPS("Triceps", Recovery.MODERATE, 2, 4, 4, 6, intArrayOf(14, 18, 22), 10),
  QUADS("Quads", Recovery.SLOW, 2, 3, 4, 6, intArrayOf(16, 20, 22), 10),
  HAMSTRINGS("Hamstrings", Recovery.SLOW, 2, 3, 3, 4, intArrayOf(12, 14, 16), 8),
  GLUTES("Glutes", Recovery.SLOW, 2, 3, 0, 4, intArrayOf(12, 16, 20), 10),
  CALVES("Calves", Recovery.MODERATE, 2, 6, 4, 6, intArrayOf(16, 20, 24), 10),
  ABS("Abs", Recovery.MODERATE, 2, 6, 0, 4, intArrayOf(16, 20, 22), 10),
  TRAPS("Traps", Recovery.FAST, 2, 4, 0, 4, intArrayOf(14, 18, 22), 10),
  FOREARMS("Forearms", Recovery.FAST, 2, 6, 0, 4, intArrayOf(14, 18, 22), 10),
  LOWER_BACK("Lower back", Recovery.SLOW, 1, 2, 0, 0, intArrayOf(8, 10, 12), 6, minor = true),
  ADDUCTORS("Adductors", Recovery.MODERATE, 1, 2, 0, 0, intArrayOf(8, 10, 12), 6, minor = true),
  NECK("Neck", Recovery.MODERATE, 1, 2, 0, 0, intArrayOf(8, 10, 12), 6, minor = true);

  /** Weekly sets that are usually still recoverable when the muscle is trained [sessions] times. */
  fun maxRecoverable(sessions: Int): Int = maxRecoverable[(sessions.coerceIn(2, 4)) - 2]

  companion object {
    /** Planning muscle for an exercise's primary muscle; null for cardio, full body and "other". */
    fun of(m: MuscleGroup): TrainingMuscle? =
      when (m) {
        MuscleGroup.CHEST -> CHEST
        MuscleGroup.LATS,
        MuscleGroup.UPPER_BACK -> BACK
        MuscleGroup.SHOULDERS -> SHOULDERS
        MuscleGroup.BICEPS -> BICEPS
        MuscleGroup.TRICEPS -> TRICEPS
        MuscleGroup.QUADRICEPS -> QUADS
        MuscleGroup.HAMSTRINGS -> HAMSTRINGS
        MuscleGroup.GLUTES,
        MuscleGroup.ABDUCTORS -> GLUTES
        MuscleGroup.CALVES -> CALVES
        MuscleGroup.ABDOMINALS -> ABS
        MuscleGroup.TRAPS -> TRAPS
        MuscleGroup.FOREARMS -> FOREARMS
        MuscleGroup.LOWER_BACK -> LOWER_BACK
        MuscleGroup.ADDUCTORS -> ADDUCTORS
        MuscleGroup.NECK -> NECK
        MuscleGroup.CARDIO,
        MuscleGroup.FULL_BODY,
        MuscleGroup.OTHER -> null
      }

    /** The twelve muscles a plan can prioritise, in display order. */
    val major: List<TrainingMuscle> = entries.filterNot { it.minor }
  }
}

/** How hard a plan pushes a muscle's volume. */
@Serializable
enum class Priority(val label: String, val description: String) {
  EMPHASIZE("Emphasize", "Adds sets whenever you recover well, up toward the most you can handle."),
  GROW("Grow", "Steady growth near the minimum that works; adds a set only when you clearly need more."),
  MAINTAIN("Maintain", "Just enough to keep what you have, so recovery goes to your priorities."),
}
