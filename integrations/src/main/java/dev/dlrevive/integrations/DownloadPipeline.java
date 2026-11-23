package dev.dlrevive.integrations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java part of the download pipeline: resolver selection and DownloadManager request
 * planning. Kept free of Android and network calls so it can be unit tested on the JVM; the
 * Android-specific translation lives in {@link DownloadIntegration}.
 */
public final class DownloadPipeline {

    /** Everything needed to enqueue a download, without depending on Android classes. */
    public static final class Request {
        public final String url;
        public final String title;
        public final String description;
        public final String relativePath;
        public final boolean allowMobileData;
        public final Map<String, String> headers;

        Request(String url, String title, String description, String relativePath,
                boolean allowMobileData, Map<String, String> headers) {
            this.url = url;
            this.title = title;
            this.description = description;
            this.relativePath = relativePath;
            this.allowMobileData = allowMobileData;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        }
    }

    private DownloadPipeline() {
    }

    /**
     * Selects the resolver from the config. The concrete resolvers are passed in so this decision
     * can be tested without constructing network clients.
     */
    public static StreamResolver selectResolver(DownloadConfig config,
                                                 StreamResolver anonymous,
                                                 StreamResolver authenticated) {
        return config.getAuthMode() == DownloadConfig.AuthMode.AUTHENTICATED
                ? authenticated
                : anonymous;
    }

    /** The counterpart of the primary resolver, tried only after the primary fails. */
    public static StreamResolver fallbackResolver(DownloadConfig config,
                                                   StreamResolver anonymous,
                                                   StreamResolver authenticated) {
        return config.getAuthMode() == DownloadConfig.AuthMode.AUTHENTICATED
                ? anonymous
                : authenticated;
    }

    /** Resolves a video id to a playable stream. Network work happens inside the resolver. */
    public static ResolvedStream resolve(StreamResolver resolver, String videoId)
            throws ResolutionException {
        return resolver.resolve(videoId);
    }

    /**
     * Resolve with the configured resolver, then retry with the other one on any failure
     * (including a {@link RuntimeException} or a {@code null} result). The first failure stays
     * as the thrown exception and the second is attached as suppressed so nothing is lost for
     * {@code Log.e}; the Android layer only shows {@link #friendlyReason(Throwable)} to the user.
     */
    public static ResolvedStream resolveWithFallback(StreamResolver primary,
                                                      StreamResolver fallback,
                                                      String videoId)
            throws ResolutionException {
        Throwable firstFailure;
        try {
            return requireStream(primary, videoId);
        } catch (Throwable t) {
            // Catch Error too: NewPipeExtractor v0.27.1 calls URLDecoder.decode(String, Charset)
            // (API 30+) which throws NoSuchMethodError on API 29 devices. Failing over to the
            // other resolver is the only way to recover from that linkage error.
            firstFailure = t;
        }
        if (fallback == null || fallback == primary) {
            throw asResolutionException(firstFailure);
        }
        try {
            return requireStream(fallback, videoId);
        } catch (Throwable t) {
            ResolutionException combined = asResolutionException(firstFailure);
            if (t != firstFailure) {
                combined.addSuppressed(t);
            }
            throw combined;
        }
    }

    private static ResolvedStream requireStream(StreamResolver resolver, String videoId)
            throws ResolutionException {
        ResolvedStream stream = resolver.resolve(videoId);
        if (stream == null) {
            throw new ResolutionException("No audio stream available for " + videoId);
        }
        return stream;
    }

    private static ResolutionException asResolutionException(Throwable failure) {
        if (failure instanceof ResolutionException) {
            return (ResolutionException) failure;
        }
        return new ResolutionException("Resolution failed: " + friendlyReason(failure), failure);
    }

    /**
     * Short, user-readable reason for the failure toast. Exception messages can be very long or
     * leak internal detail, so common cases are mapped to a fixed phrase; the full exception is
     * still logged by {@link DownloadIntegration}.
     */
    public static String friendlyReason(Throwable failure) {
        if (failure == null) {
            return "Unknown error";
        }
        StringBuilder details = new StringBuilder();
        Throwable cursor = failure;
        while (cursor != null) {
            details.append(cursor.getClass().getSimpleName()).append(' ');
            if (cursor.getMessage() != null) {
                details.append(cursor.getMessage()).append(' ');
            }
            cursor = cursor.getCause() == cursor ? null : cursor.getCause();
        }
        String text = details.toString().toLowerCase(Locale.ROOT);
        if (containsAny(text, "contentnotavailable", "not available", "unavailable",
                "private video", "removed", "playability", "login required")) {
            return "Video unavailable";
        }
        if (containsAny(text, "unknownhost", "connectexception", "sockettimeout",
                "timeoutexception", "failed to connect", "unable to resolve host",
                "network is unreachable", "sslhandshake", "socketexception")) {
            return "Network failure";
        }
        if (containsAny(text, "no audio-only stream", "no audio stream", "no stream",
                "no playable stream", "no extractor available")) {
            return "No audio stream available";
        }
        String message = failure.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return failure.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 120 ? message.substring(0, 117) + "..." : message;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Builds the DownloadManager request description from a resolved stream. */
    public static Request buildRequest(DownloadConfig config, ResolvedStream stream, String title) {
        String extension = stream.getExtension().replaceAll("[^a-z0-9]", "");
        if (extension.isEmpty()) {
            extension = "m4a";
        }
        String relativePath = config.getDownloadSubdirectory() + "/"
                + FileNames.sanitize(title) + "." + extension;

        Map<String, String> headers = new LinkedHashMap<>(stream.getHeaders());
        headers.put("User-Agent", DownloadIntegration.USER_AGENT);

        return new Request(
                stream.getUrl(),
                title,
                extension.toUpperCase(Locale.ROOT) + " audio",
                relativePath,
                config.isAllowMobileData(),
                headers
        );
    }
}
