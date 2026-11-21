# dlrevive

Revive song downloads on a ReVanced-patched **YouTube Music** build, without a Premium subscription.

## Status

Early development. Follow the [plan](docs/plan.md) for scope and milestones.

| Milestone | Status |
| --- | --- |
| 1. Repo + research notes | Done |
| 2. Gradle multi-module scaffold | Pending |
| 3. Integrations entrypoint + config | Pending |
| 4. Anonymous audio resolution | Pending |
| 5. Authenticated audio resolution | Pending |
| 6. ReVanced injection patch | Pending |
| 7. On-device calibration + verification | Pending |
| 8. Docs + final commit | Pending |

## Approach

A ReVanced patch injects a **Download** action into YouTube Music's song/player menu. The
action calls a small Java "integrations" library that is bundled into the patched app. That
library resolves the song to the best **audio-only** stream and hands it to Android's
`DownloadManager`, which saves a playable file to the Music folder with a notification.

`yt-dlp` is used as the **reference specification** for the download flow and format
selection (see [research notes](docs/research.md)). It is not bundled: a patched APK cannot
practically ship a Python interpreter plus a JS runtime. An optional yt-dlp bridge is left
as future work.

### Components

- `integrations/` — Java library (compiled to DEX and merged into YTM).
  - Resolves streams through a `StreamResolver` interface.
  - Anonymous mode uses **NewPipeExtractor**.
  - Authenticated mode uses a small custom Innertube client (cookies / PO tokens).
  - Saves via `android.app.DownloadManager`.
- `patches/` — Kotlin ReVanced patch (compiled to a JAR for ReVanced CLI/Manager).
  - Contains APK-specific fingerprints and the integrations DEX merge.
  - Hooks the download menu click and calls the integrations entrypoint.

## Prerequisites

- JDK 17+
- Gradle (via the wrapper)
- Android SDK build-tools (for `d8` dexing)
- A specific YouTube Music APK to calibrate fingerprints against
- An adb-connected device or emulator for verification

## Disclaimer

This project is for personal, educational use. It does not bypass YouTube's own Premium
entitlement checks; it downloads publicly accessible audio streams directly. Respect local
law and YouTube's Terms of Service. Use of the patched app replaces the stock YouTube Music
installation due to APK signature mismatch.

## License

See [LICENSE](LICENSE).
