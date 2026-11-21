package dev.dlrevive.patches

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.AccessFlags
import java.io.File

/**
 * The share-intent builder in YouTube Music 8.40.54.
 *
 * Production YTM classes are obfuscated, and the per-song download menu handler could not be
 * identified statically. This method is stable enough to locate: it receives the Context, the
 * song title and the share Uri, which is everything the downloader needs. Hooking it makes
 * sharing a song also start its download.
 */
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

@Patch(
    name = "Restore song downloads",
    description = "Starts an audio download when a song is shared, using in-process resolution.",
    compatiblePackages = [
        CompatiblePackage(name = "com.google.android.apps.youtube.music", versions = ["8.40.54"]),
    ],
)
class DownloadSongsPatch : BytecodePatch(setOf(shareIntentFingerprint)) {

    override fun execute(context: BytecodeContext) {
        mergeIntegrationsDex(context)

        val result = shareIntentFingerprint.result ?: throw PatchException(
            "Share intent builder was not found. Recalibrate shareIntentFingerprint for the " +
                "target YouTube Music APK before applying this patch.",
        )

        // Static method: p0 = Context, p1 = title, p4 = Uri. Invoke using the parameter
        // registers directly so no extra scratch registers are required.
        val smali =
            "invoke-static {p0, p4, p1}, " +
                "Ldev/dlrevive/integrations/DownloadIntegration;->downloadFromUri(" +
                "Landroid/content/Context;Landroid/net/Uri;Ljava/lang/String;)V"

        result.mutableMethod.addInstructions(0, smali)
    }

    private fun mergeIntegrationsDex(context: BytecodeContext) {
        val stream = DownloadSongsPatch::class.java
            .getResourceAsStream("/dlrevive-classes.dex")
            ?: throw PatchException("Bundled integrations DEX (/dlrevive-classes.dex) is missing")

        val dexFile = File.createTempFile("dlrevive-integrations", ".dex")
        dexFile.deleteOnExit()
        stream.use { input ->
            dexFile.outputStream().use { output -> input.copyTo(output) }
        }
        DexMerge.add(context, dexFile)
    }
}
