"""Repair driver v2: re-records ONLY the scenes whose run-1 footage was unusable.
Fixes vs v1 (all verified against run-1 dumps + source):
 1. find() visibility band (0.07-0.86*SH) hid welcome 'Sign up' (y~2154) and the whole
    bottom bar (y~2300) -> bottom-nav taps use tap_bottom(); welcome buttons use tap_all().
 2. Material date-dialog day cells are checkable, NOT clickable -> pick_day taps any node
    whose text matches the weekday-day pattern.
 3. adb push does not index into MediaStore -> media-scan broadcast after pushing the
    sample report, plus picker fallbacks (Recent -> Documents -> Downloads root).
 4. Assistant send is a textless icon button -> tap_send_in_row taps the icon at the
    input row's right edge.
 5. Check strings updated to current UI copy (see per-scene comments).
Product facts encoded (verified in source at 1b31eee):
 - 'Report vault' exists only in POSTPARTUM quick access -> r18 recorded after r23.
 - Birth record is a local on-device store (no server call); confirm needs date+gender.
 - Danger screen at 22w shows general warning signs only (preeclampsia checklist 28w+);
   scene kept from run 1, not re-recorded here.
Synthetic data only; isolated local backend; no production systems touched."""
import json, os, re, subprocess, sys, time, urllib.request
import xml.etree.ElementTree as ET

OUT = "/tmp/demo-out"; os.makedirs(OUT, exist_ok=True)
PKG = "com.caretrail.native.test"
API = "http://localhost:8000"
log = open(f"{OUT}/driver.log", "w")
def say(*a):
    s = " ".join(str(x) for x in a); print(s, flush=True); log.write(s + "\n"); log.flush()
def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True).stdout
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
        t = ((e.get("text") or "") + "|" + (e.get("content-desc") or "")).lower()
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", e.get("bounds") or "")
        if m and t.strip("|"):
            yield t, *map(int, m.groups())
SW, SH = 720, 1600
def screen_size():
    global SW, SH
    m = re.search(r"(\d+)x(\d+)", sh("wm size"))
    if m: SW, SH = int(m.group(1)), int(m.group(2))
    say("screen", SW, SH)
BAND = (0.06, 0.87)      # content band: excludes header back-row and bottom bar
def find(text, root=None, band=BAND):
    root = root or dump()
    for t, x1, y1, x2, y2 in nodes(root):
        y = (y1 + y2) // 2; x = (x1 + x2) // 2
        if text.lower() in t and band[0] * SH < y < band[1] * SH and 0 < x < SW:
            return x, y
def swipe_down_content():
    sh(f"input swipe {SW//2} {int(SH*0.72)} {SW//2} {int(SH*0.32)} 400"); time.sleep(0.8)
def to_top():
    for _ in range(4):
        sh(f"input swipe {SW//2} {int(SH*0.30)} {SW//2} {int(SH*0.75)} 300"); time.sleep(0.5)
def tap(text, scroll=True, tries=9, wait=1.5, band=BAND):
    went_top = False
    for i in range(tries):
        p = find(text, band=band)
        if p:
            sh(f"input tap {p[0]} {p[1]}"); time.sleep(wait); say("tap ok:", text, p); return True
        if not scroll: break
        if not went_top: to_top(); went_top = True; continue
        swipe_down_content()
    say("TAP FAILED:", text); shot(f"fail_{re.sub(r'[^a-z]+','_',text.lower())}"); return False
def tap_last(text, scroll=True, tries=9, wait=1.5, band=BAND):
    # tap the LOWEST match - for buttons whose label repeats the Page title
    went_top = False
    for i in range(tries):
        root = dump(); best = None
        for t, x1, y1, x2, y2 in nodes(root):
            y = (y1 + y2) // 2; x = (x1 + x2) // 2
            if text.lower() in t and band[0] * SH < y < band[1] * SH and 0 < x < SW:
                if best is None or y > best[1]: best = (x, y)
        if best:
            sh(f"input tap {best[0]} {best[1]}"); time.sleep(wait); say("tap-lowest ok:", text, best); return True
        if not scroll: break
        if not went_top: to_top(); went_top = True; continue
        swipe_down_content()
    say("TAP FAILED:", text); shot(f"fail_{re.sub(r'[^a-z]+','_',text.lower())}"); return False
def tap_bottom(text, **kw):   # bottom nav bar
    return tap(text, scroll=False, band=(0.86, 0.999), **kw)
def tap_all(text, **kw):      # anywhere on screen (welcome buttons etc.)
    return tap(text, scroll=False, band=(0.02, 0.999), **kw)
def type_into(label, text, keep_keyboard=False):
    if tap(label, wait=0.8):
        sh("input text '" + text.replace(" ", "%s") + "'"); time.sleep(0.8)
        if not keep_keyboard: sh("input keyevent 4"); time.sleep(0.8)
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
HOME_MARKERS = ("your pregnancy journey", "your baby", "quick access")
def back_to_home():
    for _ in range(5):
        if find("your pregnancy journey", band=(0.02, 0.999)) or find("your baby", band=(0.02, 0.999)) \
           or find("quick access", band=(0.02, 0.999)):
            return True
        sh("input keyevent 4"); time.sleep(1.5)
    tap_bottom("home", wait=2)
    if seen("your pregnancy journey", "your baby", "quick access", wait=2): return True
    # last resort: relaunch and re-login to the demo journey
    launch_app(); login_demo()
    return seen("your pregnancy journey", "your baby", "quick access", wait=3)
def launch_app():
    sh(f"am force-stop {PKG}"); time.sleep(2)
    sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(8)
def login_demo():
    if not find("try the demo", band=(0.02, 0.999)):
        tap_all("log in", wait=3)
    if find("try the demo", band=(0.02, 0.999)):
        tap_all("try the demo", wait=9)
    if find("skip for now", band=(0.02, 0.999)): tap("skip for now", scroll=False, wait=4)
    seen("your pregnancy journey", wait=4)

def pick_day_in_date_dialog():
    # Material 3 date dialog: day cells carry the full date as TEXT and are
    # checkable="true" but clickable="false" (verified in run-1 dump370).
    root = dump(); picked = False
    for e in (root.iter("node") if root is not None else []):
        txt = e.get("text") or ""
        if re.search(r"(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s+\w+\s+\d+,\s+20\d\d", txt):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", e.get("bounds") or "")
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                sh(f"input tap {(x1+x2)//2} {(y1+y2)//2}"); picked = True; break
    say("date picked:", picked); time.sleep(1.5); return picked

def tap_send_in_row(row_text):
    # assistant send is a textless circular icon button at the right edge of the input row
    root = dump()
    for t, x1, y1, x2, y2 in nodes(root):
        if row_text.lower() in t:
            sh(f"input tap {x2 - 62} {(y1+y2)//2}"); say("send tapped beside:", row_text); time.sleep(2); return True
    say("SEND ROW NOT FOUND:", row_text); return False

class Rec:
    def __init__(s, name): s.name = name
    def __enter__(s):
        s.p = subprocess.Popen(["adb", "shell", "screenrecord", "--size", "720x1600", "--bit-rate", "6000000", "--time-limit", "170", f"/sdcard/{s.name}.mp4"])
        time.sleep(2); return s
    def __exit__(s, *a):
        time.sleep(1.5); sh("pkill -2 screenrecord"); time.sleep(3); s.p.wait(timeout=20)
        adb("pull", f"/sdcard/{s.name}.mp4", f"{OUT}/{s.name}.mp4"); say("recorded", s.name)

adb("wait-for-device")
screen_size()
for _ in range(60):
    if sh("getprop sys.boot_completed").strip() == "1": break
    time.sleep(2)
say("boot completed; settling"); time.sleep(20)
sh("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS"); sh("input keyevent 3"); time.sleep(3)
adb("install", "-r", "android-native/app/build/outputs/apk/debug/app-debug.apk")
for perm in ["POST_NOTIFICATIONS", "ACCESS_FINE_LOCATION"]: sh(f"pm grant {PKG} android.permission.{perm}")
sh("settings put global window_animation_scale 0")
# sample report for the upload scene + MediaStore indexing (adb push alone is invisible to the picker)
adb("push", "backend/app/sample_reports/cbc_malayalam_low_hb.txt", "/sdcard/Download/cbc_malayalam_low_hb.txt")
sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/cbc_malayalam_low_hb.txt")
sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/")
time.sleep(4)
# emulator GPS for the facilities fallback
adb("emu", "geo", "fix", "73.8567", "18.5204")

# ---------- r02 signup + journey setup + logout ----------
with Rec("r02_signup"):
    launch_app(); time.sleep(4); shot("welcome")
    check("s02", "welcome", seen("caretrail"))
    tap_all("sign up", wait=3)
    check("s02", "signup_form", seen("your name"))
    type_into("your name", "Demo User")
    type_into("phone number", "9000000099")
    type_into("password", "synthetic123")
    tap("i understand caretrail", scroll=True, wait=1.5)   # consent row
    shot("signup_filled")
    tap_last("sign up", scroll=True, wait=8)                # form submit (title says 'Sign up' too)
    check("s02", "journey_setup", seen("we build your timeline from one date", "first day of your last period", wait=4))
    tap("first day of your last period", scroll=False, wait=2)
    pick_day_in_date_dialog()
    tap_all("ok", wait=2)
    tap_last("start my journey", scroll=True, wait=8)      # title repeats the button label
    check("s02", "new_journey_home", seen("your pregnancy journey", "quick access", wait=6))
    shot("new_account_home")
    # log the fresh account out again: logout lives at the very bottom of Home
    tap("log out", scroll=True, tries=14, wait=3)
    tap("remove local records and log out", scroll=False, tries=3, wait=5)   # dialog confirm
    check("s02", "logged_out", seen("try the demo", "log in", wait=6))

# ---------- phase B entry: demo login (not recorded) ----------
login_demo()

# ---------- r05b timeline screen (milestone footage salvaged from run 1) ----------
with Rec("r05b_timeline"):
    back_to_home()
    tap_bottom("timeline", wait=3)
    check("s05", "timeline", seen("your timeline", "you are here", wait=4))
    shot("timeline_top")
    swipe_down_content(); time.sleep(1); shot("timeline_mid")
    swipe_down_content(); time.sleep(1); shot("timeline_low")
    back_to_home()

# ---------- r06 report upload + on-device reading + confirm ----------
with Rec("r06_upload"):
    back_to_home()
    tap("upload report", scroll=True, wait=3)
    check("s06", "upload_screen", seen("take a photo of your report or choose a file"))
    tap("choose file", scroll=False, wait=5)
    shot("picker")
    ok = tap("cbc_malayalam_low_hb", scroll=False, tries=2, wait=5)
    if not ok:
        tap_all("documents", wait=3); ok = tap("cbc_malayalam_low_hb", scroll=True, tries=3, wait=5)
    if not ok:
        tap_all("recent files", wait=3); ok = tap("cbc_malayalam_low_hb", scroll=True, tries=3, wait=5)
    check("s06", "file_picked", ok)
    check("s06", "reading", seen("reading your report", "read on this device", "read by ai", wait=8))
    if not seen("i checked the original report", wait=45):
        say("extraction slow or failed"); shot("extract_state")
    swipe_down_content()
    tap("i checked the original report", scroll=True, wait=1.5)
    shot("extract_proposed")
    tap("save confirmed values", scroll=True, wait=6)
    check("s06", "saved", seen("values saved to your timeline", wait=6))
    shot("upload_saved"); time.sleep(2); back_to_home()

# ---------- r07 attention flag on home (needs r06's saved low Hb) ----------
with Rec("r07_attention"):
    back_to_home(); to_top(); time.sleep(1)
    swipe_down_content(); time.sleep(1)
    check("s07", "attention_flag", seen("review pending", "needs doctor review", wait=5))
    shot("attention"); time.sleep(2); to_top()

# ---------- r08 help assistant: real question, real answer ----------
with Rec("r08_assistant"):
    back_to_home()
    tap("help assistant", scroll=True, wait=3)
    if find("i understand"): tap("i understand", scroll=False, wait=2)
    check("s08", "assistant_open", seen("hello, how can i help you", "ask anything"))
    type_into("ask anything", "When is my next scan due")
    tap_send_in_row("when is my next scan due")
    check("s08", "answer", seen("anomaly", "tiffa", "18-22", "scheduled", wait=20))
    shot("assistant_answer"); time.sleep(2)
    back_to_home()

# ---------- r11 reminders: care reminders + medication/hydration ----------
with Rec("r11_reminders"):
    back_to_home()
    tap("reminders", scroll=True, wait=3)        # quick-access tile (bottom tab kept out of band)
    check("s11", "reminders_screen", seen("care reminders", wait=4))
    shot("reminders")
    tap("medication and hydration reminders", scroll=False, tries=3, wait=3)
    check("s11", "med_screen", seen("set up your daily reminders", "add medication", wait=4))
    swipe_down_content(); time.sleep(1.5)
    check("s11", "hydration", seen("hydration reminders", "gentle nudges for water"))
    shot("med_schedule"); time.sleep(1.5)
    back_to_home()

# ---------- r20 facilities with a real PIN search ----------
with Rec("r20_facilities"):
    back_to_home()
    tap("facilities", scroll=True, wait=3)
    check("s20", "facilities_screen", seen("where should we look", "facilities near you"))
    type_into("pin code", "411001")
    tap("find", scroll=False, tries=2, wait=15)
    if seen("place not found", "could not", wait=2):
        say("PIN geocode failed; trying GPS"); tap("use my gps location", scroll=False, wait=15)
    check("s20", "results", seen("hospital", "clinic", "km", wait=12))
    shot("facilities_list"); swipe_down_content(); time.sleep(1.5); shot("facilities_list2")
    back_to_home()

# ---------- r22 profile + your data ----------
with Rec("r22_profile"):
    back_to_home()
    tap_bottom("profile", wait=3)
    check("s22", "profile_screen", seen("journey code", "profile", wait=4))
    shot("profile"); swipe_down_content(); time.sleep(1); shot("profile2")
    tap("download or delete your data", scroll=True, tries=5, wait=3)
    check("s22", "data_screen", seen("download my data", "delete my account", wait=4))
    shot("data_options"); swipe_down_content(); time.sleep(1.5); shot("data_options2")
    back_to_home()

# ---------- r23 record birth -> postpartum mode (local store, no server call) ----------
with Rec("r23_birth"):
    back_to_home()
    tap("birth details or unfinished draft", scroll=True, tries=8, wait=3)
    check("s23", "birth_form", seen("about the delivery", "date of delivery", wait=4))
    tap("date of delivery", scroll=False, tries=3, wait=2)
    pick_day_in_date_dialog()
    tap_all("ok", wait=2)
    type_into("time of birth", "09:42")
    type_into("weeks", "39")
    type_into("birth weight (kg)", "3.1")
    tap("girl", scroll=False, tries=2, wait=1)
    shot("birth_filled")
    tap("confirm birth", scroll=True, tries=5, wait=5)
    shot("birth_recorded")
    check("s23", "postpartum_mode", seen("birth recorded - caretrail is in postpartum mode", wait=5))
    time.sleep(1); back_to_home()
    check("s23", "postpartum_home", seen("your baby", "growth chart", wait=6))
    shot("postpartum_home")

# ---------- r24 baby: growth chart + vaccination schedule ----------
with Rec("r24_baby"):
    back_to_home()
    tap("growth chart", scroll=True, tries=6, wait=3)
    check("s24", "growth_screen", seen("baby growth chart", "who child growth standards", wait=4))
    shot("growth"); swipe_down_content(); time.sleep(1.5); shot("growth2")
    back_to_home()
    tap("next vaccine", scroll=True, tries=6, wait=3)
    check("s24", "vaccine_screen", seen("vaccination schedule", "universal immunization programme", wait=4))
    shot("vaccines"); swipe_down_content(); time.sleep(1.5); shot("vaccines2")
    back_to_home()

# ---------- r25 mother: wellbeing hub + mind check-in + BP log ----------
with Rec("r25_wellness"):
    back_to_home()
    tap("mother: recovery and wellbeing", scroll=True, tries=6, wait=3)
    check("s25", "wellness_hub", seen("recovery & wound care", "mind check-in", wait=4))
    shot("wellness_hub"); swipe_down_content(); time.sleep(1.5); shot("wellness_hub2")
    tap("mind check-in", scroll=True, tries=4, wait=3)
    check("s25", "mindcheck", seen("in the last 2 weeks", "tele-manas", "kiran", wait=4))
    shot("mindcheck"); swipe_down_content(); time.sleep(1.5); shot("mindcheck2")
    sh("input keyevent 4"); time.sleep(2)
    tap("bp & sugar log", scroll=True, tries=4, wait=3)
    check("s25", "bplog", seen("were you told you had any of these", "blood pressure", wait=4))
    shot("bplog"); swipe_down_content(); time.sleep(1.5); shot("bplog2")
    back_to_home()

# ---------- r26 mindfulness corner + family planning ----------
with Rec("r26_mindful"):
    back_to_home()
    tap("mother: recovery and wellbeing", scroll=True, tries=6, wait=3)
    tap("mindfulness corner", scroll=True, tries=4, wait=3)
    check("s26", "mindful_screen", seen("today's affirmation", "two quiet minutes", wait=4))
    shot("mindful"); swipe_down_content(); time.sleep(2); shot("mindful2")
    sh("input keyevent 4"); time.sleep(2)
    tap("family planning", scroll=True, tries=4, wait=3)
    check("s26", "familyplan", seen("family planning & spacing", "why spacing matters", wait=4))
    shot("familyplan"); swipe_down_content(); time.sleep(1.5); shot("familyplan2")
    back_to_home()

# ---------- r18 report vault (postpartum quick access only) ----------
with Rec("r18_vault"):
    back_to_home()
    tap("report vault", scroll=True, tries=8, wait=3)
    check("s18", "vault_screen", seen("report vault", "cbc_malayalam_low_hb", wait=4))
    shot("vault"); swipe_down_content(); time.sleep(1.5); shot("vault2")
    back_to_home()

json.dump(RESULTS, open(f"{OUT}/results.json", "w"), indent=1)
say("REPAIR DONE", json.dumps(RESULTS))
