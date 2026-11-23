package dev.dlrevive.integrations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

/** JVM tests for the stream-resolution and enqueue-planning logic (no Android, no network). */
public class DownloadPipelineTest {

    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    private static DownloadConfig config(String json) throws Exception {
        Path file = Files.createTempFile("dlrevive-config", ".json");
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        return DownloadConfig.load(file.toFile());
    }

    @Test
    public void buildRequest_usesSanitizedPathStreamUrlAndHeaders() throws Exception {
        DownloadConfig config = config("{\"downloadSubdirectory\":\"dlrevive\"}");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer token");
        ResolvedStream stream = new ResolvedStream(
                "https://cdn.example/audio", "opus", "opus", 128, headers);

        DownloadPipeline.Request request = DownloadPipeline.buildRequest(config, stream, "A/B: Song?");

        assertEquals("https://cdn.example/audio", request.url);
        assertEquals("A/B: Song?", request.title);
        assertEquals("OPUS audio", request.description);
        assertEquals("dlrevive/A B Song.opus", request.relativePath);
        assertEquals("Bearer token", request.headers.get("Authorization"));
        assertEquals(DownloadIntegration.USER_AGENT, request.headers.get("User-Agent"));
    }

    @Test
    public void buildRequest_fallsBackToM4aForUnexpectedExtension() throws Exception {
        DownloadConfig config = config("{}");
        ResolvedStream stream = new ResolvedStream(
                "https://cdn.example/audio", "??", "?", 0, null);

        DownloadPipeline.Request request = DownloadPipeline.buildRequest(config, stream, "Song");

        assertEquals("dlrevive/Song.m4a", request.relativePath);
        assertEquals("M4A audio", request.description);
        assertTrue(request.allowMobileData);
    }

    @Test
    public void buildRequest_honorsAllowMobileDataFalse() throws Exception {
        DownloadConfig config = config("{\"allowMobileData\":false}");
        ResolvedStream stream = new ResolvedStream(
                "https://cdn.example/audio", "m4a", "mp4a", 128, Collections.emptyMap());

        assertFalse(DownloadPipeline.buildRequest(config, stream, "Song").allowMobileData);
    }

    @Test
    public void selectResolver_usesAuthModeFromConfig() throws Exception {
        StreamResolver anonymous = videoId -> null;
        StreamResolver authenticated = videoId -> null;

        assertSame(anonymous, DownloadPipeline.selectResolver(
                config("{\"authMode\":\"ANONYMOUS\"}"), anonymous, authenticated));
        assertSame(authenticated, DownloadPipeline.selectResolver(
                config("{\"authMode\":\"AUTHENTICATED\"}"), anonymous, authenticated));
    }

    @Test
    public void resolve_returnsTheStreamFromTheSelectedResolver() throws Exception {
        ResolvedStream expected = new ResolvedStream(
                "https://cdn.example/audio", "m4a", "mp4a", 128, Collections.emptyMap());
        StreamResolver resolver = videoId -> {
            assertEquals(VIDEO_ID, videoId);
            return expected;
        };

        assertSame(expected, DownloadPipeline.resolve(resolver, VIDEO_ID));
    }

    @Test
    public void resolve_propagatesResolutionFailure() {
        StreamResolver resolver = videoId -> {
            throw new ResolutionException("no audio streams");
        };
        try {
            DownloadPipeline.resolve(resolver, VIDEO_ID);
            fail("expected ResolutionException");
        } catch (ResolutionException e) {
            assertEquals("no audio streams", e.getMessage());
        }
    }
}
