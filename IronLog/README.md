# IronLog

**A free, private workout tracker for Android, with smart progression and training plans.**

IronLog logs your training and tells you what to lift next: a weight, reps and effort for every set, worked out
from your own history and the equipment you actually have. Training plans run your routines in blocks of 4–8
weeks and adjust how many sets each muscle gets from week to week.

Your data stays on your phone. IronLog has no account, no ads and no tracking, and it doesn't even ask for
internet access. You can export everything to CSV at any time.

IronLog is the first app in [Free the Stack](../README.md).

## Features

**Logging**
- Workouts with weight × reps, bodyweight (added or assisted), time and distance exercises.
- Warm-up, failure and drop sets; supersets; notes; reps in reserve (RIR) per set.
- Rest timer with a notification and an alarm.
- Plate and warm-up calculators, and personal records marked as you lift.
- Routines in folders. A library of 100+ exercises, plus your own.

**Smart targets**
- Each set gets a target weight, reps and RIR from your history.
- Targets snap to your plates, dumbbells and machine steps.
- Each exercise follows a rule: smart, double progression, linear, or off.
- After each set, ↑ = ↓ shows whether you got stronger than last time.
- A one-line reason explains every target.

**Training plans**
- 4–8 week blocks, made from your routines or laid out for you by "Build it for me" from your days, priorities
  and equipment.
- Sets get closer to failure each week, and the last week is a deload.
- Volume per muscle adapts to your performance and to optional quick questions (how hard it felt, pump,
  soreness, joints).
- Recovery sessions when a muscle keeps going backwards.

**Everything else**
- History and exercise charts.
- Bodyweight, body fat and body measurements.
- Weekly stats and muscle split.
- Dark and light themes.
- CSV export.

How the progression engine decides is explained in [docs/PROGRESSION.md](docs/PROGRESSION.md).

## Status

IronLog is early but working:
- Tested on a Galaxy S25 Ultra (Android 17) and covered by 75 automated tests, including UI flow tests.
- There is no store listing yet; build it yourself (below).
- Requires Android 8.0 or newer.

Not built yet: importing from other apps, home-screen widgets, wearables, and strength-style programs (e.g. top
set and back-offs). There is no cloud sync, by design.

## Build and install

You need:
- JDK 21 (Gradle can download it for you through its toolchain resolver);
- the Android SDK with platform 36. Set `ANDROID_HOME`, or create `local.properties` containing
  `sdk.dir=/path/to/sdk`.

```sh
git clone https://github.com/Nikhil12108/Free-the-Stack.git
cd Free-the-Stack/IronLog
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the tests with `./gradlew testDebugUnitTest`. They run on the JVM, using Robolectric for the database and UI
tests; no device is needed.

## Project layout

```
app/src/main/java/com/ironlog/app/
  data/          Room database (entities, DAOs, migrations), repositories, settings, CSV export
  domain/        units, one-rep max and records, stats
  progression/   the target and plan engines (plain Kotlin, no Android)
  workout/       the workout in progress, rest timer, notifications
  ui/            Jetpack Compose screens
app/schemas/     exported database schemas; every change ships as an automatic migration
docs/            how the progression engine works
```

Built with Kotlin, Jetpack Compose (Material 3), Room, Navigation 3 and kotlinx.serialization.

## Contributing

Bug reports, ideas and pull requests are welcome. For anything large, open an issue first so we can agree on the
scope.

IronLog is an independent implementation: please don't contribute code, text, images or exercise data copied
from other apps, and respect the licences of anything you bring in.

## Licence

IronLog is released under the [MIT licence](LICENSE).

The bundled typefaces, [Inter](https://github.com/rsms/inter) and
[Playfair Display](https://github.com/clauseggers/Playfair-Display), are under the SIL Open Font License 1.1.
Their licence texts ship with the app in `app/src/main/assets/licenses/`.
