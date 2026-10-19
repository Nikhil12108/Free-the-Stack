package com.ironlog.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.ironlog.app.data.db.IronLogDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a database exactly as version 1 of the app created it (from the exported 1.json), fills it
 * with routines like the user's, then opens it with the current app code. Room must run the 1→2
 * migration and accept the result, and nothing may be lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val name = "ironlog.db"

  @After
  fun tearDown() {
    context.deleteDatabase(name)
  }

  private fun createVersion1() {
    val schema = Json.parseToJsonElement(File("schemas/com.ironlog.app.data.db.IronLogDatabase/1.json").readText()).jsonObject["database"]!!.jsonObject
    val file = context.getDatabasePath(name)
    file.parentFile?.mkdirs()
    val db = SQLiteDatabase.openOrCreateDatabase(file, null)
    for (e in schema["entities"]!!.jsonArray) {
      val entity = e.jsonObject
      val table = entity["tableName"]!!.jsonPrimitive.content
      db.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
      (entity["indices"]?.jsonArray ?: emptyList()).forEach { i -> db.execSQL((i as JsonObject)["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
    }
    schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
    db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '${schema["identityHash"]!!.jsonPrimitive.content}')")
    // A custom exercise, a folder with a routine using a rep range, and one logged workout with RPE.
    db.execSQL("INSERT INTO exercise (id, name, type, equipment, primaryMuscle, secondaryMuscles, isCustom) VALUES (1, 'Bench Press (Barbell)', 'WEIGHT_REPS', 'BARBELL', 'CHEST', 'TRICEPS', 1)")
    db.execSQL("INSERT INTO routine_folder (id, name, position) VALUES (1, 'Imported', 0)")
    db.execSQL("INSERT INTO routine (id, name, folderId, notes, position, createdAt, updatedAt) VALUES (1, 'Push 1', 1, '', 0, 0, 0)")
    db.execSQL("INSERT INTO routine_exercise (id, routineId, exerciseId, position, supersetGroup, restSeconds, notes) VALUES (1, 1, 1, 0, NULL, 120, '')")
    for (i in 0 until 3) db.execSQL("INSERT INTO routine_set (routineExerciseId, position, type, weightKg, reps, repMin, repMax, durationSec, distanceM) VALUES (1, $i, 'NORMAL', NULL, NULL, 8, 10, NULL, NULL)")
    db.execSQL("INSERT INTO workout (id, name, notes, routineId, startTime, endTime) VALUES (1, 'Push 1', '', 1, 1000, 2000)")
    db.execSQL("INSERT INTO workout_exercise (id, workoutId, exerciseId, position, supersetGroup, notes) VALUES (1, 1, 1, 0, NULL, '')")
    db.execSQL("INSERT INTO workout_set (workoutExerciseId, workoutId, exerciseId, position, type, weightKg, reps, durationSec, distanceM, rpe) VALUES (1, 1, 1, 0, 'NORMAL', 80.0, 9, NULL, NULL, 8.0)")
    db.version = 1
    db.close()
  }

  @Test
  fun version1DatabaseMigratesWithEverythingKept() = runBlocking {
    createVersion1()
    val db = IronLogDatabase.create(context)
    try {
      val routines = RoutineRepository(db).routines.first()
      assertEquals(1, routines.size)
      val r = routines.single()
      assertEquals("Push 1", r.routine.name)
      assertEquals(1L, r.routine.folderId)
      assertEquals(listOf(8, 8, 8), r.exercises.single().sets.map { it.repMin })
      assertNull(r.exercises.single().entry.progressionRule)
      val history = WorkoutRepository(db).historySessions(1)
      assertEquals(1, history.size)
      val set = history.single().sets.single()
      assertEquals(80.0, set.weightKg!!, 1e-9)
      assertEquals(8.0, set.rpe!!, 1e-9)
      assertNull(set.rir)
      // The new tables work too.
      assertEquals(0, db.plans().getSessions(1).size)
    } finally {
      db.close()
    }
  }
}
