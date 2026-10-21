#!/usr/bin/env bash
# Real downloads through the installed debug app on an emulator (or any adb device).
# Every URL in urls.tsv goes through the app's own scheduler -> worker -> yt-dlp -> MediaStore
# path (via the debug-only DebugCommandReceiver), then the saved file is pulled back off the
# device and checked with ffprobe. Writes $OUT/results-emulator.json.
set -uo pipefail

PKG=com.bentlybro.grabbit
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=${OUT:-real-downloads}
APK=${APK:-app/build/outputs/apk/debug/app-x86_64-debug.apk}
FORMAT=${FORMAT:-P480}
TIMEOUT=${TIMEOUT:-900}       # whole batch
RECEIVER="$PKG/.debug.DebugCommandReceiver"
mkdir -p "$OUT/files"

say() { echo "== $*"; }
# </dev/null: adb shell otherwise swallows the stdin of the `while read` loop below, and only
# the first URL ever gets queued.
cmd() { adb shell am broadcast -n "$RECEIVER" -a "com.bentlybro.grabbit.debug.$1" "${@:2}" >/dev/null </dev/null; }
appfile() { adb exec-out run-as "$PKG" cat "files/$1" 2>/dev/null </dev/null; }

say "install"
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r -g "$APK" || { echo "::error::install failed"; exit 1; }
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c || true
adb shell am start -W -n "$PKG/.ui.main.MainActivity" >/dev/null

# First launch unpacks Python and kicks off the engine self-update; force one more update
# explicitly so we know (and can report) exactly which yt-dlp the downloads ran on.
say "engine: bundled version"
cmd ENGINE_INFO; sleep 5
for i in $(seq 1 30); do appfile debug/engine.txt | grep -q '^version=[0-9]' && break; sleep 3; cmd ENGINE_INFO; done
BUNDLED=$(appfile debug/engine.txt | sed -n 's/^version=//p')
say "engine: update"
adb exec-out run-as "$PKG" rm -f files/debug/engine.txt
cmd UPDATE_ENGINE
for i in $(seq 1 60); do appfile debug/engine.txt | grep -q '^result=' && break; sleep 3; done
appfile debug/engine.txt | tee "$OUT/engine.txt"
ENGINE=$(appfile debug/engine.txt | sed -n 's/^version=//p')
echo "bundled=$BUNDLED updated=$ENGINE"

say "enqueue"
while IFS=$'\t' read -r platform url _; do
  case "$platform" in \#*) continue ;; esac
  [ -z "$platform" ] && continue
  cmd ENQUEUE --es url "$url" --es tag "$platform" --es format "$FORMAT"
  sleep 1
done < "$HERE/urls.tsv"

say "wait (up to ${TIMEOUT}s)"
start=$(date +%s)
while :; do
  appfile queue.json > "$OUT/queue.json"
  active=$(python3 -c 'import json,sys; q=json.load(open(sys.argv[1])); print(sum(1 for i in q if i["status"] in ("QUEUED","RUNNING")))' "$OUT/queue.json" 2>/dev/null || echo 99)
  [ "$active" = 0 ] && break
  [ $(( $(date +%s) - start )) -ge "$TIMEOUT" ] && { echo "::warning::timed out with $active still active"; break; }
  sleep 10
done
appfile debug/tags.txt > "$OUT/tags.txt"
adb exec-out screencap -p > "$OUT/final.png" || true

say "verify"
python3 - "$OUT" "$ENGINE" "$BUNDLED" "$HERE" <<'PY'
import json, os, re, subprocess, sys
out, engine, bundled, here = sys.argv[1:5]
sys.path.insert(0, here)
import matrix  # noqa: E402

queue = {i["id"]: i for i in json.load(open(os.path.join(out, "queue.json")))}
tags = dict(l.split("\t") for l in open(os.path.join(out, "tags.txt")).read().split("\n") if "\t" in l)
url_of = {u["platform"]: u["url"] for u in matrix.urls()}

def sh(*a):
    return subprocess.run(["adb", "shell", *a], capture_output=True, text=True).stdout

rows = []
for platform, url in url_of.items():
    item = queue.get(tags.get(platform, ""))
    if not item:
        rows.append(matrix.result(platform, url, "FAIL", "never reached the queue")); continue
    st = item["status"]
    extra = {"retries": item.get("autoRetries", 0), "engine": item.get("engineVersion")}
    if st == "DONE":
        uri = item.get("outputUri", "")
        m = re.search(r"/(\d+)$", uri)
        # One column per query: titles contain commas, which make a multi-column row ambiguous.
        path = re.search(r"_data=(.*)$", sh("content", "query", "--uri", uri, "--projection", "_data").strip(), re.M)
        size = re.search(r"_size=(\d+)", sh("content", "query", "--uri", uri, "--projection", "_size"))
        size = int(size.group(1)) if size else 0
        local = os.path.join(out, "files", f"{platform}{os.path.splitext(item.get('outputName') or '.mp4')[1]}")
        ok, desc = False, "not in MediaStore"
        if path and size > 0:
            subprocess.run(["adb", "pull", path.group(1).strip(), local], capture_output=True)
            ok, desc = matrix.ffprobe(local)
            desc = f"{desc}; MediaStore {size} B as {item.get('outputName')}"
        elif path:
            desc = f"MediaStore row has size {size}"
        rows.append(matrix.result(platform, url, "PASS" if ok else "FAIL", desc, bytes=size, **extra))
    elif st == "FAILED":
        detail = item.get("errorDetail") or item.get("error") or ""
        kind = matrix.classify(detail)
        rows.append(matrix.result(platform, url, "BLOCKED" if kind in matrix.BLOCKED else "FAIL",
                                  f"{detail}  [app said: {item.get('error')}]", kind=kind, **extra))
    else:
        rows.append(matrix.result(platform, url, "FAIL", f"still {st} at timeout", **extra))
    print(f"{platform}: {rows[-1]['outcome']} - {rows[-1]['detail']}")

json.dump({"source": "emulator", "engine": engine, "bundledEngine": bundled, "results": rows},
          open(os.path.join(out, "results-emulator.json"), "w"), indent=2)
PY
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
# The pulled videos are only evidence for this run; don't bloat the artifact with them.
du -sh "$OUT/files" || true
exit 0
