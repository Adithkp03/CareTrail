"""Deterministic, owner-scoped reminders. No diagnosis or delivery claims."""
from datetime import date, timedelta
from .engine import gestational_age_days
from .flags import compute_flags
from .models import ReminderState
from .template_loader import load_template


def reminder_items(db, journey, today=None):
    today = today or date.today()
    template = load_template(journey.template_id, journey.template_version)
    definitions = {m['key']: m for m in template['milestones']}
    ga = gestational_age_days(journey.lmp, journey.edd, today)
    items = []
    for m in journey.milestones:
        if m.completed_at:
            continue
        window = definitions.get(m.key, {}).get('window', [m.window_start_weeks, m.window_end_weeks])
        kind = None
        if m.scheduled_date:
            if m.scheduled_date < today:
                kind = 'missed_appointment'
            elif m.scheduled_date <= today + timedelta(days=3):
                kind = 'appointment'
            # Future bookings replace overdue-window reminders.
        elif ga / 7 > window[1]:
            kind = 'overdue'
        if kind:
            anchor = m.scheduled_date.isoformat() if m.scheduled_date else str(window[1])
            key = f'{journey.id}:{m.id}:{kind}:{anchor}'
            message = ('Your booked appointment is today or within the next 3 days.' if kind == 'appointment' else
                       'The booked date has passed. If you attended, mark this complete; otherwise contact your clinic to reschedule.' if kind == 'missed_appointment' else
                       'This care window has passed without a completion recorded. Contact your clinic to review the next step.')
            items.append(dict(id=key, kind=kind, title=m.title, message=message, scheduled_date=m.scheduled_date.isoformat() if m.scheduled_date else None, link=f'/milestone?id={m.id}'))
    for flag in compute_flags(db, journey.id, template):
        if not flag.get('signed_off'):
            items.append(dict(id=f'{journey.id}:observation:{flag["observation_id"]}', kind='review', title=f'{flag["label"]}: clinician review needed', message=flag['message'], scheduled_date=None, link='/doctor'))
    state = {s.reminder_key:s for s in db.query(ReminderState).filter(ReminderState.patient_id == journey.patient_id).all()}
    for item in items:
        saved = state.get(item['id'])
        item.update(read=bool(saved and saved.read), dismissed=bool(saved and saved.dismissed))
    priority = {'review':0, 'missed_appointment':1, 'overdue':2, 'appointment':3}
    return sorted(items, key=lambda x: (priority[x['kind']],x['scheduled_date'] or '',x['id']))
