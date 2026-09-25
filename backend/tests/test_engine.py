from datetime import date, timedelta

import pytest

from app import engine as e

TODAY = date(2026, 9, 24)


def test_ga_from_lmp():
    lmp = TODAY - timedelta(days=154)
    assert e.gestational_age_days(lmp=lmp, edd=None, on_date=TODAY) == 154


def test_ga_from_edd():
    edd = TODAY + timedelta(days=126)  # 280 - 126 = 154
    assert e.gestational_age_days(lmp=None, edd=edd, on_date=TODAY) == 154


def test_ga_requires_a_date():
    with pytest.raises(ValueError):
        e.gestational_age_days(lmp=None, edd=None, on_date=TODAY)


def test_edd_from_lmp_is_280_days():
    lmp = date(2026, 4, 23)
    assert e.edd_from_lmp(lmp) == lmp + timedelta(days=280)


GA_22W = 154


def test_status_done():
    assert e.milestone_status(GA_22W, 6, 10, completed_at=date(2026, 6, 1), scheduled_date=None, today=TODAY) == ("done", False)


def test_status_now_inside_window():
    assert e.milestone_status(GA_22W, 18, 24, completed_at=None, scheduled_date=None, today=TODAY) == ("now", False)


def test_status_overdue_is_now_with_flag():
    assert e.milestone_status(GA_22W, 6, 12, completed_at=None, scheduled_date=None, today=TODAY) == ("now", True)


def test_status_upcoming_when_scheduled_in_future():
    future = TODAY + timedelta(days=4)
    assert e.milestone_status(GA_22W, 18, 23, completed_at=None, scheduled_date=future, today=TODAY) == ("upcoming", False)


def test_status_next_before_window():
    assert e.milestone_status(GA_22W, 24, 28, completed_at=None, scheduled_date=None, today=TODAY) == ("next", False)


def test_scheduled_past_date_falls_back_to_window():
    past = TODAY - timedelta(days=2)
    assert e.milestone_status(GA_22W, 18, 24, completed_at=None, scheduled_date=past, today=TODAY) == ("now", False)


def test_nt_window_is_11_weeks_through_13_plus_6():
    from app.template_loader import load_template
    nt = next(m for m in load_template()["milestones"] if m["key"] == "nt_scan")
    start, end = nt["window"]
    assert e.milestone_status(77, start, end, None, None, TODAY) == ("now", False)
    assert e.milestone_status(97, start, end, None, None, TODAY) == ("now", False)
    assert e.milestone_status(98, start, end, None, None, TODAY) == ("now", True)


def test_tft_threshold_still_pending_and_danger_signs_present():
    template = __import__("app.template_loader", fromlist=["load_template"]).load_template()
    assert "tft" not in template["thresholds"]
    assert "pregnancy-specific" in template["tft_threshold_pending"]
    for sign in ("Severe nausea or vomiting", "Right-sided upper abdominal pain", "Reduced urine", "Sudden swelling of the whole body"):
        assert sign in template["danger_signs"]
