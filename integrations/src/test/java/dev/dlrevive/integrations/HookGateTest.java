package dev.dlrevive.integrations;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Verifies the enable/disable guard used by all three download hooks. */
public final class HookGateTest {

    @Test
    public void enabledConfigAllowsHooks() {
        DownloadConfig config = DownloadConfig.defaults();
        assertTrue(config.isEnabled());
        assertTrue(HookGate.isEnabled(config));
    }

    @Test
    public void disabledConfigBlocksHooks() {
        DownloadConfig config = DownloadConfig.defaults();
        config.setEnabled(false);
        assertFalse(config.isEnabled());
        assertFalse(HookGate.isEnabled(config));
    }

    @Test
    public void missingConfigDefaultsToEnabled() {
        assertTrue(HookGate.isEnabled(null));
    }
}
