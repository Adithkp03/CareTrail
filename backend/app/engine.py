"""The journey engine: given a pregnancy (LMP or EDD) and a pathway template,
work out what is Done, Now and Next. Pure functions, no database access."""

from datetime import date, timedelta

GA_FULL_TERM_DAYS = 280  # 40 weeks

DONE = "done"
NOW = "now"
UPCOMING = "upcoming"
NEXT = "next"


def gestational_age_days(lmp: date | None, edd: date | None, on_date: date) -> int:
    """Gestational age in days. LMP wins when both are given."""
    if lmp is not None:
        return (on_date - lmp).days
    if edd is not None:
        return GA_FULL_TERM_DAYS - (edd - on_date).days
    raise ValueError("Either lmp or edd is required to compute gestational age")


def edd_from_lmp(lmp: date) -> date:
    return lmp + timedelta(days=GA_FULL_TERM_DAYS)


def milestone_status(
    ga_days: int,
    window_start_weeks: float,
    window_end_weeks: float,
    completed_at: date | None,
    scheduled_date: date | None,
    today: date,
) -> tuple[str, bool]:
    """Returns (status, overdue).

    done     - completed
    upcoming - has a future scheduled date (booked appointment/scan)
    now      - inside its window, or past it and still not done (overdue=True)
    next     - window has not opened yet
    """
    if completed_at is not None:
        return DONE, False
    if scheduled_date is not None and scheduled_date >= today:
        return UPCOMING, False
    ga_weeks = ga_days / 7.0
    if ga_weeks > window_end_weeks:
        return NOW, True
    if ga_weeks >= window_start_weeks:
        return NOW, False
    return NEXT, False


def build_milestone_rows(template: dict) -> list[dict]:
    """Flatten the template's milestone definitions into row dicts for the Milestone table."""
    rows = []
    for order, m in enumerate(template["milestones"]):
        start, end = m["window"]
        rows.append(
            {
                "key": m["key"],
                "title": m["title"],
                "type": m["type"],
                "window_start_weeks": float(start),
                "window_end_weeks": float(end),
                "required_tests": m.get("required_tests", []),
                "prep_notes": m.get("prep_notes", ""),
                "sort_order": order,
            }
        )
    return rows
