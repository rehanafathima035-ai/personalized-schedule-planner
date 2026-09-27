"""Core domain model for the scheduling engine.

Deliberately dependency-free (standard library only) so the engine can be
unit-tested, reasoned about, and reused without FastAPI, Pydantic or any
web framework being present. The FastAPI layer converts Pydantic request
models into these dataclasses at the boundary.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from enum import Enum, IntEnum
from typing import Optional


# --------------------------------------------------------------------------
# Enumerations
# --------------------------------------------------------------------------

class Weekday(IntEnum):
    MONDAY = 0
    TUESDAY = 1
    WEDNESDAY = 2
    THURSDAY = 3
    FRIDAY = 4
    SATURDAY = 5
    SUNDAY = 6


class Flexibility(Enum):
    """How willing the scheduler is to move an activity."""
    FIXED = "FIXED"          # never moved automatically
    PREFERRED = "PREFERRED"  # has a preference, may be moved if needed
    FLEXIBLE = "FLEXIBLE"    # can go anywhere that fits


class Priority(IntEnum):
    LOW = 1
    MEDIUM = 2
    HIGH = 3
    CRITICAL = 4


class TimeOfDay(Enum):
    ANY = "ANY"
    MORNING = "MORNING"
    AFTERNOON = "AFTERNOON"
    EVENING = "EVENING"
    NIGHT = "NIGHT"


#: Inclusive-start / exclusive-end minute-of-day windows for each band.
TIME_OF_DAY_WINDOWS: dict[TimeOfDay, tuple[int, int]] = {
    TimeOfDay.MORNING: (5 * 60, 12 * 60),
    TimeOfDay.AFTERNOON: (12 * 60, 17 * 60),
    TimeOfDay.EVENING: (17 * 60, 21 * 60),
    TimeOfDay.NIGHT: (21 * 60, 24 * 60),
}


class RelationType(Enum):
    """User-defined relationships between two activities (spec section 8)."""
    NONE = "NONE"
    AVOID_SAME_DAY = "AVOID_SAME_DAY"
    AVOID_CONSECUTIVE_DAYS = "AVOID_CONSECUTIVE_DAYS"
    MIN_DAYS_APART = "MIN_DAYS_APART"
    SCHEDULE_BEFORE = "SCHEDULE_BEFORE"
    SCHEDULE_AFTER = "SCHEDULE_AFTER"
    CAN_OVERLAP = "CAN_OVERLAP"


class ConflictType(Enum):
    HARD_TIME_OVERLAP = "HARD_TIME_OVERLAP"
    NO_AVAILABLE_SLOT = "NO_AVAILABLE_SLOT"
    PARTIAL_FREQUENCY = "PARTIAL_FREQUENCY"
    SPACING_VIOLATION = "SPACING_VIOLATION"
    RELATIONSHIP_VIOLATION = "RELATIONSHIP_VIOLATION"
    DEADLINE_RISK = "DEADLINE_RISK"
    DEPENDENCY_ORDER = "DEPENDENCY_ORDER"
    OVERLOADED_DAY = "OVERLOADED_DAY"
    CAPACITY_PRESSURE = "CAPACITY_PRESSURE"


class Severity(Enum):
    HARD = "HARD"   # literally impossible as requested
    SOFT = "SOFT"   # possible, but worth telling the user about
    INFO = "INFO"


# --------------------------------------------------------------------------
# Inputs
# --------------------------------------------------------------------------

@dataclass(frozen=True)
class FixedCommitment:
    """An immovable block of time: college, internship, an appointment.

    These are never moved by the engine. They define where time is *not*
    available.
    """
    id: str
    name: str
    day: date
    start_minute: int
    end_minute: int
    source: str = "CALENDAR_EVENT"

    @property
    def duration(self) -> int:
        return self.end_minute - self.start_minute


@dataclass
class Activity:
    """Something the user wants to fit into their life.

    Covers habits ("exercise twice a week"), recurring goals-derived work
    and one-off tasks. ``occurrences`` is how many times it must appear in
    the planning horizon.
    """
    id: str
    name: str
    duration_minutes: int
    occurrences: int = 1
    kind: str = "HABIT"

    priority: Priority = Priority.MEDIUM
    flexibility: Flexibility = Flexibility.FLEXIBLE

    preferred_days: tuple[Weekday, ...] = ()
    excluded_days: tuple[Weekday, ...] = ()
    preferred_time_of_day: TimeOfDay = TimeOfDay.ANY
    preferred_start_minute: Optional[int] = None

    #: Hard window inside a day; defaults to the user's waking window.
    earliest_minute: Optional[int] = None
    latest_minute: Optional[int] = None

    min_spacing_days: Optional[int] = None
    max_spacing_days: Optional[int] = None
    max_per_day: int = 1

    deadline: Optional[date] = None
    depends_on: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        if self.duration_minutes <= 0:
            raise ValueError(f"{self.name}: duration_minutes must be positive")
        if self.occurrences < 0:
            raise ValueError(f"{self.name}: occurrences cannot be negative")
        if self.max_per_day < 1:
            raise ValueError(f"{self.name}: max_per_day must be at least 1")


@dataclass(frozen=True)
class Relationship:
    """A user-declared relationship. The engine never invents these."""
    activity_a: str
    activity_b: str
    type: RelationType
    days: int = 0            # used by MIN_DAYS_APART
    note: str = ""

    def involves(self, activity_id: str) -> bool:
        return activity_id in (self.activity_a, self.activity_b)

    def other(self, activity_id: str) -> str:
        return self.activity_b if activity_id == self.activity_a else self.activity_a


@dataclass
class UserPreferences:
    """Global scheduling preferences for one user."""
    day_start_minute: int = 6 * 60       # 06:00 - earliest anything may start
    day_end_minute: int = 23 * 60        # 23:00 - latest anything may end
    #: Civilised hours. Slots outside these are legal but scored down, so
    #: the engine only reaches for 6am when the day is genuinely full.
    core_start_minute: int = 8 * 60      # 08:00
    core_end_minute: int = 22 * 60       # 22:00
    min_gap_between_activities: int = 10  # minutes of breathing room
    #: Minutes of scheduled activity after which a day is flagged as dense.
    heavy_day_threshold_minutes: int = 240
    #: Number of discrete activities after which a day is flagged as dense.
    heavy_day_threshold_items: int = 6
    timezone: str = "Asia/Kolkata"


@dataclass
class PlanningRequest:
    """Everything the engine needs. No I/O, no database, no hidden state."""
    horizon_start: date
    horizon_days: int
    activities: list[Activity] = field(default_factory=list)
    fixed_commitments: list[FixedCommitment] = field(default_factory=list)
    relationships: list[Relationship] = field(default_factory=list)
    preferences: UserPreferences = field(default_factory=UserPreferences)
    #: Already-approved placements that must be honoured but not re-planned.
    locked_items: list["ScheduledItem"] = field(default_factory=list)
    strategy: str = "SPREAD"


# --------------------------------------------------------------------------
# Outputs
# --------------------------------------------------------------------------

@dataclass(frozen=True)
class ScheduledItem:
    activity_id: str
    activity_name: str
    day: date
    start_minute: int
    end_minute: int
    locked: bool = False
    #: Structured reason codes explaining why this slot was chosen.
    reasons: tuple[str, ...] = ()

    @property
    def weekday(self) -> Weekday:
        return Weekday(self.day.weekday())


@dataclass(frozen=True)
class Conflict:
    type: ConflictType
    severity: Severity
    message: str
    activity_ids: tuple[str, ...] = ()
    day: Optional[date] = None
    details: tuple[tuple[str, str], ...] = ()


@dataclass
class DayLoad:
    day: date
    scheduled_minutes: int
    item_count: int
    free_minutes: int

    @property
    def density(self) -> float:
        total = self.scheduled_minutes + self.free_minutes
        return 0.0 if total == 0 else self.scheduled_minutes / total


@dataclass
class Proposal:
    """A candidate schedule. Never persisted until the user approves it."""
    strategy: str
    items: list[ScheduledItem] = field(default_factory=list)
    conflicts: list[Conflict] = field(default_factory=list)
    unplaced: list[tuple[str, int]] = field(default_factory=list)
    day_loads: list[DayLoad] = field(default_factory=list)
    score: float = 0.0
    explanations: list[str] = field(default_factory=list)

    @property
    def has_hard_conflicts(self) -> bool:
        return any(c.severity is Severity.HARD for c in self.conflicts)

    def items_for(self, activity_id: str) -> list[ScheduledItem]:
        return [i for i in self.items if i.activity_id == activity_id]
