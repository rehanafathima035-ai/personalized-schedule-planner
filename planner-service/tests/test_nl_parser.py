"""Natural-language parsing tests (spec sections 21, 22, 68)."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.scheduler.models import TimeOfDay, Weekday  # noqa: E402
from app.services.nl_parser import interpret  # noqa: E402


class TestAcceptanceSecondDemo(unittest.TestCase):
    """Spec section 68: 'Learn Korean every weekday at 7 PM for 30 minutes.'"""

    def setUp(self):
        self.result = interpret("Learn Korean every weekday at 7 PM for 30 minutes")

    def test_name(self):
        self.assertEqual(self.result.name, "Learn Korean")

    def test_days_are_monday_to_friday(self):
        self.assertEqual(
            self.result.preferred_days,
            (Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY,
             Weekday.THURSDAY, Weekday.FRIDAY),
        )

    def test_time_and_duration(self):
        self.assertEqual(self.result.preferred_start_minute, 19 * 60)
        self.assertEqual(self.result.duration_minutes, 30)
        self.assertEqual(self.result.preferred_time_of_day, TimeOfDay.EVENING)

    def test_nothing_left_to_ask(self):
        self.assertEqual(self.result.missing, [])
        self.assertEqual(self.result.confidence, 1.0)


class TestFrequency(unittest.TestCase):
    def test_twice_a_week(self):
        result = interpret("Exercise twice a week")
        self.assertEqual(result.occurrences, 2)
        self.assertEqual(result.period, "WEEK")
        self.assertEqual(result.name, "Exercise")

    def test_numeric_frequency(self):
        result = interpret("Do LeetCode 5 times a week")
        self.assertEqual(result.occurrences, 5)

    def test_daily(self):
        result = interpret("Read 20 pages every day")
        self.assertEqual(result.occurrences, 7)
        self.assertEqual(result.preferred_days, ())

    def test_nightly(self):
        result = interpret("Read 20 pages every night")
        self.assertEqual(result.occurrences, 7)
        self.assertEqual(result.preferred_time_of_day, TimeOfDay.NIGHT)

    def test_monthly_period_is_kept(self):
        result = interpret("Read 2 books 2 times a month")
        self.assertEqual(result.period, "MONTH")
        self.assertEqual(result.occurrences, 2)

    def test_day_span(self):
        result = interpret("Learn Korean Monday to Saturday for 30 minutes")
        self.assertEqual(result.occurrences, 6)
        self.assertIn(Weekday.SATURDAY, result.preferred_days)
        self.assertNotIn(Weekday.SUNDAY, result.preferred_days)

    def test_named_days(self):
        result = interpret("Gym on Monday, Wednesday and Friday for 1 hour")
        self.assertEqual(
            set(result.preferred_days),
            {Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY},
        )
        self.assertEqual(result.occurrences, 3)
        self.assertEqual(result.duration_minutes, 60)


class TestFrequencyRegressions(unittest.TestCase):
    """Cases that were parsed wrongly on first inspection."""

    def test_count_separated_from_period_by_the_object(self):
        result = interpret("Try three restaurants every week")
        self.assertEqual(result.occurrences, 3)
        self.assertEqual(result.period, "WEEK")

    def test_quantity_is_not_mistaken_for_a_session_count(self):
        # "20 pages every day" is one daily session, not twenty.
        self.assertEqual(interpret("Read 20 pages every day").occurrences, 7)
        # An implausibly large count is declined rather than guessed.
        self.assertIsNone(interpret("Save 5000 rupees every month").occurrences)

    def test_next_year_is_a_period(self):
        result = interpret("Travel abroad twice next year")
        self.assertEqual(result.occurrences, 2)
        self.assertEqual(result.period, "YEAR")
        self.assertEqual(result.name, "Travel abroad")

    def test_name_drops_the_schedule_clause(self):
        self.assertEqual(interpret("Try 3 new restaurants a week").name,
                         "Try 3 new restaurants")


class TestDurationAndTime(unittest.TestCase):
    def test_hours(self):
        self.assertEqual(interpret("Study for 2 hours daily").duration_minutes, 120)

    def test_half_an_hour(self):
        self.assertEqual(interpret("Meditate for half an hour daily").duration_minutes, 30)

    def test_24_hour_clock(self):
        result = interpret("Korean practice at 19:30 every weekday for 30 minutes")
        self.assertEqual(result.preferred_start_minute, 19 * 60 + 30)

    def test_time_of_day_word(self):
        result = interpret("Exercise twice a week in the morning for 45 minutes")
        self.assertEqual(result.preferred_time_of_day, TimeOfDay.MORNING)


class TestClarification(unittest.TestCase):
    def test_vague_input_asks_only_what_is_missing(self):
        result = interpret("Exercise regularly")
        self.assertIn("frequency", result.missing)
        self.assertIn("duration", result.missing)
        self.assertLessEqual(len(result.questions), 3)
        self.assertLess(result.confidence, 0.5)

    def test_complete_input_asks_nothing(self):
        result = interpret("Drink water 7 times a week for 5 minutes in the morning")
        self.assertEqual(result.questions, [])

    def test_partial_input_asks_one_question(self):
        result = interpret("Exercise twice a week in the evening")
        self.assertEqual(result.missing, ["duration"])
        self.assertEqual(len(result.questions), 1)

    def test_parser_never_returns_a_saved_object(self):
        result = interpret("Exercise twice a week")
        self.assertFalse(hasattr(result, "id"))
        self.assertIn("name", result.to_dict())


class TestNameDerivation(unittest.TestCase):
    def test_strips_leading_intent(self):
        self.assertEqual(interpret("I want to exercise twice a week").name, "Exercise")

    def test_strips_trailing_schedule(self):
        self.assertEqual(
            interpret("Solve LeetCode problems 5 times a week for 45 minutes").name,
            "Solve LeetCode problems",
        )

    def test_never_empty(self):
        self.assertTrue(interpret("daily").name)


if __name__ == "__main__":
    unittest.main(verbosity=2)
