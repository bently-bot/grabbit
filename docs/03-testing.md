# 03 · Testing

## Unit tests (JVM)

```sh
./gradlew testDebugUnitTest
```

| Test | Covers |
|---|---|
| `UrlExtractorTest` | TikTok, Instagram and X share text, multiple lines, trailing punctuation, balanced brackets, known-host preference, subject fallback, bare links, non-links |
| `SharedTextTest` | priority between EXTRA_TEXT, data URI, ClipData and EXTRA_SUBJECT |
| `FormatSelectorTest` | the yt-dlp arguments for every format, fallbacks, MP3 extraction |
| `ProgressAndMediaTest` | yt-dlp line to phase, % and ETA clamping, durations, MediaStore folder and MIME mapping, filename sanitising |
| `QueueReducerTest` | queue state machine: lifecycle, cancel/fail/progress races, retry, clear, restart reconciliation, friendly error messages (YouTube bot check, 429, unsupported URL…) |
| `StatusTextTest` | the status line each queue row shows |

## Emulator smoke test (GitHub Actions)

`.github/workflows/emulator.yml` runs on pushes to `main` that touch the app (or when
started by hand). It boots a KVM-accelerated API 34 `google_apis` x86_64 emulator and
runs `scripts/ci/emulator-smoke.sh`, which:

1. installs the x86_64 debug APK and launches the main screen
2. sends a messy TikTok-style `ACTION_SEND` to `ShareActivity`, checks the sheet appears,
   waits for the preview, taps **Download**, and checks the sheet closes
3. opens the main screen with a direct public test video (Big Buck Bunny, CC BY), waits
   for the preview, taps **Download**, watches the queue go RUNNING then DONE, and checks
   the file is in `Movies/Grabbit` through MediaStore

Screenshots, `queue.json`, the MediaStore listing and logcat are uploaded as the
`emulator-smoke-<sha>` artifact. Whether the real-site download from step 2 succeeds is
logged but doesn't fail the job. GitHub runners use datacenter IPs, and YouTube always answers those with a "Sign in to confirm you're not a bot" check, which the app shows as *"YouTube blocked this request…"*.

## On a phone

```sh
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Then share a video from YouTube or TikTok to **Download with Grabbit**.

## Real downloads (`real-downloads.yml`)

The smoke test proves the UI flows. This workflow proves **downloads actually work**, site by site. It runs on app changes, on PRs, daily at 06:17 UTC (sites break without us changing anything) and on demand.

- **URLs**: `scripts/ci/real-downloads/urls.tsv`. These are short, long-lived public videos from YouTube (×2), TikTok, Vimeo, Reddit, X, Instagram and Dailymotion, plus a plain `.mp4` link.
- **Host job** (`matrix.py host`): runs yt-dlp straight on the runner with the app's exact options (Python 3.11 and QuickJS, like the APK). It runs twice, once with the yt-dlp **bundled in the APK** and once with the **latest nightly** (what the app self-updates to). If the host fails too, the problem is the engine or the site, not the app.
- **Emulator job** (`emulator.sh`): installs the debug APK on a KVM API 34 emulator and lets it self-update its engine. It then queues every URL through the debug-only `DebugCommandReceiver`, which uses the same scheduler, worker, engine and MediaStore saver as a share. When each download finishes it reads `queue.json` over `run-as`, pulls the saved file from MediaStore and checks it with `ffprobe`. The file has to be non-empty, parse, and have a duration and a stream.
- **Summary job**: writes a per-platform table to the run summary:
  - ✅ **pass**: the file was saved and is valid.
  - 🚫 **blocked**: the site refused the datacenter IP (bot check, IP block, login wall or 429). That isn't an app result.
  - ❌ **fail**: anything else.

  The job fails if the plain mp4 fails anywhere, or if the app fails where the same engine succeeded on the host (an app bug).
