package dev.dlrevive.integrations;

/**
 * Builds the dlrevive status line appended to the stock version summary in Settings > About.
 *
 * <p>Pure Java so it can be verified with JVM unit tests. The injected smali calls
 * {@link #build(CharSequence)} with the version string the app already computed.</p>
 */
public final class AboutStatus {

    /** Kept in sync with the patch bundle manifest Version attribute. */
    public static final String PATCH_VERSION = "1.0.0";

    private AboutStatus() {
    }

    public static String build(CharSequence stockVersion) {
        return build(stockVersion, RuntimeState.hasDownloadHookRan());
    }

    public static String build(CharSequence stockVersion, boolean downloadHookRan) {
        String line = "dlrevive " + PATCH_VERSION + " (patch loaded, download hook "
                + (downloadHookRan ? "ACTIVE" : "NEVER RAN") + ")";
        String base = stockVersion == null ? "" : stockVersion.toString().trim();
        return base.isEmpty() ? line : base + "\n" + line;
    }
}
