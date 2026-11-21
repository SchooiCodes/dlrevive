package dev.dlrevive.integrations;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DownloadConfigTest {

    @Test
    public void missingFileYieldsDefaults() {
        DownloadConfig config = DownloadConfig.load(new File("does-not-exist.json"));
        assertEquals(DownloadConfig.AuthMode.ANONYMOUS, config.getAuthMode());
        assertEquals("m4a", config.getPreferredExtension());
        assertEquals("dlrevive", config.getDownloadSubdirectory());
        assertTrue(config.isAllowMobileData());
    }

    @Test
    public void parsesAuthenticatedSettings() throws Exception {
        File file = File.createTempFile("dlrevive-config", ".json");
        file.deleteOnExit();
        String json = "{\"authMode\":\"AUTHENTICATED\",\"cookieHeader\":\"SID=x\","
                + "\"poToken\":\"abc\",\"visitorData\":\"v1\","
                + "\"preferredExtension\":\"OPUS\",\"allowMobileData\":false}";
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }

        DownloadConfig config = DownloadConfig.load(file);
        assertEquals(DownloadConfig.AuthMode.AUTHENTICATED, config.getAuthMode());
        assertEquals("SID=x", config.getCookieHeader());
        assertEquals("abc", config.getPoToken());
        assertEquals("v1", config.getVisitorData());
        assertEquals("opus", config.getPreferredExtension());
        assertFalse(config.isAllowMobileData());
    }

    @Test
    public void malformedFileFallsBackToDefaults() throws Exception {
        File file = File.createTempFile("dlrevive-config", ".json");
        file.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("not json".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(DownloadConfig.AuthMode.ANONYMOUS, DownloadConfig.load(file).getAuthMode());
    }
}
