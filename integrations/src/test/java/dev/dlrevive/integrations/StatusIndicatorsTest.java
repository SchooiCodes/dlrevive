package dev.dlrevive.integrations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests for the status-indicator logic (no Android runtime required). */
public class StatusIndicatorsTest {

    @Test
    public void aboutLineShowsVersionAndPatchVersion() {
        String line = AboutStatus.build("8.40.54", false);
        assertTrue(line.contains("8.40.54"));
        assertTrue(line.contains("dlrevive"));
        assertTrue(line.contains(AboutStatus.PATCH_VERSION));
        assertEquals("1.0.0", AboutStatus.PATCH_VERSION);
    }

    @Test
    public void aboutLineRevealsHookNeverRan() {
        String line = AboutStatus.build("8.40.54", false);
        assertTrue(line.contains("patch loaded"));
        assertTrue(line.contains("NEVER RAN"));
        assertFalse(line.contains("ACTIVE"));
    }

    @Test
    public void aboutLineShowsActiveWhenHookRan() {
        String line = AboutStatus.build("8.40.54", true);
        assertTrue(line.contains("ACTIVE"));
        assertFalse(line.contains("NEVER RAN"));
    }

    @Test
    public void aboutLineHandlesEmptyAndNullStockVersion() {
        assertEquals(
                AboutStatus.build("", false),
                AboutStatus.build(null, false));
        assertTrue(AboutStatus.build(null, false).startsWith("dlrevive "));
    }

    @Test
    public void runtimeFlagChangesOnlyWhenHookIsMarked() {
        assertFalse(RuntimeState.hasDownloadHookRan());
        RuntimeState.markDownloadHookRan();
        assertTrue(RuntimeState.hasDownloadHookRan());
        assertTrue(AboutStatus.build("8.40.54").contains("ACTIVE"));
    }

    @Test
    public void failureReasonIsShortAndSingleLine() {
        assertEquals("No route", DownloadIntegration.shortReason(new Exception("No route")));
        assertEquals("IllegalStateException",
                DownloadIntegration.shortReason(new IllegalStateException()));
        String longMessage = new String(new char[300]).replace('\0', 'x');
        String reason = DownloadIntegration.shortReason(new Exception(longMessage));
        assertTrue(reason.length() <= 120);
        assertFalse(reason.contains("\n"));
    }
}
