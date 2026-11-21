package dev.dlrevive.integrations;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileNamesTest {

    @Test
    public void removesIllegalCharacters() {
        assertEquals("A B C", FileNames.sanitize("A/B:C"));
        assertFalseContains(FileNames.sanitize("song?name*with|chars"), "?");
        assertFalseContains(FileNames.sanitize("song?name*with|chars"), "*");
        assertFalseContains(FileNames.sanitize("song?name*with|chars"), "|");
    }

    @Test
    public void handlesEmptyAndDeviceNames() {
        assertEquals("untitled", FileNames.sanitize(""));
        assertEquals("untitled", FileNames.sanitize(null));
        assertEquals("_CON", FileNames.sanitize("CON"));
    }

    @Test
    public void capsLength() {
        String longName = "a".repeat(500);
        assertTrue(FileNames.sanitize(longName).length() <= 120);
    }

    private static void assertFalseContains(String value, String forbidden) {
        assertTrue("Expected no '" + forbidden + "' in " + value, !value.contains(forbidden));
    }
}
