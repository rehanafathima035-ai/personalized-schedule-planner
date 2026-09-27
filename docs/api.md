# API reference

Base URL: `http://localhost:8080/api`

All routes except registration and login require
`Authorization: Bearer <token>`.

## Errors

Every failure returns the same shape. Stack traces are logged, never sent.

```json
{
  "timestamp": "2026-09-21T10:14:33Z",
  "error": "CONFLICT",
  "message": "You already have an active habit called \"Exercise\"."
}
```

| Code | Status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Request body failed validation |
| `AUTHENTICATION_FAILED` | 401 | Bad credentials or expired token |
| `NOT_FOUND` | 404 | No such record *for this user* |
| `CONFLICT` | 409 | Duplicate, or an already-decided proposal |
| `PLANNER_UNAVAILABLE` | 503 | Planner unreachable; nothing was changed |
| `INTERNAL_ERROR` | 500 | Unexpected failure |

`PLANNER_UNAVAILABLE` is deliberately distinct: it tells the user no
schedule was generated rather than showing an empty one.

## Authentication

| Method | Path | Body |
|---|---|---|
| POST | `/auth/register` | `{email, displayName, password}` — min 8 chars |
| POST | `/auth/login` | `{email, password}` |
| GET | `/auth/me` | — |

Returns `{token, userId, displayName, email}`. Passwords are BCrypt-hashed
and never returned.

## Habits

| Method | Path | Purpose |
|---|---|---|
| GET | `/habits` | Active habits for the signed-in user |
| GET | `/habits/{id}` | One habit |
| POST | `/habits/interpret` | `{text}` → structured reading. **Saves nothing** |
| POST | `/habits/preview` | `{draft, horizonStart, horizonDays}` → a pending proposal |

There is no `POST /habits`. Habits are created only by approving a
proposal.

### `POST /habits/preview` response

```json
{
  "proposalId": 12,
  "status": "PENDING_APPROVAL",
  "proposal": {
    "strategy": "SPREAD",
    "score": 93.876,
    "items": [
      {"activity_name": "Exercise", "day": "2026-09-22",
       "weekday": "Tuesday", "start_minute": 1020, "end_minute": 1065,
       "reason_codes": ["SPACED_FROM_PREVIOUS:4", "EMPTY_DAY"]}
    ],
    "conflicts": [
      {"type": "PARTIAL_FREQUENCY", "severity": "SOFT",
       "message": "... These are not incompatible, but how they are distributed will matter."}
    ],
    "explanations": ["Exercise: 2 session(s) on Tue, Sat at 5:00 PM."],
    "has_hard_conflicts": false
  },
  "alternatives": [],
  "balance": [],
  "message": "Nothing has been saved yet. Approve to add this to your schedule."
}
```

## Schedule and approval

| Method | Path | Purpose |
|---|---|---|
| GET | `/schedule/proposals/pending` | Proposals awaiting a decision |
| POST | `/schedule/proposals/{id}/approve` | Create the habit, commit the slots |
| POST | `/schedule/proposals/{id}/reject` | Discard; nothing was ever written |
| GET | `/schedule/today` | Today's approved items |
| GET | `/schedule?from=&to=` | Approved items in a date range |

Approving an already-decided proposal returns 409.

## Internal planner service

Not exposed publicly. Called only by Spring Boot, on port 8001.

| Method | Path | Purpose |
|---|---|---|
| POST | `/planner/interpret` | Free text → structured interpretation |
| POST | `/planner/propose` | Best schedule plus alternatives |
| POST | `/planner/conflicts` | Analysis only, for Schedule Health |
| POST | `/planner/what-if` | Simulate an addition |
| POST | `/planner/rebalance` | Rehome missed work |
| GET | `/health` | Liveness |

Times are integer minutes from local midnight (`1020` = 5:00 PM). Dates are
ISO `YYYY-MM-DD`.
