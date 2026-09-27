"""Scheduling engine tests.

These are the scenarios named in the build spec (sections 66, 67, 69, 70).
Run with:  python -m unittest discover -s tests -v
"""

from __future__ import annotations

import sys
import unittest
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.scheduler import (  # noqa: E402
    Activity,
    ConflictType,
    FixedCommitment,
    Flexibility,
    PlanningRequest,
    Priority,
    RelationType,
    Relationship,
    Severity,
    TimeOfDay,
    UserPreferences,
    Weekday,
    propose,
    rebalance,
    what_if,
)

# A known Monday, so weekday assertions are stable.
MONDAY = date(2026, 9, 21)


def week(**kwargs) -> PlanningRequest:
    kwargs.setdefault("horizon_start", MONDAY)
    kwargs.setdefault("horizon_days", 7)
    return PlanningRequest(**kwargs)


def conflict_types(proposal) -> set:
    return {c.type for c in proposal.conflicts}


class TestBasicPlacement(unittest.TestCase):
    def test_places_requested_number_of_sessions(self):
        request = week(
            activities=[
                Activity(id="ex", name="Exercise", duration_minutes=45, occurrences=2)
            ]
        )
        result = propose(request)
        self.assertEqual(len(result.proposal.items_for("ex")), 2)
        self.assertFalse(result.proposal.has_hard_conflicts)

    def test_is_deterministic(self):
        request = week(
            activities=[
                Activity(id="ex", name="Exercise", duration_minutes=45, occurrences=3),
                Activity(id="kr", name="Korean", duration_minutes=30, occurrences=5),
            ]
        )
        first = propose(request).proposal
        second = propose(request).proposal
        self.assertEqual(
            [(i.activity_id, i.day, i.start_minute) for i in first.items],
            [(i.activity_id, i.day, i.start_minute) for i in second.items],
        )

    def test_never_double_books_a_minute(self):
        request = week(
            activities=[
                Activity(id="a", name="A", duration_minutes=60, occurrences=7),
                Activity(id="b", name="B", duration_minutes=60, occurrences=7),
                Activity(id="c", name="C", duration_minutes=60, occurrences=7),
            ]
        )
        items = propose(request).proposal.items
        by_day: dict = {}
        for item in items:
            by_day.setdefault(item.day, []).append(item)
        for day_items in by_day.values():
            ordered = sorted(day_items, key=lambda i: i.start_minute)
            for earlier, later in zip(ordered, ordered[1:]):
                self.assertLessEqual(earlier.end_minute, later.start_minute)


class TestFixedCommitments(unittest.TestCase):
    def test_does_not_schedule_on_top_of_college(self):
        college = FixedCommitment(
            id="college",
            name="College",
            day=MONDAY,
            start_minute=9 * 60,
            end_minute=16 * 60,
        )
        request = week(
            activities=[
                Activity(
                    id="ex",
                    name="Exercise",
                    duration_minutes=60,
                    occurrences=1,
                    preferred_days=(Weekday.MONDAY,),
                    preferred_start_minute=10 * 60,
                )
            ],
            fixed_commitments=[college],
        )
        item = propose(request).proposal.items_for("ex")[0]
        self.assertFalse(
            item.start_minute < college.end_minute
            and college.start_minute < item.end_minute,
            "Exercise was scheduled inside college hours",
        )

    def test_hard_conflict_detected_and_flexible_side_moves(self):
        """Spec section 69: College fixed 5-6 PM vs Gym flexible 5-6 PM."""
        college = FixedCommitment(
            id="college",
            name="College",
            day=MONDAY,
            start_minute=17 * 60,
            end_minute=18 * 60,
        )
        gym = Activity(
            id="gym",
            name="Gym",
            duration_minutes=60,
            occurrences=1,
            flexibility=Flexibility.FLEXIBLE,
            preferred_days=(Weekday.MONDAY,),
            preferred_start_minute=17 * 60,
        )
        proposal = propose(week(activities=[gym], fixed_commitments=[college])).proposal

        self.assertIn(ConflictType.HARD_TIME_OVERLAP, conflict_types(proposal))
        overlap = next(
            c for c in proposal.conflicts if c.type is ConflictType.HARD_TIME_OVERLAP
        )
        self.assertEqual(overlap.severity, Severity.SOFT)  # resolvable: gym is flexible
        self.assertIn(("resolvable_automatically", "true"), overlap.details)

        placed = proposal.items_for("gym")[0]
        self.assertNotEqual(
            (placed.day, placed.start_minute), (MONDAY, 17 * 60),
            "Gym should have been moved off the blocked slot",
        )

    def test_two_fixed_activities_at_same_time_need_user_decision(self):
        java = FixedCommitment(
            id="java", name="Java class", day=MONDAY,
            start_minute=19 * 60, end_minute=20 * 60,
        )
        korean = Activity(
            id="kr", name="Korean", duration_minutes=60, occurrences=1,
            flexibility=Flexibility.FIXED,
            preferred_days=(Weekday.MONDAY,),
            preferred_start_minute=19 * 60,
        )
        proposal = propose(week(activities=[korean], fixed_commitments=[java])).proposal
        overlap = next(
            c for c in proposal.conflicts if c.type is ConflictType.HARD_TIME_OVERLAP
        )
        self.assertEqual(overlap.severity, Severity.HARD)


class TestSpacing(unittest.TestCase):
    def test_minimum_spacing_is_respected(self):
        request = week(
            activities=[
                Activity(
                    id="ex", name="Exercise", duration_minutes=45,
                    occurrences=3, min_spacing_days=1,
                )
            ]
        )
        days = sorted(i.day for i in propose(request).proposal.items_for("ex"))
        for earlier, later in zip(days, days[1:]):
            self.assertGreaterEqual((later - earlier).days, 1)

    def test_sessions_are_spread_not_clustered(self):
        request = week(
            activities=[
                Activity(id="ex", name="Exercise", duration_minutes=45, occurrences=3)
            ]
        )
        days = sorted(i.day for i in propose(request).proposal.items_for("ex"))
        self.assertGreaterEqual((days[-1] - days[0]).days, 3)

    def test_impossible_spacing_reports_shortfall(self):
        request = week(
            activities=[
                Activity(
                    id="ex", name="Exercise", duration_minutes=45,
                    occurrences=5, min_spacing_days=3,
                )
            ]
        )
        proposal = propose(request).proposal
        self.assertTrue(proposal.has_hard_conflicts)
        self.assertIn(
            ConflictType.PARTIAL_FREQUENCY, conflict_types(proposal)
        )


class TestPreferences(unittest.TestCase):
    def test_evening_preference_is_honoured(self):
        request = week(
            activities=[
                Activity(
                    id="kr", name="Korean", duration_minutes=30, occurrences=5,
                    preferred_time_of_day=TimeOfDay.EVENING,
                    preferred_start_minute=19 * 60,
                )
            ]
        )
        for item in propose(request).proposal.items_for("kr"):
            self.assertGreaterEqual(item.start_minute, 17 * 60)
            self.assertLess(item.start_minute, 21 * 60)

    def test_excluded_days_are_never_used(self):
        request = week(
            activities=[
                Activity(
                    id="ex", name="Exercise", duration_minutes=45, occurrences=4,
                    excluded_days=(Weekday.SUNDAY, Weekday.SATURDAY),
                )
            ]
        )
        for item in propose(request).proposal.items_for("ex"):
            self.assertNotIn(item.weekday, (Weekday.SATURDAY, Weekday.SUNDAY))

    def test_weekday_only_preference(self):
        request = week(
            activities=[
                Activity(
                    id="kr", name="Korean", duration_minutes=30, occurrences=5,
                    preferred_days=(
                        Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY,
                        Weekday.THURSDAY, Weekday.FRIDAY,
                    ),
                )
            ]
        )
        for item in propose(request).proposal.items_for("kr"):
            self.assertLessEqual(item.weekday, Weekday.FRIDAY)


class TestPriorityAndFlexibility(unittest.TestCase):
    def test_critical_work_gets_the_contested_slot(self):
        """A critical task and a low-priority habit both want the one free hour."""
        prefs = UserPreferences(day_start_minute=18 * 60, day_end_minute=19 * 60)
        critical = Activity(
            id="cert", name="Certification study", duration_minutes=60,
            occurrences=1, priority=Priority.CRITICAL,
            preferred_days=(Weekday.MONDAY,),
        )
        low = Activity(
            id="game", name="Casual gaming", duration_minutes=60,
            occurrences=1, priority=Priority.LOW,
            preferred_days=(Weekday.MONDAY,),
        )
        proposal = propose(
            week(activities=[low, critical], preferences=prefs)
        ).proposal
        self.assertEqual(proposal.items_for("cert")[0].day, MONDAY)
        placed_low = proposal.items_for("game")
        self.assertTrue(
            not placed_low or placed_low[0].day != MONDAY,
            "The low-priority habit should have yielded the contested Monday slot",
        )


class TestRelationships(unittest.TestCase):
    def test_avoid_same_day_rule_is_enforced(self):
        request = week(
            activities=[
                Activity(id="ex", name="Exercise", duration_minutes=45, occurrences=2),
                Activity(id="rest", name="Restaurant", duration_minutes=90, occurrences=3),
            ],
            relationships=[
                Relationship(
                    activity_a="ex", activity_b="rest",
                    type=RelationType.AVOID_SAME_DAY,
                )
            ],
        )
        proposal = propose(request).proposal
        exercise_days = {i.day for i in proposal.items_for("ex")}
        restaurant_days = {i.day for i in proposal.items_for("rest")}
        self.assertEqual(exercise_days & restaurant_days, set())

    def test_no_relationship_means_no_enforcement(self):
        """Without a user-declared rule the engine must not invent one."""
        request = week(
            activities=[
                Activity(id="ex", name="Exercise", duration_minutes=45, occurrences=2),
                Activity(id="rest", name="Restaurant", duration_minutes=90, occurrences=3),
            ]
        )
        proposal = propose(request).proposal
        self.assertNotIn(ConflictType.RELATIONSHIP_VIOLATION, conflict_types(proposal))


class TestDependenciesAndDeadlines(unittest.TestCase):
    def test_dependency_order_is_kept(self):
        research = Activity(id="research", name="Research destinations",
                            duration_minutes=60, occurrences=1)
        budget = Activity(id="budget", name="Create budget", duration_minutes=60,
                          occurrences=1, depends_on=("research",))
        visa = Activity(id="visa", name="Visa research", duration_minutes=60,
                        occurrences=1, depends_on=("budget",))
        relationships = [
            Relationship("research", "budget", RelationType.SCHEDULE_BEFORE),
            Relationship("budget", "visa", RelationType.SCHEDULE_BEFORE),
        ]
        proposal = propose(
            week(activities=[visa, budget, research], relationships=relationships)
        ).proposal
        day_of = {a: proposal.items_for(a)[0].day for a in ("research", "budget", "visa")}
        self.assertLessEqual(day_of["research"], day_of["budget"])
        self.assertLessEqual(day_of["budget"], day_of["visa"])
        self.assertNotIn(ConflictType.DEPENDENCY_ORDER, conflict_types(proposal))

    def test_deadline_is_never_overrun(self):
        deadline = MONDAY + timedelta(days=2)
        request = week(
            activities=[
                Activity(
                    id="cert", name="Certification", duration_minutes=60,
                    occurrences=2, deadline=deadline,
                )
            ]
        )
        proposal = propose(request).proposal
        for item in proposal.items_for("cert"):
            self.assertLessEqual(item.day, deadline)
        self.assertNotIn(ConflictType.DEADLINE_RISK, conflict_types(proposal))

    def test_unreachable_deadline_is_reported(self):
        request = week(
            activities=[
                Activity(
                    id="cert", name="Certification", duration_minutes=120,
                    occurrences=10, deadline=MONDAY,
                )
            ]
        )
        proposal = propose(request).proposal
        self.assertTrue(proposal.has_hard_conflicts)


class TestCapacityAndDensity(unittest.TestCase):
    def test_overcommitted_week_is_flagged_not_silently_truncated(self):
        request = week(
            activities=[
                Activity(id=f"a{n}", name=f"Activity {n}",
                         duration_minutes=180, occurrences=7)
                for n in range(6)
            ]
        )
        proposal = propose(request).proposal
        self.assertIn(ConflictType.CAPACITY_PRESSURE, conflict_types(proposal))

    def test_day_loads_cover_every_day(self):
        proposal = propose(
            week(activities=[Activity(id="ex", name="Exercise",
                                      duration_minutes=45, occurrences=2)])
        ).proposal
        self.assertEqual(len(proposal.day_loads), 7)


class TestAcceptanceMainDemo(unittest.TestCase):
    """Spec section 67: exercise 2x + restaurants 3x."""

    def setUp(self):
        self.request = week(
            activities=[
                Activity(id="ex", name="Exercise", duration_minutes=45,
                         occurrences=2, min_spacing_days=1),
                Activity(id="rest", name="Try new restaurant",
                         duration_minutes=90, occurrences=3),
            ]
        )

    def test_flags_a_potential_not_an_incompatibility(self):
        proposal = propose(self.request).proposal
        soft = [c for c in proposal.conflicts if c.severity is Severity.SOFT]
        self.assertTrue(soft, "Expected at least one soft observation")
        # The engine may say "not incompatible"; it must never *declare* the
        # two goals incompatible.
        for conflict in proposal.conflicts:
            message = conflict.message.lower()
            self.assertNotIn("are incompatible", message)
            self.assertNotIn("cannot coexist", message)
            self.assertNotIn("conflict with each other", message)

        relationship_note = next(
            c for c in soft if c.type is ConflictType.PARTIAL_FREQUENCY
        )
        self.assertIn("not incompatible", relationship_note.message.lower())

    def test_optimize_distributes_across_the_week(self):
        proposal = propose(self.request).proposal
        exercise_days = sorted(i.day for i in proposal.items_for("ex"))
        restaurant_days = sorted(i.day for i in proposal.items_for("rest"))

        self.assertEqual(len(exercise_days), 2)
        self.assertEqual(len(restaurant_days), 3)
        self.assertGreaterEqual((exercise_days[1] - exercise_days[0]).days, 1)
        # Five sessions across seven days should leave free days, not cluster.
        used = set(exercise_days) | set(restaurant_days)
        self.assertLessEqual(len(used), 7)
        self.assertGreaterEqual(len(used), 4)

    def test_produces_explanations_and_alternatives(self):
        result = propose(self.request)
        self.assertTrue(result.proposal.explanations)
        self.assertTrue(result.alternatives)
        self.assertTrue(result.balance)
        for alternative in result.alternatives:
            self.assertNotEqual(alternative.strategy, result.proposal.strategy)


class TestWhatIf(unittest.TestCase):
    def test_what_if_reports_impact_without_changing_anything(self):
        base_activities = [
            Activity(id="kr", name="Korean", duration_minutes=30, occurrences=5),
            Activity(id="lc", name="LeetCode", duration_minutes=45, occurrences=5),
        ]
        request = week(activities=base_activities)
        before = propose(request).proposal

        gym = Activity(id="gym", name="Gym", duration_minutes=60,
                       occurrences=4, min_spacing_days=1)
        report = what_if(request, gym)

        self.assertTrue(report["fits"])
        self.assertEqual(len(report["proposal"].items_for("gym")), 4)
        self.assertIn("Nothing has been saved", report["note"])
        # The original request object is untouched.
        self.assertEqual(len(request.activities), 2)
        self.assertEqual(len(propose(request).proposal.items), len(before.items))


class TestRebalance(unittest.TestCase):
    def test_missed_work_is_rescheduled_around_existing_commitments(self):
        """Spec section 70: four missed tasks get new homes."""
        approved = propose(
            week(activities=[Activity(id="kr", name="Korean",
                                      duration_minutes=30, occurrences=5)])
        ).proposal

        missed = [
            Activity(id=f"t{n}", name=f"Missed task {n}",
                     duration_minutes=45, occurrences=1, priority=Priority.HIGH)
            for n in range(4)
        ]
        request = week(locked_items=approved.items)
        result = rebalance(request, missed)

        self.assertEqual(len(result.proposal.items), 4)
        self.assertFalse(result.proposal.has_hard_conflicts)

        # New work must not collide with the already-approved Korean sessions.
        locked = {(i.day, i.start_minute, i.end_minute) for i in approved.items}
        for item in result.proposal.items:
            for day, start, end in locked:
                if item.day == day:
                    self.assertFalse(
                        item.start_minute < end and start < item.end_minute,
                        "Rebalanced task collided with an approved session",
                    )

    def test_rebalance_reports_when_there_is_no_room(self):
        prefs = UserPreferences(day_start_minute=9 * 60, day_end_minute=10 * 60)
        blockers = [
            FixedCommitment(id=f"b{n}", name="Busy", day=MONDAY + timedelta(days=n),
                            start_minute=9 * 60, end_minute=10 * 60)
            for n in range(7)
        ]
        request = week(preferences=prefs, fixed_commitments=blockers)
        missed = [Activity(id="t", name="Missed task",
                           duration_minutes=45, occurrences=1)]
        result = rebalance(request, missed)
        self.assertTrue(result.proposal.has_hard_conflicts)


class TestRealisticWeek(unittest.TestCase):
    """The nine-activity life from spec section 5, coordinated as one plan."""

    def test_full_week_plans_without_hard_conflicts(self):
        fixed = [
            FixedCommitment(id=f"college{n}", name="College",
                            day=MONDAY + timedelta(days=n),
                            start_minute=9 * 60, end_minute=16 * 60)
            for n in range(5)
        ]
        activities = [
            Activity(id="kr", name="Korean", duration_minutes=30, occurrences=6,
                     preferred_time_of_day=TimeOfDay.EVENING),
            Activity(id="lc", name="LeetCode", duration_minutes=45, occurrences=5),
            Activity(id="ex", name="Exercise", duration_minutes=45, occurrences=2,
                     min_spacing_days=1, preferred_time_of_day=TimeOfDay.MORNING),
            Activity(id="read", name="Reading", duration_minutes=20, occurrences=7,
                     preferred_time_of_day=TimeOfDay.NIGHT),
            Activity(id="rest", name="Restaurant", duration_minutes=90, occurrences=3),
            Activity(id="clean", name="Cleaning", duration_minutes=60, occurrences=1),
            Activity(id="grocery", name="Grocery shopping", duration_minutes=60,
                     occurrences=1),
        ]
        result = propose(week(activities=activities, fixed_commitments=fixed))
        proposal = result.proposal

        for activity in activities:
            self.assertEqual(
                len(proposal.items_for(activity.id)), activity.occurrences,
                f"{activity.name} did not get all its sessions",
            )
        self.assertFalse(proposal.has_hard_conflicts, proposal.conflicts)

        # Nothing lands inside college hours.
        for item in proposal.items:
            for commitment in fixed:
                if item.day == commitment.day:
                    self.assertFalse(
                        item.start_minute < commitment.end_minute
                        and commitment.start_minute < item.end_minute,
                        f"{item.activity_name} overlapped College",
                    )


if __name__ == "__main__":
    unittest.main(verbosity=2)
