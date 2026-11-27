"""driver_scene_v3.py - ONE isolated scene-group per invocation.
Usage: python3 driver_scene_v3.py <job>
Jobs: signup | timeline | upload_attention | assistant | reminders | facilities |
      profile | birth_baby | wellness | mindful | vault
Each job cold-boots its own emulator (handled by the workflow), installs the
prebuilt APK, demo-logs-in, performs any unrecorded state setup the group needs
(birth for postpartum jobs, upload for vault/attention), records ONLY its own
footage, and writes results-<job>.json + <job>.mp4 + dumps + driver-<job>.log.
No cross-job state is assumed; a failure costs one job, never thirteen.

Every tap target below is verified against run-1 UI dumps (468 files) or source
at 1b31eee - see the per-job comments. Fixes carried from v2:
 - bottom-bar taps use the bottom band; welcome buttons use the full screen
 - Material date-dialog day cells are checkable, tapped by text pattern
 - adb push needs a MediaStore scan; v3 additionally VERIFIES indexing via
   content query before opening the picker, and retries the scan once
 - assistant send is a textless icon at the input row's right edge
 - facilities PIN field is a label-less EditText (verified dump293): tapped by
   class, not by label text
 - reminders page title is "Daily reminders" when entered outside onboarding
   (source DailyReminderScreens.kt:48; v2 checked the onboarding title)
Synthetic data only; isolated local backend; no production systems touched."""
import json, os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

JOB = sys.argv[1] if len(sys.argv) > 1 else "timeline"
OUT = "/tmp/demo-out"; os.makedirs(OUT, exist_ok=True)
PKG = "com.caretrail.native.test"
APK = os.environ.get("CARETRAIL_APK", "app-debug.apk")
SAMPLE = os.environ.get("CARETRAIL_SAMPLE", "cbc_malayalam_low_hb.txt")
log = open(f"{OUT}/driver-{JOB}.log", "w")
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
        say("ANR dialog, tapping Wait")
        m = re.search(r'text="Wait"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
        if m:
            x1, y1, x2, y2 = map(int, m.groups()); sh(f"input tap {(x1+x2)//2} {(y1+y2)//2}")
        else: sh("input keyevent 4")
        time.sleep(3); return dump(_depth + 1)
    n_dump += 1
    open(f"{OUT}/dump{JOB}-{n_dump:03d}.xml", "w").write(xml)
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
BAND = (0.06, 0.87)
def find(text, root=None, band=BAND):
    root = root or dump()
    for t, x1, y1, x2, y2 in nodes(root):
        y = (y1 + y2) // 2; x = (x1 + x2) // 2
        if text.lower() in t and band[0] * SH < y < band[1] * SH and 0 < x < SW:
            return x, y
def find_edittext(root=None):
    # first visible EditText by class - for label-less fields (facilities PIN, dump293)
    root = root or dump()
    for e in root.iter("node") if root is not None else []:
        if "EditText" in (e.get("class") or ""):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", e.get("bounds") or "")
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                return (x1 + x2) // 2, (y1 + y2) // 2
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
def tap_bottom(text, **kw):
    return tap(text, scroll=False, band=(0.86, 0.999), **kw)
def tap_all(text, **kw):
    return tap(text, scroll=False, band=(0.02, 0.999), **kw)
def type_into(label, text, keep_keyboard=False):
    if tap(label, wait=0.8):
        sh("input text '" + text.replace(" ", "%s") + "'"); time.sleep(0.8)
        if not keep_keyboard: sh("input keyevent 4"); time.sleep(0.8)
        return True
    return False
def type_into_edittext(text):
    p = find_edittext()
    if not p: say("NO EDITTEXT FOUND"); return False
    sh(f"input tap {p[0]} {p[1]}"); time.sleep(0.8)
    sh("input text '" + text.replace(" ", "%s") + "'"); time.sleep(0.8)
    sh("input keyevent 4"); time.sleep(0.8); return True
def shot(name):
    sh(f"screencap -p /sdcard/{JOB}-{name}.png"); adb("pull", f"/sdcard/{JOB}-{name}.png", f"{OUT}/{JOB}-{name}.png")
RESULTS = {}
def seen(*texts, wait=2.0):
    time.sleep(wait)
    for _ in range(2):
        root = dump(); blob = " ".join(t for t, *_ in nodes(root))
        if any(x.lower() in blob for x in texts): return True
        time.sleep(1.5)
    return False
def check(name, ok):
    RESULTS.setdefault(JOB, {})[name] = bool(ok); say("CHECK", JOB, name, "PASS" if ok else "FAIL")
HOME_MARKERS = ("your pregnancy journey", "after birth: mother and baby", "quick access")
def at_home():
    return any(find(m, band=(0.02, 0.999)) for m in HOME_MARKERS)
def back_to_home():
    for _ in range(5):
        if at_home(): return True
        sh("input keyevent 4"); time.sleep(1.5)
    tap_bottom("home", wait=2)
    if seen(*HOME_MARKERS, wait=2): return True
    launch_app(); login_demo()
    return seen(*HOME_MARKERS, wait=3)
def launch_app():
    sh(f"am force-stop {PKG}"); time.sleep(2)
    sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(8)
def login_demo():
    if find("your pregnancy journey", band=(0.02, 0.999)) or find("after birth", band=(0.02, 0.999)):
        say("already logged in"); return
    if not find("try the demo", band=(0.02, 0.999)):
        tap_all("log in", wait=3)
    if find("try the demo", band=(0.02, 0.999)):
        tap_all("try the demo", wait=9)
    if find("skip for now", band=(0.02, 0.999)): tap("skip for now", scroll=False, wait=4)
    seen("your pregnancy journey", wait=4)
def pick_day_in_date_dialog():
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
    root = dump()
    for t, x1, y1, x2, y2 in nodes(root):
        if row_text.lower() in t:
            sh(f"input tap {x2 - 62} {(y1+y2)//2}"); say("send tapped beside:", row_text); time.sleep(2); return True
    say("SEND ROW NOT FOUND:", row_text); return False

def open_date_field(label):
    # DateField renders its label twice: a standalone Text above the button and a
    # copy inside the button (dumps2 dump009: label [53,431] vs button [53,499]).
    # Tapping the standalone label does nothing - run-2's root cascade. Tap the
    # lower copy, verify the dialog actually opened, then fall back to an offset tap.
    if tap_last(label, scroll=True, wait=2):
        if seen("select date", wait=1.5): return True
    p = find(label)
    if p:
        sh(f"input tap {p[0]} {p[1]+150}"); time.sleep(2)
        if seen("select date", wait=1.5): return True
    say("DATE DIALOG FAILED TO OPEN:", label); shot("fail_datefield"); return False

class Rec:
    def __init__(s, name): s.name = name
    def __enter__(s):
        s.p = subprocess.Popen(["adb", "shell", "screenrecord", "--size", "720x1600", "--bit-rate", "6000000", "--time-limit", "170", f"/sdcard/{s.name}.mp4"])
        time.sleep(2); return s
    def __exit__(s, *a):
        time.sleep(1.5); sh("pkill -2 screenrecord"); time.sleep(3)
        try: s.p.wait(timeout=20)
        except Exception: pass
        adb("pull", f"/sdcard/{s.name}.mp4", f"{OUT}/{s.name}.mp4"); say("recorded", s.name)

def setup_device():
    adb("wait-for-device"); screen_size()
    for _ in range(60):
        if sh("getprop sys.boot_completed").strip() == "1": break
        time.sleep(2)
    say("boot completed; settling"); time.sleep(20)
    sh("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS"); sh("input keyevent 3"); time.sleep(3)
    adb("install", "-r", APK)
    sh(f"pm clear {PKG}"); time.sleep(2)   # fresh state: no half-onboarded account can resume
    for perm in ["POST_NOTIFICATIONS", "ACCESS_FINE_LOCATION"]: sh(f"pm grant {PKG} android.permission.{perm}")
    sh("settings put global window_animation_scale 0")
    adb("push", SAMPLE, "/sdcard/Download/cbc_malayalam_low_hb.txt")
    sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/cbc_malayalam_low_hb.txt")
    time.sleep(4)
    # verify MediaStore indexing; retry scan once (adb push alone is invisible to the picker)
    q = sh("content query --uri content://media/external/file --projection _display_name | grep cbc_malayalam_low_hb")
    if "cbc_malayalam_low_hb" not in q:
        say("mediastore miss; rescanning"); sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/"); time.sleep(5)
        q = sh("content query --uri content://media/external/file --projection _display_name | grep cbc_malayalam_low_hb")
    check("mediastore_indexed", "cbc_malayalam_low_hb" in q)
    adb("emu", "geo", "fix", "73.8567", "18.5204")

def do_birth_setup():
    # unrecorded: fill + confirm the birth so the app enters postpartum mode
    say("SETUP birth")
    launch_app(); login_demo(); back_to_home()
    tap("birth details or unfinished draft", scroll=True, tries=8, wait=3)
    if not seen("about the delivery", "date of delivery", wait=4):
        say("already postpartum?"); return
    open_date_field("date of delivery")
    pick_day_in_date_dialog(); tap_all("ok", wait=2)
    type_into("time of birth", "09:42"); type_into("weeks", "39"); type_into("birth weight (kg)", "3.1")
    tap("girl", scroll=False, tries=2, wait=1)
    tap("confirm birth", scroll=True, tries=5, wait=5)
    check("setup_birth", seen("postpartum mode", wait=5))
    back_to_home()

def do_upload_setup():
    # unrecorded: upload the sample CBC so the vault/attention have content
    say("SETUP upload")
    back_to_home()
    tap("upload report", scroll=True, wait=3)
    tap("choose file", scroll=False, wait=5)
    ok = tap("cbc_malayalam_low_hb", scroll=False, tries=2, wait=5)
    if not ok:
        tap_all("documents", wait=3); ok = tap("cbc_malayalam_low_hb", scroll=True, tries=3, wait=5)
    if not ok:
        tap_all("recent files", wait=3); ok = tap("cbc_malayalam_low_hb", scroll=True, tries=3, wait=5)
    if not ok: say("SETUP upload: file not picked"); return False
    if not seen("i checked the original report", wait=45):
        say("SETUP extraction slow/failed"); shot("setup_extract_state"); return False
    swipe_down_content()
    tap("i checked the original report", scroll=True, wait=1.5)
    tap("save confirmed values", scroll=True, wait=6)
    ok = seen("values saved to your timeline", wait=6)
    check("setup_upload", ok)
    back_to_home(); return ok

setup_device()

# ============================== JOBS ==============================
if JOB == "signup":
    # r02: welcome -> sign up -> journey setup -> home -> log out (fresh install)
    with Rec("r02_signup"):
        launch_app(); time.sleep(4); shot("welcome")
        check("welcome", seen("caretrail"))
        tap_all("sign up", wait=3)
        check("signup_form", seen("your name"))   # AuthScreens.kt:158 l.t("yourName")
        type_into("your name", "Demo User")
        type_into("phone number", "9000000099")   # I18nData "phone" -> "Phone number"
        type_into("password", "synthetic123")
        tap("i understand caretrail", scroll=True, wait=1.5)   # I18nData "consent"
        shot("signup_filled")
        tap_last("sign up", scroll=True, wait=8)
        check("journey_setup", seen("we build your timeline from one date", "first day of your last period", wait=4))  # I18nData journeyIntro/lmpLabel
        open_date_field("first day of your last period")
        pick_day_in_date_dialog(); tap_all("ok", wait=2)
        tap_last("start my journey", scroll=True, wait=8)      # I18nData startJourney
        check("new_journey_home", seen("your pregnancy journey", "quick access", wait=6))
        shot("new_account_home")
        tap("log out", scroll=True, tries=14, wait=3)          # JourneyScreens.kt:499
        tap("remove local records and log out", scroll=False, tries=3, wait=5)  # MoreScreens.kt:121
        check("logged_out", seen("try the demo", "log in", wait=6))

elif JOB == "timeline":
    # r05b: bottom nav Timeline -> "Your timeline" (JourneyScreens.kt:519)
    with Rec("r05b_timeline"):
        launch_app(); login_demo(); back_to_home()
        tap_bottom("timeline", wait=3)
        check("timeline", seen("your timeline", wait=4))
        shot("timeline_top"); swipe_down_content(); time.sleep(1); shot("timeline_mid")
        swipe_down_content(); time.sleep(1); shot("timeline_low")
        back_to_home()

elif JOB == "upload_attention":
    # r06 + r07: upload flow (dump105/107 verified) then the home attention flag
    with Rec("r06_upload"):
        launch_app(); login_demo(); back_to_home()
        tap("upload report", scroll=True, wait=3)
        check("upload_screen", seen("take a photo of your report or choose a file"))
        tap("choose file", scroll=False, wait=5); shot("picker")
        ok = tap("cbc_malayalam_low_hb", scroll=False, tries=2, wait=5)
        if not ok:
            tap_all("documents", wait=3); ok = tap("cbc_malayalam_low_hb", scroll=True, tries=3, wait=5)
        if not ok:
            tap_all("recent files", wait=3); ok = tap("cbc_malayalam_low_hb", scroll=True, tries=3, wait=5)
        check("file_picked", ok)
        check("reading", seen("reading your report", "read on this device", "read by ai", wait=8))
        if not seen("i checked the original report", wait=45):
            say("extraction slow or failed"); shot("extract_state")
        swipe_down_content()
        tap("i checked the original report", scroll=True, wait=1.5)
        shot("extract_proposed")
        tap("save confirmed values", scroll=True, wait=6)
        check("saved", seen("values saved to your timeline", wait=6))
        shot("upload_saved"); time.sleep(2)
    with Rec("r07_attention"):
        back_to_home(); to_top(); time.sleep(1); swipe_down_content(); time.sleep(1)
        check("attention_flag", seen("review pending", "needs doctor review", "attention needed", wait=5))
        shot("attention"); time.sleep(2)

elif JOB == "assistant":
    # r08: home "Help assistant" (dump086) -> consent (dump141) -> chat (dump143)
    with Rec("r08_assistant"):
        launch_app(); login_demo(); back_to_home()
        tap("help assistant", scroll=True, wait=3)
        if find("i understand"): tap("i understand", scroll=False, wait=2)
        check("assistant_open", seen("hello, how can i help you", "ask anything"))
        type_into("ask anything", "When is my next scan due")
        tap_send_in_row("when is my next scan due")
        check("answer", seen("anomaly", "tiffa", "18-22", "scheduled", wait=20))
        shot("assistant_answer"); time.sleep(2); back_to_home()

elif JOB == "reminders":
    # r11: quick access "Reminders" (dump104) -> "Care reminders" (CareScreens.kt:93)
    # -> "Medication and hydration reminders" -> "Daily reminders" (DailyReminderScreens.kt:48, NOT onboarding title)
    with Rec("r11_reminders"):
        launch_app(); login_demo(); back_to_home()
        tap("reminders", scroll=True, wait=3)
        check("reminders_screen", seen("care reminders", wait=4)); shot("reminders")
        tap("medication and hydration reminders", scroll=False, tries=3, wait=3)
        check("med_screen", seen("daily reminders", "add medication", wait=4))
        swipe_down_content(); time.sleep(1.5)
        check("hydration", seen("hydration reminders", "gentle nudges for water"))
        shot("med_schedule"); time.sleep(1.5); back_to_home()

elif JOB == "facilities":
    # r20: quick access "Facilities" (dump104) -> dump293 layout; PIN field is a
    # label-less EditText -> tap by class; "Find" and "Use my GPS location" verified clickable
    with Rec("r20_facilities"):
        launch_app(); login_demo(); back_to_home()
        tap("facilities", scroll=True, wait=3)
        check("facilities_screen", seen("where should we look", "facilities near you"))
        check("pin_field", type_into_edittext("411001"))
        tap("find", scroll=False, tries=2, wait=15)
        if seen("place not found", "could not", wait=2):
            say("PIN geocode failed; trying GPS"); tap("use my gps location", scroll=False, wait=15)
        check("results", seen("hospital", "clinic", "km", wait=12))
        shot("facilities_list"); swipe_down_content(); time.sleep(1.5); shot("facilities_list2")
        back_to_home()

elif JOB == "profile":
    # r22: bottom nav Profile -> "Profile" page (MoreScreens.kt:62):
    # Your doctor / About this app / Your data -> "Download or delete your data"
    with Rec("r22_profile"):
        launch_app(); login_demo(); back_to_home()
        tap_bottom("profile", wait=3)
        check("profile_screen", seen("your doctor", "about this app", wait=4))
        shot("profile"); swipe_down_content(); time.sleep(1); shot("profile2")
        tap("download or delete your data", scroll=True, tries=5, wait=3)
        check("data_screen", seen("download my data", "delete my account", wait=4))
        shot("data_options"); swipe_down_content(); time.sleep(1.5); shot("data_options2")
        back_to_home()

elif JOB == "birth_baby":
    # r23 + r24: record birth (Sprint2Screens.kt:116) -> postpartum, then
    # "Baby: growth chart" and "Baby: vaccination record" (JourneyScreens.kt:434-438)
    with Rec("r23_birth"):
        launch_app(); login_demo(); back_to_home()
        tap("birth details or unfinished draft", scroll=True, tries=8, wait=3)
        check("birth_form", seen("about the delivery", "date of delivery", wait=4))
        open_date_field("date of delivery")
        pick_day_in_date_dialog(); tap_all("ok", wait=2)
        type_into("time of birth", "09:42"); type_into("weeks", "39"); type_into("birth weight (kg)", "3.1")
        tap("girl", scroll=False, tries=2, wait=1)
        shot("birth_filled")
        tap("confirm birth", scroll=True, tries=5, wait=5)
        shot("birth_recorded")
        check("postpartum_mode", seen("postpartum mode", wait=5))
        time.sleep(1); back_to_home()
        check("postpartum_home", seen("your baby", "after birth: mother and baby", wait=6))
        shot("postpartum_home")
    with Rec("r24_baby"):
        back_to_home()
        tap("baby: growth chart", scroll=True, tries=6, wait=3)
        check("growth_screen", seen("baby growth chart", "who child growth standards", wait=4))
        shot("growth"); swipe_down_content(); time.sleep(1.5); shot("growth2")
        back_to_home()
        tap("baby: vaccination record", scroll=True, tries=6, wait=3)
        check("vaccine_screen", seen("vaccination schedule", wait=4))
        shot("vaccines"); swipe_down_content(); time.sleep(1.5); shot("vaccines2")
        back_to_home()

elif JOB == "wellness":
    # r25: needs postpartum -> unrecorded birth setup, then wellness hub
    # (Sprint4Screens.kt:144-152 verified strings)
    do_birth_setup()
    with Rec("r25_wellness"):
        back_to_home()
        tap("mother: recovery and wellbeing", scroll=True, tries=6, wait=3)
        check("wellness_hub", seen("recovery & wound care", "mind check-in", wait=4))
        shot("wellness_hub"); swipe_down_content(); time.sleep(1.5); shot("wellness_hub2")
        tap("mind check-in", scroll=True, tries=4, wait=3)
        check("mindcheck", seen("in the last 2 weeks", "tele-manas", "kiran", wait=4))
        shot("mindcheck"); swipe_down_content(); time.sleep(1.5); shot("mindcheck2")
        sh("input keyevent 4"); time.sleep(2)
        tap("bp & sugar log", scroll=True, tries=4, wait=3)
        check("bplog", seen("blood pressure", wait=4))
        shot("bplog"); swipe_down_content(); time.sleep(1.5); shot("bplog2")
        back_to_home()

elif JOB == "mindful":
    # r26: needs postpartum -> unrecorded birth setup; page title is
    # "Family planning & spacing" (Sprint4Screens.kt:196)
    do_birth_setup()
    with Rec("r26_mindful"):
        back_to_home()
        tap("mother: recovery and wellbeing", scroll=True, tries=6, wait=3)
        tap("mindfulness corner", scroll=True, tries=4, wait=3)
        check("mindful_screen", seen("affirmation", "breathing", wait=4))
        shot("mindful"); swipe_down_content(); time.sleep(2); shot("mindful2")
        sh("input keyevent 4"); time.sleep(2)
        tap("family planning", scroll=True, tries=4, wait=3)
        check("familyplan", seen("family planning & spacing", "spacing", wait=4))
        shot("familyplan"); swipe_down_content(); time.sleep(1.5); shot("familyplan2")
        back_to_home()

elif JOB == "vault":
    # r18: postpartum-only quick access (JourneyScreens.kt:461) + needs a saved report
    do_birth_setup()
    do_upload_setup()
    with Rec("r18_vault"):
        back_to_home()
        tap("report vault", scroll=True, tries=8, wait=3)
        check("vault_screen", seen("report vault", "cbc_malayalam_low_hb", wait=4))
        shot("vault"); swipe_down_content(); time.sleep(1.5); shot("vault2")
        back_to_home()

else:
    say("UNKNOWN JOB", JOB); sys.exit(2)

json.dump(RESULTS, open(f"{OUT}/results-{JOB}.json", "w"), indent=1)
say("JOB DONE", JOB, json.dumps(RESULTS))
