"""Deterministic natural-language interpretation.

This is the fallback parser: regex and vocabulary, no model, no network.
It exists for three reasons.

  1. An external LLM provider may be unavailable, and pretending a failed
     call succeeded is not acceptable (spec section 62).
  2. Most real input is formulaic enough to parse exactly, and an exact
     parse beats a probabilistic one.
  3. It is fully testable, so the parsing behaviour is pinned down.

The output is always an *interpretation*, never a saved habit. Confidence
and unresolved questions are reported so the clarification engine knows
what still needs asking (spec section 22).
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

from ..scheduler.models import TimeOfDay, Weekday

WEEKDAY_WORDS: dict[str, Weekday] = {
    "monday": Weekday.MONDAY, "mon": Weekday.MONDAY,
    "tuesday": Weekday.TUESDAY, "tue": Weekday.TUESDAY, "tues": Weekday.TUESDAY,
    "wednesday": Weekday.WEDNESDAY, "wed": Weekday.WEDNESDAY,
    "thursday": Weekday.THURSDAY, "thu": Weekday.THURSDAY, "thurs": Weekday.THURSDAY,
    "friday": Weekday.FRIDAY, "fri": Weekday.FRIDAY,
    "saturday": Weekday.SATURDAY, "sat": Weekday.SATURDAY,
    "sunday": Weekday.SUNDAY, "sun": Weekday.SUNDAY,
}

WEEKDAYS = (Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY,
            Weekday.THURSDAY, Weekday.FRIDAY)
WEEKEND = (Weekday.SATURDAY, Weekday.SUNDAY)

NUMBER_WORDS = {
    "once": 1, "one": 1, "twice": 2, "two": 2, "thrice": 3, "three": 3,
    "four": 4, "five": 5, "six": 6, "seven": 7, "eight": 8, "nine": 9, "ten": 10,
    "daily": 7, "every day": 7, "everyday": 7,
}

TIME_OF_DAY_WORDS = {
    "morning": TimeOfDay.MORNING,
    "afternoon": TimeOfDay.AFTERNOON,
    "evening": TimeOfDay.EVENING,
    "night": TimeOfDay.NIGHT,
    "tonight": TimeOfDay.NIGHT,
}

#: Leading verbs and filler stripped when deriving a habit name.
_LEAD_FILLER = re.compile(
    r"^(i\s+want\s+to|i\s+would\s+like\s+to|i'?d\s+like\s+to|i\s+need\s+to|"
    r"please|let'?s|help\s+me|remind\s+me\s+to)\s+",
    re.IGNORECASE,
)


@dataclass
class Interpretation:
    """What the parser believes the user meant. Nothing is saved from this."""
    name: str
    raw_text: str
    occurrences: int | None = None
    period: str = "WEEK"
    duration_minutes: int | None = None
    preferred_days: tuple[Weekday, ...] = ()
    preferred_time_of_day: TimeOfDay = TimeOfDay.ANY
    preferred_start_minute: int | None = None
    confidence: float = 0.0
    missing: list[str] = field(default_factory=list)
    questions: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "raw_text": self.raw_text,
            "occurrences": self.occurrences,
            "period": self.period,
            "duration_minutes": self.duration_minutes,
            "preferred_days": [d.name for d in self.preferred_days],
            "preferred_time_of_day": self.preferred_time_of_day.value,
            "preferred_start_minute": self.preferred_start_minute,
            "confidence": round(self.confidence, 2),
            "missing": self.missing,
            "questions": self.questions,
        }


# --------------------------------------------------------------------------
# Field extractors
# --------------------------------------------------------------------------

def _parse_frequency(text: str) -> tuple[int | None, str]:
    """Return (occurrences, period)."""
    if re.search(r"\b(every ?day|everyday|daily|each day)\b", text):
        return 7, "WEEK"
    if re.search(r"\b(every night|each night|nightly)\b", text):
        return 7, "WEEK"

    match = re.search(
        r"\b(\d+)\s*(?:x|times?)\s*(?:a|per|every|each)\s*(day|week|month|year)\b", text
    )
    if match:
        return int(match.group(1)), match.group(2).upper()

    for word, value in NUMBER_WORDS.items():
        if re.search(rf"\b{word}\s*(?:a|per|every|each|next|this)\s*(day|week|month|year)\b", text):
            period = re.search(
                rf"\b{word}\s*(?:a|per|every|each|next|this)\s*(day|week|month|year)\b", text
            ).group(1)
            return value, period.upper()

    # "try three restaurants every week" - the count is separated from the
    # period by the object. Only applied for week/month/year, because the
    # daily rules above already claimed "20 pages every day", where the
    # number is a quantity rather than a session count.
    loose = re.search(
        r"\b(\d+|" + "|".join(NUMBER_WORDS) + r")\b(?:\s+[\w,]+){0,3}\s+"
        r"(?:a|per|every|each|next|this)\s*(week|month|year)\b",
        text,
    )
    if loose:
        token = loose.group(1)
        count = int(token) if token.isdigit() else NUMBER_WORDS[token]
        # A session count above this is almost certainly a quantity
        # ("save 5000 rupees every month"), so we decline to guess.
        if 1 <= count <= 30:
            return count, loose.group(2).upper()

    # "monday to saturday" / "every weekday" imply a count
    if re.search(r"\bweekdays?\b", text):
        return 5, "WEEK"
    if re.search(r"\bweekends?\b", text):
        return 2, "WEEK"

    span = re.search(
        r"\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\s*"
        r"(?:to|through|\-|\u2013)\s*"
        r"(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b",
        text,
    )
    if span:
        start = WEEKDAY_WORDS[span.group(1)]
        end = WEEKDAY_WORDS[span.group(2)]
        return (int(end) - int(start)) % 7 + 1, "WEEK"

    named = _parse_days(text)
    if named:
        return len(named), "WEEK"
    return None, "WEEK"


def _parse_days(text: str) -> tuple[Weekday, ...]:
    if re.search(r"\bevery ?day|everyday|daily\b", text):
        return ()
    if re.search(r"\bweekdays?\b", text):
        return WEEKDAYS
    if re.search(r"\bweekends?\b", text):
        return WEEKEND

    span = re.search(
        r"\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\s*"
        r"(?:to|through|\-|\u2013)\s*"
        r"(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b",
        text,
    )
    if span:
        start = int(WEEKDAY_WORDS[span.group(1)])
        end = int(WEEKDAY_WORDS[span.group(2)])
        length = (end - start) % 7 + 1
        return tuple(Weekday((start + offset) % 7) for offset in range(length))

    found: list[Weekday] = []
    for match in re.finditer(r"\b([a-z]{3,9})\b", text):
        day = WEEKDAY_WORDS.get(match.group(1))
        if day is not None and day not in found:
            found.append(day)
    return tuple(found)


def _parse_duration(text: str) -> int | None:
    match = re.search(r"\b(\d+)\s*(?:min|mins|minute|minutes)\b", text)
    if match:
        return int(match.group(1))
    match = re.search(r"\b(\d+(?:\.\d+)?)\s*(?:h|hr|hrs|hour|hours)\b", text)
    if match:
        return int(float(match.group(1)) * 60)
    if re.search(r"\bhalf an hour\b", text):
        return 30
    if re.search(r"\ban hour\b", text):
        return 60
    return None


def _parse_clock_time(text: str) -> int | None:
    match = re.search(r"\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b", text)
    if match:
        hour = int(match.group(1)) % 12
        minute = int(match.group(2) or 0)
        if match.group(3) == "pm":
            hour += 12
        return hour * 60 + minute
    match = re.search(r"\bat\s+(\d{1,2}):(\d{2})\b", text)
    if match:
        hour, minute = int(match.group(1)), int(match.group(2))
        if 0 <= hour < 24 and 0 <= minute < 60:
            return hour * 60 + minute
    return None


def _parse_time_of_day(text: str) -> TimeOfDay:
    for word, band in TIME_OF_DAY_WORDS.items():
        if re.search(rf"\b{word}s?\b", text):
            return band
    return TimeOfDay.ANY


def _derive_name(raw: str) -> str:
    text = _LEAD_FILLER.sub("", raw.strip())
    # Drop the scheduling clause, keeping the activity itself.
    text = re.split(
        r"\b(every|each|twice|once|thrice|daily|weekly|monthly|\d+\s*(?:x|times?))\b",
        text,
        maxsplit=1,
        flags=re.IGNORECASE,
    )[0]
    text = re.sub(
        r"\s*\b(?:a|per|every|each|next|this)\s+(?:day|week|month|year)\b.*$",
        "", text, flags=re.IGNORECASE,
    )
    text = re.sub(r"\bfor\s+\d+.*$", "", text, flags=re.IGNORECASE)
    text = re.sub(r"\bat\s+\d.*$", "", text, flags=re.IGNORECASE)
    text = text.strip(" ,.;:-")
    if not text:
        return raw.strip(" ,.;:-")[:60] or "Untitled habit"
    return text[0].upper() + text[1:]


# --------------------------------------------------------------------------
# Entry point
# --------------------------------------------------------------------------

def interpret(raw_text: str) -> Interpretation:
    text = raw_text.lower().strip()

    occurrences, period = _parse_frequency(text)
    days = _parse_days(text)
    duration = _parse_duration(text)
    start_minute = _parse_clock_time(text)
    band = _parse_time_of_day(text)

    if start_minute is not None and band is TimeOfDay.ANY:
        if start_minute < 12 * 60:
            band = TimeOfDay.MORNING
        elif start_minute < 17 * 60:
            band = TimeOfDay.AFTERNOON
        elif start_minute < 21 * 60:
            band = TimeOfDay.EVENING
        else:
            band = TimeOfDay.NIGHT

    interpretation = Interpretation(
        name=_derive_name(raw_text),
        raw_text=raw_text,
        occurrences=occurrences,
        period=period,
        duration_minutes=duration,
        preferred_days=days,
        preferred_time_of_day=band,
        preferred_start_minute=start_minute,
    )

    # Confidence is the share of the fields we actually resolved. It is a
    # transparency signal for the UI, not a probability.
    resolved = sum(
        1 for value in (occurrences, duration, start_minute or days or None) if value
    )
    interpretation.confidence = round(resolved / 3, 2)

    # Only ask about what is genuinely missing (spec section 22).
    if occurrences is None:
        interpretation.missing.append("frequency")
        interpretation.questions.append("How often would you like to do this?")
    if duration is None:
        interpretation.missing.append("duration")
        interpretation.questions.append("How long should each session be?")
    if band is TimeOfDay.ANY and start_minute is None:
        interpretation.missing.append("time_of_day")
        interpretation.questions.append(
            "Do you prefer morning, afternoon, or evening?"
        )

    return interpretation
