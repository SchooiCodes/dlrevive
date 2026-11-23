package dev.dlrevive.integrations;

/**
 * Runtime state that survives for the lifetime of the patched app process.
 *
 * <p>Kept independent of Android classes so the About-screen status line can be unit tested on
 * the JVM. The flag is set only when the patched share/download hook actually executes; this is
 * what distinguishes "patch applied" from "patch applied but its runtime code never ran".</p>
 */
public final class RuntimeState {

    private static volatile boolean downloadHookRan;

    private RuntimeState() {
    }

    public static void markDownloadHookRan() {
        downloadHookRan = true;
    }

    public static boolean hasDownloadHookRan() {
        return downloadHookRan;
    }
}
