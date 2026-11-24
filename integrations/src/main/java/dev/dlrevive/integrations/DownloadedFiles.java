package dev.dlrevive.integrations;

import java.util.Locale;

/**
 * MIME helper for our downloaded audio files.
 *
 * <p>The file-listing logic ({@code list}, {@code Entry}, {@code humanSize}) was intentionally
 * removed together with the downloads-list settings screen. Only {@link #mimeForExtension} remains,
 * because the success toast and the retained YTM preview-player path still use it.</p>
 */
public final class DownloadedFiles {

    private static final String DEFAULT_MIME = "application/octet-stream";

    private DownloadedFiles() {
    }

    /**
     * Maps an extension to a MIME type: m4a -> audio/mp4, opus -> audio/ogg,
     * webm -> audio/webm, else application/octet-stream. Leading dots, surrounding
     * whitespace and case are ignored; null maps to the default.
     */
    public static String mimeForExtension(String extension) {
        String ext = normalizeExtension(extension);
        if ("m4a".equals(ext)) {
            return "audio/mp4";
        }
        if ("opus".equals(ext)) {
            return "audio/ogg";
        }
        if ("webm".equals(ext)) {
            return "audio/webm";
        }
        return DEFAULT_MIME;
    }

    private static String normalizeExtension(String extension) {
        if (extension == null) {
            return "";
        }
        String s = extension.trim();
        while (s.startsWith(".")) {
            s = s.substring(1);
        }
        return s.toLowerCase(Locale.ROOT);
    }
}
