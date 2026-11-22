# Final report

Target: YouTube Music `com.google.android.apps.youtube.music` **8.40.54**
(`apk/youtube-music-8.40.54.apk`). Patches are calibrated for this version only.

## Implemented

| Area | File | What it does |
| --- | --- | --- |
| Patch | `patches/src/main/kotlin/dev/dlrevive/patches/DownloadSongsPatch.kt` | ReVanced `BytecodePatch`. Fingerprints the 8.40.54 share-intent builder `Lajgu;->a(...)` (returns `Intent`, `public static`, 5 params) and injects, at index 0, `invoke-static {p0, p4, p1}, Ldev/dlrevive/integrations/DownloadIntegration;->downloadFromUri(Context, Uri, String)V`. Declares compatibility with YTM 8.40.54. |
| Patch | `patches/src/main/java/dev/dlrevive/patches/DexMerge.java` | Adds the bundled integrations DEX to the patcher context and enables DEX merging. |
| Entrypoint | `integrations/.../DownloadIntegration.java` | Parses the video ID from the shared URL, runs resolution + `DownloadManager` enqueue on a background executor, writes a default config on first use, posts a toast on the main looper. |
| Resolvers | `StreamResolver.java`, `NewPipeResolver.java`, `InnertubeResolver.java`, `InnertubeResponseParser.java`, `HttpDownloader.java` | Anonymous resolution via NewPipeExtractor; authenticated resolution via a small Innertube `player` client using cookies / PO token / visitor data, falling back to anonymous on failure. |
| Selection | `AudioStreamSelector.java`, `ResolvedStream.java` | Mirrors yt-dlp `best*[vcodec=none]`: picks the highest-bitrate audio-only stream with a small preference bonus for the configured container / opus / m4a. |
| Persistence / helpers | `DownloadConfig.java`, `FileNames.java`, `ResolutionException.java`, `UnavailableResolver.java` | JSON config, safe filenames, typed errors. |
| Packaging | `integrations/build.gradle.kts`, `patches/build.gradle.kts` | D8-dexes a fat JAR to `classes.dex`, embeds it in the patch JAR as `/dlrevive-classes.dex`. |
| Tests | `integrations/src/test/java/dev/dlrevive/integrations/*` | 5 JUnit test classes covering ID extraction, config parsing, filename sanitising, stream selection and Innertube response parsing. |
| Docs | `docs/research.md`, `docs/plan.md`, `docs/calibration.md`, `README.md` | Research, plan, fingerprint recalibration guide, build/apply/config instructions. |

Commits, in order: `f856d0e`, `b8ab899`, `2d3621c`, `ec7acd4`, `c31e1d3`, `bbaae64`, `b8a33f5`, `cbf1806`.

## Build result

Verified from a clean state with JDK 17 (the default `java` on PATH is a JRE 25 and cannot run
Gradle's compilation tasks):

```
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat clean build
```

Result: **BUILD SUCCESSFUL**, 13/13 tasks executed, including `integrations:test` and
`integrations:dexIntegrations`. Outputs:

- `integrations/build/dex/classes.dex`
- `patches/build/libs/dlvrevive-patches.jar`

The built APK and all keystores/secrets are gitignored; the working tree is clean.

## Exact build and install command

```powershell
cd C:<repo-root>
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat clean build

# ReVanced CLI is not shipped in this repo (no revanced-cli.jar is present).
# Put your own copy on disk and set its path here:
$cli = 'C:\path\to\revanced-cli.jar'
$apk = 'C:<repo-root>\apk\youtube-music-8.40.54.apk'
$out = 'youtube-music-revived.apk'

java -jar $cli patch -p 'patches\build\libs\dlvrevive-patches.jar' -o $out $apk
adb install -r $out
```

Check `java -jar $cli patch --help` if your CLI version uses different flag names. The patched
APK is signed with a different key than stock YouTube Music, so uninstall stock YTM first (or
use a rooted/mounted setup). After install, open a song and use **Share** — this both shares and
starts the download. Inspect `Music/dlvrevive/` and `adb logcat | findstr dlrevive` to confirm.
A config file is written on first use at `<external files dir>/dlrevive/config.json`
(see `README.md` for keys).

## Not implemented, with evidence

1. **On-device verification / apply-time fingerprint check is blocked.** `adb devices` (SDK
   `platform-tools`) lists no attached device and no emulator is running; no `revanced-cli.jar`
   is present in the repo. Therefore the patch could not be applied or exercised at runtime.
   Static calibration against the supplied APK and a full clean build did pass. Evidence and
   the steps to close this are recorded in `docs/plan.md` and `docs/calibration.md`.
2. **Literal yt-dlp is not bundled.** The confirmed design uses yt-dlp only as the reference
   specification for format selection (`-f bestaudio` → `best*[vcodec=none]`); the in-app engine
   is NewPipeExtractor plus a small Innertube client. Evidence: yt-dlp needs Python and a JS
   runtime, which cannot practically ship inside an APK patch (`docs/research.md` §1), and the
   decision recorded in `docs/plan.md`.
3. **A dedicated per-song Download menu item was not added.** Production YTM 8.40.54 obfuscates
   its classes and static inspection found no per-song download handler; the share-intent builder
   was the stable, reachable hook. Consequence: sharing a song also starts its download.
   Evidence: `docs/calibration.md`.
4. **YTM's own Premium-gated download service is not bypassed.** It validates entitlement
   server-side; the patch fetches the direct audio stream instead (`docs/research.md` §4).
