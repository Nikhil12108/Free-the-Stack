package com.ironlog.app.data

import androidx.room.withTransaction
import com.ironlog.app.data.db.BodyMeasurementEntity
import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.data.db.IronLogDatabase
import com.ironlog.app.data.db.RoutineEntity
import com.ironlog.app.data.db.RoutineExerciseEntity
import com.ironlog.app.data.db.RoutineFolderEntity
import com.ironlog.app.data.db.RoutineSetEntity
import com.ironlog.app.data.db.RoutineWithExercises
import com.ironlog.app.data.db.SetHistoryRow
import com.ironlog.app.data.db.WorkoutEntity
import com.ironlog.app.data.db.WorkoutExerciseEntity
import com.ironlog.app.data.db.WorkoutSetEntity
import com.ironlog.app.data.db.WorkoutWithExercises
import com.ironlog.app.progression.ExerciseHistoryFacts
import com.ironlog.app.progression.HistorySession
import com.ironlog.app.progression.LoggedSet
import com.ironlog.app.workout.ActiveWorkout
import com.ironlog.app.workout.SessionExercise
import com.ironlog.app.workout.SessionSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ExerciseRepository(private val db: IronLogDatabase) {
  private val dao = db.exercises()

  val exercises: Flow<List<ExerciseEntity>> = dao.observeAll()

  /** exerciseId -> number of workouts it appears in. */
  val usage: Flow<Map<Long, Int>> = dao.observeUsage().map { rows -> rows.associate { it.exerciseId to it.times } }

  fun observe(id: Long): Flow<ExerciseEntity?> = dao.observe(id)

  suspend fun get(id: Long): ExerciseEntity? = dao.get(id)

  suspend fun create(exercise: ExerciseEntity): Long = dao.insert(exercise.copy(id = 0, isCustom = true))

  suspend fun update(exercise: ExerciseEntity) = dao.update(exercise)

  suspend fun delete(exercise: ExerciseEntity) = dao.delete(exercise)

  /** Familiarity and the latest joint-pain rating per exercise, for exercise suggestions. */
  suspend fun facts(workouts: WorkoutRepository): Map<Long, ExerciseHistoryFacts> {
    val used = dao.observeUsage().first().associate { it.exerciseId to it.times }
    val pain = workouts.latestJointPain()
    return (used.keys + pain.keys).associateWith { ExerciseHistoryFacts(used[it] ?: 0, pain[it]) }
  }

  suspend fun seedIfEmpty() {
    if (dao.count() == 0) dao.insertAll(SeedExercises.all)
  }
}

class RoutineRepository(private val db: IronLogDatabase) {
  private val dao = db.routines()

  val folders: Flow<List<RoutineFolderEntity>> = dao.observeFolders()
  val routines: Flow<List<RoutineWithExercises>> = dao.observeRoutines()

  suspend fun get(id: Long): RoutineWithExercises? = dao.getRoutine(id)

  /** Inserts or fully rewrites a routine. Returns its id. */
  suspend fun save(id: Long?, name: String, folderId: Long?, notes: String, exercises: List<SessionExercise>): Long =
    db.withTransaction {
      val now = System.currentTimeMillis()
      val routineId =
        if (id == null) {
          dao.insertRoutine(RoutineEntity(name = name, folderId = folderId, notes = notes, position = dao.nextRoutinePosition(), createdAt = now, updatedAt = now))
        } else {
          val existing = dao.getRoutine(id)?.routine ?: error("Routine $id not found")
          dao.updateRoutine(existing.copy(name = name, folderId = folderId, notes = notes, updatedAt = now))
          dao.clearRoutineExercises(id)
          id
        }
      exercises.forEachIndexed { i, ex ->
        val entryId =
          dao.insertRoutineExercise(
            RoutineExerciseEntity(
              routineId = routineId,
              exerciseId = ex.exerciseId,
              position = i,
              supersetGroup = ex.supersetGroup,
              restSeconds = ex.restSeconds,
              notes = ex.notes,
              progressionRule = ex.rule,
              targetRir = ex.targetRir,
            )
          )
        dao.insertRoutineSets(
          ex.sets.mapIndexed { j, s ->
            RoutineSetEntity(
              routineExerciseId = entryId,
              position = j,
              type = s.type,
              weightKg = s.weightKg,
              reps = s.reps,
              repMin = s.repMin,
              repMax = s.repMax,
              durationSec = s.durationSec,
              distanceM = s.distanceM,
            )
          }
        )
      }
      routineId
    }

  suspend fun duplicate(id: Long): Long? {
    val r = dao.getRoutine(id) ?: return null
    return save(null, "${r.routine.name} (copy)", r.routine.folderId, r.routine.notes, toSession(r, defaultRest = 0))
  }

  suspend fun delete(id: Long) = dao.deleteRoutine(id)

  suspend fun move(routineId: Long, folderId: Long?) = dao.moveRoutine(routineId, folderId)

  suspend fun createFolder(name: String): Long =
    db.withTransaction {
      // New folders go to the top, like the rest of the list expects.
      dao.updateFolders(dao.getFolders().map { it.copy(position = it.position + 1) })
      dao.insertFolder(RoutineFolderEntity(name = name, position = 0))
    }

  suspend fun renameFolder(folder: RoutineFolderEntity, name: String) = dao.updateFolders(listOf(folder.copy(name = name)))

  suspend fun reorderFolders(ordered: List<RoutineFolderEntity>) = dao.updateFolders(ordered.mapIndexed { i, f -> f.copy(position = i) })

  suspend fun deleteFolder(id: Long) = dao.deleteFolder(id)

  companion object {
    fun toSession(r: RoutineWithExercises, defaultRest: Int): List<SessionExercise> =
      r.orderedExercises.map { e ->
        SessionExercise.from(e.exercise, e.entry.restSeconds ?: defaultRest)
          .copy(
            supersetGroup = e.entry.supersetGroup,
            notes = e.entry.notes,
            rule = e.entry.progressionRule,
            targetRir = e.entry.targetRir,
            sets =
              e.sets
                .sortedBy { it.position }
                .map { s -> SessionSet(type = s.type, weightKg = s.weightKg, reps = s.reps, repMin = s.repMin, repMax = s.repMax, durationSec = s.durationSec, distanceM = s.distanceM) },
          )
      }
  }
}

class WorkoutRepository(private val db: IronLogDatabase) {
  private val dao = db.workouts()

  val workouts: Flow<List<WorkoutWithExercises>> = dao.observeWorkouts()
  val allSets: Flow<List<SetHistoryRow>> = dao.observeAllSets()

  fun observe(id: Long): Flow<WorkoutWithExercises?> = dao.observeWorkout(id)

  fun observeExerciseHistory(exerciseId: Long): Flow<List<SetHistoryRow>> = dao.observeExerciseHistory(exerciseId)

  suspend fun exerciseHistory(exerciseId: Long): List<SetHistoryRow> = dao.getExerciseHistory(exerciseId)

  /** Past sessions of one exercise for the progression engine, newest first. */
  suspend fun historySessions(exerciseId: Long): List<HistorySession> = toHistory(dao.getExerciseHistory(exerciseId))

  suspend fun get(id: Long): WorkoutWithExercises? = dao.getWorkout(id)

  /** exerciseId -> most recent joint-pain rating. */
  suspend fun latestJointPain(): Map<Long, Int> = dao.jointPainHistory().distinctBy { it.exerciseId }.associate { it.exerciseId to it.jointPain }

  /** Saves the completed sets of [workout]. Exercises without a completed set are dropped. */
  suspend fun save(workout: ActiveWorkout, endTime: Long): Long =
    db.withTransaction {
      val workoutId =
        dao.insertWorkout(
          WorkoutEntity(name = workout.name, notes = workout.notes, routineId = workout.routineId, startTime = workout.startTime, endTime = endTime, planSessionId = workout.plan?.sessionId)
        )
      workout.exercises
        .filter { ex -> ex.sets.any { it.completed } }
        .forEachIndexed { i, ex ->
          val light = (workout.plan?.deload == true || ex.recovery).takeIf { it }
          val entryId =
            dao.insertWorkoutExercise(
              WorkoutExerciseEntity(workoutId = workoutId, exerciseId = ex.exerciseId, position = i, supersetGroup = ex.supersetGroup, notes = ex.notes, jointPain = ex.jointPain)
            )
          dao.insertWorkoutSets(
            ex.sets
              .filter { it.completed }
              .mapIndexed { j, s ->
                WorkoutSetEntity(
                  workoutExerciseId = entryId,
                  workoutId = workoutId,
                  exerciseId = ex.exerciseId,
                  position = j,
                  type = s.type,
                  weightKg = s.weightKg,
                  reps = s.reps,
                  durationSec = s.durationSec,
                  distanceM = s.distanceM,
                  rpe = s.rpe,
                  rir = s.rir,
                  targetWeightKg = s.targetWeightKg,
                  targetReps = s.targetReps,
                  targetRir = s.targetRir,
                  light = light,
                )
              }
          )
        }
      workoutId
    }

  suspend fun delete(id: Long) = dao.deleteWorkout(id)

  companion object {
    /** Groups set rows (newest workout first) into per-workout sessions for the progression engine. */
    fun toHistory(rows: List<SetHistoryRow>): List<HistorySession> =
      rows.groupBy { it.set.workoutId }.values.map { group ->
        HistorySession(
          group.first().startTime,
          group.sortedBy { it.set.position }.map { r -> LoggedSet(r.set.type, r.set.weightKg, r.set.reps, r.set.rir, r.set.rpe, r.set.targetReps, r.set.targetRir) },
          light = group.any { it.set.light == true },
        )
      }

    /** A logged workout as an editable template (all sets un-ticked). */
    fun toSession(w: WorkoutWithExercises, defaultRest: Int): List<SessionExercise> =
      w.orderedExercises.map { e ->
        SessionExercise.from(e.exercise, defaultRest)
          .copy(
            supersetGroup = e.entry.supersetGroup,
            notes = e.entry.notes,
            sets = e.orderedSets.map { s -> SessionSet(type = s.type, weightKg = s.weightKg, reps = s.reps, durationSec = s.durationSec, distanceM = s.distanceM) },
          )
      }
  }
}

class MeasurementRepository(db: IronLogDatabase) {
  private val dao = db.measurements()

  val measurements: Flow<List<BodyMeasurementEntity>> = dao.observeAll()

  /** Most recent logged bodyweight, used for bodyweight exercises. */
  suspend fun latestBodyweightKg(): Double? = dao.latestWeightKg()

  suspend fun save(entry: BodyMeasurementEntity) = dao.upsert(entry)

  suspend fun delete(date: String) = dao.delete(date)
}
