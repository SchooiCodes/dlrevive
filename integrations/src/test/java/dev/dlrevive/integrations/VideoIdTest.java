package dev.dlrevive.integrations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class VideoIdTest {

    private static final String ID = "dQw4w9WgXcQ";

    @Test
    public void extractsFromMusicWatchUrl() {
        assertEquals(ID, DownloadIntegration.extractVideoId(
                "https://music.youtube.com/watch?v=" + ID + "&list=test"));
    }

    @Test
    public void extractsFromShortLink() {
        assertEquals(ID, DownloadIntegration.extractVideoId("https://youtu.be/" + ID));
    }

    @Test
    public void extractsFromEmbedUrl() {
        assertEquals(ID, DownloadIntegration.extractVideoId(
                "https://www.youtube.com/embed/" + ID));
    }

    @Test
    public void returnsNullForUnrelatedUrl() {
        assertNull(DownloadIntegration.extractVideoId("https://example.com/song"));
        assertNull(DownloadIntegration.extractVideoId(null));
    }

    @Test
    public void nativeCommandTokenAcceptsBareId() {
        assertEquals(ID, DownloadIntegration.extractTrackId(ID));
        assertEquals(ID, DownloadIntegration.extractTrackId("  " + ID + "  "));
    }

    @Test
    public void nativeCommandTokenAcceptsUrl() {
        assertEquals(ID, DownloadIntegration.extractTrackId(
                "https://music.youtube.com/watch?v=" + ID));
    }

    @Test
    public void nativeCommandTokenRejectsNonId() {
        assertNull(DownloadIntegration.extractTrackId("PPSV"));
        assertNull(DownloadIntegration.extractTrackId(""));
        assertNull(DownloadIntegration.extractTrackId(null));
    }
}
