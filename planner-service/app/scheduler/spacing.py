"""Spacing rules between repeated occurrences of the same activity.

min_spacing_days = 1  ->  "exercise sessions must be at least a day apart",
                          meaning one clear day in between, so Mon+Tue is
                          rejected and Mon+Wed is allowed.
max_spacing_days = 2  ->  "don't skip Korean for more than two days"
"""

from __future__ import annotations

from datetime import date

from .models import Activity


def respects_min_spacing(activity: Activity, chosen: list[date], candidate: date) -> bool:
    """True when placing ``candidate`` keeps every pair far enough apart."""
    if not activity.min_spacing_days:
        return True
    required_gap = activity.min_spacing_days + 1
    return all(
        abs((candidate - existing).days) >= required_gap
        for existing in chosen
    )


def min_gap_to(chosen: list[date], candidate: date) -> int | None:
    """Distance in days to the nearest already-chosen occurrence."""
    if not chosen:
        return None
    return min(abs((candidate - existing).days) for existing in chosen)


def max_spacing_violations(activity: Activity, chosen: list[date]) -> list[tuple[date, date]]:
    """Consecutive pairs that drift further apart than ``max_spacing_days``."""
    if not activity.max_spacing_days or len(chosen) < 2:
        return []
    ordered = sorted(chosen)
    return [
        (a, b)
        for a, b in zip(ordered, ordered[1:])
        if (b - a).days > activity.max_spacing_days
    ]


def ideal_stride(occurrences: int, horizon_days: int) -> float:
    """Even spacing an activity would get if nothing else existed.

    Used as the target the SPREAD strategy scores against, which is what
    turns "3x per week" into Tue/Thu/Sat rather than Mon/Tue/Wed.
    """
    if occurrences <= 1:
        return float(horizon_days)
    return horizon_days / occurrences
