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
| `QueueReducerTest` | queue state machine: lifecycle, cancel/fail/progress races, retry, clear, restart reconciliation, error messages |
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
logged but doesn't fail the job, because sites like YouTube often block datacenter IPs.

## On a phone

```sh
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Then share a video from YouTube or TikTok to **Download with Grabbit**.
