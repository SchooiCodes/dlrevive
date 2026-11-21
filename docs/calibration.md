# Fingerprint calibration guide

The patch in `patches/src/main/kotlin/dev/dlrevive/patches/DownloadSongsPatch.kt` currently uses
a **deliberately broad** fingerprint:

```kotlin
customFingerprint = { method, classDef ->
    classDef.type.startsWith("Lcom/google/android/apps/youtube/music/") &&
        method.name.contains("download", ignoreCase = true)
}
```

ReVanced patches are version-specific: this will not reliably match a real YouTube Music build
yet. Calibrate it against the exact APK you intend to patch, then rebuild the patch JAR.

## 1. Get the target APK and decompile it

You need the **exact** YouTube Music APK you will patch (same version and architecture). Pull it
from the device if you already have YTM installed:

```sh
adb shell pm path com.google.android.apps.youtube.music
adb pull <path-printed-above> youtube-music.apk
```

Open the APK in **jadx-gui** (or decompile with `apktool d youtube-music.apk`). Search the decompiled
sources for song download / menu handling code.

## 2. Find the right method

You are looking for a method that:

- runs when the user taps a Download action in a song or player menu,
- has access to a `Context`,
- has the current song's **video ID** (`String`), and ideally its **title** (`String`).

Useful search terms in decompiled code: `download`, `DownloadAction`, `onDownload`, `menu`,
`videoId`, `getVideoId`, `downloadMenuItem`.

Record these facts about the matched method:

| Fact | Example |
| --- | --- |
| Class type (smali, with `L...;`) | `Lcom/google/android/apps/youtube/music/foo/Bar;` |
| Method name | `onDownloadClicked` |
| Static or instance | instance |
| Parameter types, in order | `Context, String, String` |
| Return type | `void` |
| Distinctive string constant in the method body | `"download_menu_item"` |
| Local register count (`.registers`) | `5` |

## 3. Narrow the fingerprint

Prefer a combination of `customFingerprint` (class + method name) and, where available,
`returnType`, `accessFlags`, `parameters`, `strings`, or `opcodes`. Example:

```kotlin
internal val downloadMenuItemFingerprint = object : MethodFingerprint(
    returnType = "V",
    accessFlags = AccessFlags.PUBLIC.value,
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "Ljava/lang/String;"),
    strings = listOf("DOWNLOAD_SONG"),
    customFingerprint = { method, classDef ->
        classDef.type == "Lcom/google/android/apps/youtube/music/foo/Bar;" &&
            method.name == "onDownloadClicked"
    },
) {}
```

Keep refining until ReVanced CLI reports **exactly one** match. A fingerprint that matches several
methods or none at all will make the patch fail loudly rather than silently patch the wrong code.

## 4. Set the parameter indices

In `execute()` the three indices are currently hardcoded:

```kotlin
val contextParam = 1   // 1-based index of the Context parameter
val videoIdParam = 2   // 1-based index of the videoId String
val titleParam = 3     // 1-based index of the title String (optional)
```

Indices are **1-based** (the first declared parameter is `1`). The patch converts them to smali
`pN` registers for you:

- instance method: declared parameter 1 is `p1` (`p0` is `this`),
- static method: declared parameter 1 is `p0`.

If the method has no title parameter, set `titleParam` to `0` or a value outside the parameter
count; the patch will inject `const/4 v2, 0x0` instead of reading a register, and the file will be
named after the video ID.

At least **3 registers** are required for the injected call. If the target method has fewer, either
pick a different hook or raise its `.registers` with a follow-up instruction patch.

## 5. Rebuild and apply

```sh
# from the repository root (PowerShell)
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :patches:jar
```

Then confirm the JAR is discovered and apply it to your APK (check your ReVanced CLI version's
`--help` for exact flag names):

```sh
java -jar revanced-cli.jar list-patches -p patches/build/libs/dlvrevive-patches.jar
java -jar revanced-cli.jar patch -p patches/build/libs/dlvrevive-patches.jar `
    -o youtube-music-revived.apk youtube-music.apk
```

## 6. Verify on the device

Install the patched APK and confirm the download action reaches the integrations library:

```sh
adb install -r youtube-music-revived.apk
adb logcat | Select-String dlrevive
```

Because the patched APK is signed with a different key, it cannot be installed **over** the stock
YouTube Music app. You must either uninstall the stock app first or use a rooted setup. This is a
destructive step and is intentionally left to you.

Open a song, tap the Download action, and check that a file appears under
`Music/dlvrevive/` (or your configured subdirectory) with a completion notification. If the menu
action never calls the library, the fingerprint is still wrong — return to step 2.
