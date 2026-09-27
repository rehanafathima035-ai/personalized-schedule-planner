"""Deterministic, constraint-aware scheduling engine.

Standard library only. No FastAPI, no database, no network. This package
is the part of the product that decides *where things go*, and it is kept
independently testable on purpose.
"""

from .engine import PlanningResult, propose, rebalance, what_if
from .models import (
    Activity,
    Conflict,
    ConflictType,
    FixedCommitment,
    Flexibility,
    PlanningRequest,
    Priority,
    Proposal,
    Relationship,
    RelationType,
    ScheduledItem,
    Severity,
    TimeOfDay,
    UserPreferences,
    Weekday,
)

__all__ = [
    "Activity",
    "Conflict",
    "ConflictType",
    "FixedCommitment",
    "Flexibility",
    "PlanningRequest",
    "PlanningResult",
    "Priority",
    "Proposal",
    "RelationType",
    "Relationship",
    "ScheduledItem",
    "Severity",
    "TimeOfDay",
    "UserPreferences",
    "Weekday",
    "propose",
    "rebalance",
    "what_if",
]
