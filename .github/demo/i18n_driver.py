"""Device proof that the branch build renders runtime-FETCHED translations.

Logs in on the demo account against the production backend (the APK's default),
lets the app fetch translations over the network, and for each language state
captures a system screencap plus every visible text node classified by script.
Also proves the offline-after-fetch path: cached translations survive a relaunch
with radios off. Synthetic demo data only; read-only against the backend."""
import json, os, re, subprocess, time
import xml.etree.ElementTree as ET

OUT = "/tmp/i18n-out"; os.makedirs(OUT, exist_ok=True)
PKG = "com.caretrail.native"
log = open(f"{OUT}/driver.log", "w")
def say(*a):
    s = " ".join(str(x) for x in a); print(s, flush=True); log.write(s + "\n"); log.flush()
def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True).stdout
def sh(cmd): return adb("shell", cmd)

def dump(_depth=0):
    sh("uiautomator dump /sdcard/u.xml"); xml = sh("cat /sdcard/u.xml")
    if ("isn't responding" in xml or "is not responding" in xml) and _depth < 4:
        say("ANR dialog, tapping Wait"); sh("input keyevent 4"); time.sleep(3)
        return dump(_depth + 1)
    try: return ET.fromstring(xml[xml.index("<"):])
    except Exception: return None

def texts(root):
    for e in (root.iter("node") if root is not None else []):
        t = (e.get("text") or "").strip()
        if t: yield t

def find(text):
    root = dump()
    for e in (root.iter("node") if root is not None else []):
        t = ((e.get("text") or "") + "|" + (e.get("content-desc") or "")).lower()
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", e.get("bounds") or "")
        if m and text.lower() in t:
            x1, y1, x2, y2 = map(int, m.groups())
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def tap(text, tries=10, wait=2.0):
    for _ in range(tries):
        p = find(text)
        if p:
            sh(f"input tap {p[0]} {p[1]}"); time.sleep(wait); say("tap ok:", text); return True
        time.sleep(2)
    say("TAP FAILED:", text); return False

def script_of(s):
    r = "LATIN"
    for ch in s:
        o = ord(ch)
        if 0x0D00 <= o <= 0x0D7F: return "MALAYALAM"
        if 0x0B80 <= o <= 0x0BFF: return "TAMIL"
        if 0x0900 <= o <= 0x097F: r = "DEVANAGARI"
    return r

def capture(state):
    """Screencap + full text-node inventory with script classification."""
    sh(f"screencap -p /sdcard/{state}.png")
    subprocess.run(["adb", "pull", f"/sdcard/{state}.png", f"{OUT}/{state}.png"], capture_output=True)
    # Two dumps: top of page, then after a scroll down, so below-fold strings count.
    seen = {}
    for swipe in (False, True):
        root = dump()
        for t in texts(root):
            seen.setdefault(t, script_of(t))
        if swipe is False:
            sh("input swipe 360 1150 360 500 400"); time.sleep(1.5)
    rows = [{"text": t, "script": s} for t, s in sorted(seen.items())]
    json.dump(rows, open(f"{OUT}/render_{state}.json", "w"), ensure_ascii=False, indent=1)
    latin = [r["text"] for r in rows if r["script"] == "LATIN" and len(r["text"]) > 3 and not re.fullmatch(r"[A-Z0-9 /:.,+%-]+", r["text"])]
    say(f"STATE {state}: {len(rows)} text nodes;",
        {s: sum(1 for r in rows if r['script'] == s) for s in ('MALAYALAM', 'TAMIL', 'DEVANAGARI', 'LATIN')})
    say(f"STATE {state} latin-ui-strings:", json.dumps(latin[:40], ensure_ascii=False))
    return rows

def wait_home(timeout=90):
    t0 = time.time()
    while time.time() - t0 < timeout:
        if find("CARETRAIL") or find("quick access"): return True
        if find("retry"): tap("retry", tries=1, wait=4)
        time.sleep(3)
    return False

say("screen", sh("wm size").strip())
sh(f"am force-stop {PKG}"); time.sleep(2)
sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(10)

# Demo login (persona defaults to Malayalam; the app fetches live on switch).
tap("try the demo", tries=20, wait=6)
if find("skip for now"): tap("skip for now", wait=3)
if not wait_home(): say("HOME NEVER REACHED"); raise SystemExit(1)

# 1) Malayalam: persona default. Give the live fetch time to land, then capture.
say("settling for ml fetch (35s)"); time.sleep(35)
capture("home_ml_fetched")

# 2) Tamil via the header language pill.
pill = None
for cand in ("മലയാളം", "malayalam"):
    pill = find(cand)
    if pill: break
if pill:
    sh(f"input tap {pill[0]} {pill[1]}"); time.sleep(2)
    tap("தமிழ்", tries=6, wait=4)
    say("settling for ta fetch (35s)"); time.sleep(35)
    capture("home_ta_fetched")
else:
    say("LANGUAGE PILL NOT FOUND")

# 3) English baseline via the pill.
pill = find("தமிழ்")
if pill:
    sh(f"input tap {pill[0]} {pill[1]}"); time.sleep(2)
    tap("english", tries=6, wait=4)
    time.sleep(6)
    capture("home_en_baseline")

# 4) Offline-after-fetch: radios off, cold relaunch, cached Malayalam must render.
tapok = find("english")
if tapok:
    sh(f"input tap {tapok[0]} {tapok[1]}"); time.sleep(2); tap("മലയാളം", tries=6, wait=4)
    say("settling for ml refetch (35s)"); time.sleep(35)
sh("svc wifi disable"); sh("svc data disable"); time.sleep(2)
sh(f"am force-stop {PKG}"); time.sleep(2)
sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1"); time.sleep(12)
if find("skip for now"): tap("skip for now", wait=3)
time.sleep(6)
rows = capture("home_ml_offline_cached")
ml_ok = any(r["script"] == "MALAYALAM" for r in rows)
say("OFFLINE CACHED ML RENDERED:", ml_ok)
sh("svc wifi enable"); sh("svc data enable")

say("done")
log.close()
