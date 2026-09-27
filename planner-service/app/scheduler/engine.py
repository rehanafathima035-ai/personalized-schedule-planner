"""Engine orchestration.

Public entry points:

  propose(request)          -> best Proposal plus ranked alternatives
  what_if(request, activity) -> impact of a hypothetical addition
  rebalance(request, missed) -> new homes for work that was missed

None of these mutate anything. A Proposal only becomes a schedule after
the user approves it, which happens in Spring Boot, not here.
"""

from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass, field, replace
from datetime import date

from . import conflicts as conflict_rules
from .availability import AvailabilityMap
from .density import balance_report, compute_day_loads, score_proposal
from .explainer import explain
from .generator import place
from .models import (
    Activity,
    Conflict,
    ConflictType,
    PlanningRequest,
    Proposal,
    ScheduledItem,
    Severity,
)
from .relationships import RelationshipIndex

STRATEGIES = ("SPREAD", "EARLY_WEEK", "WEEKEND_FIRST")


@dataclass
class PlanningResult:
    proposal: Proposal
    alternatives: list[Proposal] = field(default_factory=list)
    balance: list[dict] = field(default_factory=list)

    @property
    def all_proposals(self) -> list[Proposal]:
        return [self.proposal, *self.alternatives]


def _build_proposal(request: PlanningRequest, strategy: str) -> tuple[Proposal, AvailabilityMap]:
    availability = AvailabilityMap(
        horizon_start=request.horizon_start,
        horizon_days=request.horizon_days,
        preferences=request.preferences,
        fixed_commitments=request.fixed_commitments,
        locked_items=request.locked_items,
    )
    relationships = RelationshipIndex(request.relationships)

    detected: list[Conflict] = []
    detected += conflict_rules.detect_requested_slot_conflicts(
        request.activities,
        request.fixed_commitments,
        request.horizon_start,
        request.horizon_days,
    )
    detected += conflict_rules.detect_capacity_pressure(
        request.activities, availability, request.horizon_days
    )
    detected += conflict_rules.detect_frequency_relationship(
        request.activities, request.horizon_days
    )

    items, shortfalls, relaxed = place(
        deepcopy(request.activities),
        availability,
        relationships,
        request.preferences,
        strategy=strategy,
    )

    detected += conflict_rules.conflicts_from_shortfalls(shortfalls)
    detected += conflict_rules.conflicts_from_relaxations(relaxed)
    detected += conflict_rules.detect_dependency_issues(request.activities, items)
    detected += conflict_rules.detect_deadline_risk(request.activities, items)

    day_loads = compute_day_loads(availability, items + list(request.locked_items))
    detected += conflict_rules.detect_overloaded_days(day_loads, request.preferences)

    proposal = Proposal(
        strategy=strategy,
        items=items,
        conflicts=_dedupe(detected),
        unplaced=[(a.id, missing) for a, missing in shortfalls],
        day_loads=day_loads,
        explanations=explain(
            items,
            request.activities,
            request.fixed_commitments,
            day_loads,
            request.preferences,
        ),
    )
    proposal.score = score_proposal(proposal, request.preferences)
    return proposal, availability


def _dedupe(items: list[Conflict]) -> list[Conflict]:
    seen: set = set()
    unique: list[Conflict] = []
    for conflict in items:
        key = (conflict.type, conflict.message, conflict.day)
        if key in seen:
            continue
        seen.add(key)
        unique.append(conflict)
    return unique


def propose(request: PlanningRequest, with_alternatives: bool = True) -> PlanningResult:
    """Generate the best schedule plus genuinely different alternatives."""
    strategies = STRATEGIES if with_alternatives else (request.strategy,)
    proposals = [_build_proposal(request, strategy)[0] for strategy in strategies]
    proposals.sort(key=lambda p: (-p.score, p.strategy))

    # Two strategies often converge on the same answer. Offering the user the
    # same schedule twice under different names is noise, not choice.
    unique: list[Proposal] = []
    seen: set = set()
    for proposal in proposals:
        signature = tuple(
            (i.activity_id, i.day, i.start_minute) for i in proposal.items
        )
        if signature in seen:
            continue
        seen.add(signature)
        unique.append(proposal)

    best = unique[0]
    return PlanningResult(
        proposal=best,
        alternatives=unique[1:],
        balance=balance_report(best.day_loads, request.preferences),
    )


def what_if(request: PlanningRequest, hypothetical: Activity) -> dict:
    """Compare life with and without a proposed activity. Persists nothing."""
    baseline = propose(request, with_alternatives=False).proposal

    trial_request = replace(
        request, activities=[*request.activities, hypothetical]
    )
    trial = propose(trial_request, with_alternatives=True)

    baseline_by_day = {load.day: load.scheduled_minutes for load in baseline.day_loads}
    changed_days = [
        {
            "day": load.day.isoformat(),
            "weekday": load.day.strftime("%A"),
            "before_minutes": baseline_by_day.get(load.day, 0),
            "after_minutes": load.scheduled_minutes,
        }
        for load in trial.proposal.day_loads
        if load.scheduled_minutes != baseline_by_day.get(load.day, 0)
    ]

    moved = _moved_items(baseline.items, trial.proposal.items)

    return {
        "hypothetical": hypothetical.name,
        "fits": not trial.proposal.has_hard_conflicts,
        "baseline_score": baseline.score,
        "trial_score": trial.proposal.score,
        "items_moved": moved,
        "changed_days": changed_days,
        "new_conflicts": [
            c for c in trial.proposal.conflicts if c not in baseline.conflicts
        ],
        "proposal": trial.proposal,
        "alternatives": trial.alternatives,
        "note": "Nothing has been saved. This is a simulation.",
    }


def _moved_items(before: list[ScheduledItem], after: list[ScheduledItem]) -> list[dict]:
    before_map: dict[str, list[ScheduledItem]] = {}
    for item in before:
        before_map.setdefault(item.activity_id, []).append(item)

    moved: list[dict] = []
    for activity_id, old_items in before_map.items():
        new_items = [i for i in after if i.activity_id == activity_id]
        for old, new in zip(
            sorted(old_items, key=lambda i: (i.day, i.start_minute)),
            sorted(new_items, key=lambda i: (i.day, i.start_minute)),
        ):
            if old.day != new.day or old.start_minute != new.start_minute:
                moved.append(
                    {
                        "activity_id": activity_id,
                        "activity_name": old.activity_name,
                        "from_day": old.day.isoformat(),
                        "from_start": old.start_minute,
                        "to_day": new.day.isoformat(),
                        "to_start": new.start_minute,
                    }
                )
    return moved


def rebalance(request: PlanningRequest, missed: list[Activity]) -> PlanningResult:
    """Find new homes for missed work without disturbing what is approved.

    Everything already approved is passed in as ``locked_items`` so it is
    treated as unavailable time. Only the missed activities are placed, and
    the result still has to be approved.
    """
    rebalance_request = replace(request, activities=missed)
    result = propose(rebalance_request, with_alternatives=True)

    if not result.proposal.items and missed:
        result.proposal.conflicts.append(
            Conflict(
                type=ConflictType.NO_AVAILABLE_SLOT,
                severity=Severity.HARD,
                message=(
                    "There is no free time left in this window for the missed work. "
                    "Try extending the window or lowering a frequency."
                ),
                activity_ids=tuple(a.id for a in missed),
            )
        )
    return result
