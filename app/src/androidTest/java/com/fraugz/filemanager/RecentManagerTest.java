package com.fraugz.filemanager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class RecentManagerTest {

    @Test
    public void pinsNeverCrowdOutTheRecentFiles() {
        List<RecentManager.RecentEntry> entries = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            entries.add(new RecentManager.RecentEntry("/pinned/" + i, 1000 - i, true));
        }
        for (int i = 0; i < 70; i++) {
            entries.add(new RecentManager.RecentEntry("/recent/" + i, 500 - i, false));
        }

        List<RecentManager.RecentEntry> kept = RecentManager.trim(entries);

        assertEquals(110, kept.size());
        assertEquals("/pinned/59", kept.get(59).path);
        assertEquals("/recent/0", kept.get(60).path);
        assertEquals("/recent/49", kept.get(109).path);
    }
}
