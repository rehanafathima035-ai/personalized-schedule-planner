"""Turns structured scheduling results into sentences.

Every line here is derived from a reason code the generator actually
emitted, or from a measured day load. Nothing is invented, and no language
model is involved (spec section 48). If the engine did not record a
reason, no sentence is produced.
"""

from __future__ import annotations

from .density import HEAVY, LIGHT, label
from .models import (
    Activity,
    DayLoad,
    FixedCommitment,
    Flexibility,
    ScheduledItem,
    UserPreferences,
)
from .time_utils import format_minute


def explain(
    items: list[ScheduledItem],
    activities: list[Activity],
    fixed_commitments: list[FixedCommitment],
    day_loads: list[DayLoad],
    prefs: UserPreferences,
) -> list[str]:
    lines: list[str] = []
    by_id = {a.id: a for a in activities}

    for activity in activities:
        own = sorted(
            (i for i in items if i.activity_id == activity.id),
            key=lambda i: (i.day, i.start_minute),
        )
        if not own:
            continue

        days = ", ".join(i.day.strftime("%a") for i in own)
        lines.append(
            f"{activity.name}: {len(own)} session(s) on {days} "
            f"at {format_minute(own[0].start_minute)}."
        )

        reasons = {r for item in own for r in item.reasons}

        if activity.min_spacing_days and any(
            r.startswith("MIN_SPACING_SATISFIED") for r in reasons
        ):
            lines.append(
                f"  \u2022 {activity.name} sessions are kept at least "
                f"{activity.min_spacing_days} day(s) apart, as you asked."
            )
        elif len(own) > 1 and any(r.startswith("SPACED_FROM_PREVIOUS") for r in reasons):
            lines.append(
                f"  \u2022 {activity.name} was spread across the week rather than "
                f"clustered together."
            )

        if any(r.startswith("TIME_OF_DAY_MATCH") for r in reasons):
            lines.append(
                f"  \u2022 Placed in your preferred "
                f"{activity.preferred_time_of_day.value.lower()} window."
            )
        if "EXACT_PREFERRED_TIME" in reasons:
            lines.append(
                f"  \u2022 Kept at exactly "
                f"{format_minute(activity.preferred_start_minute or 0)}."
            )
        if "RELAXED_TIME_OF_DAY" in reasons:
            lines.append(
                f"  \u2022 Your preferred time of day was not free every time, so one "
                f"or more sessions moved outside it."
            )
        if "RELAXED_PREFERRED_DAY" in reasons:
            lines.append(
                f"  \u2022 Your preferred days could not hold every session, so one "
                f"or more moved to another day."
            )
        if "EMPTY_DAY" in reasons:
            lines.append(f"  \u2022 Some sessions were put on otherwise empty days.")

        for dependency_id in activity.depends_on:
            if dependency_id in by_id:
                lines.append(
                    f"  \u2022 Scheduled after {by_id[dependency_id].name}, which it "
                    f"depends on."
                )

    fixed_names = sorted({c.name for c in fixed_commitments})
    if fixed_names:
        lines.append(
            f"Your fixed commitments ({', '.join(fixed_names)}) were not changed."
        )

    for activity in activities:
        if activity.flexibility is Flexibility.FIXED and any(
            i.activity_id == activity.id for i in items
        ):
            lines.append(f"{activity.name} is marked fixed and was left where it is.")

    heavy = [load for load in day_loads if label(load, prefs) == HEAVY]
    light = [load for load in day_loads if label(load, prefs) == LIGHT]
    if heavy:
        names = ", ".join(load.day.strftime("%a") for load in heavy)
        lines.append(f"{names} already carried the most, so less was added there.")
    if light:
        names = ", ".join(load.day.strftime("%a") for load in light)
        lines.append(f"{names} stayed light and still has room.")

    return lines
