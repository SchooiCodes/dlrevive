package dev.dlrevive.patches;

import app.revanced.patcher.data.BytecodeContext;

import java.io.File;

/** Bridges to the patcher's integrations DEX list from Java (the accessor is Kotlin-internal). */
public final class DexMerge {

    private DexMerge() {
    }

    public static void add(BytecodeContext context, File dexFile) {
        context.getIntegrations$revanced_patcher().add(dexFile);
        context.getIntegrations$revanced_patcher().setMerge(true);
    }
}
