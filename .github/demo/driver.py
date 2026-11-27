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
def dump(_depth=0):
    global n_dump
    sh("uiautomator dump /sdcard/u.xml"); xml = sh("cat /sdcard/u.xml")
    if ("isn't responding" in xml or "is not responding" in xml) and _depth < 4:
        say("ANR dialog detected, tapping Wait")
        m = re.search(r'text="Wait"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
        if m:
            x1, y1, x2, y2 = map(int, m.groups()); sh(f"input tap {(x1+x2)//2} {(y1+y2)//2}")
        else: sh("input keyevent 4")
        time.sleep(3); return dump(_depth + 1)
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
SW, SH = 720, 1600
def screen_size():
    global SW, SH
    m = re.search(r"(\d+)x(\d+)", sh("wm size"))
    if m: SW, SH = int(m.group(1)), int(m.group(2))
    say("screen", SW, SH)
def find(text, root=None, visible=True):
    """First node containing text. With visible=True, nodes whose centre is outside the
    scrollable viewport (under the header or the bottom bar, or off-screen) are ignored,
    so we scroll instead of blind-tapping a clipped node."""
    root = root or dump()
    for t, x, y, h in nodes(root):
        if text.lower() in t:
            if visible and not (0.07 * SH < y < 0.86 * SH and 0 < x < SW): continue
            return x, y
def swipe_down_content():  # finger moves up: reveals content further down the page
    sh(f"input swipe {SW//2} {int(SH*0.72)} {SW//2} {int(SH*0.32)} 400"); time.sleep(0.8)
def to_top():
    for _ in range(4):
        sh(f"input swipe {SW//2} {int(SH*0.30)} {SW//2} {int(SH*0.75)} 300"); time.sleep(0.5)
def tap(text, scroll=True, tries=9, wait=1.5, visible=True):
    went_top = False
    for i in range(tries):
        p = find(text, visible=visible)
        if p:
            sh(f"input tap {p[0]} {p[1]}"); time.sleep(wait); say("tap ok:", text, p); return True
        if not scroll: break
        if not went_top: to_top(); went_top = True; continue  # page may already be scrolled past the target
        swipe_down_content()
    say("TAP FAILED:", text); shot(f"fail_{re.sub(r'[^a-z]+','_',text.lower())}"); return False
def type_into(label, text):
    if tap(label, wait=0.8):
        sh("input text '" + text.replace(" ", "%s") + "'"); time.sleep(0.8); sh("input keyevent 4"); time.sleep(0.8)
def shot(name):
    sh(f"screencap -p /sdcard/{name}.png"); adb("pull", f"/sdcard/{name}.png", f"{OUT}/{name}.png")
RESULTS = {}
def seen(*texts, wait=2.0):
    """True if ANY of the texts is currently on screen (case-insensitive)."""
    time.sleep(wait)
    for _ in range(2):
        root = dump(); blob = " ".join(t for t, *_ in nodes(root))
        if any(x.lower() in blob for x in texts): return True
        time.sleep(1.5)
    return False
def check(scene, name, ok):
    RESULTS.setdefault(scene, {})[name] = bool(ok); say("CHECK", scene, name, "PASS" if ok else "FAIL")
def ensure_english():
    # Demo persona defaults to Malayalam UI; switch via the header language pill.
    if find("മലയാളം"):
        tap("മലയാളം", scroll=False, wait=2)
        tap("english", scroll=False, wait=3)

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

adb("wait-for-device")
screen_size()
for _ in range(120):
    if sh("getprop sys.boot_completed").strip() == "1": break
    time.sleep(2)
say("boot completed; settling"); time.sleep(40)
sh("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS"); sh("input keyevent 3"); time.sleep(3)
for _ in range(3): dump()  # clears any launcher ANR before the first scene
adb("install", "-r", "android-native/app/build/outputs/apk/debug/app-debug.apk")
for perm in ["POST_NOTIFICATIONS", "ACCESS_FINE_LOCATION"]: sh(f"pm grant {PKG} android.permission.{perm}")
sh("settings put global window_animation_scale 0")

with Rec("scene1_login"):
    sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(7); shot("welcome")
    if not find("try the demo"): tap("log in", scroll=False, wait=2)
    check("scene1", "login_screen", seen("try the demo"))
    tap("try the demo", wait=8)
    if find("skip for now"): tap("skip for now", wait=4)  # reminders-setup interstitial after fresh demo login
    ensure_english()
    shot("home")
    check("scene1", "home_after_login", seen("your pregnancy journey"))  # hero title, English-only assertion

with Rec("scene2_link_doctor"):
    tap("doctor view", wait=3)
    type_into("doctor account email", "dr.demo@example.org")
    tap("give doctor access", wait=4); shot("doctor_linked")
    check("scene2", "grant_confirmed", seen("doctor now has access"))
    sh("input keyevent 4"); time.sleep(2)

with Rec("scene3_ask_doctor"):
    tap("ask your doctor", wait=3)
    check("scene3", "ask_screen_open", seen("send a question", "no doctor linked yet"))
    type_into("type your question", QUESTION)
    tap("send to", wait=4); shot("question_sent")
    check("scene3", "question_listed", seen("your questions", "waiting for your doctor"))
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
    sh(f"am force-stop {PKG}"); time.sleep(2); sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(8)
    if find("skip for now"): tap("skip for now", wait=4)  # same interstitial if setup state did not persist
    ensure_english()
    tap("ask your doctor", wait=3); shot("answer_seen")
    check("scene4", "doctor_reply_visible", seen("please do not change the dose"))
    time.sleep(2); sh("input keyevent 4"); time.sleep(2)
    check("scene4", "instruction_visible", seen("repeat haemoglobin"))  # exact seeded instruction text, not the section header
    tap("acknowledge", wait=3); shot("instruction_ack")
    check("scene4", "acknowledged", seen("you acknowledged"))

with Rec("scene5_referral"):
    tap("referral transfer", wait=3); shot("ref_initial")
    check("scene5", "referral_screen", seen("referral transfer", "patient-carried"))
    tap("prepare my referral", wait=3); sh(f"input swipe {SW//2} {int(SH*0.75)} {SW//2} {int(SH*0.45)} 400"); time.sleep(1.5); shot("ref_prepared")
    check("scene5", "summary_shown", seen("review before export", "caretrail referral summary"))
    tap("i have reviewed and want to export", wait=1.5)
    tap("save unencrypted referral json", wait=4); shot("ref_picker_save")
    tap("save", scroll=False, wait=3); shot("ref_saved")
    tap("preview a received referral", wait=4); shot("ref_picker_open")
    tap("caretrail-referral", scroll=False, wait=4); shot("ref_received")
json.dump(RESULTS, open(f"{OUT}/results.json", "w"), indent=1)
bad = [(sc, k) for sc, d in RESULTS.items() for k, v in d.items() if not v]
for sc in {b[0] for b in bad}:
    for f in os.listdir(OUT):
        if f.startswith({"scene1":"scene1_","scene2":"scene2_","scene3":"scene3_","scene4":"scene4_","scene5":"scene5_"}[sc]) and f.endswith(".mp4"):
            os.rename(f"{OUT}/{f}", f"{OUT}/FAILED_{f}")
say("FAILED CHECKS:", bad if bad else "none"); say("done")
sys.exit(1 if bad else 0)

