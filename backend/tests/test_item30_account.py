from tests.conftest import auth, signup


def test_export_and_delete_account(client):
    token = signup(client)["token"]
    other = signup(client, phone="9000000084")["token"]
    j = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token)).json()["journey_id"]
    j2 = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(other)).json()["journey_id"]
    up = client.post(f"/journeys/{j}/documents/upload", files={"file": ("r.txt", b"Hemoglobin 11.2 g/dL", "text/plain")}, headers=auth(token)).json()
    client.post(f"/documents/{up['document_id']}/extract", headers=auth(token))
    client.post(f"/documents/{up['document_id']}/confirm", json={"values": [{"code": "hb", "value": 11.2, "unit": "g/dL"}]}, headers=auth(token))
    r = client.get("/me/export", headers=auth(token))
    assert r.status_code == 200 and "attachment" in r.headers["content-disposition"]
    data = r.json()
    assert data["journeys"][0]["id"] == j and data["journeys"][0]["results"][0]["code"] == "hb"
    assert all(x["id"] != j2 for x in data["journeys"])
    assert client.get("/me/export").status_code == 401
    assert client.request("DELETE", "/me", json={"confirm": "no"}, headers=auth(token)).status_code == 422
    assert client.request("DELETE", "/me", json={"confirm": "DELETE"}, headers=auth(token)).json() == {"deleted": True}
    assert client.get("/auth/me", headers=auth(token)).status_code == 401
    assert client.get(f"/journey/{j2}", headers=auth(other)).status_code == 200


def test_demo_account_cannot_be_deleted(client):
    client.post("/demo/seed")
    tok = client.post("/auth/login", json={"phone": "9000000001", "password": "demo1234"}).json()["token"]
    assert client.request("DELETE", "/me", json={"confirm": "DELETE"}, headers=auth(tok)).status_code == 403
# re-trigger checks
