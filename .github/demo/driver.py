"""Drives the native app on the emulator and records each scene with screenrecord.
Best-effort per step: a failed step is logged with a UI dump and the run continues,
so every run yields usable artifacts. Synthetic data only."""
import json, os, re, subprocess, sys, time, urllib.request
import xml.etree.ElementTree as ET

OUT = "/tmp/demo-out"; os.makedirs(OUT, exist_ok=True)
PKG = "com.caretrail.native.test"
API = "http://localhost:8000"
QUESTION = "My haemoglobin came back low. Should I change my iron tablets before the next visit"
ANSWER = "Please do not change the dose yet. Take your iron after food and bring the original report to your visit. I will review it first."
NOTE = "Repeat haemoglobin in 4 weeks. Eat iron rich food daily and come earlier if you feel breathless or dizzy."
log = open(f"{OUT}/driver.log", "w")
def say(*a):
    s = " ".join(str(x) for x in a); print(s, flush=True); log.write(s + "\n"); log.flush()
def adb(*args, check=False):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check).stdout
def sh(cmd): return adb("shell", cmd)
n_dump = 0
def dump():
    global n_dump
    sh("uiautomator dump /sdcard/u.xml"); xml = sh("cat /sdcard/u.xml")
    n_dump += 1
    open(f"{OUT}/dump{n_dump:03d}.xml", "w").write(xml)
    try: return ET.fromstring(xml[xml.index("<"):])
    except Exception: return None
def nodes(root):
    for e in (root.iter("node") if root is not None else []):
        t = (e.get("text") or "") + "|" + (e.get("content-desc") or "")
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", e.get("bounds") or "")
        if m and t.strip("|"):
            x1, y1, x2, y2 = map(int, m.groups()); yield t.lower(), (x1 + x2) // 2, (y1 + y2) // 2, y2 - y1
def find(text, root=None):
    root = root or dump()
    for t, x, y, h in nodes(root):
        if text.lower() in t: return x, y
def tap(text, scroll=True, tries=7, wait=1.5):
    for i in range(tries):
        p = find(text)
        if p:
            sh(f"input tap {p[0]} {p[1]}"); time.sleep(wait); say("tap ok:", text); return True
        if not scroll: break
        sh("input swipe 540 1700 540 800 400"); time.sleep(0.8)
    say("TAP FAILED:", text); shot(f"fail_{re.sub(r'[^a-z]+','_',text.lower())}"); return False
def type_into(label, text):
    if tap(label, wait=0.8):
        sh("input text '" + text.replace(" ", "%s") + "'"); time.sleep(0.8); sh("input keyevent 4"); time.sleep(0.8)
def shot(name):
    sh(f"screencap -p /sdcard/{name}.png"); adb("pull", f"/sdcard/{name}.png", f"{OUT}/{name}.png")
class Rec:
    def __init__(s, name): s.name = name
    def __enter__(s):
        s.p = subprocess.Popen(["adb", "shell", "screenrecord", "--size", "720x1600", "--bit-rate", "6000000", "--time-limit", "170", f"/sdcard/{s.name}.mp4"])
        time.sleep(2); return s
    def __exit__(s, *a):
        time.sleep(1.5); sh("pkill -2 screenrecord"); time.sleep(3); s.p.wait(timeout=20)
        adb("pull", f"/sdcard/{s.name}.mp4", f"{OUT}/{s.name}.mp4"); say("recorded", s.name)
def call(method, path, body=None, headers=None):
    req = urllib.request.Request(API + path, method=method, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json", **(headers or {})})
    return json.load(urllib.request.urlopen(req, timeout=30))

adb("install", "-r", "android-native/app/build/outputs/apk/debug/app-debug.apk")
for perm in ["POST_NOTIFICATIONS", "ACCESS_FINE_LOCATION"]: sh(f"pm grant {PKG} android.permission.{perm}")
sh("settings put global window_animation_scale 0")

with Rec("scene1_login"):
    sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(7); shot("welcome")
    if not find("try the demo"): tap("log in", scroll=False, wait=2)
    tap("try the demo", wait=8); shot("home")

with Rec("scene2_link_doctor"):
    tap("doctor view", wait=3)
    type_into("doctor account email", "dr.demo@example.org")
    tap("give doctor access", wait=4); shot("doctor_linked")
    sh("input keyevent 4"); time.sleep(2)

with Rec("scene3_ask_doctor"):
    tap("ask your doctor", wait=3)
    type_into("type your question", QUESTION)
    tap("send to", wait=4); shot("question_sent")
    sh("input keyevent 4"); time.sleep(1)

# Doctor acts on the web portal (recorded separately); same API the web page calls.
try:
    pt = call("POST", "/auth/login", {"phone": "9000000001", "password": "demo1234"})["token"]
    jid = call("GET", "/journeys", headers={"Authorization": "Bearer " + pt})["journeys"][0]["journey_id"]
    ct = call("POST", "/clinician/login", {"email": "dr.demo@example.org", "password": "Doctor-Demo-2026"})["token"]
    H = {"Authorization": "Bearer " + ct}
    qs = call("GET", f"/clinician/journeys/{jid}/questions", headers=H)["items"]
    call("POST", f"/clinician/questions/{qs[0]['id']}/answer", {"answer": ANSWER}, H)
    call("POST", f"/clinician/journeys/{jid}/instructions", {"note": NOTE}, H)
    say("doctor reply + instruction posted via API")
except Exception as e: say("DOCTOR API STEP FAILED:", e)

with Rec("scene4_doctor_reply_seen"):
    tap("ask your doctor", wait=3); shot("answer_seen"); time.sleep(2)
    # Home was composed before the doctor replied, so relaunch for fresh data
    # before acknowledging the new instruction (button label: "Acknowledge").
    sh(f"am force-stop {PKG}"); time.sleep(1)
    sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(8); shot("home_reloaded")
    tap("acknowledge", wait=3); shot("instruction_ack")

with Rec("scene5_referral"):
    tap("referral transfer", wait=3); shot("ref_initial")
    tap("prepare my referral", wait=3); sh("input swipe 540 1700 540 1100 400"); time.sleep(1.5); shot("ref_prepared")
    tap("export acknowledged", wait=1.5)
    tap("save unencrypted referral json", wait=4); shot("ref_picker_save")
    tap("save", scroll=False, wait=3); shot("ref_saved")
    tap("preview a received referral", wait=4); shot("ref_picker_open")
    tap("caretrail-referral", scroll=False, wait=4); shot("ref_received")
    time.sleep(2)
say("done")
