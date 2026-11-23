package dev.dlrevive.integrations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import dev.dlrevive.integrations.DownloadedFiles.Entry;

/** JUnit 4 tests for {@link DownloadedFiles}. Pure local file I/O only. */
public class DownloadedFilesTest {

    private final List<File> cleanupDirs = new ArrayList<File>();

    @After
    public void tearDown() {
        for (File dir : cleanupDirs) {
            deleteRecursively(dir);
        }
        cleanupDirs.clear();
    }

    private File newTempDir() throws IOException {
        File base = File.createTempFile("dlrevive-files-", "");
        assertTrue("placeholder deleted", base.delete());
        assertTrue("temp dir created", base.mkdirs());
        cleanupDirs.add(base);
        return base;
    }

    private static File newFile(File dir, String name, int length) throws IOException {
        File file = new File(dir, name);
        FileOutputStream out = new FileOutputStream(file);
        try {
            byte[] data = new byte[length];
            Arrays.fill(data, (byte) 0x41);
            out.write(data);
        } finally {
            out.close();
        }
        file.deleteOnExit();
        return file;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
        file.deleteOnExit();
    }

    @Test
    public void listFiltersRegularFilesAndSortsCaseInsensitively() throws Exception {
        File dir = newTempDir();
        File b = newFile(dir, "B.m4a", 340);
        File a = newFile(dir, "a.OPUS", 812 * 1024);
        File c = newFile(dir, "c.webm", 1024);
        newFile(dir, "notes.txt", 10);
        assertTrue("subdirectory created", new File(dir, "fake.m4a").mkdirs());

        List<Entry> entries = DownloadedFiles.list(dir);
        assertEquals(3, entries.size());
        assertEquals("a.OPUS", entries.get(0).name);
        assertEquals("B.m4a", entries.get(1).name);
        assertEquals("c.webm", entries.get(2).name);

        Entry first = entries.get(0);
        assertEquals("opus", first.extension);
        assertEquals(a.getAbsolutePath(), first.path);
        assertEquals(a.length(), first.sizeBytes);
        assertEquals(a.lastModified(), first.lastModified);

        Entry second = entries.get(1);
        assertEquals("m4a", second.extension);
        assertEquals(b.getAbsolutePath(), second.path);
        assertEquals(b.length(), second.sizeBytes);

        Entry third = entries.get(2);
        assertEquals("webm", third.extension);
        assertEquals(c.getAbsolutePath(), third.path);
        assertEquals(c.length(), third.sizeBytes);
    }

    @Test
    public void listSortsByNameIgnoringCase() throws Exception {
        File dir = newTempDir();
        newFile(dir, "zebra.m4a", 1);
        newFile(dir, "Alpha.opus", 1);
        newFile(dir, "middle.WEBM", 1);

        List<Entry> entries = DownloadedFiles.list(dir);
        assertEquals(3, entries.size());
        assertEquals("Alpha.opus", entries.get(0).name);
        assertEquals("middle.WEBM", entries.get(1).name);
        assertEquals("zebra.m4a", entries.get(2).name);
    }

    @Test
    public void listReturnsEmptyForNullMissingNonDirectoryAndEmptyCases() throws Exception {
        assertNotNull(DownloadedFiles.list(null));
        assertTrue(DownloadedFiles.list(null).isEmpty());

        File missing = new File(System.getProperty("java.io.tmpdir"),
                "dlrevive-no-such-dir-" + System.nanoTime());
        assertFalse(missing.exists());
        assertTrue(DownloadedFiles.list(missing).isEmpty());

        File regularFile = newFile(newTempDir(), "song.m4a", 1);
        assertTrue(DownloadedFiles.list(regularFile).isEmpty()); // a file is not a directory

        assertTrue(DownloadedFiles.list(newTempDir()).isEmpty()); // empty directory

        File onlyText = newTempDir();
        newFile(onlyText, "readme.txt", 5);
        assertTrue(DownloadedFiles.list(onlyText).isEmpty()); // no matching files
    }

    @Test
    public void listReturnsEmptyForUnreadableDirectoryWhenPlatformAllows() throws Exception {
        File dir = newTempDir();
        newFile(dir, "track.m4a", 1);
        dir.setReadable(false, false);
        try {
            // Windows may not actually revoke directory read access; the guard
            // keeps the test portable while still checking the real contract.
            if (!dir.canRead()) {
                assertTrue(DownloadedFiles.list(dir).isEmpty());
            }
        } finally {
            dir.setReadable(true, false);
        }
    }

    @Test
    public void mimeForExtensionHandlesCaseLeadingDotNullAndUnknown() {
        assertEquals("audio/mp4", DownloadedFiles.mimeForExtension("m4a"));
        assertEquals("audio/mp4", DownloadedFiles.mimeForExtension(".M4A"));
        assertEquals("audio/mp4", DownloadedFiles.mimeForExtension(" .M4a "));
        assertEquals("audio/ogg", DownloadedFiles.mimeForExtension("opus"));
        assertEquals("audio/ogg", DownloadedFiles.mimeForExtension(".OPUS"));
        assertEquals("audio/webm", DownloadedFiles.mimeForExtension("WeBm"));
        assertEquals("audio/webm", DownloadedFiles.mimeForExtension(".webm"));
        assertEquals("application/octet-stream", DownloadedFiles.mimeForExtension(null));
        assertEquals("application/octet-stream", DownloadedFiles.mimeForExtension(""));
        assertEquals("application/octet-stream", DownloadedFiles.mimeForExtension(".txt"));
        assertEquals("application/octet-stream", DownloadedFiles.mimeForExtension("mp3"));
    }

    @Test
    public void humanSizeFormatsBytesKilobytesMegabytesAndGigabytes() {
        assertEquals("0 B", DownloadedFiles.humanSize(0));
        assertEquals("0 B", DownloadedFiles.humanSize(-1));
        assertEquals("0 B", DownloadedFiles.humanSize(Long.MIN_VALUE));
        assertEquals("340 B", DownloadedFiles.humanSize(340));
        assertEquals("1023 B", DownloadedFiles.humanSize(1023));
        assertEquals("1.0 KB", DownloadedFiles.humanSize(1024));
        assertEquals("812.0 KB", DownloadedFiles.humanSize(812L * 1024));
        assertEquals("1.0 MB", DownloadedFiles.humanSize(1024L * 1024));
        assertEquals("2.9 MB", DownloadedFiles.humanSize((long) (2.9 * 1024 * 1024)));
        assertEquals("1.0 GB", DownloadedFiles.humanSize(1024L * 1024 * 1024));
    }
}
