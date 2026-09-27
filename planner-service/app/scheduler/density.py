"""Schedule density and balance metrics (spec sections 17 and 32)."""

from __future__ import annotations

from .availability import AvailabilityMap
from .models import DayLoad, Proposal, ScheduledItem, UserPreferences

LIGHT = "LIGHT"
MODERATE = "MODERATE"
HEAVY = "HEAVY"


def compute_day_loads(
    availability: AvailabilityMap, items: list[ScheduledItem]
) -> list[DayLoad]:
    """Build a per-day load report.

    ``availability`` already has every placement consumed, so busy minutes
    include both fixed commitments and newly scheduled work.
    """
    counts: dict = {}
    for item in items:
        counts[item.day] = counts.get(item.day, 0) + 1

    return [
        DayLoad(
            day=day,
            scheduled_minutes=availability.busy_minutes(day),
            item_count=availability.item_count(day),
            free_minutes=availability.free_minutes(day),
        )
        for day in availability.days
    ]


def label(load: DayLoad, prefs: UserPreferences) -> str:
    if load.scheduled_minutes >= prefs.heavy_day_threshold_minutes:
        return HEAVY
    if load.scheduled_minutes >= prefs.heavy_day_threshold_minutes / 2:
        return MODERATE
    return LIGHT


def balance_report(day_loads: list[DayLoad], prefs: UserPreferences) -> list[dict]:
    return [
        {
            "day": load.day.isoformat(),
            "weekday": load.day.strftime("%A"),
            "scheduled_minutes": load.scheduled_minutes,
            "free_minutes": load.free_minutes,
            "items": load.item_count,
            "density": round(load.density, 3),
            "label": label(load, prefs),
        }
        for load in day_loads
    ]


def score_proposal(proposal: Proposal, prefs: UserPreferences) -> float:
    """Rank competing proposals.

    Rewards: placing everything that was asked for.
    Punishes: hard conflicts, then soft conflicts, then uneven days.
    """
    score = 100.0
    score -= 40.0 * sum(1 for c in proposal.conflicts if c.severity.value == "HARD")
    score -= 6.0 * sum(1 for c in proposal.conflicts if c.severity.value == "SOFT")
    score -= 25.0 * sum(missing for _, missing in proposal.unplaced)

    # Balance is judged on the work the engine actually placed. Including
    # fixed commitments would make every proposal look equally lumpy and
    # would hide genuine clustering of the new activities.
    placed_per_day: dict = {load.day: 0 for load in proposal.day_loads}
    for item in proposal.items:
        placed_per_day[item.day] = placed_per_day.get(item.day, 0) + (
            item.end_minute - item.start_minute
        )

    loads = list(placed_per_day.values())
    if loads:
        mean = sum(loads) / len(loads)
        variance = sum((value - mean) ** 2 for value in loads) / len(loads)
        # Standard deviation in hours; a lumpy week loses points.
        score -= (variance ** 0.5) / 60.0 * 12.0

    # Reward using distinct days rather than stacking everything together.
    if proposal.items:
        distinct_days = len({i.day for i in proposal.items})
        score += 2.5 * distinct_days
    return round(score, 3)
