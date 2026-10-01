"""Team-supplied pregnancy TSH fallback rules, never model-decided."""
from datetime import date, timedelta
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from app.database import Base
from app.models import Patient, Journey, Observation
from app.flags import compute_flags
from app.extraction import normalize_value, offline_extract
from app.template_loader import load_template

@pytest.mark.parametrize('days,value,flagged', [
    (97,0.1,False),(97,4.0,False),(97,4.01,True),(97,0.09,True),
    (98,4.0,False),(195,0.1,False),(196,0.29,True),(196,0.3,False),
    (196,4.5,False),(196,4.51,True)])
def test_tsh_trimester_boundaries(days,value,flagged):
    engine=create_engine('sqlite://')
    Base.metadata.create_all(engine)
    with Session(engine) as db:
        report_day=date.today()-timedelta(days=100)
        patient=Patient(name='Synthetic',phone='0000',password_hash='unused'); db.add(patient); db.flush()
        # Today's GA is different. Rules must use the report's gestational age.
        journey=Journey(patient_id=patient.id,lmp=report_day-timedelta(days=days)); db.add(journey); db.flush()
        db.add(Observation(journey_id=journey.id,code='tsh',value=value,unit='mU/L',observed_on=report_day)); db.flush()
        flags=compute_flags(db,journey.id,load_template('antenatal','v1'))
        assert bool(flags) is flagged
        if flags:
            assert flags[0]['trimester']==(1 if days<98 else 2 if days<196 else 3)
            assert flags[0]['severity']=='review'

@pytest.mark.parametrize('unit', ['mU/L','mIU/L','uIU/mL','µIU/mL','μIU/mL'])
def test_tsh_explicit_equivalent_units(unit):
    assert normalize_value('tsh',4.2,unit)==(4.2,'mU/L')

@pytest.mark.parametrize('unit', ['', 'mg/dL','IU/L'])
def test_tsh_unknown_units_rejected(unit):
    with pytest.raises(ValueError): normalize_value('tsh',4.2,unit)

def test_tsh_offline_extract_requires_units():
    template=load_template('antenatal','v1')
    values=offline_extract('Date: 2026-09-01\nTSH: 4.7 mIU/L',template)
    assert len(values)==1
    assert values[0]['code']=='tsh' and values[0]['unit']=='mU/L'
    assert values[0]['observed_on']=='2026-09-01'
    assert offline_extract('TSH: 4.7',template)==[]
