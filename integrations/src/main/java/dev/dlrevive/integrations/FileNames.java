package dev.dlrevive.integrations;

import java.util.Locale;

/** Pure helpers for turning media titles into safe file names. */
public final class FileNames {

    private static final int MAX_LENGTH = 120;

    private FileNames() {
    }

    /** Remove path separators, control characters and characters illegal on common filesystems. */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "untitled";
        }
        String cleaned = raw.replace('\u0000', ' ')
                .replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return "untitled";
        }
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH).trim();
        }
        // Avoid Windows device names, which would make the download fail silently.
        String upper = cleaned.toUpperCase(Locale.ROOT);
        if (upper.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            cleaned = "_" + cleaned;
        }
        return cleaned;
    }
}
