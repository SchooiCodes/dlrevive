# Research: how YouTube Music downloads work, and what a ReVanced patch can do

This document records the findings that informed the design in [plan.md](plan.md).

Sources are linked inline. Direct web search was unavailable during research, so the
primary-source repositories were fetched and searched locally via context-mode's
`ctx_index` / `ctx_search`.

---

## 1. How yt-dlp fetches and downloads YouTube Music audio

References: [yt-dlp/yt-dlp](https://github.com/yt-dlp/yt-dlp).

### The extraction pipeline

`yt-dlp` is both a CLI and a Python library. The essential flow is:

1. `YoutubeDL.extract_info(url, download=False)` -> `youtube_dl.extractor.youtube` matches a
   `watch?v=`, `music.youtube.com/...`, playlist or channel URL.
2. The extractor builds an **Innertube** `player` request using a set of clients and obtains a
   `playerResponse` containing `streamingData.formats` / `adaptiveFormats`.
3. Each format is parsed into an `info_dict` with `url` (or a signature-deciphered URL),
   `format_id`, `ext`, `vcodec`, `acodec`, `tbr`, etc.
4. `YoutubeDL.process_info` selects a format via `format_selector` and dispatches to a
   downloader (`HttpFD` for direct URLs; ffmpeg post-processing for merges).
5. The downloader writes the file with resume support and optional rate limiting.

### Format selection (the part we mirror)

- Default selector: `bv*+ba/b` — best video + best audio, merged with **ffmpeg**.
- Audio-only: `-f bestaudio` is `best*[vcodec=none]` — typically m4a (AAC) or opus.
  `-x --audio-format mp3` extracts/transcodes using ffmpeg.
- Our patch deliberately mirrors `best*[vcodec=none]`: pick the audio-only stream with the
  highest bitrate, preferring opus then m4a.

### YouTube Music specifics

- For `music.youtube.com` URLs yt-dlp adds the `web_music` Innertube client **when logged-in
  cookies are present**.
- For free/anonymous accounts the client order is roughly `tv_samsung,web_embedded,web`.
- Clients such as `web_music` and `web_creator` are **PO-token (proof-of-origin) gated**;
  without a valid PO token many audio formats are withheld or degraded.

### Dependencies yt-dlp relies on

| Dependency | Purpose |
| --- | --- |
| `yt-dlp-ejs` | Provides the challenge solver scripts for YouTube's signature/n-throttle JS |
| JS runtime (deno recommended; node, bun, QuickJS also work) | Executes those scripts |
| ffmpeg | Muxing A/V and audio extraction/transcoding |
| cookies (`--cookies-from-browser`) | Authenticated requests, members-only content |
| `visitor_data`, `po_token` | Required by several modern Innertube clients |
| `curl_cffi` | TLS fingerprint impersonation |

**Conclusion:** yt-dlp achieves no-Premium audio downloads by talking to public Innertube
endpoints and solving their JS challenges *outside* the app. That logic cannot be embedded
verbatim in an APK patch (Python + JS runtime), so we implement the equivalent subset in
Java.

---

## 2. What stacher contributes

Reference: [stacher.io](https://stacher.io/) (page is client-rendered; only the raw HTML was
retrievable during research).

Stacher is an **Electron GUI frontend for yt-dlp**, not an alternative extractor. Its flow is:

1. User pastes a URL (or a queue of URLs) and picks options/format.
2. Stacher builds a yt-dlp command line, spawns the yt-dlp process.
3. yt-dlp emits machine-readable progress (`--newline` / JSON); Stacher parses it.
4. Completed files land wherever yt-dlp was told to write them.

So studying Stacher clarifies the **end-to-end UX/flow** — queue, format choice, progress,
output folder — but contains no audio-extraction logic. The extraction intelligence is
entirely yt-dlp's. Our patch mirrors the UX shape (one tap -> resolved audio stream -> saved
file with notification) while replacing the engine.

---

## 3. NewPipeExtractor as the in-process engine

Reference: [TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).

- Java library, distributed through JitPack:
  `com.github.teamnewpipe:NewPipeExtractor:<version>`.
- `YouTubeService` handles both `youtube.com` and `music.youtube.com` URLs and returns
  `StreamInfo` objects containing `AudioStream` and `VideoStream` entries.
- Audio-only selection maps directly onto our `best*[vcodec=none]` requirement.
- Build notes from its README: keep Rhino in ProGuard rules (used for JS decryption), and
  enable core library desugaring when `minSdk < 33`.
- Limitation: it is **anonymous-first**. Cookie/PO-token support is limited compared with
  yt-dlp, which is why the design includes a separate authenticated resolver behind the same
  interface, with graceful fallback.

The user selected this option over shipping a local yt-dlp service, accepting that the final
patch is therefore *not* literally "via yt-dlp."

---

## 4. What a ReVanced patch can and cannot do

References:
[revanced-patcher](https://github.com/ReVanced/revanced-patcher),
[revanced-cli](https://github.com/ReVanced/revanced-cli),
[revanced-patches-template](https://github.com/ReVanced/revanced-patches-template).

- A patch is Kotlin code using `app.revanced:revanced-patcher:<version>`, built into a JAR by
  Gradle and applied by ReVanced CLI/Manager to **one specific APK version**.
- Matching is done with **fingerprints** (method names, opcodes, string constants, resource
  IDs). These are brittle and must be re-calibrated for every YTM release.
- Patches can modify bytecode and resources, and can merge a companion DEX into the target
  APK (the pattern used by ReVanced Integrations). This is how our Java library gets in.
- There is no working official patch that restores Premium-gated downloads: the old
  "Downloads" patch called YTM's own download service, whose entitlement is verified
  **server-side**. It cannot be made to work without Premium.
- Therefore our patch does not touch YTM's download service at all. It injects a call to our
  own resolver and uses `DownloadManager` to fetch the direct stream.

Publishing to ReVanced's repository requires GPG signing keys; local builds via the template
do not. We use the template's structure (`patches/build.gradle.kts`,
`gradle/libs.versions.toml`) without publishing.

---

## 5. Local toolchain snapshot

| Tool | State |
| --- | --- |
| git | 2.55.0 (`C:\Program Files\Git\cmd\git.exe`) — OK |
| Java | Temurin **JRE** 25.0.4.1 only; no `javac`; `JAVA_HOME` unset — **JDK needed** |
| Gradle | Not installed — wrapper will fetch it |
| Android SDK | `ANDROID_HOME` / `ANDROID_SDK_ROOT` empty; no `d8`, no `adb` — **SDK needed** |
| Python | `py` 3.14.7; `python` is the WindowsApps stub; yt-dlp installed under Python311 Scripts |
| node | Installed |
| deno | Not installed |

A YTM APK and an adb-connected device must be supplied before fingerprint calibration and
on-device verification can happen.
