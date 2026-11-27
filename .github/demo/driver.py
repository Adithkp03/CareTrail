"""Drives the native app on the emulator and records each scene with screenrecord.
Full-journey demo coverage: welcome -> signup -> demo login -> every feature.
Best-effort per step: a failed step is logged with a UI dump and the run continues,
so every run yields usable artifacts. Synthetic data only, isolated local backend."""
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
    root = root or dump()
    for t, x, y, h in nodes(root):
        if text.lower() in t:
            if visible and not (0.07 * SH < y < 0.86 * SH and 0 < x < SW): continue
            return x, y
def swipe_down_content():
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
        if not went_top: to_top(); went_top = True; continue
        swipe_down_content()
    say("TAP FAILED:", text); shot(f"fail_{re.sub(r'[^a-z]+','_',text.lower())}"); return False
def type_into(label, text):
    if tap(label, wait=0.8):
        sh("input text '" + text.replace(" ", "%s") + "'"); time.sleep(0.8); sh("input keyevent 4"); time.sleep(0.8)
def shot(name):
    sh(f"screencap -p /sdcard/{name}.png"); adb("pull", f"/sdcard/{name}.png", f"{OUT}/{name}.png")
RESULTS = {}
def seen(*texts, wait=2.0):
    time.sleep(wait)
    for _ in range(2):
        root = dump(); blob = " ".join(t for t, *_ in nodes(root))
        if any(x.lower() in blob for x in texts): return True
        time.sleep(1.5)
    return False
def check(scene, name, ok):
    RESULTS.setdefault(scene, {})[name] = bool(ok); say("CHECK", scene, name, "PASS" if ok else "FAIL")
def back_to_home():
    for _ in range(4):
        if find("your pregnancy journey", visible=False) or find("quick access", visible=False):
            return True
        sh("input keyevent 4"); time.sleep(1.5)
    tap("home", scroll=False, wait=2)
    return seen("your pregnancy journey", "quick access", wait=2)
def ensure_english():
    if find("മലയാളം"):
        tap("മലയാളം", scroll=False, wait=2)
        tap("english", scroll=False, wait=3)

def pick_day_in_date_dialog():
    # Material date picker: tap any clickable day cell (desc like 'Friday, November 28, 2026')
    root = dump(); picked = False
    for e in (root.iter("node") if root is not None else []):
        d = e.get("content-desc") or ""
        if re.search(r"\w+,\s+\w+\s+\d+,\s+20\d\d", d) and e.get("clickable") == "true":
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", e.get("bounds") or "")
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                sh(f"input tap {(x1+x2)//2} {(y1+y2)//2}"); picked = True; break
    say("date picked:", picked); time.sleep(1.5)

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
for _ in range(3): dump()
adb("install", "-r", "android-native/app/build/outputs/apk/debug/app-debug.apk")
for perm in ["POST_NOTIFICATIONS", "ACCESS_FINE_LOCATION"]: sh(f"pm grant {PKG} android.permission.{perm}")
sh("settings put global window_animation_scale 0")
# sample report for the upload scene
adb("push", "backend/app/sample_reports/cbc_malayalam_low_hb.txt", "/sdcard/Download/cbc_malayalam_low_hb.txt")

# ---------- s01 welcome + language picker ----------
with Rec("s01_welcome"):
    sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(7); shot("welcome")
    check("s01", "welcome_screen", seen("caretrail"))
    tap("english", scroll=False, wait=2)          # language dropdown on welcome
    shot("lang_options")
    check("s01", "languages_listed", seen("മലയാളം", "हिन्दी"))
    tap("english", scroll=False, wait=2)          # stay in English
    time.sleep(1)

# ---------- s02 signup (account creation) ----------
with Rec("s02_signup"):
    tap("sign up", scroll=False, wait=3)
    check("s02", "signup_form", seen("your name"))
    type_into("your name", "Demo User")
    type_into("phone number", "9000000099")
    type_into("password", "synthetic123")
    tap("consent", scroll=True, wait=1.5)         # toggles the checkbox row
    shot("signup_filled")
    tap("sign up", scroll=True, wait=6)
    check("s02", "journey_setup", seen("first day of your last period"))
    tap("first day of your last period", scroll=False, wait=2)
    pick_day_in_date_dialog()
    tap("ok", scroll=False, wait=2)
    tap("start my journey", scroll=False, wait=6)
    check("s02", "new_journey_home", seen("your pregnancy journey", "quick access"))
    shot("new_account_home")
    # log out of the fresh account, then continue with the seeded demo
    tap("profile", scroll=False, wait=3)
    tap("remove local records and log out", scroll=True, wait=3)   # opens the warning dialog
    tap("remove local records and log out", scroll=False, tries=2, wait=5)  # dialog confirm button
    time.sleep(3)

# ---------- s03 demo login (rich seeded journey) ----------
with Rec("s03_demo_login"):
    if not find("try the demo"): tap("log in", scroll=False, wait=2)
    check("s03", "login_screen", seen("try the demo"))
    tap("try the demo", wait=9)
    if find("skip for now"): tap("skip for now", wait=4)
    elif find("skip"): tap("skip", scroll=False, wait=4)
    ensure_english()
    check("s03", "home_after_login", seen("your pregnancy journey"))
    shot("home")

# ---------- s04 home hero ----------
with Rec("s04_home"):
    back_to_home(); to_top(); time.sleep(1)
    check("s04", "hero", seen("you are here"))
    swipe_down_content(); time.sleep(1); shot("home_mid")
    check("s04", "quick_access", seen("quick access"))
    swipe_down_content(); time.sleep(1.5); to_top()

# ---------- s05 timeline + milestone ----------
with Rec("s05_timeline"):
    back_to_home()
    tap("timeline", scroll=False, wait=3)         # bottom tab
    check("s05", "timeline", seen("pregnancy timeline", "show all care steps"))
    tap("show all care steps", scroll=False, wait=2)
    swipe_down_content(); time.sleep(1)
    # open a milestone card that is not a button label: tap a trimester section item
    tap("anomaly", scroll=True, tries=5, wait=3)  # anomaly scan milestone if listed
    shot("timeline")
    check("s05", "milestone_open", seen("why now?", "planned for this stage"))
    tap("why now?", scroll=False, wait=2); shot("milestone_whynow")
    time.sleep(1); back_to_home()

# ---------- s06 report upload + extraction confirm ----------
with Rec("s06_upload"):
    back_to_home()
    tap("upload report", scroll=True, wait=3)
    check("s06", "upload_screen", seen("take a photo of your report or choose a file"))
    tap("choose file", scroll=False, wait=4)
    shot("picker")
    tap("cbc_malayalam_low_hb", scroll=False, tries=2, wait=4)
    check("s06", "reading", seen("reading your report", "read on this device", "read by ai"), )
    if not seen("i checked the original report", wait=12):
        say("extraction slow or failed"); shot("extract_state")
    swipe_down_content()
    tap("i checked the original report", scroll=True, wait=1.5)
    shot("extract_proposed")
    tap("save confirmed values", scroll=True, wait=4)
    check("s06", "saved", seen("values saved to your timeline"))
    shot("upload_saved"); time.sleep(1); back_to_home()

# ---------- s07 attention flag on home ----------
with Rec("s07_attention"):
    back_to_home(); to_top(); time.sleep(1)
    swipe_down_content(); time.sleep(1)
    check("s07", "attention_card", seen("items needing attention", "needs doctor review", "review pending"))
    shot("attention"); time.sleep(2); to_top()

# ---------- s08 help assistant ----------
with Rec("s08_assistant"):
    back_to_home()
    tap("help assistant", scroll=True, wait=3)
    if find("i understand"): tap("i understand", scroll=False, wait=2)
    check("s08", "assistant_open", seen("hello, how can i help you", "ask anything"))
    type_into("ask anything", "When is my next scan due")
    time.sleep(4); shot("assistant_answer")
    check("s08", "answer", seen("scan", "curated", "appointment"))
    back_to_home()

# ---------- s09 link doctor + ask a question ----------
with Rec("s09_doctor"):
    back_to_home()
    tap("doctor view", scroll=True, wait=3)
    type_into("doctor account email", "dr.demo@example.org")
    tap("give doctor access", scroll=True, wait=4); shot("doctor_linked")
    check("s09", "grant_confirmed", seen("doctor now has access", "linked"))
    sh("input keyevent 4"); time.sleep(2)
    back_to_home()
    tap("ask your doctor", scroll=True, wait=3)
    type_into("type your question", QUESTION)
    tap("send to", scroll=True, wait=4); shot("question_sent")
    check("s09", "question_listed", seen("your questions", "waiting for your doctor"))
    back_to_home()

# Doctor acts via API (web portal scene is recorded separately against the same flow)
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

# ---------- s10 patient sees reply + instruction ----------
with Rec("s10_reply"):
    sh(f"am force-stop {PKG}"); time.sleep(2); sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(8)
    if find("skip for now"): tap("skip for now", wait=4)
    elif find("skip"): tap("skip", scroll=False, wait=4)
    ensure_english()
    tap("ask your doctor", scroll=True, wait=3); shot("answer_seen")
    check("s10", "doctor_reply_visible", seen("please do not change the dose"))
    time.sleep(2); back_to_home()
    tap("reminders", scroll=False, wait=3)
    check("s10", "instruction_visible", seen("repeat haemoglobin"))
    tap("acknowledge", scroll=True, wait=3); shot("instruction_ack")
    check("s10", "acknowledged", seen("you acknowledged"))

# ---------- s11 reminders: medication + hydration ----------
with Rec("s11_reminders"):
    back_to_home(); tap("reminders", scroll=False, wait=3)
    check("s11", "reminders_screen", seen("care reminders", "medication and hydration"))
    tap("medication and hydration reminders", scroll=True, wait=3)
    shot("med_schedule")
    check("s11", "med_screen", seen("today's doses", "add daily time", "hydration reminders"))
    swipe_down_content(); time.sleep(1.5)
    check("s11", "hydration", seen("hydration reminders", "gentle nudges for water"))
    back_to_home()

# ---------- s12 movement counter ----------
with Rec("s12_movements"):
    back_to_home()
    tap("movements", scroll=True, wait=3)
    check("s12", "movement_screen", seen("baby movements", "movements noted today"))
    tap("+ 1", scroll=False, tries=3, wait=1); time.sleep(1)
    tap("+ 1", scroll=False, tries=3, wait=1); shot("movement_count")
    check("s12", "counted", seen("movements noted today"))
    back_to_home()

# ---------- s13 health profile intake ----------
with Rec("s13_intake"):
    back_to_home()
    tap("health profile", scroll=True, wait=3)
    check("s13", "intake_screen", seen("conditions, surgeries, allergies", "first-visit tests"))
    swipe_down_content(); time.sleep(1.5); shot("intake")
    back_to_home()

# ---------- s14 first-visit checklist ----------
with Rec("s14_registration"):
    back_to_home()
    tap("first-visit checklist", scroll=True, wait=3)
    check("s14", "checklist_screen", seen("hiv test", "first visit"))
    tap("hiv test - you", scroll=True, tries=5, wait=1.5)
    shot("checklist_tick")
    back_to_home()

# ---------- s15 Rh & ICT ----------
with Rec("s15_rh"):
    back_to_home()
    tap("rh & ict", scroll=True, wait=3)
    check("s15", "rh_screen", seen("rh", "ict"))
    swipe_down_content(); time.sleep(1.5); shot("rh")
    back_to_home()

# ---------- s16 danger signs ----------
with Rec("s16_danger"):
    back_to_home()
    tap("danger signs", scroll=True, wait=3)
    check("s16", "danger_screen", seen("danger-sign check", "call your doctor now if you have"))
    tap("severe headache", scroll=True, tries=5, wait=2)
    shot("danger_triage")
    check("s16", "triage", seen("contact your doctor now", "call 108"))
    back_to_home()

# ---------- s17 visit prep + care guidance ----------
with Rec("s17_prep"):
    back_to_home()
    tap("prepare for your next visit", scroll=True, wait=3)
    check("s17", "prep_screen", seen("questions worth asking", "visit"))
    swipe_down_content(); time.sleep(1.5); shot("prep")
    back_to_home()
    tap("care guidance", scroll=True, wait=3)
    check("s17", "care_screen", seen("care guidance", "this week"))
    swipe_down_content(); time.sleep(1.5); shot("care")
    back_to_home()

# ---------- s18 report vault ----------
with Rec("s18_vault"):
    back_to_home()
    tap("report vault", scroll=True, wait=3)
    check("s18", "vault_screen", seen("all your reports in one place", "lab report", "cbc"))
    shot("vault"); swipe_down_content(); time.sleep(1.5)
    back_to_home()

# ---------- s19 referral export ----------
with Rec("s19_referral"):
    back_to_home()
    tap("referral transfer", scroll=True, wait=3)
    check("s19", "referral_screen", seen("referral transfer", "patient-carried"))
    tap("prepare my referral", scroll=True, wait=3)
    sh(f"input swipe {SW//2} {int(SH*0.75)} {SW//2} {int(SH*0.45)} 400"); time.sleep(1.5); shot("ref_prepared")
    check("s19", "summary_shown", seen("review before export", "caretrail referral summary"))
    tap("show complete file contents", scroll=True, tries=4, wait=2)
    shot("ref_contents"); time.sleep(1)
    back_to_home()

# ---------- s20 facilities ----------
with Rec("s20_facilities"):
    back_to_home()
    tap("facilities", scroll=True, wait=3)
    check("s20", "facilities_screen", seen("where should we look", "facilities near you"))
    type_into("pin code", "411001")
    tap("find", scroll=False, wait=8); shot("facilities_list")
    check("s20", "results", seen("hospitals & clinics", "blood banks", "no hospitals"))
    swipe_down_content(); time.sleep(1.5)
    back_to_home()

# ---------- s21 community ----------
with Rec("s21_community"):
    back_to_home()
    tap("community", scroll=False, wait=3)        # bottom tab
    check("s21", "community_screen", seen("community room", "choose a nickname", "write to the room"))
    if find("choose a nickname"):
        type_into("choose a nickname", "ExpectingMom28")
        tap("accept and join", scroll=False, wait=5); shot("community_joined")
        check("s21", "joined_as_demo", seen("anjali (demo)", "write to the room"))
    if find("write to the room"):
        type_into("write to the room", "Hello everyone, week 22 here")
        tap("send", scroll=False, wait=4); shot("community_posted")
        check("s21", "posted", seen("hello everyone, week 22 here"))
    back_to_home()

# ---------- s22 profile + language switch + data ----------
with Rec("s22_profile"):
    back_to_home()
    tap("profile", scroll=False, wait=3)
    check("s22", "profile_screen", seen("profile", "journey code"))
    swipe_down_content(); time.sleep(1); shot("profile")
    tap("മലയാളം", scroll=True, tries=4, wait=2)   # language picker on profile/home header
    if tap("മലയാളം", scroll=False, tries=1, wait=3):
        time.sleep(2); shot("home_malayalam")
        check("s22", "malayalam_ui", seen("ഹോം", "ടൈംലൈൻ", "നിങ്ങളുടെ"))
        tap("english", scroll=True, tries=4, wait=3)  # switch back
        time.sleep(2)
    back_to_home()
    tap("profile", scroll=False, wait=2)
    tap("download or delete your data", scroll=True, tries=5, wait=3)
    if not find("download my data"): tap("your data", scroll=True, tries=4, wait=3)
    check("s22", "data_screen", seen("download my data", "delete my account"))
    shot("data_options")
    back_to_home()

# ---------- s23 record birth -> postpartum mode ----------
with Rec("s23_birth"):
    back_to_home()
    tap("baby arrived?", scroll=True, tries=6, wait=3)
    if not find("record the birth"): tap("birth details or unfinished draft", scroll=True, tries=4, wait=3)
    check("s23", "birth_form", seen("about the delivery", "date of delivery"))
    tap("date of delivery", scroll=False, tries=3, wait=2)
    pick_day_in_date_dialog()
    tap("ok", scroll=False, wait=2)
    type_into("birth weight (kg)", "3.1")
    tap("girl", scroll=False, tries=2, wait=1)
    tap("confirm birth", scroll=True, tries=5, wait=4)
    if find("confirm"): tap("confirm", scroll=False, wait=3)
    shot("birth_recorded")
    check("s23", "postpartum_mode", seen("postpartum", "growth chart"))
    back_to_home()

# ---------- s24 baby: growth + vaccines ----------
with Rec("s24_baby"):
    back_to_home()
    tap("baby: growth chart", scroll=True, tries=6, wait=3)
    if not find("growth chart"): tap("growth chart", scroll=True, tries=4, wait=3)
    check("s24", "growth_screen", seen("weight, length and head against who standards", "add a measurement"))
    shot("growth"); swipe_down_content(); time.sleep(1.5)
    back_to_home()
    tap("baby: vaccination record", scroll=True, tries=6, wait=3)
    if not find("vaccination"): tap("vaccines", scroll=True, tries=4, wait=3)
    check("s24", "vaccine_screen", seen("universal immunization programme", "next vaccine"))
    shot("vaccines"); swipe_down_content(); time.sleep(1.5)
    back_to_home()

# ---------- s25 mother: wellbeing hub + mindcheck + bp log ----------
with Rec("s25_wellness"):
    back_to_home()
    tap("mother: recovery and wellbeing", scroll=True, tries=6, wait=3)
    check("s25", "wellness_hub", seen("recovery & wound care", "mind check-in", "bp & sugar log"))
    shot("wellness_hub"); swipe_down_content(); time.sleep(1.5)
    tap("mind check-in", scroll=True, tries=4, wait=3)
    check("s25", "mindcheck", seen("in the last 2 weeks", "tele-manas", "kiran"))
    shot("mindcheck"); time.sleep(2)
    back_to_home()
    tap("mother: recovery and wellbeing", scroll=True, tries=6, wait=3)
    tap("bp & sugar log", scroll=True, tries=4, wait=3)
    check("s25", "bplog", seen("add today's blood pressure", "daily home readings"))
    shot("bplog"); time.sleep(1.5)
    back_to_home()

# ---------- s26 mindfulness + family planning ----------
with Rec("s26_mindful"):
    back_to_home()
    tap("mother: recovery and wellbeing", scroll=True, tries=6, wait=3)
    tap("mindfulness corner", scroll=True, tries=4, wait=3)
    check("s26", "mindful_screen", seen("breathe in for 4", "start breathing", "daily affirmation"))
    shot("mindful"); time.sleep(2)
    back_to_home()
    tap("mother: recovery and wellbeing", scroll=True, tries=6, wait=3)
    tap("family planning", scroll=True, tries=4, wait=3)
    check("s26", "familyplan", seen("family planning", "spacing"))
    shot("familyplan"); time.sleep(1.5)
    back_to_home()

json.dump(RESULTS, open(f"{OUT}/results.json", "w"), indent=1)
bad = [(sc, k) for sc, d in RESULTS.items() for k, v in d.items() if not v]
prefixes = {f"s{i:02d}": f"s{i:02d}_" for i in range(1, 27)}
for sc in {b[0] for b in bad}:
    for f in os.listdir(OUT):
        if f.startswith(prefixes.get(sc, "zzz")) and f.endswith(".mp4"):
            os.rename(f"{OUT}/{f}", f"{OUT}/FAILED_{f}")
say("FAILED CHECKS:", bad if bad else "none"); say("done")
sys.exit(1 if bad else 0)
