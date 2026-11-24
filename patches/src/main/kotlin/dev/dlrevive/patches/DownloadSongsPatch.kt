package dev.dlrevive.patches

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val TRACK_CONTROLLER_CLASS = "Llhe;"
private const val SHARE_CLASS = "Lajgu;"
private const val ABOUT_CLASS =
    "Lcom/google/android/apps/youtube/music/settings/fragment/AboutSettingsFragment;"
private const val SETTINGS_HEADERS_CLASS =
    "Lcom/google/android/apps/youtube/music/settings/fragment/SettingsHeadersFragment;"

/**
 * Restores downloads in YouTube Music 8.40.54.
 *
 * The primary trigger is the native per-song Download action: `Ljtz;.c` (OfflineVideoCommand)
 * dispatches the server's ACTION_ADD command to `Lawsd;.h`, implemented only by `Llhe;`
 * (MusicOfflineTrackEntityActionsController). We take over there before YTM's Premium gate.
 * The share-intent builder `Lajgu;.a` is kept as a documented fallback trigger.
 *
 * A third hook in AboutSettingsFragment appends a dlrevive status line to the stock version
 * summary, including whether the download hook has actually run at runtime.
 */
@Suppress("unused")
val restoreSongDownloadsPatch = bytecodePatch(
    name = "Restore song downloads",
    description = "Starts an audio download from YTM's native Download action (share fallback), "
            + "using in-process resolution instead of Premium-gated offline downloads, "
            + "and shows patch status in Settings > About.",
) {
    compatibleWith(
        "com.google.android.apps.youtube.music"("8.40.54"),
        "app.revanced.android.apps.youtube.music"("8.40.54"),
    )

    // Bundled integrations DEX (NewPipeExtractor + Innertube resolver + DownloadManager entrypoint).
    extendWith("dlrevive-classes.dex")

    apply {
        // 1. PRIMARY: the native per-song Download action. OfflineVideoCommand (Ljtz;.c) turns the
        //    server's ACTION_ADD command into Lawsd;.h(String, ...), implemented only by Llhe;
        //    (MusicOfflineTrackEntityActionsController). Its first instruction logs a "blocked"
        //    check that returns early for non-Premium users. We take over at entry: p0 is the
        //    controller (its field .b is the Activity Context), p1 is the video-id token.
        val trackClass = classDefs.firstOrNull { it.type == TRACK_CONTROLLER_CLASS }
            ?: throw PatchException("track actions controller $TRACK_CONTROLLER_CLASS not found")
        val trackMethod = classDefs.getOrReplaceMutable(trackClass).methods.firstOrNull { method ->
            method.name == "h" &&
                method.returnType == "V" &&
                method.parameters?.map { it.type } == listOf(
                    "Ljava/lang/String;",
                    "Lbuwa;",
                    "Lapzw;",
                    "Lbumy;",
                )
        } as? MutableMethod
            ?: throw PatchException("$TRACK_CONTROLLER_CLASS;.h track download handler not found")

        // handleTrackDownload returns true when it handled the tap; on false (disabled by the
        // user, or no valid video id) we fall through to YTM's original Premium-gated handler.
        trackMethod.addInstructions(
            0,
            """
                iget-object v0, p0, Llhe;->b:Ldh;
                invoke-static {v0, p1}, Ldev/dlrevive/integrations/DownloadIntegration;->handleTrackDownload(Landroid/content/Context;Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :continue
                return-void
                :continue
            """.trimIndent(),
        )
        // 2. FALLBACK: the share-intent builder, kept for users who share instead of using the
        //    Download button. p0 = Context, p1 = title, p4 = Uri. Parameter registers are used
        //    directly; the method has outs = 3, which is enough for this 3-argument call.
        val shareClass = classDefs.firstOrNull { it.type == SHARE_CLASS }
            ?: throw PatchException("share intent builder class $SHARE_CLASS not found")
        val shareMethod = classDefs.getOrReplaceMutable(shareClass).methods.firstOrNull { method ->
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
            ?: throw PatchException("$SHARE_CLASS;.a share intent builder not found")

        shareMethod.addInstructions(
            0,
            """
                invoke-static {p0, p4, p1}, Ldev/dlrevive/integrations/DownloadIntegration;->downloadFromUri(Landroid/content/Context;Landroid/net/Uri;Ljava/lang/String;)V
            """.trimIndent(),
        )

        // 3. Append the status line in Settings > About. Find the Preference.n(CharSequence) call
        //    (obfuscated setSummary) that displays the stock version, and inject right before it.
        val aboutClass = classDefs.firstOrNull { it.type == ABOUT_CLASS }
            ?: throw PatchException("About screen class $ABOUT_CLASS not found")
        val aboutMethod = classDefs.getOrReplaceMutable(aboutClass).methods.firstOrNull { method ->
            method.name == "onCreatePreferences" &&
                method.returnType == "V" &&
                method.parameters?.map { it.type } == listOf(
                    "Landroid/os/Bundle;",
                    "Ljava/lang/String;",
                )
        } as? MutableMethod
            ?: throw PatchException("$ABOUT_CLASS;.onCreatePreferences not found")

        val summaryCallIndex = aboutMethod.implementation!!.instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference &&
                reference.definingClass == "Landroidx/preference/Preference;" &&
                reference.name == "n"
        }
        if (summaryCallIndex < 0) {
            throw PatchException("version summary call not found in $ABOUT_CLASS;.onCreatePreferences")
        }

        // v5 holds the stock version String at this point. Replace it with the combined status
        // text; the original Preference.n(v4, v5) then displays both values.
        aboutMethod.addInstructions(
            summaryCallIndex,
            """
                invoke-static {v5}, Ldev/dlrevive/integrations/AboutStatus;->build(Ljava/lang/CharSequence;)Ljava/lang/String;
                move-result-object v5
            """.trimIndent(),
        )

        // 4. Add the dlrevive settings section to the root Settings screen. Verified target
        //    (8.40.54): SettingsHeadersFragment.onCreatePreferences(Bundle,String)V, 7 registers;
        //    setPreferencesFromResource(0x7f170023) is instruction index 8, so inject at index 9.
        //    v0 holds the fragment throughout. The helper resolves Context + PreferenceScreen
        //    reflectively and never throws into the app, so a single instruction is enough.
        val headersClass = classDefs.firstOrNull { it.type == SETTINGS_HEADERS_CLASS }
            ?: throw PatchException("settings headers class $SETTINGS_HEADERS_CLASS not found")
        val headersMethod = classDefs.getOrReplaceMutable(headersClass).methods.firstOrNull { method ->
            method.name == "onCreatePreferences" &&
                method.returnType == "V" &&
                method.parameters?.map { it.type } == listOf(
                    "Landroid/os/Bundle;",
                    "Ljava/lang/String;",
                )
        } as? MutableMethod
            ?: throw PatchException("$SETTINGS_HEADERS_CLASS;.onCreatePreferences not found")

        headersMethod.addInstructions(
            9,
            """
                invoke-static {v0}, Ldev/dlrevive/integrations/SettingsUiBuilder;->installFromFragment(Ljava/lang/Object;)V
            """.trimIndent(),
        )
        // NOTE: the Library entry-point hooks (menu item and toolbar button) were intentionally
        // removed. The menu hook caused a VerifyError crash on entering Library; the button hook
        // was never visually verified. The About/Settings section remains the dlrevive UI.
    }
}
