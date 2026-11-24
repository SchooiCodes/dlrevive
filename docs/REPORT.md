# Report

Target: YouTube Music **8.40.54**. All patches are calibrated for this version only.

## Current state (ReVanced Manager compatible)

The patch is a **Patcher 22** patch and is distributed as a prebuilt patch bundle JAR that
ReVanced Manager loads as a local patch source:

- `patches/src/main/kotlin/dev/dlrevive/patches/DownloadSongsPatch.kt` — top-level
  `restoreSongDownloadsPatch = bytecodePatch(...) { compatibleWith("com.google.android.apps.youtube.music"("8.40.54")); extendWith("dlrevive-classes.dex"); apply { ... } }`.
  It finds `Lajgu;->a` (the 8.40.54 share-intent builder) and injects at index 0:
  `invoke-static {p0, p4, p1}, Ldev/dlrevive/integrations/DownloadIntegration;->downloadFromUri(Landroid/content/Context;Landroid/net/Uri;Ljava/lang/String;)V`
  (`p0` = Context, `p1` = title, `p4` = Uri).
- `patches/build.gradle.kts` — compiles against the Patcher 22 classes bundled in
  `revanced-cli-6.0.0-all.jar` (`compileOnly`, downloaded into `build/cli`), sets the Manager
  manifest attributes, copies `integrations/build/dex/classes.dex` into the bundle as
  `dlrevive-classes.dex`, and **D8-compiles the patch classes into a root-level `classes.dex`**
  (required by Manager's Android patch loader; otherwise
  `lanchon.multidexlib2.EmptyMultiDexContainerException` / "Couldn't load patches").
- Output: `patches/build/libs/dlvrevive-patches-manager.jar` (~1.57 MB) containing
  `classes.dex`, `dlrevive-classes.dex`, `DownloadSongsPatchKt.class`, and manifest attributes
  `Name: dlrevive patches`, `Version: 1.0.0`, plus description/source/author/license.
- `integrations/` — unchanged Java resolver/selector/DownloadManager code.

Commits for this work: `193c3f6` (Patcher 22 port, `DexMerge.java` removed) and `3160807`
(D8-compiled `classes.dex` packaged in the Manager bundle).

## Root cause of the "crashes the moment I open it" report

The earlier single-patch build installed over the **stock package name**
`com.google.android.apps.youtube.music` with a self-signed key. Process-alive checks were
misleading: the process stays up while the activity exits. Filtering logcat by the launch
window showed:

```
E YouTubeMusic: Failed to fetch kids onboarding status, finishing the App.
E Auth: java.lang.SecurityException: GoogleCertificatesRslt: not allowed:
  pkg=com.google.android.apps.youtube.music,
  sha256=[c9dc1bf028374f0cb2be20f0f420fd2eba734f137e83b59c6b4b74d61742e9f0], ...
```

So the app deliberately calls `finish()` when the kids-onboarding status call fails,
because stock Google Play Services rejects the re-signed APK's certificate. It is **not** a
Java crash and **not** our injected instruction:

- the hook is in the share-intent builder, not startup;
- `dexdump` of the installed APK showed `Lajgu;->a` is `PUBLIC STATIC`, `ins: 5`, parameters
  `(Context, String, String, String, Uri)` — the registered `{p0, p4, p1}` mapping is correct;
- a control launch of the stock, Google-signed 8.40.54 APK produced no onboarding error and
  the activity stayed in the foreground.

**Fix/configuration:** build through ReVanced Manager with the **full default patch set**,
which includes the stock **GmsCore support** patch (and the stock package-name change to
`app.revanced.android.apps.youtube.music`) in addition to `Restore song downloads`.
Manager 2.6.0's ReVanced Patches 6.2.1 has **no "Spoof signature" patch**, so that older
workaround is unavailable; GmsCore support is the supported path.

## Verified with ReVanced Manager 2.6.0 (device <device-serial>)

1. Build bundle: `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\gradlew.bat :patches:build --console=plain --offline` → BUILD SUCCESSFUL.
2. Push `patches/build/libs/dlvrevive-patches-manager.jar` to the device (Manager's local
   picker requires a `.bin` MIME workaround: `adb shell cp ... /sdcard/Download/dlvrevive-patches.bin`).
3. Manager → Patches → pencil → Add patches → Select from storage → pick the `.bin`.
   Manager shows `dlrevive patches 1.0.0 • 1 patch` next to `ReVanced Patches 6.2.1 • 46 patches`.
4. Select YouTube Music 8.40.54 APK, keep the stock default selection (20 patches) and
   ensure `Restore song downloads` is checked, tap Patch. Build completed: stock patches and
   `Restore song downloads` all `completed`, `Saving 2/2`.
5. Install through Manager (Play Protect scan → install) → installs as
   `app.revanced.android.apps.youtube.music`.
6. Launch with `adb shell monkey -p app.revanced.android.apps.youtube.music -c android.intent.category.LAUNCHER 1`.
   Logcat: **0** "Failed to fetch kids onboarding", **0** `GoogleCertificatesRslt`,
   **0** FATAL EXCEPTION / VerifyError / NoSuchMethodError / NoClassDefFoundError / dlrevive
   errors. `dumpsys window` showed focus on YTM's own activity, and the UI was interactive.
7. Pulled the installed `base.apk`: the integration DEX (`DownloadIntegration`) is present in
   its DEX files. Full-set compatibility confirmed.

### Exact Manager build/install commands

```powershell
cd <repo-root>
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :patches:build --console=plain --offline

adb push patches\build\libs\dlvrevive-patches-manager.jar /sdcard/Download/dlvrevive-patches-manager.jar
adb shell cp /sdcard/Download/dlvrevive-patches-manager.jar /sdcard/Download/dlvrevive-patches.bin
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/dlvrevive-patches.bin
# In ReVanced Manager 2.6.0: Patches tab -> pencil -> Add patches -> Select from storage
# -> My Files -> Internal storage -> Download -> dlvrevive-patches.bin -> Done -> Add.
# Apps tab -> YouTube Music 8.40.54 -> Select patches (keep defaults + tick Restore song
# downloads) -> Save -> Patch -> Install.
```

## Legacy CLI path (Patcher 19; superseded by the Manager path)

For reference, the original patch (before the Patcher 22 port) was applied with ReVanced CLI
**v4.6.0**; CLI v5/v6 cannot load Patcher 19 patches. That build was signed with SDK
`apksigner` (CLI's BKS signer fails on JDK 17 with `JCE cannot authenticate the provider BC`)
and installed, but with the stock package name and no GmsCore support it exhibited the
signature/onboarding failure described above. See git history before `193c3f6`.

## Not implemented / unverified

1. **Signed-in end-to-end download (Share → playable file)** is still untested; the attached
   device is in active personal use. The app with the full patch set launches and our
   integration DEX is present, but the actual download action should be checked manually.
2. **Not literal yt-dlp**: yt-dlp is the reference spec only; the in-app engine is
   NewPipeExtractor plus a small custom Innertube client. Evidence: [research.md](research.md) §1.
3. **No dedicated Download menu item**: the 8.40.54 share-intent builder is the stable hook,
   so sharing a song also starts its download. Evidence: [calibration.md](calibration.md).
4. **No Premium entitlement bypass**: YTM's own download service is server-side gated;
   the patch fetches the direct audio stream. Evidence: [research.md](research.md) §4.
5. **32-bit APK cannot install on the arm64-only device**: use an arm64 8.40.54 APK.

## Note: sanitization before publishing (2026-11-25)
Before this repository was made public, device-specific identifiers and a local signing-password
mention were scrubbed from the tracked docs (this file and `plan.md`), and internal device-log notes
were removed from the repository entirely. Git history was rewritten and verified to contain no
device serials, personal paths, or password strings. This is why those documents changed shape.
No keystore or API secret was ever committed.
