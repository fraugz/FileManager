package com.fraugz.filemanager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class UpdateCheckerTest {

    @Test
    public void failedOrUnreadableResponsesAreErrorsNotMissingUpdates() throws Exception {
        assertThrows(IOException.class, () -> UpdateChecker.parseLatestTag(403, ""));
        assertThrows(IOException.class,
                () -> UpdateChecker.parseLatestTag(200, "{\"message\":\"Not Found\"}"));
        assertEquals("1.3.9",
                UpdateChecker.parseLatestTag(200, "{\"tag_name\": \"v1.3.9\", \"name\": \"x\"}"));
    }

    @Test
    public void dismissedVersionIsOnlyOfferedOnAManualCheck() {
        assertFalse(UpdateChecker.shouldOffer("1.3.9", "1.3.8", "1.3.9", false));
        assertTrue(UpdateChecker.shouldOffer("1.3.9", "1.3.8", "1.3.9", true));
        assertTrue(UpdateChecker.shouldOffer("1.4.0", "1.3.8", "1.3.9", false));
        assertFalse(UpdateChecker.shouldOffer("1.3.8", "1.3.8", "", true));
    }
}
