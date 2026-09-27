# The scheduling engine

This is the part of the product that decides *where things go*. It is a
deterministic constraint solver, not a language model. Everything here is
reproducible: the same input always produces the same week.

Location: `planner-service/app/scheduler/`. Standard library only, no
FastAPI import anywhere in the package, so it can be unit-tested and reused
by any caller.

## Why it is not an LLM call

An LLM asked to "make me a schedule" will produce something plausible that
double-books Tuesday. Worse, it cannot tell you *why* it chose a slot, so
the explanation you show the user is a second guess rather than a record of
the decision. Splitting the work is what makes both halves trustworthy:

```
"exercise twice a week"          natural language
        ↓
  parser / LLM                   understands intent
        ↓
  structured constraints         occurrences=2, duration=45, spacing=1
        ↓
  deterministic engine           decides Tue 5pm and Sat 5pm
        ↓
  reason codes                   SPACED_FROM_PREVIOUS:4, EMPTY_DAY
        ↓
  explanation                    rendered from the codes, not invented
```

## The problem shape

This is a variant of **resource-constrained scheduling**. The single
resource is the user's waking time. Each activity needs *k* non-overlapping
intervals placed within a horizon, subject to hard constraints, while
maximising satisfaction of soft preferences.

The general problem is NP-hard. We do not solve it optimally. We use a
greedy construction with a most-constrained-first ordering and a scoring
function, which is fast, good enough for a week of a human life, and — the
part that matters — explainable line by line.

## The algorithm

### 1. Build the availability map (`availability.py`)

For each day in the horizon, start with the waking window and subtract
every fixed commitment and every already-approved item, padded by the
user's minimum gap. What remains is a list of free intervals.

The map is **mutable**. As the generator places work, it consumes
capacity, so no two activities can ever be handed the same minute. This is
what makes double-booking structurally impossible rather than something we
check for afterwards.

### 2. Order the activities (`generator.order_activities`)

```
(is it FIXED, priority descending, fewest legal days, most occurrences, name)
```

Fewest-legal-days first is the **minimum remaining values** heuristic from
constraint satisfaction: place the activity with the least freedom while
there is still room for it, because placing the flexible ones first paints
the constrained ones into a corner. The final `name` term exists only to
make ties deterministic.

### 3. Generate candidate slots (`generator.build_candidates`)

For each day that passes the hard filters, intersect the free intervals
with the activity's allowed window, and sample start times on a half-hour
grid.

> The grid matters. An earlier version sampled only the *edges* of each
> free interval, which meant that on an empty day the only candidates were
> 6am and 10pm — a 6pm slot was never considered at all.

### 4. Score each candidate (`generator._score`)

Starting at 100:

| Signal | Effect |
|---|---|
| Lands on a preferred day | +25 |
| Inside the preferred time-of-day band | +20 |
| Near an exact requested time | up to +15, decaying over 3 hours |
| Gap from the previous session near the ideal stride | up to +22 |
| Minutes already booked that day | −0.035 per minute |
| Items already on that day | −3 each |
| Starts before the user's core hours | −28 |
| Ends after core hours | −14 |
| Breaks a user-declared relationship | −60 each |
| Each relaxation tier used | −8 |

The day-load penalties are what produce distribution. Nothing tells the
engine to "spread things out"; spreading falls out of emptier days scoring
better.

The *ideal stride* is `horizon_days / occurrences`, so three sessions in
seven days aim for roughly every 2.3 days. This is why "3× weekly" becomes
Mon/Wed/Fri rather than Mon/Tue/Wed.

### 5. Relax, in order, only what is soft

If nothing is legal, widen the search one tier at a time:

```
tier 0  everything honoured
tier 1  drop the time-of-day preference
tier 2  drop the preferred-days restriction
tier 3  allow a user-declared relationship to be broken (and report it)
```

**Minimum spacing is deliberately not in this ladder.** If the user said
"at least a day apart", quietly breaking it would be a silent change to
their intent. The engine reports a shortfall instead. Excluded days,
deadlines and existing occupancy are hard for the same reason.

An activity marked `FIXED` gets no relaxation at all.

### 6. Detect conflicts (`conflicts.py`)

Two categories, never conflated:

**Hard** — literally impossible. A requested slot overlapping a fixed
commitment; a frequency that cannot be placed; a dependency scheduled
before its prerequisite; a deadline that cannot be met.

**Soft** — possible, but worth saying. A day that is unusually dense; a
relaxation that was needed; combined frequencies claiming most of the week.

The soft message for the second case is phrased as *"these are not
incompatible, but how they are distributed will matter"*. The engine never
declares two of a person's goals incompatible — it does not get to decide
what a good life looks like.

It also never *infers* a relationship. If the user has not said that gym
and restaurants should avoid each other, nothing enforces it.

### 7. Explain (`explainer.py`)

The generator attaches reason codes to every placement
(`SPACED_FROM_PREVIOUS:3`, `TIME_OF_DAY_MATCH:EVENING`, `EMPTY_DAY`,
`RELAXED_PREFERRED_DAY`). The explainer renders those codes into
sentences. If no code was recorded, no sentence is produced — so the
explanation cannot drift from what actually happened.

### 8. Rank alternatives (`density.score_proposal`)

Three strategies run (`SPREAD`, `EARLY_WEEK`, `WEEKEND_FIRST`) and are
scored on: hard conflicts (−40 each), soft conflicts (−6), unplaced
sessions (−25), the standard deviation of placed minutes per day, and a
bonus for using distinct days.

Balance is measured over *placed* work only. Including fixed commitments
would make every proposal look equally lumpy and would hide real
clustering. Identical proposals are de-duplicated before being offered.

## Complexity

Let `d` = days, `a` = activities, `k` = occurrences, `s` = candidate starts
per day (bounded at 36).

Candidate generation per occurrence is `O(d · s)`. Total placement is
`O(Σk · d · s)`. For a week with ten activities this is a few thousand
scored candidates — under a tenth of a second, measured.

## Entry points

| Function | Purpose |
|---|---|
| `propose(request)` | Best schedule plus ranked alternatives |
| `what_if(request, activity)` | Impact of a hypothetical, changes nothing |
| `rebalance(request, missed)` | New homes for missed work, around approved items |

All three are pure functions. None writes to a database. The engine cannot
persist anything even in principle — approval happens in Spring Boot.

## Known limitations

- Greedy with no backtracking across activities. An early placement can
  block a later one where a different arrangement existed. Reported as a
  shortfall rather than silently dropped.
- `max_spacing_days` is validated and reported but not yet used as a
  placement driver.
- Horizon-bounded. Monthly and yearly recurrence is expanded by the caller
  into a horizon before the engine sees it.
- Single-resource. It models time, not energy, travel between locations, or
  equipment.
