package dev.dlrevive.integrations;

/**
 * Pure-Java decision logic for the user-visible toast messages. Kept free of Android APIs so the
 * wording (including short failure reasons) can be verified with JVM unit tests.
 */
public final class ToastMessages {

    private static final String PREFIX = "dlrevive: ";

    private ToastMessages() {
    }

    /** Immediate feedback shown on the main thread when a Download tap is intercepted. */
    public static String downloading(String name) {
        return PREFIX + "downloading " + label(name) + "...";
    }

    /** Shown after the download has been queued with DownloadManager. */
    public static String started(String name) {
        return PREFIX + "downloading - " + label(name);
    }

    /** Shown when resolution or enqueue fails; the reason is kept short. */
    public static String failed(String reason) {
        String shortReason = reason == null || reason.trim().isEmpty()
                ? "unknown error" : reason.trim();
        return PREFIX + "failed - " + shortReason;
    }

    private static String label(String name) {
        return name == null || name.trim().isEmpty() ? "song" : name.trim();
    }
}
