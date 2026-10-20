# 01 · Architecture

Grabbit is a single-module Compose app. All the logic that can be pure Kotlin is, so it can
be unit tested on the JVM. Android-specific code sits at the edges.

```
com.bentlybro.grabbit
├── core/        pure logic, no Android imports
│   ├── UrlExtractor      finds the video link in messy share or clipboard text
│   ├── SharedText        every field an intent can carry, run through UrlExtractor
│   ├── DownloadFormat    Best / 1080p / 720p / 480p / MP3, plus FormatSelector (yt-dlp args)
│   ├── Progress          Phase + ProgressParser (yt-dlp lines to phase, %, ETA)
│   └── MediaKind         file extension to MediaStore collection, folder and MIME type; FileNames
├── data/
│   ├── QueueItem         a queue entry, QueueReducer (pure state transitions), ErrorText
│   ├── QueueRepository   StateFlow<List<QueueItem>> backed by files/queue.json
│   └── SettingsRepository DataStore preferences (default format, instant share, clipboard)
├── engine/
│   ├── Engine            youtubedl-android wrapper: init, fetchInfo, version, update yt-dlp
│   └── MediaSaver        copies a finished file into MediaStore (pending, then published)
├── download/
│   ├── DownloadScheduler the only class that talks to WorkManager (enqueue/cancel/retry)
│   ├── DownloadWorker    CoroutineWorker running as a dataSync foreground service
│   └── Notifications     channels, progress / done / failed notifications
├── ui/
│   ├── main/             MainActivity, MainViewModel, MainScreen
│   ├── share/            ShareActivity (translucent), ShareViewModel, ShareSheet
│   ├── settings/         SettingsScreen + SettingsViewModel
│   ├── components/       PreviewCard, FormatChips, GrabButton, QueueRow
│   └── theme/, icons/
├── AppGraph              small manual DI: one Engine / repos / scheduler per process
└── GrabbitApp            warms up the engine and reconciles the queue at startup
```

## Data flow

```
paste / share ──► UrlExtractor ──► Engine.fetchInfo (yt-dlp --dump-json) ──► PreviewState
                                                                               │ Download
                                                                               ▼
                         DownloadScheduler.enqueue ──► QueueRepository.add + WorkManager.enqueue
                                                                               │
DownloadWorker: setForeground ─► semaphore(2) ─► yt-dlp into cacheDir/dl/<id> ─┤ progress ─► QueueRepository (StateFlow) ─► UI + notification
                                                                               ▼
                                         MediaSaver (MediaStore, IS_PENDING) ──► DONE + "Saved" notification
```

- **The queue is the source of truth for the UI.** Workers and the UI share one process, so
  the worker updates `QueueRepository` directly, and the UI collects its StateFlow.
  Structural changes (added, started, done, failed and so on) are written to
  `files/queue.json`. Progress ticks are not, because they change many times a second and
  mean nothing after a restart.
- **The state machine is `QueueReducer`.** It's pure and fully unit tested. Some rules
  matter for races: a cancel always beats a late failure or start, a cancel can't undo a
  finished download, and progress updates are ignored unless the item is RUNNING.
- **Restart reconciliation.** When the process starts, any item that claims to be active
  but whose WorkManager work has finished or is missing is marked *Interrupted* (failed)
  so it can be retried. An active item whose work is still alive is set back to QUEUED.
- **Cancel** marks the item cancelled first, so the UI updates straight away. It then
  kills the yt-dlp process tree (`destroyProcessById`) and cancels the work. The Cancel
  action on the notification uses WorkManager's cancel PendingIntent, and the worker then
  records the cancellation itself. On API 31 and later, a stop the system initiated (for
  example, losing the network) re-queues the item instead of marking it cancelled.
- **Concurrency.** WorkManager may start several workers, but a process-wide semaphore
  lets only 2 download at once. The rest show "Waiting…".

## Download engine

[youtubedl-android](https://github.com/yausername/youtubedl-android) **0.18.1**
(`io.github.junkfood02.youtubedl-android`, Maven Central, Nov 2025) bundles a Python
runtime, yt-dlp, QuickJS (used for YouTube's JS challenges) and FFmpeg as per-ABI native
libraries. On first use, `YoutubeDL.init` unpacks Python and yt-dlp into `noBackupFilesDir`,
which takes a few seconds. `Engine.ensureReady()` puts this behind a mutex, because the
UI, the share sheet and a worker can all race to it after a cold start.

**Update yt-dlp** in Settings calls `YoutubeDL.updateYoutubeDL(STABLE)`, which downloads the
latest yt-dlp release from GitHub into app storage. App updates aren't needed for
extractor fixes.

Format mapping (`FormatSelector`, unit tested):

| Choice | yt-dlp args |
|---|---|
| Best | `-f "bv*+ba/b" -S res,ext:mp4:m4a --merge-output-format mp4 --remux-video mp4` |
| 1080p / 720p / 480p | `-f "bv*[height<=H]+ba/b[height<=H]/bv*+ba/b" -S res:H,ext:mp4:m4a …mp4` |
| MP3 | `-f "ba/b" -x --audio-format mp3 --audio-quality 0 --embed-metadata` |

The height-capped selectors end with a plain `bv*+ba/b` fallback. Many sites (TikTok, X,
Reddit) don't report heights or only offer one muxed file, and a strict filter would fail
on them.

Every download also gets `--no-playlist --no-mtime --newline -o "<scratch>/%(title).80B [%(id)s].%(ext)s"`.

## Storage

Files are downloaded into private `cacheDir/dl/<id>/`. They are then copied through
MediaStore with `IS_PENDING` and published once the copy completes, and the scratch
directory is deleted.

| Kind | Collection | Folder |
|---|---|---|
| Video (mp4, webm, mkv…) | `MediaStore.Video` | `Movies/Grabbit` |
| Audio (mp3, m4a…) | `MediaStore.Audio` | `Music/Grabbit` |
| Anything else | `MediaStore.Downloads` | `Download/Grabbit` |

minSdk is **29**, so `RELATIVE_PATH` and scoped storage are always available. No storage
permission is ever requested.

## APK size

The native payload (Python, FFmpeg, QuickJS) is large, so the build uses ABI splits
(`arm64-v8a`, `armeabi-v7a`, `x86_64`) instead of one fat APK. `useLegacyPackaging = true`
is required because the library execs its binaries from `nativeLibraryDir`.
