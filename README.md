# 🐇 Grabbit

**Share a link, grab the video.** A personal Android video downloader for YouTube, TikTok, Instagram, X, Reddit and the [1,800+ other sites yt-dlp supports](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md).

> 🧪 **Status: v0.1.0, early but working.** All the MVP features below are built. Unit tests pass, and on every push an emulator test on GitHub Actions opens the share sheet and does a real download into `Movies/Grabbit`. It hasn't been tried on a physical phone yet.

---

## ✨ Share first

The quickest way to use Grabbit is to never open it:

1. In YouTube, TikTok, Instagram, X, Reddit or any other app, tap **Share** and pick **Download with Grabbit**.
2. A bottom sheet slides up **over the app you're in**. It shows the title, uploader, duration and thumbnail, with your **default format already selected**.
3. Tap **Download**. The sheet closes and you're back where you were. The download carries on in the background with a progress notification.

**Even fewer taps:** turn on **Settings → Download instantly on share**. Sharing to Grabbit then starts the download straight away with your default format. You get a toast and a notification instead of the sheet.

**Messy share text is fine.** TikTok's `Check out this video! https://vm.tiktok.com/… #fyp`, links spread over several lines, links that only appear in the share subject, and bare `youtu.be/…` links without `https://` all work. Grabbit picks out the video link and ignores tracking or "get the app" links. See [docs/02-share-flow.md](docs/02-share-flow.md).

## 📸 Screenshots

These were captured automatically by the emulator smoke test on GitHub Actions.

| Main screen | Share sheet (over the home screen) | Link preview |
|---|---|---|
| <img src="docs/screenshots/01-main.png" width="240"> | <img src="docs/screenshots/02-share-sheet.png" width="240"> | <img src="docs/screenshots/03-preview.png" width="240"> |

| Downloading | Progress notification | Saved |
|---|---|---|
| <img src="docs/screenshots/04-progress.png" width="240"> | <img src="docs/screenshots/05-notification.png" width="240"> | <img src="docs/screenshots/06-done.png" width="240"> |

The test video is *Big Buck Bunny*, © Blender Foundation, licensed CC BY 3.0.

## 📋 Features

- **Share target** (`ACTION_SEND` text/plain). It opens a translucent bottom sheet that's kept out of recents, so you never leave the app you shared from.
- **Download instantly on share** (optional): no sheet, just a toast and a notification.
- **Paste box** with a **Paste** button. When you open the app with a link on your clipboard, Grabbit offers to use it (you can turn this off).
- **Preview first**: title, uploader, duration, site and thumbnail (loaded with Coil).
- **Formats**: **Best** (video and audio merged to MP4), **1080p**, **720p**, **480p**, or **Audio only (MP3)**. The height limits fall back gracefully on sites that don't report a resolution.
- **Download queue** with per-item progress, ETA, merging and converting status, **cancel**, **retry** and **remove**. Up to 2 downloads run at once.
- **Background downloads** use WorkManager as a `dataSync` foreground service. The notification shows progress and has a Cancel button, and you get a "Saved" or "Failed" notification when a download ends.
- **Saved where you'd expect**, through MediaStore, so files show up in your gallery and music apps without any storage permission:
  - video goes to `Movies/Grabbit`
  - MP3 goes to `Music/Grabbit`
  - anything else goes to `Download/Grabbit`

  Tap a finished item to open it, or use the share icon to send it on.
- **Plain-English errors**: for example, *"YouTube blocked this request. Try again later, or update yt-dlp in Settings."* instead of a raw yt-dlp stack.
- **Settings** (saved with DataStore): default format, instant-on-share, clipboard offers, and **Update yt-dlp**, which shows the current version.
- **Dark Material 3 design** built with Jetpack Compose, with a custom adaptive icon that also has a themed-icon version.

## ⚠️ Known limits

- **YouTube sometimes asks for a bot check** (*"Sign in to confirm you're not a bot"*). This happens mostly on datacenter and VPN IP addresses. It **always** happens on GitHub Actions runners, so the emulator test only reports the YouTube result and doesn't fail on it. On a normal home or mobile connection it's usually fine. If it happens on your phone, try again later or tap **Update yt-dlp**. Grabbit doesn't support cookies or logins.
- Posts that need a login (private Instagram accounts and similar) won't work.
- The APK is large (see below) because it bundles Python, yt-dlp, FFmpeg and QuickJS.

## 📲 Install

You need **Android 10 or later** (minSdk 29).

1. Get the **arm64-v8a** APK. Almost every phone from the last several years needs this one. You can download it from:
   - the latest successful [CI run](../../actions/workflows/ci.yml): open the run, then **Artifacts → `grabbit-debug-<sha>`**, which is a zip of all three APKs
   - a [release](../../releases), once one has been published
   - or a local build (see below)
2. Install it, either over USB:
   ```sh
   adb install -r app-arm64-v8a-debug.apk
   ```
   or by copying it to the phone and opening it there. You'll need to allow installs from that source.
3. Open Grabbit once. It unpacks its bundled Python and yt-dlp, which takes a few seconds the first time, and asks for permission to show notifications so you can see download progress.

| APK (debug) | Size | For |
|---|---|---|
| `app-arm64-v8a-debug.apk` | ~90 MB | almost every modern phone |
| `app-armeabi-v7a-debug.apk` | ~83 MB | old 32-bit phones |
| `app-x86_64-debug.apk` | ~93 MB | emulators / Chromebooks |

## 🛠️ Build

You need JDK 17 and the Android SDK (compileSdk 37). Everything else comes from the Gradle wrapper.

```sh
git clone https://github.com/bently-bot/grabbit.git
cd grabbit
./gradlew assembleDebug lint testDebugUnitTest
# APKs: app/build/outputs/apk/debug/app-{arm64-v8a,armeabi-v7a,x86_64}-debug.apk
```

## 🧱 Tech stack

| | |
|---|---|
| Language / UI | Kotlin 2.4, Jetpack Compose (BOM 2026.09), Material 3 |
| Architecture | ViewModel + StateFlow. Packages: `core` (pure logic), `data`, `engine`, `download`, `ui` |
| Download engine | [youtubedl-android](https://github.com/yausername/youtubedl-android) **0.18.1** (bundles Python, yt-dlp, FFmpeg and QuickJS). yt-dlp can be updated from inside the app |
| Background work | WorkManager 2.12 with a foreground service (`dataSync`) |
| Storage | MediaStore for downloaded files, DataStore for settings, a small JSON file for the queue |
| Images | Coil 3 |
| Build | AGP 9.4, Gradle 9.8 wrapper, version catalog (`gradle/libs.versions.toml`), JDK 17, ABI splits |
| Android | minSdk 29, targetSdk 36, compileSdk 37 |

## 🤖 CI, tests and releases

- **CI** (`ci.yml`) runs on every push to `main` and every PR. It runs `assembleDebug`, `lint` and `testDebugUnitTest`, fails if no tests actually ran, and uploads the debug APKs and the lint report as artifacts.
- **Emulator smoke test** (`emulator.yml`) runs on app changes or when started by hand. It uses a KVM-accelerated API 34 emulator on Actions to:
  - launch the app
  - send a messy TikTok-style share intent and check that the sheet appears and closes after **Download**
  - download a public test video and check that it reaches `Movies/Grabbit`

  Screenshots, the queue state and logcat are uploaded as artifacts. Details are in [docs/03-testing.md](docs/03-testing.md).
- **Cutting a release**:
  1. Set `appVersionName` in `app/build.gradle.kts`, for example `0.2.0`.
  2. Commit, then run `git tag v0.2.0 && git push origin v0.2.0`.
  3. `release.yml` checks that the tag matches the version, runs the tests, builds per-ABI APKs and creates a **draft** GitHub release with them attached. Review it and publish it by hand.

  If the repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` are set, the APKs are release-signed. Otherwise they're debug-signed. Keystores never go in git.

## 📚 Docs

- [01 · Architecture](docs/01-architecture.md): packages, data flow, download engine, format mapping, storage, APK size
- [02 · The share flow](docs/02-share-flow.md): ShareActivity, instant mode, finding the link in messy share text
- [03 · Testing](docs/03-testing.md): unit tests and the emulator smoke test

## ⚖️ Use responsibly

Grabbit is a personal tool. **You're responsible for how you use it.** Respect each site's terms of service and the rights of creators. Only download content you own, have permission to download, or are otherwise legally allowed to save, for example content under a licence that allows it. Don't use Grabbit to redistribute copyrighted material.

## 🙏 Credits

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) does the actual extracting and downloading.
- [youtubedl-android](https://github.com/yausername/youtubedl-android) by yausername, maintained by [JunkFood02](https://github.com/JunkFood02), packages it for Android.
- [FFmpeg](https://ffmpeg.org/) handles merging and MP3 conversion.
