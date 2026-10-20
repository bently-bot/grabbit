# 02 · The share flow

Sharing is the main way to use Grabbit, so it gets its own activity.

## ShareActivity

- Handles `ACTION_SEND` with `text/plain`. It's labelled **Download with Grabbit** in the
  share sheet.
- Uses a translucent theme (`Theme.Grabbit.Share`): the window is transparent with no
  animation, so only the Compose `ModalBottomSheet` and its scrim are drawn. The sheet
  floats over YouTube, TikTok and other apps.
- Sets `excludeFromRecents="true"` and `taskAffinity=""`, so it never shows up in recents
  and never brings the main Grabbit task to the front.
- **Download** queues the item through `DownloadScheduler`, shows a short toast and
  finishes. You stay in the source app while WorkManager runs the download as a
  foreground service with a progress notification.
- On Android 13 and later, the first share asks for notification permission after the
  download is queued, then closes. The download runs either way.
- **Open in Grabbit** passes the link to `MainActivity` (`EXTRA_URL`).

### Instant mode

With **Settings → Download instantly on share** turned on, `ShareActivity` draws nothing.
It queues the link with the default format, shows *"🐇 Grabbing it in the background"*
and finishes. The worker fetches the title and thumbnail itself, because nothing was
previewed.

## Finding the link

Share payloads are messy. `ShareIntents.toSharedText()` collects every place a link can
be:

1. `EXTRA_TEXT` (a CharSequence, which may span several lines)
2. the intent's data URI
3. each `ClipData` item (text or URI)
4. `EXTRA_SUBJECT`, then `EXTRA_TITLE`

`UrlExtractor` (see `UrlExtractorTest` / `SharedTextTest`) then:

- collects every `http(s)://` URL across those fields, in that order
- trims trailing punctuation, ellipses, quotes and *unbalanced* closing brackets, so
  `…/Rabbit_(disambiguation)` keeps its `)`
- prefers the first URL on a known video host (YouTube, TikTok, Instagram, X/Twitter,
  Reddit, Facebook, Vimeo, Twitch, SoundCloud, Bluesky, Threads, …) over tracking or
  "download our app" links
- falls back to the first URL found
- with no scheme at all, accepts a bare `youtu.be/…` or `www.…` link and adds `https://`

Example: TikTok's
`Check out this video! https://vm.tiktok.com/ZMabc123/ #fyp`
→ `https://vm.tiktok.com/ZMabc123/`.

## Testing it by hand

```sh
adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "'Check out this video! https://youtu.be/jNQXAC9IVRw #fyp'" \
  -n com.bentlybro.grabbit/.ui.share.ShareActivity
```

The emulator workflow (`.github/workflows/emulator.yml`, `scripts/ci/emulator-smoke.sh`)
runs exactly this and saves a screenshot of the sheet.
