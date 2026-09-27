"""Candidate slot generation, scoring and greedy placement.

The algorithm is deliberately deterministic and explainable (spec 47/80):

  1. Order activities by how constrained they are.
  2. For each required occurrence, enumerate legal candidate slots.
  3. Score each candidate against soft preferences.
  4. Take the best-scoring slot, consume the time, repeat.
  5. If nothing is legal, relax *soft preferences* one tier at a time and
     record why the relaxation was needed. Hard constraints - excluded
     days, deadlines, minimum spacing, existing occupancy - are never
     relaxed; the engine reports a shortfall rather than quietly breaking
     something the user asked for.

No randomness, no LLM. Running it twice on the same input produces the
same schedule, which is what makes the explanations trustworthy.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date

from .availability import AvailabilityMap
from .models import (
    Activity,
    Flexibility,
    Relationship,
    ScheduledItem,
    TIME_OF_DAY_WINDOWS,
    TimeOfDay,
    UserPreferences,
    Weekday,
)
from .relationships import RelationshipIndex
from .spacing import ideal_stride, min_gap_to, respects_min_spacing
from .time_utils import Interval, clamp

GRID_MINUTES = 5
#: Start times are sampled every half hour inside each free interval.
CANDIDATE_STEP_MINUTES = 30
MAX_STARTS_PER_INTERVAL = 36

# Relaxation tiers, applied in order when an occurrence cannot be placed.
TIER_STRICT = 0
TIER_DROP_TIME_OF_DAY = 1
TIER_DROP_PREFERRED_DAYS = 2
TIER_ALLOW_RULE_BREAK = 3
MAX_TIER = TIER_ALLOW_RULE_BREAK

# Note: minimum spacing is deliberately NOT in the relaxation ladder. If the
# user said "at least a day apart", quietly breaking that would be a silent
# change to their intent. The engine reports a shortfall instead.


@dataclass
class Candidate:
    day: date
    start: int
    end: int
    score: float = 0.0
    tier: int = TIER_STRICT
    reasons: list[str] = field(default_factory=list)
    broken_rules: list[tuple[Relationship, str]] = field(default_factory=list)

    @property
    def sort_key(self) -> tuple:
        # Deterministic tie-breaking: best score, then earliest day, then
        # earliest start, then lowest relaxation tier.
        return (-self.score, self.day, self.start, self.tier)


def _align_up(value: int) -> int:
    remainder = value % GRID_MINUTES
    return value if remainder == 0 else value + (GRID_MINUTES - remainder)


def _align_down(value: int) -> int:
    return value - (value % GRID_MINUTES)


def order_activities(activities: list[Activity], horizon_days: int) -> list[Activity]:
    """Most-constrained-first ordering.

    Fixed commitments claim their ground before anything else, then high
    priority work, then activities with the fewest legal days (classic
    minimum-remaining-values heuristic from constraint satisfaction).
    """
    def feasible_days(activity: Activity) -> int:
        if activity.preferred_days:
            return len(set(activity.preferred_days) - set(activity.excluded_days))
        return max(1, horizon_days - len(set(activity.excluded_days)))

    return sorted(
        activities,
        key=lambda a: (
            0 if a.flexibility is Flexibility.FIXED else 1,
            -int(a.priority),
            feasible_days(a),
            -a.occurrences,
            a.name,
            a.id,
        ),
    )


def _day_windows(activity: Activity, tier: int, prefs: UserPreferences) -> list[Interval]:
    """The parts of a day this activity is allowed to occupy."""
    start = activity.earliest_minute if activity.earliest_minute is not None else prefs.day_start_minute
    end = activity.latest_minute if activity.latest_minute is not None else prefs.day_end_minute
    base: Interval = (start, end)

    if tier >= TIER_DROP_TIME_OF_DAY or activity.preferred_time_of_day is TimeOfDay.ANY:
        return [base]

    band = TIME_OF_DAY_WINDOWS[activity.preferred_time_of_day]
    clipped = clamp(band, base)
    return [clipped] if clipped else [base]


def _candidate_starts(interval: Interval, duration: int, preferred_start: int | None) -> list[int]:
    """Sample legal start times across a free interval.

    Sampling on a half-hour grid (rather than only the interval edges)
    matters: on an otherwise empty day the edges are 6am and 10pm, so an
    edges-only generator could never propose a 6pm slot at all.
    """
    start, end = interval
    latest = end - duration
    if latest < start:
        return []

    options: set[int] = {_align_up(start)} if _align_up(start) <= latest else set()
    options.add(_align_down(latest))

    cursor = _align_up(start)
    steps = 0
    while cursor <= latest and steps < MAX_STARTS_PER_INTERVAL:
        options.add(cursor)
        cursor += CANDIDATE_STEP_MINUTES
        steps += 1

    if preferred_start is not None and start <= preferred_start <= latest:
        options.add(preferred_start)

    return sorted(option for option in options if start <= option <= latest)


def build_candidates(
    activity: Activity,
    tier: int,
    availability: AvailabilityMap,
    placed_same: list[ScheduledItem],
    placed_all: list[ScheduledItem],
    relationships: RelationshipIndex,
    prefs: UserPreferences,
    strategy: str,
) -> list[Candidate]:
    candidates: list[Candidate] = []
    chosen_days = [i.day for i in placed_same]
    horizon = len(availability.days)
    stride = ideal_stride(activity.occurrences, horizon)
    per_day_counts: dict[date, int] = {}
    for item in placed_same:
        per_day_counts[item.day] = per_day_counts.get(item.day, 0) + 1

    for index, day in enumerate(availability.days):
        weekday = Weekday(day.weekday())

        if weekday in activity.excluded_days:
            continue
        if activity.deadline and day > activity.deadline:
            continue
        if per_day_counts.get(day, 0) >= activity.max_per_day:
            continue
        if activity.preferred_days and tier < TIER_DROP_PREFERRED_DAYS:
            if weekday not in activity.preferred_days:
                continue
        if not respects_min_spacing(activity, chosen_days, day):
            continue

        broken = relationships.evaluate(activity.id, day, placed_all)
        if broken and tier < TIER_ALLOW_RULE_BREAK:
            continue

        for window in _day_windows(activity, tier, prefs):
            for free in availability.free_intervals(day):
                usable = clamp(free, window)
                if not usable:
                    continue
                for start in _candidate_starts(
                    usable, activity.duration_minutes, activity.preferred_start_minute
                ):
                    candidate = Candidate(
                        day=day,
                        start=start,
                        end=start + activity.duration_minutes,
                        tier=tier,
                        broken_rules=broken,
                    )
                    _score(
                        candidate,
                        activity=activity,
                        index=index,
                        horizon=horizon,
                        availability=availability,
                        chosen_days=chosen_days,
                        stride=stride,
                        prefs=prefs,
                        strategy=strategy,
                    )
                    candidates.append(candidate)

    candidates.sort(key=lambda c: c.sort_key)
    return candidates


def _score(
    candidate: Candidate,
    *,
    activity: Activity,
    index: int,
    horizon: int,
    availability: AvailabilityMap,
    chosen_days: list[date],
    stride: float,
    prefs: UserPreferences,
    strategy: str,
) -> None:
    score = 100.0
    reasons: list[str] = []
    weekday = Weekday(candidate.day.weekday())

    # -- day preference ---------------------------------------------------
    if activity.preferred_days:
        if weekday in activity.preferred_days:
            score += 25
            reasons.append("PREFERRED_DAY")
        else:
            score -= 25
            reasons.append("RELAXED_PREFERRED_DAY")

    # -- time-of-day preference ------------------------------------------
    if activity.preferred_time_of_day is not TimeOfDay.ANY:
        band_start, band_end = TIME_OF_DAY_WINDOWS[activity.preferred_time_of_day]
        if band_start <= candidate.start < band_end:
            score += 20
            reasons.append(f"TIME_OF_DAY_MATCH:{activity.preferred_time_of_day.value}")
        else:
            score -= 20
            reasons.append("RELAXED_TIME_OF_DAY")

    # -- exact time preference -------------------------------------------
    if activity.preferred_start_minute is not None:
        drift = abs(candidate.start - activity.preferred_start_minute)
        score += max(0.0, 15.0 * (1 - drift / 180.0))
        if drift == 0:
            reasons.append("EXACT_PREFERRED_TIME")
        elif drift <= 60:
            reasons.append(f"NEAR_PREFERRED_TIME:{drift}")

    # -- spacing / distribution ------------------------------------------
    gap = min_gap_to(chosen_days, candidate.day)
    if gap is not None and stride > 0:
        if strategy == "CLUSTER":
            score += max(0.0, 15.0 * (1 - gap / max(stride, 1.0)))
        else:
            closeness = 1 - min(1.0, abs(gap - stride) / max(stride, 1.0))
            score += 22.0 * closeness
            if gap >= max(1, int(stride)):
                reasons.append(f"SPACED_FROM_PREVIOUS:{gap}")
    if activity.min_spacing_days and gap is not None and gap >= activity.min_spacing_days:
        reasons.append(f"MIN_SPACING_SATISFIED:{activity.min_spacing_days}")

    # -- day load: prefer emptier days -----------------------------------
    busy = availability.busy_minutes(candidate.day)
    items = availability.item_count(candidate.day)
    score -= 0.035 * busy
    score -= 3.0 * items
    if busy == 0:
        reasons.append("EMPTY_DAY")
    elif busy >= prefs.heavy_day_threshold_minutes:
        reasons.append("BUSY_DAY")

    # -- civilised hours ---------------------------------------------------
    # An activity with no stated time preference should not land at 6am just
    # because that minute happened to be free first.
    if candidate.start < prefs.core_start_minute:
        score -= 28.0
        reasons.append("OUTSIDE_CORE_HOURS")
    elif candidate.end > prefs.core_end_minute:
        score -= 14.0
        reasons.append("LATE_FINISH")

    # -- deadlines: earlier is safer --------------------------------------
    if activity.deadline:
        score += 12.0 * (1 - index / max(1, horizon))
        reasons.append("BEFORE_DEADLINE")

    # -- strategy shaping -------------------------------------------------
    if strategy == "EARLY_WEEK":
        score += 10.0 * (1 - index / max(1, horizon))
    elif strategy == "WEEKEND_FIRST" and weekday in (Weekday.SATURDAY, Weekday.SUNDAY):
        score += 18.0
        reasons.append("WEEKEND_PREFERRED")

    # -- penalties --------------------------------------------------------
    score -= 60.0 * len(candidate.broken_rules)
    score -= 8.0 * candidate.tier

    candidate.score = round(score, 3)
    candidate.reasons = reasons


def place(
    activities: list[Activity],
    availability: AvailabilityMap,
    relationships: RelationshipIndex,
    prefs: UserPreferences,
    strategy: str = "SPREAD",
) -> tuple[list[ScheduledItem], list[tuple[Activity, int]], list[tuple[Activity, Candidate]]]:
    """Place every activity.

    Returns ``(items, shortfalls, relaxed)`` where ``shortfalls`` lists
    activities that could not reach their requested frequency and
    ``relaxed`` lists placements that needed a preference relaxed.
    """
    items: list[ScheduledItem] = []
    shortfalls: list[tuple[Activity, int]] = []
    relaxed: list[tuple[Activity, Candidate]] = []

    for activity in order_activities(activities, len(availability.days)):
        placed_same: list[ScheduledItem] = []

        for _ in range(activity.occurrences):
            best: Candidate | None = None
            max_tier = TIER_STRICT if activity.flexibility is Flexibility.FIXED else MAX_TIER

            for tier in range(TIER_STRICT, max_tier + 1):
                candidates = build_candidates(
                    activity,
                    tier,
                    availability,
                    placed_same,
                    items,
                    relationships,
                    prefs,
                    strategy,
                )
                if candidates:
                    best = candidates[0]
                    break

            if best is None:
                break

            if best.tier > TIER_STRICT or best.broken_rules:
                relaxed.append((activity, best))

            item = ScheduledItem(
                activity_id=activity.id,
                activity_name=activity.name,
                day=best.day,
                start_minute=best.start,
                end_minute=best.end,
                reasons=tuple(best.reasons),
            )
            items.append(item)
            placed_same.append(item)
            availability.occupy(best.day, best.start, best.end)

        missing = activity.occurrences - len(placed_same)
        if missing > 0:
            shortfalls.append((activity, missing))

    items.sort(key=lambda i: (i.day, i.start_minute, i.activity_name))
    return items, shortfalls, relaxed
