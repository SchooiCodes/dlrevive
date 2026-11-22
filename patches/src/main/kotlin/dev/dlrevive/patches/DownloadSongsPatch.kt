package dev.dlrevive.patches

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch

/**
 * The share-intent builder in YouTube Music 8.40.54.
 *
 * Production YTM classes are obfuscated, and the per-song download menu handler could not be
 * identified statically. This method is stable enough to locate: it receives the Context, the
 * song title and the share Uri, which is everything the downloader needs. Hooking it makes
 * sharing a song also start its download.
 */
@Suppress("unused")
val restoreSongDownloadsPatch = bytecodePatch(
    name = "Restore song downloads",
    description = "Starts an audio download when a song is shared, using in-process resolution.",
) {
    compatibleWith("com.google.android.apps.youtube.music"("8.40.54"))

    // Bundled integrations DEX (NewPipeExtractor + Innertube resolver + DownloadManager entrypoint).
    extendWith("dlrevive-classes.dex")

    apply {
        val classDef = classDefs.firstOrNull { it.type == "Lajgu;" }
            ?: throw PatchException("share-intent builder class Lajgu; not found")

        val mutableMethod = classDefs.getOrReplaceMutable(classDef).methods.firstOrNull { method ->
            method.name == "a" &&
                method.returnType == "Landroid/content/Intent;" &&
                method.parameters?.map { it.type } == listOf(
                    "Landroid/content/Context;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Landroid/net/Uri;",
                )
        } as? MutableMethod
            ?: throw PatchException("share-intent builder method Lajgu;->a not found")

        // p0 = Context, p1 = title, p4 = Uri.
        mutableMethod.addInstructions(
            0,
            """
                invoke-static {p0, p4, p1}, Ldev/dlrevive/integrations/DownloadIntegration;->downloadFromUri(Landroid/content/Context;Landroid/net/Uri;Ljava/lang/String;)V
            """.trimIndent(),
        )
    }
}
