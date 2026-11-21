package dev.dlrevive.integrations;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AudioStreamSelectorTest {

    private static AudioStream stream(String url, MediaFormat format, int bitrate) {
        return new AudioStream.Builder()
                .setId("test")
                .setContent(url, true)
                .setMediaFormat(format)
                .setAverageBitrate(bitrate)
                .build();
    }

    @Test
    public void returnsNullForEmptyInput() {
        assertNull(AudioStreamSelector.select(Collections.emptyList(), "m4a"));
    }

    @Test
    public void picksHighestBitrate() {
        ResolvedStream selected = AudioStreamSelector.select(Arrays.asList(
                stream("https://example.com/low.m4a", MediaFormat.M4A, 128_000),
                stream("https://example.com/high.m4a", MediaFormat.M4A, 256_000)
        ), "m4a");
        assertEquals("https://example.com/high.m4a", selected.getUrl());
        assertEquals(256_000, selected.getBitrate());
    }

    @Test
    public void preferenceWinsCloseRace() {
        ResolvedStream selected = AudioStreamSelector.select(Arrays.asList(
                stream("https://example.com/opus.webm", MediaFormat.OPUS, 160_000),
                stream("https://example.com/aac.m4a", MediaFormat.M4A, 128_000)
        ), "m4a");
        assertEquals("https://example.com/aac.m4a", selected.getUrl());
    }

    @Test
    public void skipsStreamsWithoutUrl() {
        // Content that is not a URL makes Stream.getUrl() return null.
        AudioStream manifest = new AudioStream.Builder()
                .setId("manifest")
                .setContent("dash-manifest-content", false)
                .setMediaFormat(MediaFormat.M4A)
                .setAverageBitrate(320_000)
                .build();
        ResolvedStream selected = AudioStreamSelector.select(Arrays.asList(
                manifest,
                stream("https://example.com/valid.m4a", MediaFormat.M4A, 128_000)
        ), "m4a");
        assertEquals("https://example.com/valid.m4a", selected.getUrl());
    }
}
