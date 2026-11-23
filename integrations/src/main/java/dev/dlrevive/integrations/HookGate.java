package dev.dlrevive.integrations;

/**
 * Pure-Java decision for whether the download hooks should act.
 *
 * <p>Kept free of Android APIs so the enable/disable guard can be verified with JVM unit tests.
 * A null config is treated as enabled so the hooks keep working before a config file exists.</p>
 */
public final class HookGate {

    private HookGate() {
    }

    /** True when downloads are enabled (a missing config means enabled). */
    public static boolean isEnabled(DownloadConfig config) {
        return config == null || config.isEnabled();
    }
}
