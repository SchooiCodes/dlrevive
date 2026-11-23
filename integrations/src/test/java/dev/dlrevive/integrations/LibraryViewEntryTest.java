package dev.dlrevive.integrations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests for the Library view entry's duplicate guard (fragment recreation safety). */
public final class LibraryViewEntryTest {

    @Test
    public void addsOnlyOnceAcrossRecreations() {
        boolean present = false;
        int added = 0;
        for (int i = 0; i < 6; i++) { // repeated onViewCreated calls
            if (LibraryViewEntry.shouldAdd(present)) {
                added++;
                present = true;
            }
        }
        assertEquals(1, added);
    }

    @Test
    public void guardRejectsWhenAlreadyPresent() {
        assertTrue(LibraryViewEntry.shouldAdd(false));
        assertFalse(LibraryViewEntry.shouldAdd(true));
    }
}
