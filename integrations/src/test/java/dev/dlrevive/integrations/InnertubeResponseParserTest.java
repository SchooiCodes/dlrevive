package dev.dlrevive.integrations;

import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InnertubeResponseParserTest {

    private static final String OK_JSON = """
            {
              "playabilityStatus": {"status": "OK"},
              "streamingData": {
                "adaptiveFormats": [
                  {"mimeType": "audio/mp4; codecs=\\"mp4a.40.2\\"", "bitrate": 128000,
                   "codecs": "mp4a.40.2", "url": "https://example.com/aac"},
                  {"mimeType": "audio/webm; codecs=\\"opus\\"", "bitrate": 130000,
                   "codecs": "opus", "url": "https://example.com/opus"},
                  {"mimeType": "audio/opus; codecs=\\"opus\\"", "bitrate": 256000,
                   "codecs": "opus", "signatureCipher": "s=abc&sp=sig&url=hidden"},
                  {"mimeType": "video/mp4", "bitrate": 900000,
                   "url": "https://example.com/video"}
                ]
              }
            }
            """;

    private static final String ERROR_JSON = """
            {"playabilityStatus": {"status": "LOGIN_REQUIRED", "reason": "Sign in to confirm"}}
            """;

    @Test
    public void playabilityErrorIsNullWhenOk() {
        assertNull(InnertubeResponseParser.playabilityError(OK_JSON));
    }

    @Test
    public void playabilityErrorReportsReason() {
        String error = InnertubeResponseParser.playabilityError(ERROR_JSON);
        assertTrue(error, error.contains("LOGIN_REQUIRED"));
        assertTrue(error, error.contains("Sign in to confirm"));
    }

    @Test
    public void selectsPreferredAudioFormatAndSkipsCipheredAndVideo() {
        Map<String, String> headers = Collections.singletonMap("Cookie", "x=1");
        ResolvedStream m4a = InnertubeResponseParser.selectBest(OK_JSON, "m4a", headers);
        assertEquals("https://example.com/aac", m4a.getUrl());
        assertEquals("m4a", m4a.getExtension());
        assertEquals("mp4a.40.2", m4a.getCodec());
        assertEquals(128000, m4a.getBitrate());
        assertEquals("x=1", m4a.getHeaders().get("Cookie"));

        ResolvedStream opus = InnertubeResponseParser.selectBest(OK_JSON, "opus", headers);
        assertEquals("https://example.com/opus", opus.getUrl());
        assertEquals("opus", opus.getExtension());
    }

    @Test
    public void returnsNullWhenNoUsableFormat() {
        assertNull(InnertubeResponseParser.selectBest(ERROR_JSON, "m4a", Collections.emptyMap()));
    }
}
