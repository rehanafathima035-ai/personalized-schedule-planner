"""Availability analysis.

Builds, for every day in the planning horizon, the list of free intervals
left after fixed commitments and already-locked items are removed. The
generator then consumes capacity from this structure as it places work, so
two activities can never be handed the same minute.
"""

from __future__ import annotations

from datetime import date

from .models import FixedCommitment, ScheduledItem, UserPreferences
from .time_utils import Interval, days_between, subtract


class AvailabilityMap:
    def __init__(
        self,
        horizon_start: date,
        horizon_days: int,
        preferences: UserPreferences,
        fixed_commitments: list[FixedCommitment],
        locked_items: list[ScheduledItem] | None = None,
    ) -> None:
        self.preferences = preferences
        self.days: list[date] = days_between(horizon_start, horizon_days)
        self._window: Interval = (preferences.day_start_minute, preferences.day_end_minute)

        self._busy: dict[date, list[Interval]] = {d: [] for d in self.days}
        self._item_count: dict[date, int] = {d: 0 for d in self.days}
        self._busy_minutes: dict[date, int] = {d: 0 for d in self.days}

        for commitment in fixed_commitments:
            if commitment.day in self._busy:
                self._busy[commitment.day].append(
                    (commitment.start_minute, commitment.end_minute)
                )
                self._item_count[commitment.day] += 1
                self._busy_minutes[commitment.day] += self._clipped_duration(commitment)

        for item in locked_items or []:
            self.occupy(item.day, item.start_minute, item.end_minute)

    def _clipped_duration(self, commitment: FixedCommitment) -> int:
        start = max(commitment.start_minute, self._window[0])
        end = min(commitment.end_minute, self._window[1])
        return max(0, end - start)

    # -- queries -----------------------------------------------------------

    def free_intervals(self, day: date) -> list[Interval]:
        """Free intervals on ``day``, padded by the user's minimum gap."""
        gap = self.preferences.min_gap_between_activities
        padded = [(max(0, s - gap), e + gap) for s, e in self._busy.get(day, [])]
        return subtract(self._window, padded)

    def busy_intervals(self, day: date) -> list[Interval]:
        return sorted(self._busy.get(day, []))

    def free_minutes(self, day: date) -> int:
        return sum(e - s for s, e in self.free_intervals(day))

    def busy_minutes(self, day: date) -> int:
        return self._busy_minutes.get(day, 0)

    def item_count(self, day: date) -> int:
        return self._item_count.get(day, 0)

    def total_free_minutes(self) -> int:
        return sum(self.free_minutes(d) for d in self.days)

    def can_fit(self, day: date, duration: int) -> bool:
        return any(e - s >= duration for s, e in self.free_intervals(day))

    # -- mutation ----------------------------------------------------------

    def occupy(self, day: date, start: int, end: int) -> None:
        if day not in self._busy:
            return
        self._busy[day].append((start, end))
        self._item_count[day] += 1
        self._busy_minutes[day] += end - start

    def release(self, day: date, start: int, end: int) -> None:
        """Undo an ``occupy``. Used when the generator backtracks."""
        if day not in self._busy:
            return
        try:
            self._busy[day].remove((start, end))
        except ValueError:
            return
        self._item_count[day] -= 1
        self._busy_minutes[day] -= end - start
