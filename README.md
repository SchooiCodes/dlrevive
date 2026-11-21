# dlrevive

Revive song downloads on a ReVanced-patched **YouTube Music** build, without a Premium subscription.

## Status

Follow the [plan](docs/plan.md) for scope and milestones.

| Milestone | Status |
| --- | --- |
| 1. Repo + research notes | Done |
| 2. Gradle multi-module scaffold | Done |
| 3. Integrations entrypoint + config | Done |
| 4. Anonymous audio resolution | Done |
| 5. Authenticated audio resolution | Done |
| 6. ReVanced injection patch | Done |
| 7. On-device calibration + verification | Static calibration done; device test pending |
| 8. Docs + final commit | Done |

The patch is calibrated for **YouTube Music 8.40.54**. Production YTM classes are obfuscated and no
per-song download menu handler could be identified statically, so the patch hooks the share-intent
builder `Lajgu;->a(...)` instead. **Sharing a song now also starts its download.** Rebuilding for
a different YTM version requires recalibration — see the [calibration guide](docs/calibration.md).

## Approach

A ReVanced patch hooks YouTube Music's **share** action. When you share a song, the patched app
also calls a small Java "integrations" library that is bundled into the patched app. That library
resolves the song to the best **audio-only** stream and hands it to Android's `DownloadManager`,
which saves a playable file to the Music folder with a notification.

This is a pragmatic deviation from a dedicated Download menu item: the per-song download handler
is obfuscated in production YTM builds and could not be located reliably. See
[calibration notes](docs/calibration.md).

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

## Build

The wrapper downloads Gradle; set `JAVA_HOME` explicitly if your default `java` is a JRE.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew build
.\gradlew :integrations:dexIntegrations
.\gradlew :patches:jar
```

Outputs:

- `integrations/build/dex/classes.dex` — bundled into the patch JAR as `dlrevive-classes.dex`
- `patches/build/libs/dlvrevive-patches.jar` — the ReVanced patch

`local.properties` (gitignored) must contain your SDK path:

```properties
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## Apply

Calibrate the fingerprint first ([guide](docs/calibration.md)), then apply with ReVanced CLI:

```powershell
java -jar revanced-cli.jar list-patches -p patches\build\libs\dlvrevive-patches.jar
java -jar revanced-cli.jar patch -p patches\build\libs\dlvrevive-patches.jar `
    -o youtube-music-revived.apk youtube-music.apk
adb install -r youtube-music-revived.apk
```

Check your ReVanced CLI version's `--help` for exact flag names. The patched APK is signed
differently from stock YouTube Music, so it cannot be installed over it — uninstall stock YTM
first or use a rooted setup.

## Configuration

On first use the app writes `<external files dir>/dlrevive/config.json`. Edit it to change
behaviour; malformed or missing files fall back to defaults.

```json
{
  "authMode": "ANONYMOUS",
  "cookieHeader": "",
  "poToken": "",
  "visitorData": "",
  "preferredExtension": "m4a",
  "downloadSubdirectory": "dlrevive",
  "allowMobileData": true
}
```

| Key | Meaning |
| --- | --- |
| `authMode` | `ANONYMOUS` uses NewPipeExtractor; `AUTHENTICATED` uses an Innertube client with your cookies/PO token and falls back to anonymous on failure. |
| `cookieHeader` | Full `Cookie:` header value from a logged-in YouTube Music session. Keep this file private. |
| `poToken` / `visitorData` | Optional PO token and visitor data required by some clients/tracks. |
| `preferredExtension` | Preferred audio format: `m4a` or `opus` (the selector still picks the highest-bitrate usable stream). |
| `downloadSubdirectory` | Folder under `Music/` where files are saved. |
| `allowMobileData` | Whether `DownloadManager` may use metered connections. |

## Limitations

- Not calibrated yet: the current fingerprint is broad and will fail until narrowed for your APK.
- Anonymous resolution depends on NewPipeExtractor, which breaks whenever YouTube changes its
  clients. Update the dependency and recalibrate as needed.
- Authenticated resolution skips `signatureCipher`-protected formats (solving YouTube's JS
  challenge is out of scope here) and may need a newer Innertube client version.
- No ffmpeg transcoding: files are saved in the stream's original audio container.
- This does not restore YTM's own Premium-gated Download service; it fetches publicly accessible
  audio streams directly.

## Disclaimer

This project is for personal, educational use. It does not bypass YouTube's own Premium
entitlement checks; it downloads publicly accessible audio streams directly. Respect local
law and YouTube's Terms of Service. Use of the patched app replaces the stock YouTube Music
installation due to APK signature mismatch.

## License

See [LICENSE](LICENSE).
