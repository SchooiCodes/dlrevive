package dev.dlrevive.integrations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Regression test for the API-29 crash: NewPipeExtractor v0.27.1 throws NoSuchMethodError
 * (an Error, not an Exception) on API 29. The resolver fallback must recover from that so
 * the download can continue via the other resolver.
 */
public final class ErrorFallbackTest {

    @Test
    public void fallbackRecoversFromNoSuchMethodError() throws Exception {
        StreamResolver crashing = videoId -> {
            throw new NoSuchMethodError("URLDecoder.decode(String, Charset)");
        };
        ResolvedStream good = new ResolvedStream(
                "https://googlevideo.com/audio", "m4a", "mp4a.40.2", 128,
                java.util.Collections.emptyMap());
        StreamResolver working = videoId -> good;

        ResolvedStream result = DownloadPipeline.resolveWithFallback(crashing, working, "vid123");
        assertSame(good, result);
    }

    @Test
    public void bothCrashingSurfacesFirstErrorWithSecondSuppressed() {
        StreamResolver first = videoId -> {
            throw new NoSuchMethodError("first");
        };
        StreamResolver second = videoId -> {
            throw new NoSuchMethodError("second");
        };
        try {
            DownloadPipeline.resolveWithFallback(first, second, "vid");
            fail("expected ResolutionException");
        } catch (ResolutionException e) {
            assertEquals(1, e.getSuppressed().length);
            assertTrue(e.getMessage().contains("first"));
        }
    }
}
