package dev.dlrevive.integrations;

import com.google.gson.JsonObject;

import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.localization.Localization;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticated resolver: talks to YouTube Music's Innertube {@code player} endpoint with the
 * user's cookies / PO token, then selects the best audio-only format. Falls back to anonymous
 * NewPipe resolution when the authenticated request cannot yield a stream.
 */
public final class InnertubeResolver implements StreamResolver {

    private static final String PLAYER_URL =
            "https://music.youtube.com/youtubei/v1/player?prettyPrint=false";
    private static final String ORIGIN = "https://music.youtube.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";
    private static final Pattern SIGNATURE_TIMESTAMP =
            Pattern.compile("\"signatureTimestamp\"\\s*:\\s*(\\d+)");

    private final DownloadConfig config;
    private final HttpDownloader downloader = new HttpDownloader();

    public InnertubeResolver(DownloadConfig config) {
        this.config = config;
    }

    @Override
    public ResolvedStream resolve(String videoId) throws ResolutionException {
        try {
            ResolvedStream stream = resolveAuthenticated(videoId);
            if (stream != null) {
                return stream;
            }
        } catch (Exception ignored) {
            // Fall through to the anonymous resolver.
        }
        return new NewPipeResolver(config.getPreferredExtension()).resolve(videoId);
    }

    private ResolvedStream resolveAuthenticated(String videoId) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Origin", ORIGIN);
        headers.put("Referer", ORIGIN + "/");
        headers.put("X-Google-Client", "WEB_MUSIC");
        addIfPresent(headers, "X-Goog-Visitor-Id", config.getVisitorData());
        addIfPresent(headers, "X-Goog-Po-Token", config.getPoToken());
        addIfPresent(headers, "Cookie", config.getCookieHeader());

        long timestamp = fetchSignatureTimestamp(videoId);
        byte[] body = buildRequest(
                videoId, timestamp, config.getVisitorData())
                .toString().getBytes(StandardCharsets.UTF_8);
        Request request = new Request(
                PLAYER_URL, "POST", toMultiMap(headers), body, Localization.DEFAULT, false);
        Response response = downloader.execute(request);
        String json = response.responseBody();

        String error = InnertubeResponseParser.playabilityError(json);
        if (error != null) {
            return null;
        }

        Map<String, String> downloadHeaders = new LinkedHashMap<>();
        downloadHeaders.put("User-Agent", USER_AGENT);
        downloadHeaders.put("Referer", ORIGIN + "/");
        addIfPresent(downloadHeaders, "X-Goog-Visitor-Id", config.getVisitorData());
        addIfPresent(downloadHeaders, "X-Goog-Po-Token", config.getPoToken());
        addIfPresent(downloadHeaders, "Cookie", config.getCookieHeader());

        return InnertubeResponseParser.selectBest(
                json, config.getPreferredExtension(), downloadHeaders);
    }

    private long fetchSignatureTimestamp(String videoId) {
        try {
            Request request = new Request(
                    "https://www.youtube.com/embed/" + videoId,
                    "GET", Collections.emptyMap(), null, Localization.DEFAULT, false);
            Response response = downloader.execute(request);
            Matcher matcher = SIGNATURE_TIMESTAMP.matcher(response.responseBody());
            return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static JsonObject buildRequest(String videoId, long signatureTimestamp,
                                            String visitorData) {
        JsonObject client = new JsonObject();
        client.addProperty("clientName", "WEB_MUSIC");
        client.addProperty("clientVersion", "1.20241009.01.00");
        client.addProperty("hl", "en");
        client.addProperty("gl", "US");
        if (visitorData != null && !visitorData.trim().isEmpty()) {
            client.addProperty("visitorData", visitorData.trim());
        }

        JsonObject context = new JsonObject();
        context.add("client", client);

        JsonObject root = new JsonObject();
        root.add("context", context);
        root.addProperty("videoId", videoId);
        root.addProperty("contentCheckOk", true);
        root.addProperty("racyCheckOk", true);
        if (signatureTimestamp > 0) {
            JsonObject playback = new JsonObject();
            JsonObject contentPlayback = new JsonObject();
            contentPlayback.addProperty("signatureTimestamp", signatureTimestamp);
            playback.add("contentPlaybackContext", contentPlayback);
            root.add("playbackContext", playback);
        }
        return root;
    }

    private static void addIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            map.put(key, value.trim());
        }
    }

    private static Map<String, List<String>> toMultiMap(Map<String, String> headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(Collections.singletonList(entry.getValue())));
        }
        return result;
    }
}
