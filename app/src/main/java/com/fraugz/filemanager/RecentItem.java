package com.fraugz.filemanager;

import java.io.File;
import java.util.List;

/**
 * Represents a single item in the Recents list.
 * Can be either an individual file or a burst group of files.
 */
public class RecentItem {

    /** True when this item represents a burst group of files. */
    public final boolean isGroup;

    /** Non-null when isGroup == false. */
    public final File singleFile;

    /** Non-null when isGroup == true. First element is used for the preview thumbnail. */
    public final List<File> groupFiles;

    /** E.g. "WhatsApp Images · 5 archivos". Non-null when isGroup == true. */
    public final String groupLabel;

    /** Unix timestamp (ms) representing the item. For groups, the timestamp of the first/newest file. */
    public final long timestamp;

    /** True if this item came from MediaStore auto-scan (vs manual user access). */
    public final boolean fromMediaStore;

    private RecentItem(boolean isGroup, File singleFile, List<File> groupFiles,
                       String groupLabel, long timestamp, boolean fromMediaStore) {
        this.isGroup = isGroup;
        this.singleFile = singleFile;
        this.groupFiles = groupFiles;
        this.groupLabel = groupLabel;
        this.timestamp = timestamp;
        this.fromMediaStore = fromMediaStore;
    }

    /** Create a single-file RecentItem. */
    public static RecentItem single(File file, long timestamp, boolean fromMediaStore) {
        if (file == null) throw new IllegalArgumentException("file must not be null");
        return new RecentItem(false, file, null, null, timestamp, fromMediaStore);
    }

    /** Create a burst-group RecentItem. */
    public static RecentItem group(List<File> files, String label, long timestamp) {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("group files must not be empty");
        return new RecentItem(true, null, files, label, timestamp, true);
    }

    /**
     * Returns the representative File for this item.
     * For groups, returns the first file (used for thumbnail).
     */
    public File getRepresentativeFile() {
        if (!isGroup) return singleFile;
        return groupFiles.isEmpty() ? null : groupFiles.get(0);
    }
}
