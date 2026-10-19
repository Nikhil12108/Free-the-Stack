package com.ironlog.app.progression

/** Rest between sets by movement: heavy lower-body lifts longest, single-joint work shortest. */
object Rest {
  fun seconds(traits: ExerciseTraits): Int =
    when {
      traits.region == Region.CORE -> 60
      traits.systemicCost >= 3 -> 180
      traits.isCompound && traits.region == Region.LOWER -> 150
      traits.isCompound -> 120
      else -> 90
    }
}
