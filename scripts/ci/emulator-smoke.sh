#!/usr/bin/env bash
# End-to-end smoke test on an emulator. Run by .github/workflows/emulator.yml, but works
# against any connected device/emulator with the x86_64 (or matching) debug APK built.
#
#  1. install + launch MainActivity            -> 01-main.png
#  2. fire a messy TikTok-style SEND intent at ShareActivity
#     -> assert the sheet appears, screenshot  -> 02-share-sheet*.png
#     -> tap Download in the sheet (real-site download, reported but not fatal)
#  3. open MainActivity with a direct public test-video URL, wait for the preview,
#     tap Download, assert progress and a DONE item saved to MediaStore
#                                              -> 03-preview, 04-progress, 05-notification, 06-done
set -uo pipefail

PKG=com.bentlybro.grabbit
OUT=${OUT:-screenshots}
APK=${APK:-app/build/outputs/apk/debug/app-x86_64-debug.apk}
SHARE_URL=${SHARE_URL:-https://www.youtube.com/watch?v=jNQXAC9IVRw}
# Big Buck Bunny (c) Blender Foundation, CC BY 3.0 - a stable, legal test file.
DIRECT_URL=${DIRECT_URL:-https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_30MB.mp4}
mkdir -p "$OUT"

log() { echo "::group::$*" 2>/dev/null; echo "== $*"; echo "::endgroup::" 2>/dev/null; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $OUT/$1.png"; }
ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb exec-out cat /sdcard/ui.xml 2>/dev/null; }
has_text() { ui | grep -q "text=\"$1\""; }
# Tap the centre of the first node whose text matches exactly.
tap_text() {
  local b
  b=$(ui | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ')
  [ -z "$b" ] && { echo "no node with text '$1'"; return 1; }
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
# uiautomator can't dump while an indeterminate spinner animates, so check the focused
# window for "is the sheet up" and treat a failed dump as "still loading".
# The sheet lives in its own dialog window, so look at the resumed activity as well as the
# focused window/app; any of them naming ShareActivity means the sheet is up.
focused() {
  adb shell dumpsys activity activities 2>/dev/null | grep -E 'topResumedActivity|mResumedActivity' | head -2
  adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -2
}
wait_focus() { # activity-substring timeout
  local t=0
  until focused | grep -q "$1"; do sleep 2; t=$((t + 2)); [ $t -ge "$2" ] && return 1; done
}
wait_loaded() { # timeout: wait until a dump succeeds and shows no "Fetching info"
  local t=0 x
  while [ $t -lt "$1" ]; do
    x=$(ui)
    if echo "$x" | grep -q 'text="Download' && ! echo "$x" | grep -q 'Fetching info'; then return 0; fi
    sleep 3; t=$((t + 3))
  done
  return 1
}
wait_text() { # text timeout
  local t=0
  until has_text "$1"; do sleep 2; t=$((t + 2)); [ $t -ge "$2" ] && return 1; done
}
queue() { adb exec-out run-as $PKG cat files/queue.json 2>/dev/null; }
status_of() { # url -> status of newest item with that url
  queue | python3 -c "import json,sys
try: q=json.load(sys.stdin)
except Exception: q=[]
m=[i for i in q if i.get('url')==sys.argv[1]]
print(m[0]['status'] if m else 'NONE')" "$1"
}
detail_of() {
  queue | python3 -c "import json,sys
q=json.load(sys.stdin); m=[i for i in q if i.get('url')==sys.argv[1]]
print(json.dumps(m[0], indent=1) if m else '{}')" "$1" 2>/dev/null
}

fail=0

log "install"
# Fresh install every time: a cached AVD can hold a build signed by another runner's
# throwaway debug key (INSTALL_FAILED_UPDATE_INCOMPATIBLE), and we want first-run state.
adb uninstall $PKG >/dev/null 2>&1 || true
adb install -g "$APK" || exit 1
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c || true

log "1. launch main"
adb shell am start -W -n $PKG/.ui.main.MainActivity
wait_text "Downloads" 60 || { echo "main screen never rendered"; fail=1; }
sleep 3
shot 01-main

log "2. share intent with messy TikTok-style text"
adb shell input keyevent KEYCODE_HOME
sleep 1
TEXT="Check out this video! 🐰 $SHARE_URL #fyp #foryou
Download TikTok now: https://www.tiktok.com/download"
adb shell am start -W -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "'$TEXT'" \
  --es android.intent.extra.SUBJECT "'Shared video'" \
  -n $PKG/.ui.share.ShareActivity
if wait_focus ShareActivity 45; then
  echo "share sheet is up: $(focused | head -1)"
  sleep 3
  shot 02-share-sheet-loading
  # Give yt-dlp time to fetch the preview (first run also unpacks Python).
  wait_loaded 120 && echo "share preview finished loading" || echo "WARN: share preview still loading after 120s"
  sleep 2
  shot 02-share-sheet
  ui > "$OUT/02-share-sheet-ui.xml"
  tap_text "Download" || tap_text "Download now" || { echo "ERROR: could not tap Download in sheet"; fail=1; }
  sleep 4
  focused | grep -q ShareActivity \
    && { echo "ERROR: share sheet did not close after Download"; fail=1; } || echo "sheet closed after Download"
  shot 02-after-share-download
  echo "share-sheet item status: $(status_of "$SHARE_URL")"
else
  echo "ERROR: share sheet did not appear. Focus state:"; focused; shot 02-share-sheet-missing; fail=1
fi

log "3. main screen direct download"
adb shell am start -W -n $PKG/.ui.main.MainActivity --es com.bentlybro.grabbit.URL "$DIRECT_URL" -f 0x24000000
wait_loaded 120 || echo "WARN: main preview still loading"
sleep 2
shot 03-preview
tap_text "Download" || tap_text "Download (fetching info…)" || { echo "no Download button"; fail=1; }

saw_progress=0
for i in $(seq 1 120); do
  s=$(status_of "$DIRECT_URL")
  if [ "$s" = "RUNNING" ] && [ $saw_progress -eq 0 ]; then
    sleep 2; shot 04-progress; saw_progress=1
    adb shell cmd statusbar expand-notifications; sleep 2; shot 05-notification
    adb shell cmd statusbar collapse
  fi
  [ "$s" = "DONE" ] || [ "$s" = "FAILED" ] || [ "$s" = "CANCELLED" ] && break
  sleep 2
done
echo "direct download final status: $s"
detail_of "$DIRECT_URL"
sleep 2
shot 06-done
[ "$s" = "DONE" ] || { echo "ERROR: direct download did not finish"; fail=1; }
[ $saw_progress -eq 1 ] || echo "WARN: never observed RUNNING state (download too fast?)"

log "MediaStore check"
adb shell "content query --uri content://media/external/video/media --projection _display_name:relative_path:_size" \
  | grep "Grabbit" | tee "$OUT/mediastore.txt"
grep -q "Grabbit" "$OUT/mediastore.txt" || { echo "ERROR: nothing in Movies/Grabbit"; fail=1; }

log "share-sheet (real site) download result"
for i in $(seq 1 60); do
  s2=$(status_of "$SHARE_URL")
  [ "$s2" = "DONE" ] || [ "$s2" = "FAILED" ] && break
  sleep 3
done
echo "share download final status: $s2 (informational only: YouTube bot-checks datacenter IPs, never fails the job)"
detail_of "$SHARE_URL"
shot 07-final

queue > "$OUT/queue.json"
adb logcat -d > "$OUT/logcat.txt" || true
echo "smoke result: $([ $fail -eq 0 ] && echo PASS || echo FAIL)"
exit $fail
