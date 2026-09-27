"""Internal planner API.

This service is not public. It is called by the Spring Boot backend, which
owns authentication and persistence. Nothing here writes to a database;
every endpoint is a pure function of its request body.
"""

from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException, status

from ..scheduler import engine
from ..schemas.planner import (
    InterpretIn,
    PlanRequestIn,
    RebalanceIn,
    WhatIfIn,
    serialize_conflict,
    serialize_proposal,
)
from ..services.nl_parser import interpret as parse_text

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/planner", tags=["planner"])


@router.post("/interpret")
def interpret_text(payload: InterpretIn) -> dict:
    """Turn free text into a structured interpretation. Saves nothing."""
    result = parse_text(payload.text)
    return {
        "interpretation": result.to_dict(),
        "requires_clarification": bool(result.missing),
        "status": "INTERPRETED",
    }


@router.post("/propose")
def propose(payload: PlanRequestIn) -> dict:
    """Generate the best schedule plus alternatives, for user approval."""
    try:
        result = engine.propose(payload.to_domain())
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)
        ) from exc

    return {
        "proposal": serialize_proposal(result.proposal),
        "alternatives": [serialize_proposal(p) for p in result.alternatives],
        "balance": result.balance,
    }


@router.post("/conflicts")
def conflicts_only(payload: PlanRequestIn) -> dict:
    """Analyse without proposing, for the Schedule Health screen."""
    result = engine.propose(payload.to_domain(), with_alternatives=False)
    return {
        "conflicts": [serialize_conflict(c) for c in result.proposal.conflicts],
        "balance": result.balance,
        "has_hard_conflicts": result.proposal.has_hard_conflicts,
    }


@router.post("/what-if")
def what_if(payload: WhatIfIn) -> dict:
    """Simulate adding an activity. Explicitly changes nothing."""
    report = engine.what_if(
        payload.plan.to_domain(), payload.hypothetical.to_domain()
    )
    return {
        "hypothetical": report["hypothetical"],
        "fits": report["fits"],
        "baseline_score": report["baseline_score"],
        "trial_score": report["trial_score"],
        "items_moved": report["items_moved"],
        "changed_days": report["changed_days"],
        "new_conflicts": [serialize_conflict(c) for c in report["new_conflicts"]],
        "proposal": serialize_proposal(report["proposal"]),
        "alternatives": [serialize_proposal(p) for p in report["alternatives"]],
        "note": report["note"],
    }


@router.post("/rebalance")
def rebalance(payload: RebalanceIn) -> dict:
    """Find new homes for missed work, around everything already approved."""
    result = engine.rebalance(
        payload.plan.to_domain(), [a.to_domain() for a in payload.missed]
    )
    return {
        "proposal": serialize_proposal(result.proposal),
        "alternatives": [serialize_proposal(p) for p in result.alternatives],
        "balance": result.balance,
    }
