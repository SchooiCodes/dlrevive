package dev.dlrevive.integrations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.Test;

/** JVM tests for resolver fallback and readable failures (no Android, no network). */
public class ResolverFallbackTest {

    private static final String VIDEO_ID = "vQqLdfJQPKw";

    private static final class StubResolver implements StreamResolver {
        private final ResolvedStream stream;
        private final Exception failure;
        int calls;

        StubResolver(ResolvedStream stream, Exception failure) {
            this.stream = stream;
            this.failure = failure;
        }

        @Override
        public ResolvedStream resolve(String videoId) throws ResolutionException {
            calls++;
            if (failure instanceof ResolutionException) {
                throw (ResolutionException) failure;
            }
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            return stream;
        }
    }

    /** Mimics NewPipe's ContentNotAvailableException without pulling the library into the test. */
    private static final class ContentNotAvailableException extends Exception {
        ContentNotAvailableException(String message) {
            super(message);
        }
    }

    private static ResolvedStream stream() {
        return new ResolvedStream("https://cdn.example/audio", "m4a", "mp4a", 128,
                Collections.<String, String>emptyMap());
    }

    private static StubResolver failing(String message) {
        return new StubResolver(null, new ResolutionException(message));
    }

    private static StubResolver returning() {
        return new StubResolver(stream(), null);
    }

    private static DownloadConfig config(String json) throws Exception {
        Path file = Files.createTempFile("dlrevive-config", ".json");
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        return DownloadConfig.load(file.toFile());
    }

    @Test
    public void fallbackResolver_selectsTheOppositeOfThePrimary() throws Exception {
        StreamResolver anonymous = videoId -> null;
        StreamResolver authenticated = videoId -> null;

        assertSame(authenticated, DownloadPipeline.fallbackResolver(
                config("{\"authMode\":\"ANONYMOUS\"}"), anonymous, authenticated));
        assertSame(anonymous, DownloadPipeline.fallbackResolver(
                config("{\"authMode\":\"AUTHENTICATED\"}"), anonymous, authenticated));
    }

    @Test
    public void configuredPrimaryFails_otherResolverCompletesTheDownload() throws Exception {
        for (String authMode : new String[]{"ANONYMOUS", "AUTHENTICATED"}) {
            DownloadConfig cfg = config("{\"authMode\":\"" + authMode + "\"}");
            boolean primaryIsAnonymous = "ANONYMOUS".equals(authMode);
            StubResolver anonymous = primaryIsAnonymous ? failing("NewPipe failed") : returning();
            StubResolver authenticated = primaryIsAnonymous ? returning() : failing("Innertube failed");

            StreamResolver primary = DownloadPipeline.selectResolver(cfg, anonymous, authenticated);
            StubResolver fallback = (StubResolver)
                    DownloadPipeline.fallbackResolver(cfg, anonymous, authenticated);

            ResolvedStream result = DownloadPipeline.resolveWithFallback(primary, fallback, VIDEO_ID);

            assertTrue(result != null);
            assertEquals(1, ((StubResolver) primary).calls);
            assertEquals(1, fallback.calls);
        }
    }

    @Test
    public void resolveWithFallback_doesNotCallFallbackWhenPrimarySucceeds() throws Exception {
        StubResolver primary = returning();
        StubResolver fallback = returning();

        ResolvedStream result = DownloadPipeline.resolveWithFallback(primary, fallback, VIDEO_ID);

        assertSame(primary.stream, result);
        assertEquals(1, primary.calls);
        assertEquals(0, fallback.calls);
    }

    @Test
    public void resolveWithFallback_retriesFallbackWhenPrimaryThrows() throws Exception {
        StubResolver primary = failing("primary exploded");
        StubResolver fallback = returning();

        ResolvedStream result = DownloadPipeline.resolveWithFallback(primary, fallback, VIDEO_ID);

        assertSame(fallback.stream, result);
        assertEquals(1, primary.calls);
        assertEquals(1, fallback.calls);
    }

    @Test
    public void resolveWithFallback_treatsNullResultAsNoStreamAndRetries() throws Exception {
        StubResolver primary = new StubResolver(null, null);
        StubResolver fallback = returning();

        ResolvedStream result = DownloadPipeline.resolveWithFallback(primary, fallback, VIDEO_ID);

        assertSame(fallback.stream, result);
        assertEquals(1, primary.calls);
        assertEquals(1, fallback.calls);
    }

    @Test
    public void resolveWithFallback_alsoCatchesRuntimeExceptionsFromPrimary() throws Exception {
        StubResolver primary = new StubResolver(null, new IllegalStateException("downloader crashed"));
        StubResolver fallback = returning();

        ResolvedStream result = DownloadPipeline.resolveWithFallback(primary, fallback, VIDEO_ID);

        assertSame(fallback.stream, result);
    }

    @Test
    public void resolveWithFallback_keepsBothFailuresWhenBothResolversFail() {
        ResolutionException first = new ResolutionException("primary failed");
        ResolutionException second = new ResolutionException("fallback failed");
        StubResolver primary = new StubResolver(null, first);
        StubResolver fallback = new StubResolver(null, second);

        try {
            DownloadPipeline.resolveWithFallback(primary, fallback, VIDEO_ID);
            fail("expected ResolutionException");
        } catch (ResolutionException e) {
            assertSame(first, e);
            assertEquals(1, e.getSuppressed().length);
            assertSame(second, e.getSuppressed()[0]);
        }
    }

    @Test
    public void resolveWithFallback_doesNotRetryTheSameResolverInstance() {
        StubResolver primary = failing("primary failed");

        try {
            DownloadPipeline.resolveWithFallback(primary, primary, VIDEO_ID);
            fail("expected ResolutionException");
        } catch (ResolutionException e) {
            assertEquals("primary failed", e.getMessage());
            assertEquals(1, primary.calls);
        }
    }

    @Test
    public void friendlyReason_mapsContentNotAvailableToVideoUnavailable() {
        ResolutionException failure = new ResolutionException(
                "Could not extract https://music.youtube.com/watch?v=" + VIDEO_ID,
                new ContentNotAvailableException("Blue is unavailable"));

        assertEquals("Video unavailable", DownloadPipeline.friendlyReason(failure));
    }

    @Test
    public void friendlyReason_mapsNetworkFailure() {
        ResolutionException failure = new ResolutionException(
                "Could not extract stream", new UnknownHostException("music.youtube.com"));

        assertEquals("Network failure", DownloadPipeline.friendlyReason(failure));
    }

    @Test
    public void friendlyReason_mapsMissingAudioStream() {
        assertEquals("No audio stream available", DownloadPipeline.friendlyReason(
                new ResolutionException("No audio-only stream available for " + VIDEO_ID)));
    }

    @Test
    public void friendlyReason_fallsBackToShortSingleLineMessage() {
        assertEquals("odd line another line",
                DownloadPipeline.friendlyReason(new Exception("odd line\nanother line")));
        assertEquals("IllegalStateException",
                DownloadPipeline.friendlyReason(new IllegalStateException()));
        assertEquals("Unknown error", DownloadPipeline.friendlyReason(null));
    }
}
