# 🐇 Grabbit

**Share a link, grab the video.** A personal Android video downloader for YouTube, TikTok, Instagram, X, Reddit and the [1,800+ other sites yt-dlp supports](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md).

> 🚧 **Status: work in progress.** The first version (v0.1.0) is being built now. This README describes what the MVP covers and will stay up to date as features land.

---

## ✨ How it works: share first

The fastest way to use Grabbit is to never open it:

1. In YouTube, TikTok, Instagram, X, Reddit or any other app, tap **Share** and pick **Grabbit**.
2. A small bottom sheet slides up **on top of the app you're in**. It shows the title, uploader, duration and thumbnail, with your default format already selected.
3. Tap **Download**. The sheet closes, you stay where you were, and the download runs in the background with a progress notification.

Prefer zero taps? Turn on **Download instantly on share** in Settings. Sharing to Grabbit then starts the download right away with a toast and a notification, and no sheet appears.

Messy share text is fine. TikTok's "Check out this video! https://vm.tiktok.com/… #fyp", links spread across several lines, and links that only appear in the share subject are all handled. Grabbit finds the video link and ignores the rest.

## 📋 Features (MVP)

- **Share target**: appears in the Android share sheet for links from any app.
- **Paste box**: open the app, paste a link (or tap **Paste**). Grabbit also offers a link it finds on your clipboard when you open it.
- **Preview before download**: title, uploader, duration and thumbnail.
- **Format choice**: **Best** (video and audio merged to MP4), **1080p**, **720p**, **480p**, or **Audio only (MP3)**.
- **Download queue**: per-item progress, ETA, cancel and retry.
- **Background downloads**: run through WorkManager as a foreground service with a progress notification, so they keep going after you leave the app.
- **Saved where you'd expect**: videos go to `Movies/Grabbit`, audio to `Music/Grabbit`, and anything else to `Download/Grabbit`. Files are saved through MediaStore, so they show up in your gallery and music apps. Tap a finished item to open or share it.
- **Settings**: default format, *Download instantly on share*, and an **Update yt-dlp** button that shows the current version. Sites change often, and updating yt-dlp is usually the fix.
- **Dark Material 3 design** built with Jetpack Compose.

## 📸 Screenshots

Coming soon. They'll live in [`docs/screenshots/`](docs/screenshots/) and cover:

- the main screen with the paste box and queue
- the link preview with format chips
- the share sheet over another app
- a download in progress

## 🧱 Tech stack

| | |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | ViewModel + StateFlow, clean packages (`core`, `data`, `engine`, `download`, `ui`) |
| Download engine | [youtubedl-android](https://github.com/yausername/youtubedl-android) `0.18.1` (bundles Python, yt-dlp, ffmpeg, and QuickJS), with yt-dlp updatable in the app |
| Background work | WorkManager with a foreground service (`dataSync`) |
| Storage | MediaStore for output, DataStore for settings |
| Images | Coil 3 |
| Build | AGP 9.4, Gradle wrapper, version catalog (`gradle/libs.versions.toml`), JDK 17 |
| Android | minSdk 29 (Android 10), targetSdk 36 |

## 🛠️ Build

You need JDK 17 and the Android SDK (compileSdk 37).

```sh
git clone https://github.com/bently-bot/grabbit.git
cd grabbit
./gradlew assembleDebug lint testDebugUnitTest
```

The bundled Python, ffmpeg, and yt-dlp native libraries make the app large, so the build makes **one APK per ABI** instead of a single fat APK:

```
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk     ← almost every modern phone
app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk   ← old 32-bit phones
app/build/outputs/apk/debug/app-x86_64-debug.apk        ← emulator
```

## 📲 Install

Install with adb:

```sh
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Or download the APK from a CI run's artifacts, or from a GitHub release once one exists, and open it on your phone. You'll need to allow installs from that source.

On first launch Grabbit unpacks its bundled Python and yt-dlp, which takes a few seconds. On Android 13 and later it asks for permission to show notifications so it can display download progress.

## 🤖 CI and releases

- **CI** runs on every push and PR. It does `assembleDebug`, `lint` and `testDebugUnitTest`, then uploads the debug APKs as artifacts.
- **Emulator smoke test** (`emulator.yml`) boots a KVM-accelerated API 34 emulator on GitHub Actions. It launches the app, fires a messy TikTok-style share intent at the share sheet, and runs a real download into `Movies/Grabbit`. Screenshots and logcat are uploaded. See [docs/03-testing.md](docs/03-testing.md).
- **Releases**: pushing a `v*` tag builds the APKs and creates a **draft** GitHub release with them attached. Nothing goes public until it's published by hand.

## 📚 Docs

- [01 · Architecture](docs/01-architecture.md): packages, data flow, download engine, storage, APK size
- [02 · The share flow](docs/02-share-flow.md): ShareActivity, instant mode, finding the link in messy share text
- [03 · Testing](docs/03-testing.md): unit tests and the emulator smoke test

## ⚖️ Use responsibly

Grabbit is a personal tool. **You are responsible for how you use it.** Respect each site's terms of service and the rights of creators. Only download content you own, have permission to download, or are otherwise legally allowed to save, such as content under a licence that permits it. Don't use Grabbit to redistribute copyrighted material.

## 🙏 Credits

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) does the actual extracting and downloading.
- [youtubedl-android](https://github.com/yausername/youtubedl-android) and [JunkFood02](https://github.com/JunkFood02) package it for Android.
- [FFmpeg](https://ffmpeg.org/) handles merging and audio conversion.
