package dev.dlrevive.patches

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch

/**
 * Repurposes YouTube Music's native Download action.
 *
 * YTM 8.40.54 has no static `download_button` resource: the per-song Download action is a
 * server-driven Innertube command that ends up in `Llfu;.a(String, Lawrn)` (classes5.dex). That
 * method normally enqueues YTM's own Premium-gated offline download. We replace it: when the
 * string argument is a bare 11-character video id, we hand it to our own resolver/DownloadManager
 * path and return, so no entitlement check is involved.
 *
 * The integration needs an Application Context. There is no convenient Context on the offline
 * command path, so the app's base Application.onCreate (`Lbflb;.onCreate()V`, the superclass of
 * YouTubeMusicTikTokRoot_Application) is hooked to capture it once at startup.
 */
@Suppress("unused")
val restoreSongDownloadsPatch = bytecodePatch(
    name = "Restore song downloads",
    description = "Repurposes YTM's native Download action to use in-process audio resolution "
            + "instead of Premium-gated offline downloads.",
) {
    compatibleWith("com.google.android.apps.youtube.music"("8.40.54"))

    // Bundled integrations DEX (NewPipeExtractor + Innertube resolver + DownloadManager entrypoint).
    extendWith("dlrevive-classes.dex")

    apply {
        // 1. Capture the Application Context for the integration.
        val appClass = classDefs.firstOrNull { it.type == "Lbflb;" }
            ?: throw PatchException("base Application class Lbflb; not found")
        val appOnCreate = classDefs.getOrReplaceMutable(appClass).methods.firstOrNull { method ->
            method.name == "onCreate" &&
                method.returnType == "V" &&
                method.parameters?.isEmpty() == true
        } as? MutableMethod
            ?: throw PatchException("Lbflb;.onCreate()V not found")

        // p0 is the Application (a Context). init() stores getApplicationContext().
        appOnCreate.addInstructions(
            0,
            """
                invoke-static {p0}, Ldev/dlrevive/integrations/DownloadIntegration;->init(Landroid/content/Context;)V
            """.trimIndent(),
        )

        // 2. Replace the native offline command handler.
        val downloadClass = classDefs.firstOrNull { it.type == "Llfu;" }
            ?: throw PatchException("offline download controller class Llfu; not found")
        val offlineAction = classDefs.getOrReplaceMutable(downloadClass).methods.firstOrNull { method ->
            method.name == "a" &&
                method.returnType == "V" &&
                method.parameters?.map { it.type } == listOf(
                    "Ljava/lang/String;",
                    "Lawrn;",
                )
        } as? MutableMethod
            ?: throw PatchException("Llfu;.a(String,Lawrn)V not found")

        // p1 = the video id / command string, p2 = Lawrn. Only bare video ids are intercepted;
        // control commands (PPSV/PPSE/PPSDST, etc.) fall through to the original handler.
        offlineAction.addInstructions(
            0,
            """
                invoke-static {p1}, Ldev/dlrevive/integrations/DownloadIntegration;->handleNativeDownload(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :continue
                return-void
                :continue
            """.trimIndent(),
        )
    }
}
