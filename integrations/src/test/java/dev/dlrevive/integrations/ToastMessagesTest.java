package dev.dlrevive.integrations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests for the toast message-decision logic (no Android APIs involved). */
public final class ToastMessagesTest {

    @Test
    public void downloadingShowsImmediateState() {
        String msg = ToastMessages.downloading("Blue");
        assertTrue(msg.contains("dlrevive"));
        assertTrue(msg.contains("downloading"));
        assertTrue(msg.contains("Blue"));
    }

    @Test
    public void downloadingFallsBackToGenericLabel() {
        assertEquals("dlrevive: downloading song...", ToastMessages.downloading(null));
        assertEquals("dlrevive: downloading song...", ToastMessages.downloading("   "));
    }

    @Test
    public void startedUsesName() {
        assertEquals("dlrevive: downloading - Blue", ToastMessages.started("Blue"));
        assertEquals("dlrevive: downloading - song", ToastMessages.started(null));
    }

    @Test
    public void failedKeepsShortReason() {
        assertEquals("dlrevive: failed - Video unavailable",
                ToastMessages.failed("Video unavailable"));
        assertEquals("dlrevive: failed - unknown error", ToastMessages.failed(null));
        assertEquals("dlrevive: failed - unknown error", ToastMessages.failed("  "));
    }

    @Test
    public void failedTrimsReason() {
        assertEquals("dlrevive: failed - Network failure",
                ToastMessages.failed("  Network failure  "));
    }
}
