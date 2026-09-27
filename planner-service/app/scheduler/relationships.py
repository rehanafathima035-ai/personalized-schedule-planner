"""Evaluation of user-declared relationships between activities.

The engine never infers that two activities conflict. It only enforces
relationships the user explicitly created (spec section 8), which is what
keeps "exercise" and "try new restaurants" from being declared
incompatible by fiat.
"""

from __future__ import annotations

from datetime import date

from .models import Relationship, RelationType, ScheduledItem


class RelationshipIndex:
    def __init__(self, relationships: list[Relationship]) -> None:
        self._by_activity: dict[str, list[Relationship]] = {}
        for rule in relationships:
            if rule.type is RelationType.NONE:
                continue
            self._by_activity.setdefault(rule.activity_a, []).append(rule)
            self._by_activity.setdefault(rule.activity_b, []).append(rule)

    def for_activity(self, activity_id: str) -> list[Relationship]:
        return self._by_activity.get(activity_id, [])

    def evaluate(
        self,
        activity_id: str,
        candidate_day: date,
        placed: list[ScheduledItem],
    ) -> list[tuple[Relationship, str]]:
        """Return the rules ``candidate_day`` would break, with a reason."""
        broken: list[tuple[Relationship, str]] = []
        for rule in self.for_activity(activity_id):
            other_id = rule.other(activity_id)
            other_days = {i.day for i in placed if i.activity_id == other_id}
            if not other_days:
                continue

            if rule.type is RelationType.AVOID_SAME_DAY:
                if candidate_day in other_days:
                    broken.append((rule, "same day"))

            elif rule.type is RelationType.AVOID_CONSECUTIVE_DAYS:
                if any(abs((candidate_day - d).days) <= 1 for d in other_days):
                    broken.append((rule, "same or adjacent day"))

            elif rule.type is RelationType.MIN_DAYS_APART:
                if any(abs((candidate_day - d).days) < rule.days for d in other_days):
                    broken.append((rule, f"less than {rule.days} day(s) apart"))

            elif rule.type is RelationType.SCHEDULE_BEFORE:
                # a must come before b
                if activity_id == rule.activity_a:
                    if any(candidate_day > d for d in other_days):
                        broken.append((rule, "would fall after the later activity"))
                elif any(candidate_day < d for d in other_days):
                    broken.append((rule, "would fall before the earlier activity"))

            elif rule.type is RelationType.SCHEDULE_AFTER:
                if activity_id == rule.activity_a:
                    if any(candidate_day < d for d in other_days):
                        broken.append((rule, "would fall before its prerequisite"))
                elif any(candidate_day > d for d in other_days):
                    broken.append((rule, "would fall after its dependent activity"))

        return broken
