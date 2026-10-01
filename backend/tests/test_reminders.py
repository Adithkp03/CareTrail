from datetime import date,timedelta
from .conftest import signup,auth

def setup_journey(client):
    s=signup(client); h=auth(s['token'])
    j=client.post('/journeys',headers=h,json={'lmp':(date.today()-timedelta(weeks=22)).isoformat()}).json()
    return h,j

def items(client,h,j):
    r=client.get(f'/journey/{j["journey_id"]}/reminders',headers=h)
    assert r.status_code==200
    assert r.headers['cache-control']=='no-store'
    return r.json()['items']

def test_owner_only(client):
    h,j=setup_journey(client)
    assert items(client,h,j)
    other=auth(signup(client,phone='000002')['token'])
    path=f'/journey/{j["journey_id"]}/reminders'
    assert client.get(path,headers=other).status_code==404
    assert client.patch(path,headers=other,json={'reminder_id':'anything'}).status_code==404
    assert client.get(path).status_code==401

def test_appointment_window_reschedule_and_completion(client):
    h,j=setup_journey(client); m=j['milestones'][0]; mid=m['id']
    for offset in (0,3,4):
        r=client.post(f'/milestones/{mid}/schedule',headers=h,json={'scheduled_date':(date.today()+timedelta(days=offset)).isoformat()})
        assert r.status_code==200,r.text
        found=[i for i in items(client,h,j) if f':{mid}:' in i['id']]
        assert bool(found)==(offset<=3)
        if found: assert found[0]['kind']=='appointment'
    client.post(f'/milestones/{mid}/complete',headers=h,json={})
    assert not [i for i in items(client,h,j) if f':{mid}:' in i['id']]

def test_missed_date_priority_and_persistent_state(client):
    h,j=setup_journey(client); mid=j['milestones'][0]['id']
    client.post(f'/milestones/{mid}/schedule',headers=h,json={'scheduled_date':(date.today()-timedelta(days=1)).isoformat()})
    found=next(i for i in items(client,h,j) if f':{mid}:' in i['id'])
    assert found['kind']=='missed_appointment'
    path=f'/journey/{j["journey_id"]}/reminders'
    assert client.patch(path,headers=h,json={'reminder_id':found['id'],'read':True,'dismissed':True}).status_code==200
    current=next(i for i in items(client,h,j) if i['id']==found['id'])
    assert current['read'] and current['dismissed']
    client.post(f'/milestones/{mid}/schedule',headers=h,json={'scheduled_date':date.today().isoformat()})
    replacement=next(i for i in items(client,h,j) if f':{mid}:' in i['id'])
    assert not replacement['dismissed'] and replacement['id']!=found['id']
    assert client.patch(path,headers=h,json={'reminder_id':found['id']}).status_code==404

def test_abnormal_result_creates_review_and_new_normal_clears(client):
    h,j=setup_journey(client)
    def upload(value,day):
        doc=client.post(f'/journeys/{j["journey_id"]}/documents/upload',headers=h,files={'file':('lab.txt',b'lab','text/plain')}).json()
        client.post(f'/documents/{doc["document_id"]}/extract',headers=h)
        r=client.post(f'/documents/{doc["document_id"]}/confirm',headers=h,json={'values':[{'code':'tsh','value':value,'unit':'mU/L','observed_on':day.isoformat()}],'confirmed':True})
        assert r.status_code==200,r.text
    upload(4.7,date.today()-timedelta(days=1))
    review=[i for i in items(client,h,j) if i['kind']=='review']
    assert len(review)==1 and '4.7' not in review[0]['title']
    upload(2.0,date.today())
    assert not [i for i in items(client,h,j) if i['kind']=='review']

def test_review_alert_clears_on_real_clinician_signoff(client):
    from app.database import get_db
    from app.models import Observation,JourneyClinicianGrant
    from .test_clinicians import provision
    h,j=setup_journey(client)
    db=next(client.app.dependency_overrides[get_db]())
    obs=Observation(journey_id=j['journey_id'],code='hb',value=9.0,unit='g/dL',observed_on=date.today());db.add(obs);db.commit()
    c=provision(client)
    client.post(f'/journeys/{j["journey_id"]}/clinicians',headers=h,json={'clinician_email':c.email})
    token=client.post('/clinician/login',json={'email':c.email,'password':'correct horse 123'}).json()['token']
    assert any(i['kind']=='review' for i in items(client,h,j))
    response=client.post('/clinician/signoffs',headers=auth(token),json={'journey_id':j['journey_id'],'observation_id':obs.id,'note':'Reviewed'})
    assert response.status_code==201,response.text
    assert not any(i['kind']=='review' for i in items(client,h,j))
