"""API contract for the planner service.

Pydantic lives only at this boundary. It validates untrusted input and
converts it into the plain dataclasses the engine works with, so the
scheduling logic never depends on the web framework.
"""

from __future__ import annotations

from datetime import date
from typing import Literal, Optional

from pydantic import BaseModel, Field, field_validator

from ..scheduler.models import (
    Activity,
    FixedCommitment,
    Flexibility,
    PlanningRequest,
    Priority,
    Relationship,
    RelationType,
    ScheduledItem,
    TimeOfDay,
    UserPreferences,
    Weekday,
)

MINUTES_IN_DAY = 24 * 60


class ActivityIn(BaseModel):
    id: str
    name: str = Field(min_length=1, max_length=160)
    duration_minutes: int = Field(gt=0, le=MINUTES_IN_DAY)
    occurrences: int = Field(default=1, ge=0, le=100)
    kind: Literal["HABIT", "TASK", "EVENT"] = "HABIT"
    priority: Literal["LOW", "MEDIUM", "HIGH", "CRITICAL"] = "MEDIUM"
    flexibility: Literal["FIXED", "PREFERRED", "FLEXIBLE"] = "FLEXIBLE"
    preferred_days: list[str] = Field(default_factory=list)
    excluded_days: list[str] = Field(default_factory=list)
    preferred_time_of_day: Literal[
        "ANY", "MORNING", "AFTERNOON", "EVENING", "NIGHT"
    ] = "ANY"
    preferred_start_minute: Optional[int] = Field(default=None, ge=0, lt=MINUTES_IN_DAY)
    earliest_minute: Optional[int] = Field(default=None, ge=0, lt=MINUTES_IN_DAY)
    latest_minute: Optional[int] = Field(default=None, gt=0, le=MINUTES_IN_DAY)
    min_spacing_days: Optional[int] = Field(default=None, ge=0, le=365)
    max_spacing_days: Optional[int] = Field(default=None, ge=1, le=365)
    max_per_day: int = Field(default=1, ge=1, le=24)
    deadline: Optional[date] = None
    depends_on: list[str] = Field(default_factory=list)

    @field_validator("preferred_days", "excluded_days")
    @classmethod
    def _known_days(cls, value: list[str]) -> list[str]:
        valid = {day.name for day in Weekday}
        unknown = [day for day in value if day.upper() not in valid]
        if unknown:
            raise ValueError(f"unknown weekday(s): {', '.join(unknown)}")
        return [day.upper() for day in value]

    def to_domain(self) -> Activity:
        return Activity(
            id=self.id,
            name=self.name,
            duration_minutes=self.duration_minutes,
            occurrences=self.occurrences,
            kind=self.kind,
            priority=Priority[self.priority],
            flexibility=Flexibility[self.flexibility],
            preferred_days=tuple(Weekday[d] for d in self.preferred_days),
            excluded_days=tuple(Weekday[d] for d in self.excluded_days),
            preferred_time_of_day=TimeOfDay[self.preferred_time_of_day],
            preferred_start_minute=self.preferred_start_minute,
            earliest_minute=self.earliest_minute,
            latest_minute=self.latest_minute,
            min_spacing_days=self.min_spacing_days,
            max_spacing_days=self.max_spacing_days,
            max_per_day=self.max_per_day,
            deadline=self.deadline,
            depends_on=tuple(self.depends_on),
        )


class CommitmentIn(BaseModel):
    id: str
    name: str
    day: date
    start_minute: int = Field(ge=0, lt=MINUTES_IN_DAY)
    end_minute: int = Field(gt=0, le=MINUTES_IN_DAY)

    @field_validator("end_minute")
    @classmethod
    def _ordered(cls, end: int, info):
        start = info.data.get("start_minute")
        if start is not None and end <= start:
            raise ValueError("end_minute must be after start_minute")
        return end

    def to_domain(self) -> FixedCommitment:
        return FixedCommitment(
            id=self.id,
            name=self.name,
            day=self.day,
            start_minute=self.start_minute,
            end_minute=self.end_minute,
        )


class RelationshipIn(BaseModel):
    activity_a: str
    activity_b: str
    type: Literal[
        "NONE", "AVOID_SAME_DAY", "AVOID_CONSECUTIVE_DAYS", "MIN_DAYS_APART",
        "SCHEDULE_BEFORE", "SCHEDULE_AFTER", "CAN_OVERLAP",
    ]
    days: int = Field(default=0, ge=0, le=365)
    note: str = ""

    def to_domain(self) -> Relationship:
        return Relationship(
            activity_a=self.activity_a,
            activity_b=self.activity_b,
            type=RelationType[self.type],
            days=self.days,
            note=self.note,
        )


class PreferencesIn(BaseModel):
    day_start_minute: int = Field(default=6 * 60, ge=0, lt=MINUTES_IN_DAY)
    day_end_minute: int = Field(default=23 * 60, gt=0, le=MINUTES_IN_DAY)
    core_start_minute: int = Field(default=8 * 60, ge=0, lt=MINUTES_IN_DAY)
    core_end_minute: int = Field(default=22 * 60, gt=0, le=MINUTES_IN_DAY)
    min_gap_between_activities: int = Field(default=10, ge=0, le=240)
    heavy_day_threshold_minutes: int = Field(default=240, ge=30, le=MINUTES_IN_DAY)
    heavy_day_threshold_items: int = Field(default=6, ge=1, le=50)
    timezone: str = "Asia/Kolkata"

    def to_domain(self) -> UserPreferences:
        return UserPreferences(**self.model_dump())


class ScheduledItemIn(BaseModel):
    activity_id: str
    activity_name: str
    day: date
    start_minute: int
    end_minute: int

    def to_domain(self) -> ScheduledItem:
        return ScheduledItem(
            activity_id=self.activity_id,
            activity_name=self.activity_name,
            day=self.day,
            start_minute=self.start_minute,
            end_minute=self.end_minute,
            locked=True,
        )


class PlanRequestIn(BaseModel):
    horizon_start: date
    horizon_days: int = Field(default=7, ge=1, le=366)
    activities: list[ActivityIn] = Field(default_factory=list)
    fixed_commitments: list[CommitmentIn] = Field(default_factory=list)
    relationships: list[RelationshipIn] = Field(default_factory=list)
    locked_items: list[ScheduledItemIn] = Field(default_factory=list)
    preferences: PreferencesIn = Field(default_factory=PreferencesIn)
    strategy: Literal["SPREAD", "EARLY_WEEK", "WEEKEND_FIRST"] = "SPREAD"

    @field_validator("activities")
    @classmethod
    def _unique_ids(cls, activities: list[ActivityIn]) -> list[ActivityIn]:
        ids = [a.id for a in activities]
        if len(ids) != len(set(ids)):
            raise ValueError("activity ids must be unique within a request")
        return activities

    def to_domain(self) -> PlanningRequest:
        return PlanningRequest(
            horizon_start=self.horizon_start,
            horizon_days=self.horizon_days,
            activities=[a.to_domain() for a in self.activities],
            fixed_commitments=[c.to_domain() for c in self.fixed_commitments],
            relationships=[r.to_domain() for r in self.relationships],
            locked_items=[i.to_domain() for i in self.locked_items],
            preferences=self.preferences.to_domain(),
            strategy=self.strategy,
        )


class WhatIfIn(BaseModel):
    plan: PlanRequestIn
    hypothetical: ActivityIn


class RebalanceIn(BaseModel):
    plan: PlanRequestIn
    missed: list[ActivityIn]


class InterpretIn(BaseModel):
    text: str = Field(min_length=1, max_length=500)


# --------------------------------------------------------------------------
# Serialization helpers (engine dataclasses -> plain JSON)
# --------------------------------------------------------------------------

def serialize_item(item: ScheduledItem) -> dict:
    return {
        "activity_id": item.activity_id,
        "activity_name": item.activity_name,
        "day": item.day.isoformat(),
        "weekday": item.day.strftime("%A"),
        "start_minute": item.start_minute,
        "end_minute": item.end_minute,
        "reason_codes": list(item.reasons),
    }


def serialize_conflict(conflict) -> dict:
    return {
        "type": conflict.type.value,
        "severity": conflict.severity.value,
        "message": conflict.message,
        "activity_ids": list(conflict.activity_ids),
        "day": conflict.day.isoformat() if conflict.day else None,
        "details": dict(conflict.details),
    }


def serialize_proposal(proposal) -> dict:
    return {
        "strategy": proposal.strategy,
        "score": proposal.score,
        "items": [serialize_item(i) for i in proposal.items],
        "conflicts": [serialize_conflict(c) for c in proposal.conflicts],
        "unplaced": [
            {"activity_id": aid, "missing_occurrences": n}
            for aid, n in proposal.unplaced
        ],
        "explanations": proposal.explanations,
        "has_hard_conflicts": proposal.has_hard_conflicts,
        # The planner never persists. Approval happens in Spring Boot.
        "status": "PROPOSED",
    }
