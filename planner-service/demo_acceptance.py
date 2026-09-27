"""Reproduces the section 67 acceptance demo against the real engine.

Run:  python demo_acceptance.py
"""

from __future__ import annotations

from datetime import date, timedelta

from app.scheduler import (
    Activity,
    FixedCommitment,
    PlanningRequest,
    Severity,
    TimeOfDay,
    propose,
)
from app.scheduler.time_utils import format_minute

MONDAY = date(2026, 9, 21)


def main() -> None:
    college = [
        FixedCommitment(
            id=f"college-{n}",
            name="College",
            day=MONDAY + timedelta(days=n),
            start_minute=9 * 60,
            end_minute=16 * 60,
        )
        for n in range(5)
    ]

    request = PlanningRequest(
        horizon_start=MONDAY,
        horizon_days=7,
        fixed_commitments=college,
        activities=[
            Activity(
                id="ex",
                name="Exercise",
                duration_minutes=45,
                occurrences=2,
                min_spacing_days=1,
                preferred_time_of_day=TimeOfDay.EVENING,
            ),
            # The time-of-day band comes from the parser, not the scheduler.
            # "Try a new restaurant" is an evening activity; the engine does
            # not and should not hard-code meal times.
            Activity(
                id="rest",
                name="Try new restaurant",
                duration_minutes=90,
                occurrences=3,
                preferred_time_of_day=TimeOfDay.EVENING,
            ),
        ],
    )

    result = propose(request)
    proposal = result.proposal

    print("=" * 66)
    print("STEP 3-4  ANALYSIS")
    print("=" * 66)
    for conflict in proposal.conflicts:
        marker = "!!" if conflict.severity is Severity.HARD else "~ "
        print(f"{marker} [{conflict.severity.value}] {conflict.message}")
    if not proposal.conflicts:
        print("   No conflicts found.")

    print()
    print("=" * 66)
    print(f"STEP 7  PROPOSED SCHEDULE  (strategy={proposal.strategy}, "
          f"score={proposal.score})")
    print("=" * 66)
    print(f"{'Day':<12}{'Activity':<24}{'Time'}")
    print("-" * 66)
    for day in [MONDAY + timedelta(days=n) for n in range(7)]:
        rows = [i for i in proposal.items if i.day == day]
        if not rows:
            print(f"{day.strftime('%a %d'):<12}{'\u2014 free \u2014':<24}")
            continue
        for index, item in enumerate(rows):
            label = day.strftime("%a %d") if index == 0 else ""
            print(
                f"{label:<12}{item.activity_name:<24}"
                f"{format_minute(item.start_minute)}"
            )

    print()
    print("=" * 66)
    print("STEP 8  WHY THIS SCHEDULE?")
    print("=" * 66)
    for line in proposal.explanations:
        print(line)

    print()
    print("=" * 66)
    print("SCHEDULE BALANCE")
    print("=" * 66)
    for row in result.balance:
        bar = "#" * (row["scheduled_minutes"] // 30)
        print(f"{row['weekday']:<11}{bar:<18}{row['label']} "
              f"({row['scheduled_minutes']} min)")

    print()
    print("=" * 66)
    print("ALTERNATIVES OFFERED")
    print("=" * 66)
    for alternative in result.alternatives:
        days = ", ".join(
            f"{i.activity_name[:9]}@{i.day.strftime('%a')}" for i in alternative.items
        )
        print(f"  {alternative.strategy:<14} score={alternative.score:<8} {days}")

    print()
    print("Nothing above is saved. It becomes a schedule only after approval.")


if __name__ == "__main__":
    main()
