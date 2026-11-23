package dev.dlrevive.integrations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests for the Library entry's duplicate guard and item id. */
public final class LibraryEntryTest {

    @Test
    public void addsWhenNoItemPresent() {
        assertTrue(LibraryEntry.shouldAdd(false));
    }

    @Test
    public void doesNotAddDuplicateOnRecreation() {
        // onCreateOptionsMenu fires again on fragment recreation / rotation / back-nav.
        assertFalse(LibraryEntry.shouldAdd(true));
        // Simulate several repeated firings: every one after the first must be skipped.
        boolean present = false;
        int added = 0;
        for (int i = 0; i < 5; i++) {
            if (LibraryEntry.shouldAdd(present)) {
                added++;
                present = true;
            }
        }
        assertEquals(1, added);
    }

    @Test
    public void itemIdIsAppPrivate() {
        // 0x7f0bfffe is in the app's private id range, well clear of small resource ids.
        assertEquals(0x7f0bfffe, LibraryEntry.ITEM_ID);
        assertTrue(LibraryEntry.ITEM_ID > 0x7f0b0000);
    }
}
