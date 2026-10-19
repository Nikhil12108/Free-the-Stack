package com.ironlog.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
  @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<ExerciseEntity>>

  @Query("SELECT * FROM exercise WHERE id = :id") fun observe(id: Long): Flow<ExerciseEntity?>

  @Query("SELECT * FROM exercise WHERE id = :id") suspend fun get(id: Long): ExerciseEntity?

  @Query("SELECT COUNT(*) FROM exercise") suspend fun count(): Int

  @Insert suspend fun insert(exercise: ExerciseEntity): Long

  @Insert suspend fun insertAll(exercises: List<ExerciseEntity>)

  @Update suspend fun update(exercise: ExerciseEntity)

  @Delete suspend fun delete(exercise: ExerciseEntity)

  /** How often each exercise was performed — used to sort the picker by familiarity. */
  @Query("SELECT exerciseId AS exerciseId, COUNT(DISTINCT workoutId) AS times FROM workout_set GROUP BY exerciseId")
  fun observeUsage(): Flow<List<ExerciseUsage>>
}

data class ExerciseUsage(val exerciseId: Long, val times: Int)

data class ExercisePain(val exerciseId: Long, val jointPain: Int)

@Dao
interface RoutineDao {
  @Query("SELECT * FROM routine_folder ORDER BY position") fun observeFolders(): Flow<List<RoutineFolderEntity>>

  @Query("SELECT * FROM routine_folder ORDER BY position") suspend fun getFolders(): List<RoutineFolderEntity>

  @Transaction @Query("SELECT * FROM routine ORDER BY position, id") fun observeRoutines(): Flow<List<RoutineWithExercises>>

  @Transaction @Query("SELECT * FROM routine WHERE id = :id") suspend fun getRoutine(id: Long): RoutineWithExercises?

  @Insert suspend fun insertFolder(folder: RoutineFolderEntity): Long

  @Update suspend fun updateFolders(folders: List<RoutineFolderEntity>)

  @Query("DELETE FROM routine_folder WHERE id = :id") suspend fun deleteFolder(id: Long)

  @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM routine") suspend fun nextRoutinePosition(): Int

  @Insert suspend fun insertRoutine(routine: RoutineEntity): Long

  @Update suspend fun updateRoutine(routine: RoutineEntity)

  @Query("DELETE FROM routine WHERE id = :id") suspend fun deleteRoutine(id: Long)

  @Query("UPDATE routine SET folderId = :folderId WHERE id = :routineId") suspend fun moveRoutine(routineId: Long, folderId: Long?)

  @Query("DELETE FROM routine_exercise WHERE routineId = :routineId") suspend fun clearRoutineExercises(routineId: Long)

  @Insert suspend fun insertRoutineExercise(entry: RoutineExerciseEntity): Long

  @Insert suspend fun insertRoutineSets(sets: List<RoutineSetEntity>)
}

@Dao
interface WorkoutDao {
  @Transaction @Query("SELECT * FROM workout ORDER BY startTime DESC") fun observeWorkouts(): Flow<List<WorkoutWithExercises>>

  @Transaction @Query("SELECT * FROM workout WHERE id = :id") fun observeWorkout(id: Long): Flow<WorkoutWithExercises?>

  @Query("SELECT * FROM workout ORDER BY startTime DESC") fun observeWorkoutHeaders(): Flow<List<WorkoutEntity>>

  @Transaction @Query("SELECT * FROM workout WHERE id = :id") suspend fun getWorkout(id: Long): WorkoutWithExercises?

  /** Joint-pain ratings, newest workout first. */
  @Query(
    "SELECT we.exerciseId AS exerciseId, we.jointPain AS jointPain FROM workout_exercise we JOIN workout w ON w.id = we.workoutId " +
      "WHERE we.jointPain IS NOT NULL ORDER BY w.startTime DESC"
  )
  suspend fun jointPainHistory(): List<ExercisePain>

  @Insert suspend fun insertWorkout(workout: WorkoutEntity): Long

  @Insert suspend fun insertWorkoutExercise(entry: WorkoutExerciseEntity): Long

  @Insert suspend fun insertWorkoutSets(sets: List<WorkoutSetEntity>)

  @Query("DELETE FROM workout WHERE id = :id") suspend fun deleteWorkout(id: Long)

  @Query(
    """
    SELECT s.*, w.name AS workoutName, w.startTime AS startTime, w.endTime AS endTime
    FROM workout_set s JOIN workout w ON w.id = s.workoutId
    WHERE s.exerciseId = :exerciseId
    ORDER BY w.startTime DESC, s.position
    """
  )
  fun observeExerciseHistory(exerciseId: Long): Flow<List<SetHistoryRow>>

  @Query(
    """
    SELECT s.*, w.name AS workoutName, w.startTime AS startTime, w.endTime AS endTime
    FROM workout_set s JOIN workout w ON w.id = s.workoutId
    WHERE s.exerciseId = :exerciseId
    ORDER BY w.startTime DESC, s.position
    """
  )
  suspend fun getExerciseHistory(exerciseId: Long): List<SetHistoryRow>

  /** Every completed set with its exercise and workout — the raw material for profile stats. */
  @Query(
    """
    SELECT s.*, w.name AS workoutName, w.startTime AS startTime, w.endTime AS endTime
    FROM workout_set s JOIN workout w ON w.id = s.workoutId
    ORDER BY w.startTime DESC, s.position
    """
  )
  fun observeAllSets(): Flow<List<SetHistoryRow>>
}

@Dao
interface MeasurementDao {
  @Query("SELECT * FROM body_measurement ORDER BY date DESC") fun observeAll(): Flow<List<BodyMeasurementEntity>>

  @Upsert suspend fun upsert(entry: BodyMeasurementEntity)

  @Query("DELETE FROM body_measurement WHERE date = :date") suspend fun delete(date: String)

  @Query("SELECT weightKg FROM body_measurement WHERE weightKg IS NOT NULL ORDER BY date DESC LIMIT 1") suspend fun latestWeightKg(): Double?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(entries: List<BodyMeasurementEntity>)
}
