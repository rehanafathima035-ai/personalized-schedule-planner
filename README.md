# Personalized Life Planner

A planning system that treats your commitments as one coordinated schedule
instead of nine independent trackers.

You say what you want to make room for. It looks at what you already do,
finds the gaps, tells you what it noticed, explains its reasoning, and
waits for you to approve before anything becomes real.

> Tell me the life you want to build. I'll help you turn it into a
> realistic schedule.

---

## Status

Built in phases. This is honest about what has been run and what has not.

| Component | State | Verified how |
|---|---|---|
| Scheduling engine | Working | 27 unit tests passing |
| Natural-language parser | Working | 22 unit tests passing |
| MySQL schema | Written | Structural check only — not run against MySQL |
| FastAPI HTTP layer | Written | Not executed (no FastAPI installed in the build environment) |
| Spring Boot backend | Written | **Not compiled** — no Maven available |
| React frontend | Written | **Not built** — no npm install available |
| Prayer tracker | Not started | — |
| Period tracker | Not started | — |
| Goals, tasks, calendar UI | Not started | — |

Nothing here is a fake button. Features that are not implemented are
absent, not stubbed.

## What works today

The core loop, end to end, at the engine level:

```
$ cd planner-service && python demo_acceptance.py
```

Given college Monday–Friday 9–4, exercise twice a week with a clear day
between sessions, and three restaurant outings:

```
Tue 22      Exercise                5:00 PM
Thu 24      Try new restaurant      5:00 PM
Sat 26      Try new restaurant      5:00 PM
            Exercise                6:40 PM
Sun 27      Try new restaurant      5:00 PM
```

with the reasoning it actually used:

```
Exercise: 2 session(s) on Tue, Sat at 5:00 PM.
  • Exercise sessions are kept at least 1 day(s) apart, as you asked.
  • Placed in your preferred evening window.
Try new restaurant was spread across the week rather than clustered together.
Your fixed commitments (College) were not changed.
```

## Design decisions worth knowing

**The engine is not a language model.** An LLM interprets *"exercise twice
a week"*; a deterministic constraint solver decides *where it goes*. Run it
twice, get the same week. See `docs/scheduling-engine.md`.

**Explanations are rendered from reason codes**, not generated prose. Each
placement records why it was chosen (`SPACED_FROM_PREVIOUS:4`,
`EMPTY_DAY`). If no reason was recorded, no sentence appears — the
explanation cannot drift from what happened.

**Hard and soft conflicts are different things.** A hard conflict is two
things in one minute. A soft conflict is worth mentioning. The engine never
tells you two of your own goals are incompatible.

**It never infers a relationship between your activities.** If you haven't
said gym and restaurants should avoid each other, nothing enforces it.

**Nothing is saved before you approve it.** The habit definition lives as
`draft_json` on the proposal, not as a row in `habit`, so rejecting leaves
no trace to clean up. There is deliberately no `POST /api/habits`.

**Minimum spacing is never relaxed.** If you said "at least a day apart"
and it doesn't fit, you get told it doesn't fit.

## Stack

| Layer | Technology |
|---|---|
| Frontend | React 18, JavaScript, Vite, plain CSS |
| API | Java 21, Spring Boot 3.3, Spring Security, JPA/Hibernate |
| Planning | Python 3.12, FastAPI, Pydantic |
| Database | MySQL 8 |

The scheduling engine itself imports nothing outside the Python standard
library, so it can be tested in milliseconds and reused anywhere.

## Layout

```
personalized-life-planner/
├── frontend/                 React
├── backend-spring/           Spring Boot: auth, persistence, approval
├── planner-service/
│   ├── app/scheduler/        the engine (stdlib only)
│   ├── app/services/         natural-language parser
│   ├── app/api/, schemas/    FastAPI boundary
│   └── tests/                49 tests
├── database/schema/          MySQL DDL
├── database/seed/            demo data, flagged is_demo
└── docs/
```

## Running it

### 1. Database

```bash
mysql -u root -p < database/schema/01_schema.sql
mysql -u root -p < database/seed/02_demo_data.sql   # optional
```

### 2. Environment

```bash
cp .env.example .env
openssl rand -base64 48        # paste into APP_JWT_SECRET
```

The app refuses to start with a missing or short JWT secret rather than
falling back to a default.

### 3. Planner service

```bash
cd planner-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

### 4. Spring Boot

```bash
cd backend-spring
set -a && source ../.env && set +a
./mvnw spring-boot:run
```

### 5. Frontend

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

Start order matters only in that Spring Boot returns a clear
`PLANNER_UNAVAILABLE` if the planner is down. It will not invent a
schedule.

## Tests

The engine tests need no dependencies at all:

```bash
cd planner-service
python -m unittest discover -s tests -v      # 49 tests
```

```bash
cd backend-spring
./mvnw test          # H2 in memory, no MySQL needed
```

Scheduler coverage includes: fixed commitments, hard time overlap,
minimum spacing, preferred days and times, excluded days, priority
contention, user-declared relationships, dependency ordering, deadlines,
capacity pressure, rebalancing around approved work, what-if isolation, and
a full seven-activity week.

## Documentation

- `docs/scheduling-engine.md` — the algorithm, heuristics, complexity, and
  its limitations
- `docs/architecture.md` — service boundaries and why there are two backends
- `docs/api.md` — endpoints and error contract

## Known limitations

- The scheduler is greedy with no cross-activity backtracking. An early
  placement can block a later one where another arrangement existed; this
  is reported as a shortfall, never silently dropped.
- `max_spacing_days` is validated and reported but does not yet drive
  placement.
- Monthly and yearly recurrence is expanded by the caller before the engine
  sees it.
- No external calendar sync. The schema reserves `external_source` for it.
- Prayer and period tracking are designed into the schema but not built.

## Screenshots

To be added once the frontend runs.
