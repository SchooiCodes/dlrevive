package dev.dlrevive.integrations;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.Map;

/** Pure parsing of an Innertube {@code player} response into a downloadable audio stream. */
public final class InnertubeResponseParser {

    private InnertubeResponseParser() {
    }

    /** Return the playability error, or {@code null} when playback is allowed. */
    public static String playabilityError(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject status = root.getAsJsonObject("playabilityStatus");
            if (status == null) {
                return null;
            }
            String code = string(status, "status");
            if (code == null || "OK".equals(code)) {
                return null;
            }
            String reason = string(status, "reason");
            return reason == null ? code : code + ": " + reason;
        } catch (Exception e) {
            return "Unreadable player response: " + e.getMessage();
        }
    }

    /**
     * Pick the best direct audio-only format, mirroring yt-dlp's {@code best*[vcodec=none]}.
     * Formats whose URL is cipher-protected are skipped: solving YouTube's JS challenge is
     * out of scope for the in-process extractor.
     */
    public static ResolvedStream selectBest(String json, String preferredExtension,
                                            Map<String, String> downloadHeaders) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject streamingData = root.getAsJsonObject("streamingData");
            if (streamingData == null) {
                return null;
            }
            String preferred = preferredExtension == null
                    ? "m4a" : preferredExtension.toLowerCase(Locale.ROOT);

            ResolvedStream best = null;
            int bestScore = Integer.MIN_VALUE;
            for (JsonObject format : formats(streamingData.getAsJsonArray("adaptiveFormats"))) {
                String mime = string(format, "mimeType");
                if (mime == null || !mime.toLowerCase(Locale.ROOT).startsWith("audio/")) {
                    continue;
                }
                String url = string(format, "url");
                if (url == null || url.isEmpty() || string(format, "signatureCipher") != null) {
                    continue;
                }
                String extension = extensionFor(mime);
                int score = intValue(format, "bitrate", 0)
                        + (extension.equals(preferred) ? 50_000 : 0)
                        + (extension.equals("opus") ? 20_000 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = new ResolvedStream(
                            url,
                            extension,
                            string(format, "codecs") == null ? extension : string(format, "codecs"),
                            intValue(format, "bitrate", 0),
                            downloadHeaders
                    );
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private static Iterable<JsonObject> formats(JsonArray array) {
        if (array == null) {
            return java.util.Collections.emptyList();
        }
        java.util.List<JsonObject> result = new java.util.ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                result.add(element.getAsJsonObject());
            }
        }
        return result;
    }

    private static String extensionFor(String mime) {
        String lower = mime.toLowerCase(Locale.ROOT);
        if (lower.contains("opus") || lower.contains("webm")) {
            return "opus";
        }
        if (lower.contains("mp4") || lower.contains("aac") || lower.contains("mpeg4")) {
            return "m4a";
        }
        if (lower.contains("mp3")) {
            return "mp3";
        }
        return "m4a";
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return value.getAsInt();
    }
}
