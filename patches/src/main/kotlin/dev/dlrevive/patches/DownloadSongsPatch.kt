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
 * Locates the YouTube Music menu handler that receives a song selection.
 *
 * This is a deliberately broad starting fingerprint. It must be narrowed against the actual
 * target APK during calibration (milestone 7): constrain the class type and method name until
 * exactly one method matches, then adjust the parameter indices below.
 */
internal val downloadMenuItemFingerprint = object : MethodFingerprint(
    customFingerprint = { method, classDef ->
        classDef.type.startsWith("Lcom/google/android/apps/youtube/music/") &&
            method.name.contains("download", ignoreCase = true)
    },
) {}

@Patch(
    name = "Restore song downloads",
    description = "Adds a Download action that resolves YouTube Music audio in-process.",
    compatiblePackages = [
        CompatiblePackage(name = "com.google.android.apps.youtube.music"),
    ],
)
class DownloadSongsPatch : BytecodePatch(setOf(downloadMenuItemFingerprint)) {

    override fun execute(context: BytecodeContext) {
        mergeIntegrationsDex(context)

        val result = downloadMenuItemFingerprint.result ?: throw PatchException(
            "Download menu method was not found. Narrow downloadMenuItemFingerprint for the " +
                "target YouTube Music APK before applying this patch.",
        )

        // 1-based parameter numbers of the matched method. Calibrate these against the APK.
        val contextParam = 1
        val videoIdParam = 2
        val titleParam = 3

        val method = result.method
        val parameterCount = method.parameters.size
        val isStatic = method.accessFlags and AccessFlags.STATIC.value != 0
        val pOffset = if (isStatic) -1 else 0

        fun requireParam(index: Int, name: String) {
            if (index < 1 || index > parameterCount) {
                throw PatchException(
                    "$name parameter index $index is out of range for " +
                        "${method.name} (has $parameterCount parameters)",
                )
            }
        }
        requireParam(contextParam, "Context")
        requireParam(videoIdParam, "videoId")

        val registers = method.implementation?.registerCount ?: 0
        if (registers < 3) {
            throw PatchException(
                "${method.name} only has $registers registers; at least 3 are required " +
                    "for the injected download call.",
            )
        }

        val smali = buildString {
            append("move-object/from16 v0, p${contextParam + pOffset}\n")
            append("move-object/from16 v1, p${videoIdParam + pOffset}\n")
            if (titleParam in 1..parameterCount) {
                append("move-object/from16 v2, p${titleParam + pOffset}\n")
            } else {
                append("const/4 v2, 0x0\n")
            }
            append(
                "invoke-static {v0, v1, v2}, " +
                    "Ldev/dlrevive/integrations/DownloadIntegration;->download(" +
                    "Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V\n",
            )
        }

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
