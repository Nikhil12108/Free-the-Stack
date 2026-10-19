# How IronLog's progression works

IronLog suggests a weight, a rep count and an effort level for every working set. Inside a training plan it
also decides how many sets each muscle gets from week to week. All of it runs on the phone, using only the
workouts you have logged.

This page explains the model. The code is in `app/src/main/java/com/ironlog/app/progression/`, and the tests in
`app/src/test/` pin down the behaviour described here.

## Effort: reps in reserve (RIR)

RIR is how many more good reps you could have done when you stopped the set; 0 means failure. Logging RIR is
optional. When a set has none, IronLog uses, in order:
1. RPE, if you logged it (RIR ≈ 10 − RPE).
2. 0 for a set marked as a failure set.
3. The RIR the set was aiming for.
4. 2.

Warm-ups and drop sets are never treated as working sets: they get no targets and are not used for
progression.

## The strength model

Each logged set gives an estimate of your strength from the load and the reps you had to failure:

```
strength = load × (1 + (reps + RIR) / 30)
```

The same formula, turned around, gives the reps you should manage at a load and RIR, or the load for a rep
count. On top of that IronLog expects a little progress per week of training:
- beginner 4 %;
- intermediate 2.5 %;
- advanced 1 %.

The gain is counted for at most two weeks since the last session.

For bodyweight exercises the load is your bodyweight plus any added weight, or minus any assistance, using your
latest logged bodyweight. Without a bodyweight, those exercises progress by reps.

## Loads you can actually lift

Targets snap to your equipment, which you set up in Settings:
- the bar weight and the plates you own (in pairs);
- the step size of your dumbbells, weight stacks and cables.

Kettlebells use standard sizes: 4 kg steps, or 9 lb in pounds.

A barbell moves in steps of twice your smallest plate. A step up is only taken if it is not too big a jump for
that kind of equipment:

| Equipment | Largest jump |
|---|---|
| Barbell | 5 % |
| Machines, cables, plate-loaded machines | 7.5 % |
| Dumbbells, added weight, assistance | 10 % |
| Kettlebells | 15 % |

These limits are scaled by experience: ×1.5 for beginners, ×0.75 for advanced lifters. When the next weight up
is too big a jump, IronLog adds a rep instead, up to four reps past the top of the range.

## Progression rules

Each exercise follows one rule. You can change it from the exercise's **Progression…** menu.

**Smart** (the default). In a routine:
- Below the top of the rep range, add a rep.
- At the top, take the next weight if it is a sensible jump, with the reps the model predicts at that weight.
- Clearly easier than intended (logged RIR 3 or more above the target): jump straight to the load that lands
  mid-range, at most +15 %.
- Reps fell below the range: drop to the load that should give the bottom of it.
- Missed the target: try the same weight again. Missed it twice in a row: one step lighter.

**Double progression.** Add a rep each time. Once every set reaches the top of the range, add weight and start
again at the bottom.

**Linear.** Add weight whenever every set hit its reps.

**Off.** Repeat last time's numbers.

A set counts as hitting its target when you got within one rep of it.

If you choose a different weight than the target, the suggested reps are re-matched to keep the same effort, for
changes up to 20 %. The new weight and reps then become that set's target, so next time is judged against what
was really asked of you.

After each set, an arrow next to last time's numbers shows whether you got stronger:
- **↑** when the estimated strength rose by at least 0.5 %;
- **↓** when it fell by 2 % or more;
- **=** otherwise.

## Training plans

A plan repeats your training days for 4 to 8 weeks. Sets get closer to failure each week, and the last week is a
deload. Reps in reserve by week:

| Plan length | Hard weeks (RIR) | Last week |
|---|---|---|
| 4 weeks | 2 · 1 · 0 | deload |
| 5 weeks | 3 · 2 · 1 · 0 | deload |
| 6 weeks | 3 · 2 · 2 · 1 · 0 | deload |
| 7 weeks | 3 · 3 · 2 · 2 · 1 · 0 | deload |
| 8 weeks | 3 · 3 · 2 · 2 · 1 · 1 · 0 | deload |

Barbell squats, lunges and presses stop one rep short of failure even in the 0-RIR week, because a failed rep
there can pin you.

Within a plan, targets keep your reps and add the heaviest load step the model says still allows them at the new
week's RIR. If no step fits, they add a rep; otherwise they hold.

**Deload week:**
- about half the sets (traps and forearms rest completely);
- the loads from week 1;
- about half the reps, with every set easy.

Deload and recovery sessions are marked as light. They are never used as the starting point for later targets
or for the ↑ = ↓ comparison, so the next block continues from your last real session.

## Sets per muscle, week to week

After you finish a planned session, IronLog sets next week's sets for that day, one muscle at a time. It uses
three things.

**How the sets went.** Each set's reps plus RIR is compared with its target reps plus target RIR, and with last
time. The result is one of: *exceeded*, *as planned*, *struggled*, or *regressed* (at least half the sets went
backwards).

**Your answers, if you choose to give them:**
- **Light:** how much work it was for the muscle, and joint pain per exercise.
- **Full:** also the pump, and how sore the muscle got after its last session.

**The muscle's priority:**
- **Emphasize** may climb toward the most volume you can recover from.
- **Grow** stays near the minimum that works.
- **Maintain** holds steady.

Roughly:
- **Add sets** when the work felt easy, soreness was gone early, or the muscle clearly outperformed: up to +2 for
  an emphasized muscle, +1 otherwise.
- **Hold** when the work was about right, you were at your limit, or the pump was great.
- **Cut a set** when the muscle was still sore or the work was too much.
- **No answers:** add one set per week, as long as performance keeps up.

Some limits always apply:
- Sets never go up after a struggled or regressed session.
- A muscle trained twice or more a week gains at most one set per session.
- Each muscle has a weekly ceiling and a per-session cap.

Two regressed sessions in a row trigger a **recovery session**: half the sets at lighter loads. Afterwards the
volume resumes halfway between the plan's starting point and where it was.

A muscle you did not train in a session keeps its sets, and so does a whole day you skip. Soreness reported later
is attached to the session it is about, and that session's decision is recalculated.

## The plan builder

"Build it for me" lays out a week from:
- how many days you can train;
- each muscle's priority, or skipping it;
- the equipment you have.

It works in three steps:
1. **Sessions per muscle.** It decides how often each muscle is trained from its priority and how quickly it
   recovers.
2. **Spreading the week.** It places those sessions across the days, keeping sessions for the same muscle apart,
   and orders each day from big muscles to small.
3. **Picking exercises.** For each slot it ranks your exercise library by:
   - fit for the position in the session;
   - your equipment and the exercise type;
   - variety of movement pattern and repeats within the week;
   - fatigue: at most one heavy squat- or deadlift-type lift per day;
   - familiarity, and recent joint pain.

Every pick can be swapped or chosen by hand.

## Warm-ups and rest

**Smart warm-ups** (optional) add warm-up sets before weighted exercises. The full ramp goes before the first
exercise for each muscle:
- compounds: 40 %, 60 % and 80 % of the working weight for 8, 5 and 3 reps;
- isolation: one set at 60 % for 8 reps;
- later compounds for the same muscle: one set at 70 % for 3 reps.

**Rest by movement** sets the default rest for exercises you add:

| Movement | Rest |
|---|---|
| Heavy whole-body lifts (squats, deadlifts) | 3:00 |
| Other lower-body compounds | 2:30 |
| Other compounds | 2:00 |
| Isolation | 1:30 |
| Core | 1:00 |
