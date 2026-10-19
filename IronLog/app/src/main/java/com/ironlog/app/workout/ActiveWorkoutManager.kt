package com.ironlog.app.workout

import android.content.Context
import android.util.Log
import com.ironlog.app.data.MeasurementRepository
import com.ironlog.app.data.RoutineRepository
import com.ironlog.app.data.SettingsStore
import com.ironlog.app.data.WorkoutRepository
import com.ironlog.app.data.db.RoutineWithExercises
import com.ironlog.app.domain.Lifting
import com.ironlog.app.domain.PersonalBests
import com.ironlog.app.domain.PrKind
import com.ironlog.app.domain.SetValues
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.SetType
import com.ironlog.app.progression.HistorySession
import com.ironlog.app.progression.LoggedSet
import com.ironlog.app.progression.Periodization
import com.ironlog.app.progression.Strength
import com.ironlog.app.progression.TargetMode
import com.ironlog.app.progression.Verdict
import com.ironlog.app.workout.SessionOps.restDeferred
import com.ironlog.app.workout.SessionOps.updateSet
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * History needed while logging an exercise: last session's sets, all-time bests, and every past
 * session (newest first) for the progression engine.
 */
data class ExerciseContext(val previous: List<SetValues>, val bests: PersonalBests, val history: List<HistorySession> = emptyList()) {
  /** Last real (not deload or recovery) session's working sets, in order: what each set's verdict compares against. */
  val previousWorking: List<LoggedSet>
    get() = HistorySession.baselines(history).firstOrNull()?.working.orEmpty()
}

enum class CompleteResult {
  OK,
  MISSING_VALUES,
}

/**
 * Owns the workout in progress. State is an immutable tree in a StateFlow and is written to disk
 * shortly after every change, so a killed process resumes exactly where the user was.
 */
@OptIn(FlowPreview::class)
class ActiveWorkoutManager(
  context: Context,
  private val scope: CoroutineScope,
  private val workouts: WorkoutRepository,
  private val routines: RoutineRepository,
  private val settings: SettingsStore,
  private val restTimer: RestTimer,
  private val measurements: MeasurementRepository,
) {
  /** Called after a planned session is saved, so the plan can record it and shape next week. */
  var onPlanSessionSaved: (suspend (ActiveWorkout, Long) -> Unit)? = null
  private val file = File(context.filesDir, "active_workout.json")
  private val json = Json { ignoreUnknownKeys = true }

  private val _state = MutableStateFlow(load())
  val state: StateFlow<ActiveWorkout?> = _state.asStateFlow()

  private val _contexts = MutableStateFlow<Map<Long, ExerciseContext>>(emptyMap())
  val contexts: StateFlow<Map<Long, ExerciseContext>> = _contexts.asStateFlow()

  /** Latest logged bodyweight, for bodyweight exercises' targets and verdicts. */
  @Volatile
  var bodyweightKg: Double? = null
    private set

  init {
    scope.launch { bodyweightKg = measurements.latestBodyweightKg() }
    scope.launch {
      var bodyweightFor: Long? = null
      _state.collect { w ->
        if (w != null) {
          // Re-read once per workout, so a bodyweight logged since the last one is used.
          if (w.startTime != bodyweightFor) {
            bodyweightKg = measurements.latestBodyweightKg()
            bodyweightFor = w.startTime
          }
          loadContexts(w)
          applyTargets()
        }
      }
    }
    scope.launch(Dispatchers.IO) { _state.drop(1).debounce(250).collect { save(it) } }
  }

  val isActive: Boolean
    get() = _state.value != null

  fun startEmpty() {
    if (isActive) return
    _state.value = ActiveWorkout(name = defaultName(), startTime = System.currentTimeMillis())
  }

  fun startRoutine(routine: RoutineWithExercises) {
    if (isActive) return
    _state.value =
      ActiveWorkout(
        name = routine.routine.name,
        routineId = routine.routine.id,
        startTime = System.currentTimeMillis(),
        exercises = suggestions(RoutineRepository.toSession(routine, settings.current.defaultRestSeconds)),
      )
  }

  /** Starts a planned session whose exercises already carry their targets. */
  fun startPlanned(name: String, plan: PlanLink, exercises: List<SessionExercise>) {
    if (isActive) return
    _state.value = ActiveWorkout(name = name, startTime = System.currentTimeMillis(), exercises = exercises, plan = plan)
  }

  /** With smart targets on, pre-filled numbers become suggestions until the set is ticked. */
  private fun suggestions(exercises: List<SessionExercise>) = if (settings.current.smartTargets) TargetPlanner.asSuggestions(exercises) else exercises

  /** Starts a workout pre-filled with [exercises], e.g. "repeat this workout" from history. */
  fun startFrom(name: String, exercises: List<SessionExercise>) {
    if (isActive) return
    _state.value = ActiveWorkout(name = name, startTime = System.currentTimeMillis(), exercises = suggestions(exercises))
  }

  fun update(f: (ActiveWorkout) -> ActiveWorkout) {
    _state.update { it?.let(f) }
  }

  fun updateExercises(f: (List<SessionExercise>) -> List<SessionExercise>) = update { it.copy(exercises = f(it.exercises)) }

  /** Forget an exercise's targets so they are worked out again (after its rule or rep range changed). */
  fun retarget(exerciseKey: Long) = updateExercises { list -> list.map { if (it.key == exerciseKey) it.copy(targetsReady = false) else it } }

  /**
   * Ticks a set. Empty fields are filled from the set's target, else last session's set at the same
   * position (or the routine's rep-range floor); completing starts the rest timer unless mid-superset.
   */
  fun toggleComplete(exerciseKey: Long, setKey: Long): CompleteResult {
    val w = _state.value ?: return CompleteResult.OK
    val ex = w.exercises.firstOrNull { it.key == exerciseKey } ?: return CompleteResult.OK
    val index = ex.sets.indexOfFirst { it.key == setKey }
    val set = ex.sets.getOrNull(index) ?: return CompleteResult.OK
    if (set.completed) {
      updateExercises { it.updateSet(exerciseKey, setKey) { s -> s.copy(completed = false) } }
      return CompleteResult.OK
    }
    val prev = placeholderFor(ex, index)
    val t = ex.type
    val weight = set.weightKg ?: set.targetWeightKg.takeIf { t.usesWeight } ?: prev?.weightKg.takeIf { t.usesWeight }
    val withWeight = set.copy(weightKg = weight)
    val effectiveReps = TargetPlanner.effectiveTargetReps(withWeight, t, bodyweightKg).takeIf { t.usesReps }
    val filled =
      withWeight.copy(
        reps = set.reps ?: effectiveReps ?: prev?.reps.takeIf { t.usesReps } ?: set.repMin.takeIf { t.usesReps },
        durationSec = set.durationSec ?: prev?.durationSec.takeIf { t.usesDuration },
        distanceM = set.distanceM ?: prev?.distanceM.takeIf { t.usesDistance },
        completed = true,
      )
    if (!hasRequiredValues(t, filled)) return CompleteResult.MISSING_VALUES
    // A weight other than the target re-matches the reps; that pair is what the set was really asked
    // for, so it is stored as the target and next time's hit-or-miss is judged against it.
    val aimed = if (effectiveReps != null && effectiveReps != set.targetReps) filled.copy(targetWeightKg = weight, targetReps = effectiveReps) else filled
    val done = aimed.copy(verdict = verdictFor(w, ex, index, aimed))
    updateExercises { it.updateSet(exerciseKey, setKey) { done } }
    if (ex.restSeconds > 0 && !w.exercises.restDeferred(exerciseKey)) restTimer.start(ex.restSeconds, ex.name)
    return CompleteResult.OK
  }

  /**
   * Improved / held / regressed against the same working set last time. None for warm-ups and drop sets,
   * nor in a deload or recovery session, which are meant to be lighter.
   */
  private fun verdictFor(w: ActiveWorkout, ex: SessionExercise, index: Int, set: SessionSet): Verdict? {
    if (!set.type.isWorking || !TargetPlanner.supports(ex.type) || w.plan?.deload == true || ex.recovery) return null
    val workingIndex = ex.sets.take(index).count { it.type.isWorking }
    val previous = _contexts.value[ex.exerciseId]?.previousWorking?.getOrNull(workingIndex) ?: return null
    val bw = bodyweightKg
    return Strength.verdict(TargetPlanner.logged(set), previous) { s -> TargetPlanner.totalLoad(ex.type, s.weightKg, bw) }
  }

  /** Last session's values for the set at [index] — what the PREVIOUS column shows. */
  fun placeholderFor(ex: SessionExercise, index: Int): SetValues? = SessionOps.previousFor(ex.sets, _contexts.value[ex.exerciseId]?.previous.orEmpty(), index)

  /** setKey -> records that set beats, considering only completed sets in order. */
  fun prs(ex: SessionExercise): Map<Long, Set<PrKind>> {
    val bests = _contexts.value[ex.exerciseId]?.bests ?: return emptyMap()
    val done = ex.sets.filter { it.completed }
    return done.map { it.key }.zip(Lifting.prsInSession(bests, ex.type, done.map { it.values() })).toMap()
  }

  /**
   * Saves the workout and ends it. Runs to the end even if the screen that asked goes away, and once
   * the workout is saved it is always cleared: a failure in the plan or routine follow-up must not
   * leave it active, to be saved again on the next try.
   */
  suspend fun finish(name: String, notes: String, updateRoutine: Boolean): Long =
    withContext(NonCancellable) {
      val w = _state.value ?: error("No workout in progress")
      // Verdicts are taken when a set is ticked; count edits made after that (weight, reps, RIR).
      val exercises = w.exercises.map { ex -> ex.copy(sets = ex.sets.mapIndexed { i, s -> if (s.completed) s.copy(verdict = verdictFor(w, ex, i, s)) else s }) }
      val final = w.copy(name = name.ifBlank { defaultName() }, notes = notes, exercises = exercises)
      val id = workouts.save(final, System.currentTimeMillis())
      followUp("plan update") { if (final.plan != null) onPlanSessionSaved?.invoke(final, id) }
      val routineId = w.routineId
      if (updateRoutine && routineId != null) {
        followUp("routine update") { routines.get(routineId)?.let { r -> routines.save(r.routine.id, r.routine.name, r.routine.folderId, r.routine.notes, routineTemplate(final, r)) } }
      }
      clear()
      id
    }

  private suspend fun followUp(what: String, block: suspend () -> Unit) {
    try {
      block()
    } catch (e: Exception) {
      Log.w(TAG, "Workout saved, but its $what failed", e)
    }
  }

  /**
   * [r] rewritten from today's workout: ticked sets with what was lifted (keeping the routine's own rep
   * scheme), and the routine's own sets for exercises left undone today.
   */
  private fun routineTemplate(w: ActiveWorkout, r: RoutineWithExercises): List<SessionExercise> {
    val original = RoutineRepository.toSession(r, settings.current.defaultRestSeconds).toMutableList()
    return w.exercises.map { ex ->
      val match = original.firstOrNull { it.exerciseId == ex.exerciseId }?.also { original.remove(it) }
      val done = ex.sets.filter { it.completed }
      val sets =
        when {
          done.isEmpty() -> match?.sets ?: ex.sets.map { it.copy(weightKg = it.weightKg ?: it.targetWeightKg, reps = it.reps ?: it.targetReps) }
          match == null -> done
          else -> {
            val scheme = match.sets.filter { it.type.isWorking }
            var k = 0
            done.map { s -> if (!s.type.isWorking) s else (scheme.getOrNull(k++) ?: scheme.lastOrNull()).let { spec -> s.copy(repMin = spec?.repMin, repMax = spec?.repMax) } }
          }
        }
      ex.copy(sets = sets.map { it.copy(completed = false, rpe = null, rir = null, verdict = null, targetWeightKg = null, targetReps = null, targetRir = null) })
    }
  }

  fun discard() = clear()

  private fun clear() {
    _state.value = null
    _contexts.value = emptyMap()
    restTimer.skip()
    file.delete() // don't wait for the debounced writer: a crash now must not resurrect the workout
  }

  private suspend fun loadContexts(w: ActiveWorkout) {
    val missing = w.exercises.map { it.exerciseId to it.type }.distinctBy { it.first }.filter { it.first !in _contexts.value }
    for ((id, type) in missing) {
      val sessions = workouts.exerciseHistory(id).groupBy { it.set.workoutId }.values // newest first
      val values = sessions.map { rows -> rows.sortedBy { it.set.position }.map { r -> SetValues(r.set.type, r.set.weightKg, r.set.reps, r.set.durationSec, r.set.distanceM) } }
      val history =
        sessions.map { rows ->
          HistorySession(
            rows.first().startTime,
            rows.sortedBy { it.set.position }.map { r -> LoggedSet(r.set.type, r.set.weightKg, r.set.reps, r.set.rir, r.set.rpe, r.set.targetReps, r.set.targetRir) },
            light = rows.any { it.set.light == true },
          )
        }
      val ctx = ExerciseContext(previous = values.firstOrNull().orEmpty(), bests = Lifting.personalBests(type, values), history = history)
      _contexts.update { it + (id to ctx) }
    }
  }

  /**
   * Works out targets for exercises whose history has loaded and that don't have them yet (new, replaced
   * or re-ruled ones). In a planned session they follow the week's effort like the rest; a deload here
   * starts from the last hard session, as the week-1 reference is only looked up when the session is prepared.
   */
  private fun applyTargets() {
    val ctx = _contexts.value
    val w = _state.value ?: return
    if (w.exercises.none { !it.targetsReady && it.exerciseId in ctx }) return
    val s = settings.current
    val now = System.currentTimeMillis()
    val plan = w.plan
    val mode =
      when {
        plan?.deload == true -> TargetMode.DELOAD
        plan != null -> TargetMode.PLAN
        else -> TargetMode.ROUTINE
      }
    _state.update { cur ->
      cur?.copy(
        exercises =
          cur.exercises.mapIndexed { i, ex ->
            val c = ctx[ex.exerciseId]
            if (ex.targetsReady || c == null) {
              ex
            } else {
              val weekRir = plan?.let { if (it.deload) Periodization.DELOAD_RIR else Periodization.safeRir(it.rir, TargetPlanner.traits(ex)) }
              val targeted = TargetPlanner.apply(ex, c.history, bodyweightKg, s, now, mode, weekRir)
              if (!s.smartWarmups) {
                targeted
              } else {
                val muscle = TargetPlanner.traits(ex).muscle
                val first = muscle == null || cur.exercises.take(i).none { TargetPlanner.traits(it).muscle == muscle }
                TargetPlanner.withWarmups(targeted, first, s)
              }
            }
          }
      )
    }
  }

  private fun save(w: ActiveWorkout?) {
    try {
      if (w == null) file.delete() else file.writeText(json.encodeToString(ActiveWorkout.serializer(), w))
    } catch (_: Exception) {
      // Persistence is best-effort; the in-memory workout is the source of truth.
    }
  }

  private fun load(): ActiveWorkout? =
    try {
      if (file.exists()) json.decodeFromString(ActiveWorkout.serializer(), file.readText()) else null
    } catch (_: Exception) {
      null
    }

  companion object {
    private const val TAG = "ActiveWorkout"

    fun defaultName(now: LocalTime = LocalTime.now()): String =
      when (now.hour) {
        in 5..11 -> "Morning Workout"
        in 12..16 -> "Afternoon Workout"
        in 17..21 -> "Evening Workout"
        else -> "Night Workout"
      }

    fun hasRequiredValues(type: ExerciseType, s: SessionSet): Boolean =
      when (type) {
        ExerciseType.WEIGHT_REPS -> s.weightKg != null && s.reps != null
        ExerciseType.REPS_ONLY,
        ExerciseType.WEIGHTED_BODYWEIGHT,
        ExerciseType.ASSISTED_BODYWEIGHT -> s.reps != null
        ExerciseType.DURATION -> s.durationSec != null
        ExerciseType.WEIGHT_DURATION -> s.durationSec != null
        ExerciseType.DISTANCE_DURATION -> s.distanceM != null || s.durationSec != null
        ExerciseType.WEIGHT_DISTANCE -> s.distanceM != null
      }

    /** Totals for the finish screen and the live header; warm-ups follow the user's setting. */
    fun volumeKg(w: ActiveWorkout, includeWarmups: Boolean): Double =
      w.exercises.sumOf { ex ->
        ex.sets.filter { it.completed && (includeWarmups || it.type != SetType.WARMUP) }.sumOf { Lifting.setVolume(ex.type, it.values()) }
      }

    fun completedSets(w: ActiveWorkout, includeWarmups: Boolean): Int =
      w.exercises.sumOf { ex -> ex.sets.count { it.completed && (includeWarmups || it.type != SetType.WARMUP) } }
  }
}
