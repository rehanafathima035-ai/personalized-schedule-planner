# Architecture

## Services

```
                        React (Vite, port 5173)
                                  |
                          REST / JSON + JWT
                                  |
                    Spring Boot API (port 8080)
                                  |
                +-----------------+------------------+
                |                                    |
            MySQL 8                      Planner service (port 8001)
       source of truth                   FastAPI, stateless
                                                     |
                                     +---------------+---------------+
                                     |                               |
                              NL parser                   Scheduling engine
                         (deterministic, stdlib)        (deterministic, stdlib)
```

The browser talks only to Spring Boot. The planner is internal and never
exposed, which keeps one obvious rule enforceable: scheduling decisions are
made server-side and cannot be forged by a client.

## Who owns what

**Spring Boot** owns everything durable — accounts, habits, goals, tasks,
calendar events, completions, proposals and the approved schedule. It is
the only service with a database connection.

**FastAPI** owns interpretation and scheduling. It holds no state, has no
database, and is a pure function of its request body. Restarting it loses
nothing.

**React** renders and collects. It contains no scheduling logic at all, so
the same decisions will serve the future React Native client without being
reimplemented.

## Why two backends

Not for novelty. They do genuinely different work.

The scheduling engine is a constraint solver: nested loops, scoring,
interval arithmetic, and a test suite that needs to run in milliseconds.
Python expresses that compactly and the engine stays readable enough to
audit.

Everything around it — transactions, authorization, entity mapping,
migration-safe persistence — is exactly what Spring Boot and JPA are for.

The boundary is narrow and typed on both sides: five POST endpoints, plain
JSON, no shared database, no shared session. Either service can be
restarted independently.

## Request flow: creating a habit

```
 1  POST /api/habits/interpret     user text -> structured reading
 2  user edits the reading         still nothing saved
 3  POST /api/habits/preview       Spring gathers existing approved items,
                                   calls the planner, saves a PENDING
                                   proposal, returns it with reasoning
 4  user reviews conflicts, reasoning, alternatives
 5  POST /api/schedule/proposals/{id}/approve
                                   creates the habit from the stored draft
                                   and copies slots into scheduled_item
```

Step 5 is the only path from a generated schedule to a real one. There is
deliberately no `POST /api/habits`.

## How "nothing is saved before approval" is enforced

Proposals live in `schedule_proposal`, `schedule_proposal_item` and
`schedule_conflict`. The live plan lives in `scheduled_item`.

The habit definition itself is held as `draft_json` on the proposal rather
than as a row in `habit`, so a rejected proposal leaves no trace anywhere
in the real tables — rejection is a status change, not a cleanup.

## Data model notes

Recurrence is stored as a **rule** (`habit_schedule`), never as
pre-generated occurrences. "Read every day" is one row, not 365. Concrete
dates are derived when a range is requested; only exceptions —
completions, skips, reschedules — are persisted.

Every user-owned table carries `user_id`, and repository methods take it as
a parameter (`findByIdAndUserId`), so a guessed id returns nothing rather
than another person's data.

Times are stored as integer minutes from local midnight. Comparisons
become integer arithmetic, and timezone handling is confined to the
boundary where dates are resolved.

## Built for a mobile client later

- Stateless JWT auth: no server-side session to replicate.
- All scheduling server-side: a phone gets identical answers.
- Minute-integer times and ISO dates: no locale-dependent parsing.
- Completion records keyed by `(habit_id, occurrence_date)` so offline
  writes can be replayed idempotently when a device reconnects.

React Native is not built and is not started here.
