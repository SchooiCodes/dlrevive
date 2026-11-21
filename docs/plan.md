# Approved project plan

## Decisions (confirmed with user)

- **Architecture:** ReVanced patch + in-process Java extractor (NewPipeExtractor), shipped as
  an integrations DEX merged into YouTube Music. (User chose this over a local yt-dlp
  service, knowing it is not yt-dlp.)
- **Target:** user supplies a specific YTM APK **and** an adb-connected device; fingerprints
  are calibrated against that APK and tested on-device.
- **Auth:** support both anonymous and authenticated (cookies / PO tokens), selectable via
  config.
- **yt-dlp's role:** reference implementation / spec for the download flow and audio-only
  format selection; NOT bundled (optional future bridge, out of scope here).

## Architecture / data flow

YTM song/player menu "Download" tap -> injected hook reads videoId (+ title) -> calls
`DownloadIntegration.download(...)` in the merged integrations DEX -> `StreamResolver`
(config chooses anonymous vs authenticated) -> pick best **audio-only** stream (opus/m4a,
highest bitrate, vcodec=none — mirrors yt-dlp `bestaudio`) -> Android `DownloadManager` saves
to public Music folder with notification -> toast result.

Modules:

- `integrations/` — Java/Android library: `DownloadIntegration`, `StreamResolver` +
  implementations, `DownloadConfig`, notification/filename helpers. Built to DEX.
- `patches/` — Kotlin module (revanced-patcher): bytecode/resource patch with APK-specific
  fingerprints + integrations DEX merge. Built to JAR.

## Tradeoffs & risks

- Deviates from literal "via yt-dlp": extractor is NewPipeExtractor.
- NewPipeExtractor is anonymous-first; cookies/PO-token support is weak. The authenticated
  path therefore uses a small custom Innertube resolver behind the same `StreamResolver`
  interface, with graceful fallback and clear logging.
- NewPipeExtractor/Innertube break frequently; pin dependency versions and re-calibrate per
  YTM version.
- Repackaged/signed YTM cannot be installed over the original (signature mismatch): test by
  uninstalling stock YTM or via root/mount. Confirm device setup with user.
- No Premium entitlement bypass of YTM's own service is attempted; we fetch the direct
  stream instead.

## Prerequisites

- From user: exact YTM APK file path + version; adb device connected (`adb devices`); whether
  device is rooted / OK to uninstall stock YTM.
- Tooling to install: **JDK 17+**, Gradle (via wrapper), Android SDK build-tools (for `d8`
  dexing). yt-dlp + Python + node are already present.

## Milestones & incremental commits

1. `chore: initialize repository and research notes` — `git init`, README, `.gitignore`,
   `docs/research.md`, `docs/plan.md`, LICENSE.
2. `build: scaffold Gradle multi-module project` — Gradle wrapper, `settings.gradle.kts`,
   version catalog, `integrations/` and `patches/` modules; empty `build` passes.
3. `feat(integrations): downloader entrypoint and config` — `DownloadIntegration`,
   `DownloadConfig` (auth-mode switch + cookie/PO-token import from a config file), Android
   `DownloadManager` save path, notification, filename sanitizer. Resolver stubbed with a
   clear error.
4. `feat(integrations): anonymous audio resolution (NewPipeExtractor)` — `StreamResolver`
   interface + `NewPipeResolver`; best audio-only selection; tests using fixture data.
5. `feat(integrations): authenticated resolution (cookies / PO tokens)` — custom
   `InnertubeResolver`; config-driven selection with fallback; tests.
6. `feat(patches): inject download action into YTM` — Kotlin bytecode patch with fingerprints
   (to be calibrated), integrations DEX merge, menu-click hook calling
   `DownloadIntegration.download(...)`.
7. `fix(patches): calibrate fingerprints and verify on device` — apply patch to the supplied
   APK with ReVanced CLI, `adb install`, test download of an anonymous-accessible track and
   an auth-required track; iterate on fingerprints/resolver.
8. `docs: build, usage, and limitations` — README (build + apply + config + known issues),
   maintenance notes; final commit.

## Verification per milestone

- M2: `./gradlew build` green.
- M3–M5: `./gradlew :integrations:test` green + manual resolver checks against known video
  IDs in both auth modes.
- M6: `./gradlew :patches:jar` produces a JAR; `revanced-cli list-patches` shows the new patch
  + compatible YTM version.
- M7: patched APK installs on device; tapping Download writes a playable audio file to the
  Music folder; both auth modes exercised; failures logged clearly.

## Out of scope

Bundling the yt-dlp binary/Python, desktop/stacher helper, settings UI (config file instead),
batch/background queue downloads, ffmpeg transcoding, bypassing YouTube's own Premium
entitlement checks.
