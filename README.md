# dlrevive

Revive song downloads on a ReVanced-patched **YouTube Music** build, without a Premium subscription.

## Add dlrevive to ReVanced Manager (patch from URL)

You can add dlrevive as a custom patch source — no manual APK building needed.

1. Open **ReVanced Manager** → **Patches** tab.
2. Tap the edit (pencil) button, then the **+** button.
3. Choose **Enter URL** and paste:
   ```
   https://raw.githubusercontent.com/SchooiCodes/dlrevive/feat/manager-compatible-source/manager.json
   ```
4. Keep ReVanced Manager's **default patch source enabled**. Add this source alongside it —
   Manager loads both together.
5. Select a **YouTube Music 8.40.54** APK (the stock package; the GmsCore-support patch will
   rename it to `app.revanced.android.apps.youtube.music`).
6. In the patch list, enable:
   - **GmsCore support** — from the official ReVanced Patches source. This is required: the patched
     app needs **ReVanced GmsCore (microG)** installed on the device to launch and sign in.
   - **Restore song downloads** — from the dlrevive source.
   Keep the official default selection as-is unless you know what you are changing.
7. Tap **Patch**, then install the patched APK through Manager.
8. Tap a song's **Download** action: a `dlrevive: downloading ...` toast appears, the audio file is
   saved under `Music/<folder>/` (default `dlrevive`), and the file can be opened in YTM's own
   preview player from the settings screen's download list. Settings → About shows the dlrevive
   version/status line, and Settings has a **dlrevive** section with enable, audio format,
   download folder, and mobile-data toggles.

**Note:** dlrevive saves public audio files that play in YTM's preview player and other players;
they do **not** appear in YTM's own Offline tab (that needs Premium entitlement).

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
| 7. On-device calibration + verification | Full patch set builds and launches on device; end-to-end download still manual |
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
- `patches/build/libs/dlvrevive-patches-manager.jar` — the ReVanced Manager patch bundle
  (contains `classes.dex`, the `dlrevive-classes.dex` resource and Manager manifest attributes)

`local.properties` (gitignored) must contain your SDK path:

```properties
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## Apply with ReVanced Manager (recommended)

Build the bundle, then add it as a **local patch source** in ReVanced Manager 2.6.0:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :patches:build --console=plain --offline
adb push patches\build\libs\dlvrevive-patches-manager.jar /sdcard/Download/dlvrevive-patches.bin
```

Manager's file picker filters by MIME type, so the JAR is pushed with a `.bin` extension. In
Manager: **Patches → pencil → Add patches → Select from storage → Download → dlvrevive-patches.bin**.
Select an arm64 YouTube Music 8.40.54 APK, keep the **full default patch selection** and tick
**Restore song downloads**, then **Patch → Install**.

The default selection must include the stock **GmsCore support** patch. Without it, a build
installed as `com.google.android.apps.youtube.music` with a non-Google signature is rejected by
Google Play Services and exits on launch with
`Failed to fetch kids onboarding status, finishing the App.` (see [REPORT.md](docs/REPORT.md)).
The stock patch set also changes the package to `app.revanced.android.apps.youtube.music`, which
can be installed alongside stock YTM. ReVanced Patches 6.2.1 has no "Spoof signature" patch.

For the legacy Patcher 19 build, ReVanced CLI **v4.6.0** was used (`revanced-cli.jar` is not
shipped here); see [REPORT.md](docs/REPORT.md) for the exact commands and caveats.

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

- Calibrated for YouTube Music 8.40.54 only; other versions need a new fingerprint (see the
  [calibration guide](docs/calibration.md)).
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
