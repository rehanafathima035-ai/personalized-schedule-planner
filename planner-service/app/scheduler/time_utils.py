"""Minute-of-day interval arithmetic.

The engine works in integer minutes from local midnight. Converting to and
from wall-clock/timezone-aware values happens at the API boundary, which
keeps every scheduling decision trivially testable.
"""

from __future__ import annotations

from datetime import date, timedelta

Interval = tuple[int, int]  # [start, end)


def overlaps(a: Interval, b: Interval) -> bool:
    """True when two half-open intervals share at least one minute."""
    return a[0] < b[1] and b[0] < a[1]


def overlap_minutes(a: Interval, b: Interval) -> int:
    return max(0, min(a[1], b[1]) - max(a[0], b[0]))


def subtract(window: Interval, blocks: list[Interval]) -> list[Interval]:
    """Remove every block from ``window``, returning the remaining gaps."""
    free = [window]
    for block in sorted(blocks):
        next_free: list[Interval] = []
        for start, end in free:
            if not overlaps((start, end), block):
                next_free.append((start, end))
                continue
            if start < block[0]:
                next_free.append((start, block[0]))
            if block[1] < end:
                next_free.append((block[1], end))
        free = next_free
    return [(s, e) for s, e in free if e > s]


def clamp(interval: Interval, window: Interval) -> Interval | None:
    start = max(interval[0], window[0])
    end = min(interval[1], window[1])
    return (start, end) if end > start else None


def days_between(start: date, count: int) -> list[date]:
    return [start + timedelta(days=i) for i in range(count)]


def format_minute(minute: int) -> str:
    """Render a minute-of-day as 12-hour wall clock, e.g. 1020 -> '5:00 PM'."""
    minute %= 24 * 60
    hour24, minutes = divmod(minute, 60)
    suffix = "AM" if hour24 < 12 else "PM"
    hour12 = hour24 % 12 or 12
    return f"{hour12}:{minutes:02d} {suffix}"


def format_range(start: int, end: int) -> str:
    return f"{format_minute(start)}\u2013{format_minute(end)}"
