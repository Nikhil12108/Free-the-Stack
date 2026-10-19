package com.ironlog.app.progression

import kotlin.math.roundToInt

/**
 * Sets-per-muscle decisions for a training block. Everything here is a pure function of what was
 * logged and answered, so a later answer (soreness reported a few days on) simply recomputes the
 * same result with more information.
 */
object Volume {
  /** Workload answers. */
  const val WORKLOAD_EASY = 0
  const val WORKLOAD_RIGHT = 1
  const val WORKLOAD_LIMIT = 2
  const val WORKLOAD_TOO_MUCH = 3

  /** Pump answers. */
  const val PUMP_LOW = 0
  const val PUMP_GREAT = 2

  /** Soreness answers (about the previous session for the muscle). */
  const val SORE_NEVER = 1
  const val SORE_HEALED_EARLY = 2
  const val SORE_ON_TIME = 3
  const val SORE_STILL = 4

  /** Performance scores, computed from the sets. */
  const val PERF_EXCEEDED = 1
  const val PERF_AS_PLANNED = 2
  const val PERF_STRUGGLED = 3
  const val PERF_REGRESSED = 4

  enum class Why {
    RAMP,
    MORE_NEEDED,
    HOLD,
    AT_LIMIT,
    CEILING,
    LESS,
    RECOVERY,
    REGRESSED,
    MAINTAIN,
  }

  data class Inputs(
    val priority: Priority,
    /** Sets this muscle got in this session. */
    val sessionSets: Int,
    /** Sets this muscle got across the whole week. */
    val weeklySets: Int,
    /** Sessions per week that train this muscle. */
    val sessionsPerWeek: Int,
    val muscle: TrainingMuscle?,
    val soreness: Int? = null,
    val pump: Int? = null,
    val workload: Int? = null,
    val performance: Int = PERF_AS_PLANNED,
    /** Performance at this day's previous session (a week earlier), if any. */
    val previousPerformance: Int? = null,
  )

  data class Decision(val delta: Int, val recovery: Boolean, val why: Why)

  /** How the next session for the same day should change this muscle's sets. */
  fun decide(i: Inputs): Decision {
    if (i.performance == PERF_REGRESSED) {
      return if (i.previousPerformance == PERF_REGRESSED) Decision(0, recovery = true, Why.RECOVERY) else Decision(0, false, Why.REGRESSED)
    }
    val s = i.soreness
    val noAnswers = s == null && i.pump == null && i.workload == null
    var delta: Int
    var why: Why
    when {
      s == SORE_STILL -> {
        delta = -1
        why = Why.LESS
      }
      i.performance == PERF_STRUGGLED -> {
        delta = 0
        why = Why.HOLD
      }
      s == SORE_NEVER && i.performance == PERF_EXCEEDED -> {
        delta = 2
        why = Why.MORE_NEEDED
      }
      s != null && s <= SORE_HEALED_EARLY -> {
        delta = 1
        why = Why.MORE_NEEDED
      }
      s == SORE_ON_TIME -> {
        delta = 0
        why = Why.HOLD
      }
      noAnswers -> {
        // Nothing asked: climb steadily while performance keeps up, like a planned ramp.
        delta = 1
        why = Why.RAMP
      }
      i.workload == WORKLOAD_EASY -> {
        delta = if (i.pump == PUMP_LOW && i.performance == PERF_EXCEEDED) 2 else 1
        why = Why.MORE_NEEDED
      }
      i.workload == WORKLOAD_RIGHT || i.workload == null -> {
        delta = if (i.performance == PERF_EXCEEDED || i.pump == PUMP_LOW) 1 else 0
        why = if (delta > 0) Why.MORE_NEEDED else Why.HOLD
      }
      else -> {
        delta = 0
        why = Why.HOLD
      }
    }
    // Answers that cap or cut regardless of the rest.
    if (i.workload == WORKLOAD_TOO_MUCH && delta > -1) {
      delta = -1
      why = Why.LESS
    }
    if (i.workload == WORKLOAD_LIMIT && delta > 0) {
      delta = 0
      why = Why.AT_LIMIT
    }
    if (i.pump == PUMP_GREAT && delta > 0) {
      delta = 0
      why = Why.HOLD
    }
    // Performance must keep up before volume goes up.
    if (i.performance >= PERF_STRUGGLED && delta > 0) {
      delta = 0
      why = Why.HOLD
    }
    // Priority: how far the muscle may climb.
    val m = i.muscle
    when (i.priority) {
      Priority.MAINTAIN ->
        if (delta > 0) {
          delta = 0
          why = Why.MAINTAIN
        }
      Priority.GROW -> delta = delta.coerceAtMost(1)
      Priority.EMPHASIZE -> delta = delta.coerceAtMost(2)
    }
    if (delta > 0) {
      // A muscle trained several times a week grows by at most one set per session.
      if (i.sessionsPerWeek >= 2) delta = delta.coerceAtMost(1)
      val ceiling = weeklyCeiling(i.priority, m, i.sessionsPerWeek)
      if (i.weeklySets + delta > ceiling) {
        delta = (ceiling - i.weeklySets).coerceAtLeast(0)
        if (delta == 0) why = Why.CEILING
      }
      if (m != null && i.sessionSets + delta > m.sessionCap) {
        delta = (m.sessionCap - i.sessionSets).coerceAtLeast(0)
        if (delta == 0) why = Why.CEILING
      }
    }
    if (delta < 0 && i.sessionSets + delta < 1) delta = 1 - i.sessionSets
    return Decision(delta, recovery = false, why = why)
  }

  /** The most weekly sets a priority lets a muscle climb to. */
  fun weeklyCeiling(priority: Priority, muscle: TrainingMuscle?, sessionsPerWeek: Int): Int {
    if (muscle == null) return 20
    return when (priority) {
      Priority.EMPHASIZE -> muscle.maxRecoverable(sessionsPerWeek)
      Priority.GROW -> muscle.minimumEffective + 4 + 2 * (sessionsPerWeek - 1).coerceAtLeast(0)
      Priority.MAINTAIN -> muscle.maintenance.coerceAtLeast(2)
    }
  }

  /**
   * How a session went for one muscle, 1 exceeded … 4 regressed. Sets are compared on reps to
   * failure against what they aimed for; [regressedShare] is the fraction of sets that fell behind the
   * same set last time.
   */
  fun performance(sets: List<LoggedSet>, regressedShare: Double): Int {
    if (regressedShare >= 0.5) return PERF_REGRESSED
    val diffs =
      sets.mapNotNull { s ->
        val reps = s.reps ?: return@mapNotNull null
        val target = s.targetReps ?: return@mapNotNull null
        val targetRir = s.targetRir ?: Strength.DEFAULT_RIR
        (reps + Strength.effectiveRir(s, targetRir)) - (target + targetRir)
      }
    if (diffs.isEmpty()) return PERF_AS_PLANNED
    val mean = diffs.average()
    return when {
      mean >= 2 -> PERF_EXCEEDED
      mean > -1 -> PERF_AS_PLANNED
      else -> PERF_STRUGGLED
    }
  }

  /**
   * Spreads a muscle's change over its exercises in one session: added sets go to the exercise with
   * the fewest, removed ones come off the one with the most; every exercise keeps at least one set.
   */
  fun distribute(current: List<Int>, delta: Int): List<Int> {
    val sets = current.toMutableList()
    if (sets.isEmpty()) return sets
    repeat(kotlin.math.abs(delta)) {
      if (delta > 0) {
        val i = sets.indices.minByOrNull { sets[it] }!!
        sets[i]++
      } else {
        val i = sets.indices.reversed().maxByOrNull { sets[it] }!!
        if (sets[i] > 1) sets[i]--
      }
    }
    return sets
  }

  /** After a recovery session: resume halfway between where the block started and where it was. */
  fun afterRecovery(startSets: Int, beforeRecovery: Int): Int = ((startSets + beforeRecovery) / 2.0).roundToInt().coerceAtLeast(1)

  /** Week-1 sets per session for a muscle with no history to go on (used by the plan builder). */
  fun startingSessionSets(priority: Priority, muscle: TrainingMuscle, sessionsPerWeek: Int, experience: Experience): Int {
    val weekly =
      when (priority) {
        Priority.EMPHASIZE -> muscle.minimumEffective + 2
        Priority.GROW -> muscle.minimumEffective
        Priority.MAINTAIN -> muscle.maintenance.coerceAtLeast(2)
      }
    val scaled = if (experience == Experience.BEGINNER) weekly * 0.75 else weekly.toDouble()
    return (scaled / sessionsPerWeek.coerceAtLeast(1)).roundToInt().coerceIn(1, muscle.sessionCap)
  }
}
