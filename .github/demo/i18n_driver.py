"""Device proof that the branch build renders runtime-FETCHED translations.

Navigation helpers are the recording driver's debugged versions (visibility-aware
find, scroll-until-visible tap, ANR recovery). The proof FAILS (non-zero exit)
unless each state is verified IN THE SELECTED LOCALE: actual Home structure
(bottom-nav / care-card in that locale, never satisfiable by the Welcome screen),
the language pill, and EXPECTED dynamic translations PRESENT (strings that only
exist on screen after a successful /i18n/translate fetch - Translator.tr() has no
seeding). Navigation failures exit immediately. Synthetic demo data only."""
import json, os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

OUT = "/tmp/i18n-out"; os.makedirs(OUT, exist_ok=True)
PKG = "com.caretrail.native"
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
        if not scroll: time.sleep(1.5); continue
        if not went_top: to_top(); went_top = True; continue
        swipe_down_content()
    say("TAP FAILED:", text); return False

def script_of(s):
    r = "LATIN"
    for ch in s:
        o = ord(ch)
        if 0x0D00 <= o <= 0x0D7F: return "MALAYALAM"
        if 0x0B80 <= o <= 0x0BFF: return "TAMIL"
        if 0x0900 <= o <= 0x097F: r = "DEVANAGARI"
    return r
def script_counts():
    seen = {}
    for swipe in (False, True):
        root = dump()
        for e in (root.iter("node") if root is not None else []):
            t = (e.get("text") or "").strip()
            if t: seen.setdefault(t, script_of(t))
        if swipe is False: swipe_down_content()
    to_top()
    return seen
def count_script(inv, script):
    return sum(1 for s in inv.values() if s == script)

# Provably-dynamic strings: rendered via l.tr(...) (JourneyScreens.kt:90,262-263),
# whose only source is the on-phone cache populated by successful /i18n/translate
# responses (Translator.kt:59-68, no seeding). EXPECTED values are the exact
# production endpoint responses probed at proof-authoring time; distinctive
# substrings are matched, so server-side rewording fails honestly, not silently.
# Nav labels confirm Home structure in the SELECTED locale; the Welcome screen
# (WelcomeScreens.kt) contains none of these, so it can never pass a Home gate.
# The demo persona HAS a current care step, so Home renders its title (server journey
# data via l.tr), not the empty-state strings. EXPECTED dyn values are the observed
# fetched renderings of that title; if Sarvam rewords, the gate fails honestly.
DYN_EN = ["second-trimester review", "anomaly scan"]
EXPECTED = {
    "ml": {"script": "MALAYALAM", "pill": "മലയാളം",
           "nav": ["മുഖപേജ്", "ആസ്ഥാനം", "ഓർമ്മപ്പെടുത്തലുകൾ", "പ്രൊഫൈല്"],
           "dyn": ["രണ്ടാം ത്രൈമാസ", "അനോമലി സ്കാൻ"]},
    "ta": {"script": "TAMIL", "pill": "தமிழ்",
           "nav": ["முகப்பு", "நினைவூட்டல்கள்", "சுயவிவരம்"],
           "dyn": ["இரண்டாம் காலாண்டு", "முரண்பாட்டு ஸ்கேன்"]},
    "en": {"script": "LATIN", "pill": "english",
           "nav": ["home", "reminders", "profile"],
           "dyn": DYN_EN},
}
def blob(inv): return " || ".join(inv.keys()).lower()
def on_home(inv, lang):
    e = EXPECTED[lang]; b = blob(inv)
    nav_ok = sum(1 for n in e["nav"] if n.lower() in b) >= 2
    dyn_ok = any(d.lower() in b for d in e["dyn"])
    return nav_ok or dyn_ok
def fetched_ok(inv, lang):
    e = EXPECTED[lang]; b = blob(inv)
    dyn_present = any(d.lower() in b for d in e["dyn"])
    return (count_script(inv, e["script"]) >= 3
            and any(e["pill"] in t.lower() for t in inv)
            and dyn_present)

CHECKS = []
def check(name, ok):
    CHECKS.append((name, bool(ok))); say("CHECK", name, "PASS" if ok else "FAIL")
def bail(msg):
    say(msg); say("FAILED CHECKS: [navigation]"); log.close(); sys.exit(1)

def capture(state, inv):
    sh(f"screencap -p /sdcard/{state}.png")
    adb("pull", f"/sdcard/{state}.png", f"{OUT}/{state}.png")
    rows = [{"text": t, "script": s} for t, s in sorted(inv.items())]
    json.dump(rows, open(f"{OUT}/render_{state}.json", "w"), ensure_ascii=False, indent=1)
    latin = [r["text"] for r in rows if r["script"] == "LATIN" and len(r["text"]) > 3 and not re.fullmatch(r"[A-Z0-9 /:.,+%()-]+", r["text"])]
    say(f"STATE {state}: {len(rows)} nodes;", {s: sum(1 for r in rows if r['script'] == s) for s in ('MALAYALAM','TAMIL','LATIN')})
    say(f"STATE {state} latin-ui-strings:", json.dumps(latin[:40], ensure_ascii=False))

def wait_for(predicate, timeout=75, poll=4):
    t0 = time.time()
    while time.time() - t0 < timeout:
        inv = script_counts()
        if predicate(inv): return inv
        time.sleep(poll)
    return script_counts()

screen_size()
sh(f"am force-stop {PKG}"); time.sleep(2)
sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(10)

# Entry is the WELCOME screen (redesign): tagline + language + Log in / Sign up.
# The demo button lives on LoginScreen, below the fold in the scrollable Page.
if not tap("log in", scroll=False, tries=6, wait=4):
    bail("NAV FAILED: welcome screen 'Log in' not found")
if not tap("try the demo", tries=25, wait=6):
    bail("NAV FAILED: LoginScreen 'try the demo' not found")
to_top()
# Post-login reminder setup shows "Skip for now" IN THE CURRENT LOCALE (ml default).
SKIP_LABELS = ["skip for now", "ഇപ്പോൾ ഒഴിവാക്കുക"]
def tap_skip(timeout=60):
    """Wait for the OBSERVED daily-setup screen (its skip button), then tap it.
    Returns False if it never appears (e.g. setupDone already true -> straight Home)."""
    t0 = time.time()
    while time.time() - t0 < timeout:
        for s in SKIP_LABELS:
            if find(s): return tap(s, scroll=False, wait=4)
        time.sleep(3)
    say("skip screen never appeared within", timeout, "s")
    return False
tap_skip()

# GATE 1: real Home, in Malayalam (demo persona's default locale).
home = wait_for(lambda inv: on_home(inv, "ml"), timeout=120)
check("home_reached_ml", on_home(home, "ml"))
if not CHECKS[-1][1]:
    bail("HOME NEVER REACHED in ml - proof failed")

# 1) Malayalam fetched: pill + script + EXPECTED dynamic translation present.
inv = wait_for(lambda inv: fetched_ok(inv, "ml"), timeout=120)
capture("home_ml", inv)  # unconditional: pixels/JSON show the observed state whatever the verdict
check("ml_fetched_render", fetched_ok(inv, "ml"))

# 2) Tamil fetched via the header pill.
if not tap("മലയാളം", scroll=False, wait=2): bail("NAV FAILED: ml pill not found")
if not tap("தமிழ்", scroll=False, wait=4): bail("NAV FAILED: ta option not found")
inv = wait_for(lambda inv: fetched_ok(inv, "ta"), timeout=120)
capture("home_ta", inv)
check("ta_fetched_render", fetched_ok(inv, "ta"))

# 3) English baseline: dynamic strings back in English.
if not tap("தமிழ்", scroll=False, wait=2): bail("NAV FAILED: ta pill not found")
if not tap("english", scroll=False, wait=4): bail("NAV FAILED: en option not found")
inv = wait_for(lambda inv: fetched_ok(inv, "en"), timeout=60)
capture("home_en", inv)
check("en_baseline", fetched_ok(inv, "en"))

# 4) Offline-after-fetch: refetch ml, radios off, cold relaunch, cache must render.
if not tap("english", scroll=False, wait=2): bail("NAV FAILED: en pill not found")
if not tap("മലയാളം", scroll=False, wait=4): bail("NAV FAILED: ml option not found")
inv = wait_for(lambda inv: fetched_ok(inv, "ml"), timeout=120)
sh("svc wifi disable"); sh("svc data disable"); time.sleep(2)
sh(f"am force-stop {PKG}"); time.sleep(2)
sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(12)
tap_skip()
inv = wait_for(lambda inv: on_home(inv, "ml"), timeout=60)
capture("home_offline", inv)
off_ok = on_home(inv, "ml") and fetched_ok(inv, "ml")
check("ml_offline_cached_render", off_ok)
sh("svc wifi enable"); sh("svc data enable")

fails = [n for n, ok in CHECKS if not ok]
say("FAILED CHECKS:", fails if fails else "none")
log.close()
sys.exit(1 if fails else 0)
