package com.fraugz.filemanager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FileOperationsTest {

    @Test
    public void renameNeverReplacesAnotherFile() throws Exception {
        // Shared storage: there "a.txt" and "A.txt" are the same file, as in the folders users browse.
        File dir = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), "rename_test");
        assertTrue(dir.mkdirs() || dir.isDirectory());
        File a = new File(dir, "a.txt");
        File b = new File(dir, "b.txt");
        try {
            Files.write(a.toPath(), "AAA".getBytes(StandardCharsets.UTF_8));
            Files.write(b.toPath(), "BBB".getBytes(StandardCharsets.UTF_8));

            assertFalse(FileOperations.rename(a, "b.txt"));
            assertEquals("AAA", new String(Files.readAllBytes(a.toPath()), StandardCharsets.UTF_8));
            assertEquals("BBB", new String(Files.readAllBytes(b.toPath()), StandardCharsets.UTF_8));

            assertTrue(FileOperations.rename(a, "A.txt"));
            assertEquals("AAA", new String(
                    Files.readAllBytes(new File(dir, "A.txt").toPath()), StandardCharsets.UTF_8));
        } finally {
            FileOperations.delete(dir);
        }
    }
}
