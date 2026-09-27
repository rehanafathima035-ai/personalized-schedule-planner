"""Conflict detection.

Two categories, kept strictly apart (spec section 7):

  HARD  - the request is literally impossible as stated. Two things cannot
          occupy the same minute.
  SOFT  - the request is possible, but something about it is worth showing
          the user before they commit. Never phrased as "incompatible".
"""

from __future__ import annotations

from datetime import date

from .availability import AvailabilityMap
from .generator import Candidate
from .models import (
    Activity,
    Conflict,
    ConflictType,
    DayLoad,
    FixedCommitment,
    Flexibility,
    ScheduledItem,
    Severity,
    UserPreferences,
    Weekday,
)
from .time_utils import days_between, format_range, overlaps


def detect_requested_slot_conflicts(
    activities: list[Activity],
    fixed_commitments: list[FixedCommitment],
    horizon_start: date,
    horizon_days: int,
) -> list[Conflict]:
    """Find activities whose explicitly requested slot is already taken.

    This is the "College 5-6 PM vs Gym 5-6 PM" case. Whether it can be
    resolved automatically depends on flexibility, which the engine
    reports rather than acting on unilaterally.
    """
    conflicts: list[Conflict] = []
    horizon = days_between(horizon_start, horizon_days)

    for activity in activities:
        if activity.preferred_start_minute is None or not activity.preferred_days:
            continue
        requested = (
            activity.preferred_start_minute,
            activity.preferred_start_minute + activity.duration_minutes,
        )
        for day in horizon:
            if Weekday(day.weekday()) not in activity.preferred_days:
                continue
            for commitment in fixed_commitments:
                if commitment.day != day:
                    continue
                if not overlaps(requested, (commitment.start_minute, commitment.end_minute)):
                    continue

                movable = activity.flexibility is not Flexibility.FIXED
                conflicts.append(
                    Conflict(
                        type=ConflictType.HARD_TIME_OVERLAP,
                        severity=Severity.SOFT if movable else Severity.HARD,
                        message=(
                            f"{commitment.name} and {activity.name} overlap on "
                            f"{day.strftime('%A %d %b')} at "
                            f"{format_range(*requested)}."
                            + (
                                f" {commitment.name} is fixed and {activity.name} is "
                                f"{activity.flexibility.value.lower()}, so "
                                f"{activity.name} can be moved \u2014 with your approval."
                                if movable
                                else " Both are fixed, so this one needs your decision."
                            )
                        ),
                        activity_ids=(activity.id, commitment.id),
                        day=day,
                        details=(
                            ("requested", format_range(*requested)),
                            ("blocked_by", commitment.name),
                            ("resolvable_automatically", str(movable).lower()),
                        ),
                    )
                )
    return conflicts


def detect_capacity_pressure(
    activities: list[Activity],
    availability: AvailabilityMap,
    horizon_days: int,
) -> list[Conflict]:
    """Flag when demanded time approaches the time that actually exists."""
    demanded = sum(a.duration_minutes * a.occurrences for a in activities)
    available = availability.total_free_minutes()
    if available == 0:
        return []

    ratio = demanded / available
    if ratio < 0.7:
        return []

    severity = Severity.HARD if ratio > 1.0 else Severity.SOFT
    if severity is Severity.HARD:
        message = (
            f"These activities need about {demanded // 60}h in a week that has "
            f"roughly {available // 60}h free. Something has to give."
        )
    else:
        message = (
            f"These activities would fill about {round(ratio * 100)}% of your free "
            f"time. It fits, but there is little room left for anything unplanned."
        )
    return [
        Conflict(
            type=ConflictType.CAPACITY_PRESSURE,
            severity=severity,
            message=message,
            activity_ids=tuple(a.id for a in activities),
            details=(
                ("demanded_minutes", str(demanded)),
                ("available_minutes", str(available)),
            ),
        )
    ]


def detect_frequency_relationship(activities: list[Activity], horizon_days: int) -> list[Conflict]:
    """The "exercise 2x + restaurants 3x" case.

    These are not incompatible and must never be reported as such. What is
    worth saying is that together they claim most days of the week, so
    their distribution starts to matter.
    """
    recurring = [a for a in activities if a.occurrences >= 2]
    if len(recurring) < 2:
        return []

    # Flag once the combined sessions claim a clear majority of the days in
    # the horizon. Below that they simply coexist and there is nothing to
    # say. This is the "exercise 2x + restaurants 3x across 7 days" trigger.
    day_demand = sum(a.occurrences for a in recurring)
    threshold = -(-horizon_days * 3 // 5)  # ceil(60% of the horizon)
    if day_demand < threshold:
        return []

    names = ", ".join(f"{a.name} ({a.occurrences}\u00d7)" for a in recurring)
    return [
        Conflict(
            type=ConflictType.PARTIAL_FREQUENCY,
            severity=Severity.SOFT,
            message=(
                f"{names} together ask for {day_demand} sessions across "
                f"{horizon_days} days. These are not incompatible, but how they "
                f"are distributed will matter. Would you like them spread out?"
            ),
            activity_ids=tuple(a.id for a in recurring),
            details=(("sessions", str(day_demand)), ("days", str(horizon_days))),
        )
    ]


def conflicts_from_shortfalls(shortfalls: list[tuple[Activity, int]]) -> list[Conflict]:
    conflicts: list[Conflict] = []
    for activity, missing in shortfalls:
        placed = activity.occurrences - missing
        conflicts.append(
            Conflict(
                type=(
                    ConflictType.NO_AVAILABLE_SLOT
                    if placed == 0
                    else ConflictType.PARTIAL_FREQUENCY
                ),
                severity=Severity.HARD,
                message=(
                    f"Could only place {placed} of {activity.occurrences} "
                    f"{activity.name} session(s) \u2014 no free slot left that "
                    f"respects your constraints."
                ),
                activity_ids=(activity.id,),
                details=(("placed", str(placed)), ("requested", str(activity.occurrences))),
            )
        )
    return conflicts


def conflicts_from_relaxations(relaxed: list[tuple[Activity, Candidate]]) -> list[Conflict]:
    conflicts: list[Conflict] = []
    for activity, candidate in relaxed:
        for rule, reason in candidate.broken_rules:
            conflicts.append(
                Conflict(
                    type=ConflictType.RELATIONSHIP_VIOLATION,
                    severity=Severity.SOFT,
                    message=(
                        f"{activity.name} on {candidate.day.strftime('%A')} breaks your "
                        f"'{rule.type.value}' rule with the other activity ({reason})."
                    ),
                    activity_ids=(rule.activity_a, rule.activity_b),
                    day=candidate.day,
                )
            )
        if "RELAXED_PREFERRED_DAY" in candidate.reasons:
            conflicts.append(
                Conflict(
                    type=ConflictType.SPACING_VIOLATION,
                    severity=Severity.SOFT,
                    message=(
                        f"{activity.name} had to go outside your preferred days to fit."
                    ),
                    activity_ids=(activity.id,),
                    day=candidate.day,
                )
            )
        elif "RELAXED_TIME_OF_DAY" in candidate.reasons:
            conflicts.append(
                Conflict(
                    type=ConflictType.SPACING_VIOLATION,
                    severity=Severity.SOFT,
                    message=(
                        f"{activity.name} had to go outside your preferred time of day "
                        f"on {candidate.day.strftime('%A')}."
                    ),
                    activity_ids=(activity.id,),
                    day=candidate.day,
                )
            )
    return conflicts


def detect_dependency_issues(
    activities: list[Activity], items: list[ScheduledItem]
) -> list[Conflict]:
    """Prerequisites must land before the work that depends on them."""
    first_day: dict[str, date] = {}
    for item in items:
        current = first_day.get(item.activity_id)
        if current is None or item.day < current:
            first_day[item.activity_id] = item.day

    by_id = {a.id: a for a in activities}
    conflicts: list[Conflict] = []
    for activity in activities:
        own = first_day.get(activity.id)
        if own is None:
            continue
        for dependency_id in activity.depends_on:
            dep_day = first_day.get(dependency_id)
            if dep_day is None or dep_day <= own:
                continue
            dep_name = by_id[dependency_id].name if dependency_id in by_id else dependency_id
            conflicts.append(
                Conflict(
                    type=ConflictType.DEPENDENCY_ORDER,
                    severity=Severity.HARD,
                    message=(
                        f"{activity.name} is scheduled before its prerequisite "
                        f"{dep_name}."
                    ),
                    activity_ids=(activity.id, dependency_id),
                    day=own,
                )
            )
    return conflicts


def detect_deadline_risk(
    activities: list[Activity], items: list[ScheduledItem]
) -> list[Conflict]:
    conflicts: list[Conflict] = []
    for activity in activities:
        if not activity.deadline:
            continue
        own = [i for i in items if i.activity_id == activity.id]
        if not own:
            conflicts.append(
                Conflict(
                    type=ConflictType.DEADLINE_RISK,
                    severity=Severity.HARD,
                    message=(
                        f"{activity.name} is due {activity.deadline.isoformat()} but "
                        f"nothing could be scheduled before then."
                    ),
                    activity_ids=(activity.id,),
                )
            )
            continue
        latest = max(i.day for i in own)
        if latest > activity.deadline:
            conflicts.append(
                Conflict(
                    type=ConflictType.DEADLINE_RISK,
                    severity=Severity.HARD,
                    message=(
                        f"{activity.name} runs past its {activity.deadline.isoformat()} "
                        f"deadline."
                    ),
                    activity_ids=(activity.id,),
                    day=latest,
                )
            )
    return conflicts


def detect_overloaded_days(
    day_loads: list[DayLoad], prefs: UserPreferences
) -> list[Conflict]:
    conflicts: list[Conflict] = []
    for load in day_loads:
        too_long = load.scheduled_minutes >= prefs.heavy_day_threshold_minutes
        too_many = load.item_count >= prefs.heavy_day_threshold_items
        if not (too_long and too_many):
            continue
        hours = round(load.scheduled_minutes / 60, 1)
        conflicts.append(
            Conflict(
                type=ConflictType.OVERLOADED_DAY,
                severity=Severity.SOFT,
                message=(
                    f"{load.day.strftime('%A %d %b')} holds {load.item_count} "
                    f"activities totalling {hours}h. That day may be unusually dense."
                ),
                day=load.day,
                details=(
                    ("items", str(load.item_count)),
                    ("minutes", str(load.scheduled_minutes)),
                ),
            )
        )
    return conflicts
