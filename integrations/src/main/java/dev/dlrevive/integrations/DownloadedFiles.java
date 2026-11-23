package dev.dlrevive.integrations;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure-Java helpers for listing downloaded audio files from a directory.
 * No Android dependencies, no network access.
 */
public final class DownloadedFiles {

    private static final String DEFAULT_MIME = "application/octet-stream";
    private static final Set<String> AUDIO_EXTENSIONS =
            new HashSet<String>(Arrays.asList("m4a", "opus", "webm"));

    private static final Comparator<Entry> ENTRY_ORDER = new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
            int byName = a.name.compareToIgnoreCase(b.name);
            if (byName != 0) {
                return byName;
            }
            return a.path.compareTo(b.path); // stable, deterministic tie-break
        }
    };

    private DownloadedFiles() {
        // Utility class; not instantiable.
    }

    /** Immutable value describing one downloaded audio file. */
    public static final class Entry {
        public final String name;
        public final String path;
        public final String extension;
        public final long sizeBytes;
        public final long lastModified;

        public Entry(String name, String path, String extension, long sizeBytes, long lastModified) {
            this.name = name;
            this.path = path;
            this.extension = extension;
            this.sizeBytes = sizeBytes;
            this.lastModified = lastModified;
        }
    }

    /**
     * Returns audio files (*.m4a, *.opus, *.webm) sorted by name
     * (case-insensitive), never null. Returns an empty list when the directory
     * is null, missing, not a directory, unreadable, or contains no matching
     * regular files.
     */
    public static List<Entry> list(File directory) {
        try {
            return listInternal(directory);
        } catch (RuntimeException e) {
            // Absolute guarantee: this method never propagates an exception.
            return Collections.emptyList();
        }
    }

    private static List<Entry> listInternal(File directory) {
        if (directory == null) {
            return Collections.emptyList();
        }
        final File[] files;
        try {
            if (!directory.isDirectory() || !directory.canRead()) {
                return Collections.emptyList();
            }
            files = directory.listFiles();
        } catch (SecurityException e) {
            return Collections.emptyList();
        }
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<Entry> result = new ArrayList<Entry>();
        for (File file : files) {
            if (file == null) {
                continue;
            }
            try {
                if (!file.isFile()) { // excludes directories, e.g. a folder named "fake.m4a"
                    continue;
                }
                String name = file.getName();
                String extension = extensionOf(name);
                if (!AUDIO_EXTENSIONS.contains(extension)) {
                    continue;
                }
                result.add(new Entry(name, file.getAbsolutePath(), extension,
                        file.length(), file.lastModified()));
            } catch (SecurityException e) {
                // Skip individual files we are not allowed to inspect.
            }
        }
        if (result.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.sort(result, ENTRY_ORDER);
        return result;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return normalizeExtension(name.substring(dot + 1));
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

    /**
     * Maps an extension to a MIME type: m4a-&gt;audio/mp4, opus-&gt;audio/ogg,
     * webm-&gt;audio/webm, else application/octet-stream. Leading dots,
     * surrounding whitespace and case are ignored; null maps to the default.
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

    /**
     * Human-readable short size using a 1024 base: bytes have no decimals,
     * KB/MB/GB have one decimal place. Negative input is clamped to zero.
     */
    public static String humanSize(long bytes) {
        long b = Math.max(0L, bytes);
        if (b < 1024L) {
            return b + " B";
        }
        if (b < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", b / 1024.0);
        }
        if (b < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", b / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.1f GB", b / (1024.0 * 1024.0 * 1024.0));
    }
}
