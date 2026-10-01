from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session
from ..database import get_db
from ..deps import get_current_patient, get_owned_journey
from ..models import Patient, ReminderState, utcnow
from ..reminders import reminder_items

router = APIRouter(tags=['reminders'])

class ReminderUpdate(BaseModel):
    model_config = ConfigDict(extra='forbid')
    reminder_id: str
    read: bool = True
    dismissed: bool = False

@router.get('/journey/{journey_id}/reminders')
def get_reminders(journey_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    journey = get_owned_journey(journey_id, patient, db)
    items = reminder_items(db, journey)
    return JSONResponse({'items':items,'unread':sum(not i['read'] and not i['dismissed'] for i in items)},headers={'Cache-Control':'no-store'})

@router.patch('/journey/{journey_id}/reminders')
def update_reminder(journey_id: str, body: ReminderUpdate, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    journey = get_owned_journey(journey_id, patient, db)
    if body.reminder_id not in {i['id'] for i in reminder_items(db,journey)}:
        raise HTTPException(status_code=404,detail='Reminder no longer active')
    state = db.get(ReminderState,(patient.id,body.reminder_id))
    if state is None:
        state = ReminderState(patient_id=patient.id,reminder_key=body.reminder_id); db.add(state)
    state.read = body.read; state.dismissed = body.dismissed; state.updated_at = utcnow()
    db.commit()
    return {'saved':True}
