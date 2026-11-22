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

Commits, in order: `f856d0e`, `b8ab899`, `2d3621c`, `ec7acd4`, `c31e1d3`, `bbaae64`, `b8a33f5`, `cbf1806`, `2059fcf`, plus the documentation commit for the verified apply/install results.

## Build result

Verified from a clean state with JDK 17 (the default `java` on PATH is a JRE 25 and cannot run
Gradle's compilation tasks):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat clean build
```

Result: **BUILD SUCCESSFUL**, 13/13 tasks executed, including `integrations:test` and
`integrations:dexIntegrations`. Outputs:

- `integrations/build/dex/classes.dex`
- `patches/build/libs/dlvrevive-patches.jar`

## On-device verification (performed)

Using ReVanced CLI **v4.6.0** (newer v5/v6 CLI versions bundle a patcher without
`BytecodeContext` and cannot load this patch):

1. `list-patches -p -v` on the patch JAR lists "Restore song downloads" for package
   `com.google.android.apps.youtube.music`, version `8.40.54`.
2. Applying the patch logs **"Restore song downloads succeeded"** and merges the DEX. The
   patched DEX contains `dev/dlrevive/integrations/DownloadIntegration` (verified by scanning
   the extracted `classes*.dex` bytes).
3. The patched APK was signed with SDK `apksigner` (`apksigner verify`: v2 + v3 schemes OK).
4. `adb install -r` → **Success** on the attached device, and
   `pm path com.google.android.apps.youtube.music` resolves to `/data/app/.../base.apk`.
5. The app process starts and remains alive with no crash/fatal in logcat.

Not verified: the signed-in UI action (Share → playable audio file). The device is in active
personal use, so its UI was not driven. This is the only remaining manual check.

## Exact build, patch, sign and install commands

```powershell
cd C:<repo-root>
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat clean build

# ReVanced CLI v4.6.0 is required for this patcher API (v5/v6 cannot load the patch).
# It is not shipped in this repo; download revanced-cli-4.6.0-all.jar and set its path:
$cli = 'C:\Users\Schooi\AppData\Local\Temp\opencode\revanced-cli-4.6.0-all.jar'

# Some Windows environments leave the Gradle output JAR unopenable (delete-pending quirk).
# If `jar tf $jar` fails, reassemble it from the compiled classes:
$jar = 'C:<repo-root>\patches\build\libs\dlvrevive-patches.jar'
$assembled = "$env:TEMP\opencode\dlvrevive-patches-assembled.jar"
$stage = "$env:TEMP\opencode\patchjar"
if (-not (Test-Path $assembled)) {
  New-Item -ItemType Directory -Force $stage | Out-Null
  Copy-Item -Recurse -Force 'patches\build\classes\kotlin\main\*' $stage
  Copy-Item -Recurse -Force 'patches\build\classes\java\main\*' $stage
  Copy-Item -Recurse -Force 'patches\build\resources\main\*' $stage
  & "$env:JAVA_HOME\bin\jar.exe" --create --file $assembled -C $stage .
}

# Use an arm64 8.40.54 APK on arm64-only devices; the bundled apk/ file is 32-bit only.
$apk = 'C:\Users\Schooi\Downloads\com.google.android.apps.youtube.music_8.40.54-84054240_minAPI26(arm64-v8a)(nodpi)_apkmirror.com.apk'
$work = "$env:TEMP\opencode\ytm-patchwork"
$unsigned = "$work\$([IO.Path]::GetFileName($apk))"
$signed = "$env:TEMP\opencode\ytmusic-dlrevive-arm64.apk"

java -jar $cli patch -b $assembled -o "$work\patched.apk" `
  --custom-aapt2-binary='C:\Users\Schooi\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe' `
  -t $work $apk
# The CLI's built-in signer fails with 'JCE cannot authenticate the provider BC' on JDK 17;
# it still leaves the aligned unsigned APK at <temp>\<apk filename>.

# Create a keystore once (keep it outside git):
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -storetype PKCS12 `
  -keystore "$env:TEMP\opencode\dlrevive.p12" -alias 'ReVanced Key' `
  -storepass <redacted> -keypass <redacted> -dname 'CN=dlrevive' -validity 10000
& 'C:\Users\Schooi\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat' sign `
  --ks "$env:TEMP\opencode\dlrevive.p12" --ks-key-alias 'ReVanced Key' `
  --ks-pass pass:<redacted> --key-pass pass:<redacted> `
  --out $signed $unsigned
& 'C:\Users\Schooi\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat' verify $signed

adb install -r $signed
```

After install, open a song and use **Share** — this both shares and starts the download.
Inspect `Music/dlvrevive/` and `adb logcat | findstr dlrevive` to confirm. A config file is
written on first use at `<external files dir>/dlrevive/config.json`
(see `README.md` for keys). The patched APK uses a different key than stock YouTube Music, so
uninstall stock YTM first (or use a rooted/mounted setup).

## Not implemented, with evidence

1. **Signed-in end-to-end download is untested.** The patch applies, the APK installs and the
   app launches (evidence above), but triggering Share → download with a signed-in account was
   not automated because the attached device is in active personal use. Evidence for what
   *was* verified: CLI v4.6.0 apply log, patched-DEX scan, `apksigner verify`, `adb install`,
   `pm path`, logcat. Remaining step: manually share a song and check `Music/dlvrevive/` and
   `adb logcat | findstr dlrevive`.
2. **Not a literal yt-dlp implementation.** Per the confirmed decision above, yt-dlp is the
   reference specification only; the in-app engine is NewPipeExtractor (anonymous) plus a
   small Innertube client (authenticated). Evidence: [research.md](research.md) §1 (yt-dlp
   needs Python + a JS runtime, which cannot be embedded in an APK patch) and the plan's
   recorded decision. An optional yt-dlp bridge remains out of scope.
3. **A dedicated per-song Download menu item was not added.** Production YTM 8.40.54 obfuscates
   its classes and static inspection found no per-song download handler; the share-intent builder
   was the stable, reachable hook. Consequence: sharing a song also starts its download.
   Evidence: `docs/calibration.md`.
4. **YTM's own Premium-gated download service is not bypassed.** It validates entitlement
   server-side; the patch fetches the direct audio stream instead (`docs/research.md` §4).
5. **32-bit APK cannot install on the device under test.** `apk/youtube-music-8.40.54.apk` is
   `armeabi-v7a` only, while the device is arm64-v8a only: `adb install` returned
   `INSTALL_FAILED_NO_MATCHING_ABIS`. An arm64 8.40.54 APK was used instead; the fingerprint
   matched and installation succeeded.
