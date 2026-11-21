# Fingerprint calibration guide

The patch is calibrated for **YouTube Music 8.40.54**. Its fingerprint targets the static
share-intent builder `Lajgu;->a(...)`:

```kotlin
internal val shareIntentFingerprint = object : MethodFingerprint(
    returnType = "Landroid/content/Intent;",
    accessFlags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Landroid/net/Uri;",
    ),
    customFingerprint = { method, classDef ->
        classDef.type == "Lajgu;" && method.name == "a"
    },
) {}
```

and injects a single smali instruction at index 0:

```smali
invoke-static {p0, p4, p1}, Ldev/dlrevive/integrations/DownloadIntegration;->downloadFromUri(Landroid/content/Context;Landroid/net/Uri;Ljava/lang/String;)V
```

For this static method, `p0` is the `Context`, `p1` is the title, and `p4` is the shared `Uri`.

## Why the share action instead of a Download menu item

Production YouTube Music builds obfuscate almost all classes to short names (for example
`Lajgu;`, `Llcp;`). Static inspection of 8.40.54 found no per-song Download menu handler:
resource-ID and string searches only surfaced the library "Downloads" shelf, the
`show_downloads_button`, and offline-badge UI. The share path, by contrast, is reachable through
distinctive strings (`android.intent.action.SEND`, `R.string.share_text`) and exposes both the
song URL and title. The trade-off is that **sharing a song also starts its download**.

## 1. Get and decompile the target APK

Use the exact APK you will patch. Pull it from a device if it is installed:

```sh
adb shell pm path com.google.android.apps.youtube.music
adb pull <path-printed-above> youtube-music.apk
```

jadx v1.5.3 works for static inspection:

```sh
# download the jadx release ZIP from https://github.com/skylot/jadx/releases
jadx\bin\jadx.bat --no-res -d jadx-out youtube-music.apk
```

Obfuscated classes `Lx;` appear as `jadx-out/sources/defpackage/x.java` (lowercase filename);
classes that keep their names stay under `com/google/android/apps/youtube/music/...`.

## 2. Find the share-intent builder

Search the jadx sources for `android.intent.action.SEND`, `EXTRA_TEXT`, or `share_text`. You want a
**static** method that takes a `Context` and a `Uri` (the song URL) and returns an `Intent`. Note:

- the class type in smali form (`L<name>;`),
- the method name,
- the parameter types in order,
- which parameter is the `Context`, which is the title `String`, and which is the `Uri`,
- whether the method is `public static`.

## 3. Update the fingerprint and injection

Replace the `classDef.type` / `method.name` check, `returnType`, `accessFlags`, and `parameters`
in `DownloadSongsPatch.kt` with the values you recorded. Set the `CompatiblePackage` version to
your target version.

Update the injected smali register list. For a **static** method, parameters are `p0..pN-1`; for an
instance method they are `p1..pN` (`p0` is `this`). The call signature must match
`DownloadIntegration.downloadFromUri(Context, Uri, String)`:

```smali
invoke-static {<contextReg>, <uriReg>, <titleReg>}, Ldev/dlrevive/integrations/DownloadIntegration;->downloadFromUri(Landroid/content/Context;Landroid/net/Uri;Ljava/lang/String;)V
```

If the method has fewer than three parameters (or no usable title), pass a null title by first
adding `const/4 vN, 0x0` and using that register.

## 4. Rebuild and apply

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :integrations:test :integrations:dexIntegrations :patches:jar

java -jar revanced-cli.jar list-patches -p patches\build\libs\dlvrevive-patches.jar
java -jar revanced-cli.jar patch -p patches\build\libs\dlvrevive-patches.jar `
    -o youtube-music-revived.apk youtube-music.apk
adb install -r youtube-music-revived.apk
```

Check your ReVanced CLI version's `--help` for exact flag names. The patched APK is signed
differently from stock YouTube Music, so it cannot be installed over it — uninstall stock YTM
first or use a rooted setup.

## 5. Verify on the device

Open a song, use **Share**, and confirm a file appears under `Music/dlvrevive/` (or your configured
subdirectory) with a completion notification. Watch `adb logcat` for `dlrevive` messages. If the
patch fails at apply time with a `PatchException`, the fingerprint still does not match — return
to step 2.
