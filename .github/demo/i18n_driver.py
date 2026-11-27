"""Device proof that the branch build renders runtime-FETCHED translations.

Navigation helpers are the recording driver's debugged versions (visibility-aware
find, scroll-until-visible tap, ANR recovery). The proof FAILS (non-zero exit)
unless each state is actually verified: Home reached, selected language confirmed
on screen, expected script present in rendered text. A job that only reached the
login screen is a failed proof, not a green one. Synthetic demo data only."""
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
    """Merged inventory of the current page (top + one scroll down)."""
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

CHECKS = []
def check(name, ok):
    CHECKS.append((name, bool(ok))); say("CHECK", name, "PASS" if ok else "FAIL")

# Provably-dynamic strings: rendered via l.tr(...) (JourneyScreens.kt:90,262-263), whose
# only sources are the on-phone SharedPreferences cache, populated ONLY by successful
# /i18n/translate responses (Translator.kt:59-68, no seeding). If these render translated
# on a fresh install, the fetch happened on this device. Pill labels and >=N script nodes
# cannot prove that (they may be bundled I18nData maps).
DYN = ["no care step due right now", "open your journey to see all steps"]
def dyn_gone(inv):
    low = [t.lower() for t in inv]
    return all(not any(d in t for t in low) for d in DYN)
def fetched_ok(inv, script, pill):
    return (count_script(inv, script) >= 3 and any(pill in t for t in inv) and dyn_gone(inv))

def capture(state, inv):
    """Name artifacts only for a verified state; caller checks first."""
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

# Demo login (persona defaults to Malayalam; the app fetches live after switch).
tap("try the demo", tries=20, wait=6)
to_top()
if find("skip for now"): tap("skip for now", wait=3)

# GATE 1: Home must actually be reached before anything is called a proof.
home = wait_for(lambda inv: any("caretrail" == t.lower() for t in inv), timeout=90)
check("home_reached", any(t.lower() == "caretrail" for t in home))
if not CHECKS[-1][1]:
    say("HOME NEVER REACHED - proof failed"); log.close(); sys.exit(1)

# 1) Malayalam fetched: pill + Malayalam script + dynamic care-card strings no longer English.
inv = wait_for(lambda inv: fetched_ok(inv, "MALAYALAM", "മലയാളം"), timeout=120)
check("ml_fetched_render", fetched_ok(inv, "MALAYALAM", "മലയാളം"))
if CHECKS[-1][1]: capture("home_ml_fetched", inv)

# 2) Tamil fetched via the header pill.
tap("മലയാളം", scroll=False, wait=2)
tap("தமிழ்", scroll=False, wait=4)
inv = wait_for(lambda inv: fetched_ok(inv, "TAMIL", "தமிழ்"), timeout=120)
check("ta_fetched_render", fetched_ok(inv, "TAMIL", "தமிழ்"))
if CHECKS[-1][1]: capture("home_ta_fetched", inv)

# 3) English baseline.
tap("தமிழ்", scroll=False, wait=2)
tap("english", scroll=False, wait=4)
inv = wait_for(lambda inv: any("english" in t.lower() for t in inv), timeout=30)
check("en_baseline", any("english" in t.lower() for t in inv))
if CHECKS[-1][1]: capture("home_en_baseline", inv)

# 4) Offline-after-fetch: refetch ml, radios off, cold relaunch, cache must render.
tap("english", scroll=False, wait=2)
tap("മലയാളം", scroll=False, wait=4)
inv = wait_for(lambda inv: fetched_ok(inv, "MALAYALAM", "മലയാളം"), timeout=120)
sh("svc wifi disable"); sh("svc data disable"); time.sleep(2)
sh(f"am force-stop {PKG}"); time.sleep(2)
sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(12)
if find("skip for now"): tap("skip for now", wait=3)
inv = wait_for(lambda inv: fetched_ok(inv, "MALAYALAM", "മലയാളം") or any(t.lower() == "caretrail" for t in inv), timeout=60)
off_ok = fetched_ok(inv, "MALAYALAM", "മലയാളം")
check("ml_offline_cached_render", off_ok)
if off_ok: capture("home_ml_offline_cached", inv)
sh("svc wifi enable"); sh("svc data enable")

fails = [n for n, ok in CHECKS if not ok]
say("FAILED CHECKS:", fails if fails else "none")
log.close()
sys.exit(1 if fails else 0)
