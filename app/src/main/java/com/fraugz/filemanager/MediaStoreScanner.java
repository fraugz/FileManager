package com.fraugz.filemanager;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Queries MediaStore for recently added images and videos, then applies
 * burst-group detection: files in the same parent directory whose DATE_ADDED
 * values fall within a 60-second sliding window are merged into a single
 * {@link RecentItem} group.
 */
public class MediaStoreScanner {

    private static final String TAG = "MediaStoreScanner";

    /** Files added within this many hours are considered. */
    private static final int LOOKBACK_HOURS = 72;

    /** Maximum seconds between consecutive files to still be considered the same burst. */
    private static final long BURST_WINDOW_SECONDS = 60L;

    /** Minimum file count to form a burst group (fewer files remain individual). */
    private static final int MIN_BURST_SIZE = 2;

    /**
     * Scan MediaStore for recently added images and videos and return a list of
     * {@link RecentItem} objects with burst grouping applied.
     *
     * @param ctx application/activity context
     * @return list of {@link RecentItem}, never null, may be empty
     */
    public static List<RecentItem> scan(Context ctx) {
        if (ctx == null) return new ArrayList<>();
        try {
            long sinceMs = System.currentTimeMillis() - (LOOKBACK_HOURS * 3600_000L);
            long sinceSec = sinceMs / 1000L;

            List<MediaEntry> raw = new ArrayList<>();
            raw.addAll(queryMediaStore(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, sinceSec));
            raw.addAll(queryMediaStore(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, sinceSec));
            raw.addAll(queryMediaStore(ctx, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, sinceSec));

            if (raw.isEmpty()) return new ArrayList<>();

            // Sort by dateAdded descending (newest first)
            Collections.sort(raw, (a, b) -> Long.compare(b.dateAddedSec, a.dateAddedSec));

            return buildRecentItems(raw);
        } catch (Exception e) {
            Log.e(TAG, "scan failed", e);
            return new ArrayList<>();
        }
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private static List<MediaEntry> queryMediaStore(Context ctx, Uri uri, long sinceSec) {
        List<MediaEntry> result = new ArrayList<>();
        ContentResolver cr = ctx.getContentResolver();

        String[] projection = {
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DISPLAY_NAME,
        };
        String selection = MediaStore.MediaColumns.DATE_ADDED + " >= ?";
        String[] selectionArgs = {String.valueOf(sinceSec)};
        String sortOrder = MediaStore.MediaColumns.DATE_ADDED + " DESC";

        Cursor cursor = null;
        try {
            cursor = cr.query(uri, projection, selection, selectionArgs, sortOrder);
            if (cursor == null) return result;

            int colData = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
            int colDate = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);

            while (cursor.moveToNext()) {
                try {
                    String path = cursor.getString(colData);
                    long dateAddedSec = cursor.getLong(colDate);
                    if (path == null || path.trim().isEmpty()) continue;
                    File f = new File(path);
                    if (!f.exists() || !f.isFile()) continue;
                    result.add(new MediaEntry(f, dateAddedSec));
                } catch (Exception rowEx) {
                    Log.w(TAG, "row error", rowEx);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "queryMediaStore uri=" + uri, e);
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Exception ignored) {}
            }
        }
        return result;
    }

    /**
     * Group entries by parent directory, then apply sliding-window burst detection
     * within each directory. Returns a flat list of {@link RecentItem}.
     */
    private static List<RecentItem> buildRecentItems(List<MediaEntry> entries) {
        // Group by parent directory path
        Map<String, List<MediaEntry>> byDir = new LinkedHashMap<>();
        for (MediaEntry e : entries) {
            File parent = e.file.getParentFile();
            String dirKey = parent != null ? parent.getAbsolutePath() : "";
            if (!byDir.containsKey(dirKey)) byDir.put(dirKey, new ArrayList<>());
            byDir.get(dirKey).add(e);
        }

        List<RecentItem> result = new ArrayList<>();

        for (Map.Entry<String, List<MediaEntry>> dirEntry : byDir.entrySet()) {
            String dirPath = dirEntry.getKey();
            List<MediaEntry> dirEntries = dirEntry.getValue();

            // Sort by dateAdded descending within directory
            Collections.sort(dirEntries, (a, b) -> Long.compare(b.dateAddedSec, a.dateAddedSec));

            // Sliding-window burst grouping
            List<List<MediaEntry>> bursts = new ArrayList<>();
            List<MediaEntry> currentBurst = new ArrayList<>();

            for (MediaEntry e : dirEntries) {
                if (currentBurst.isEmpty()) {
                    currentBurst.add(e);
                } else {
                    long lastSec = currentBurst.get(currentBurst.size() - 1).dateAddedSec;
                    if (Math.abs(lastSec - e.dateAddedSec) <= BURST_WINDOW_SECONDS) {
                        currentBurst.add(e);
                    } else {
                        bursts.add(new ArrayList<>(currentBurst));
                        currentBurst.clear();
                        currentBurst.add(e);
                    }
                }
            }
            if (!currentBurst.isEmpty()) bursts.add(currentBurst);

            // Convert bursts to RecentItems
            String dirName = new File(dirPath).getName();
            if (dirName.isEmpty()) dirName = dirPath;

            for (List<MediaEntry> burst : bursts) {
                if (burst.size() >= MIN_BURST_SIZE) {
                    List<File> files = new ArrayList<>();
                    for (MediaEntry me : burst) files.add(me.file);
                    long ts = burst.get(0).dateAddedSec * 1000L; // newest first
                    String label = dirName + " · " + files.size() + " archivos";
                    result.add(RecentItem.group(files, label, ts));
                } else {
                    // Single file — individual item
                    MediaEntry me = burst.get(0);
                    result.add(RecentItem.single(me.file, me.dateAddedSec * 1000L, true));
                }
            }
        }

        // Final sort: newest first across all directories
        Collections.sort(result, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        return result;
    }

    // ─── Recent discovery via MediaStore.Files (unified query, no hardcoded paths) ───

    /**
     * Lookback window for {@link #queryRecentFiles}: files added in the last 14 days.
     */
    private static final long QUERY_LOOKBACK_MS = 14L * 24L * 3600_000L;

    /**
     * Known extensions accepted when MEDIA_TYPE is NONE (0), i.e. files Android does not
     * classify as image/video/audio/document but that are still useful in a file manager:
     * archives, APKs, spreadsheets opened by office suites, e-books, scripts, etc.
     */
    private static final java.util.Set<String> KNOWN_EXTENSIONS = new java.util.HashSet<>(java.util.Arrays.asList(
            // Archives
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
            // Android packages
            "apk", "xapk", "apks",
            // Office / documents (fallback for pre-API-29 or unrecognised files)
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
            // Text / markup
            "txt", "rtf", "csv", "json", "xml", "html", "htm", "md",
            // E-books
            "epub", "mobi", "fb2",
            // Subtitles / captions
            "srt", "vtt",
            // Config / data
            "vcf", "ics"
    ));

    /**
     * Holds a file together with its MediaStore {@code DATE_ADDED} timestamp (milliseconds).
     * Using {@code DATE_ADDED} instead of {@link File#lastModified()} is critical for files
     * received via WhatsApp, Telegram, etc., whose filesystem mtime may be set to the
     * original creation date of the media (e.g. a photo taken months ago and shared today).
     */
    public static final class Entry {
        public final File file;
        /** Epoch milliseconds — when the file was first indexed by MediaStore (= arrival time). */
        public final long dateAddedMs;

        Entry(File file, long dateAddedMs) {
            this.file = file;
            this.dateAddedMs = dateAddedMs;
        }
    }

    /**
     * Queries {@link MediaStore.Files} for files added since {@code sinceMs} that match
     * known media types or known extensions (for MEDIA_TYPE_NONE files).
     * Results are ordered by DATE_ADDED descending, capped at 100 entries.
     *
     * <p>Returns {@link Entry} objects so callers can use the MediaStore {@code DATE_ADDED}
     * timestamp (arrival time) rather than the filesystem {@code lastModified} (which for
     * received media may reflect the original creation date, not when it arrived).
     *
     * @param ctx     application / activity context
     * @param sinceMs epoch-milliseconds lower bound
     * @return non-null list, newest first
     */
    public static List<Entry> queryRecentFiles(Context ctx, long sinceMs) {
        List<Entry> result = new ArrayList<>();
        if (ctx == null) return result;

        long sinceSecs = sinceMs / 1000L;

        String[] projection = {
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.SIZE
        };

        // Include all media types (images, video, audio, documents) plus MEDIA_TYPE_NONE
        // so that archives, APKs, etc. are also candidates (filtered by extension below).
        String selection =
                MediaStore.Files.FileColumns.DATE_ADDED + " > ? AND " +
                MediaStore.Files.FileColumns.SIZE + " > 0";

        String[] selectionArgs = { String.valueOf(sinceSecs) };
        // Plain DESC — no LIMIT in sortOrder: Android 10+ throws SecurityException for SQL
        // injected into sortOrder. The while-loop caps results at 100 on the Java side.
        String sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC";

        Log.d(TAG, "queryRecentFiles: since=" + sinceSecs + " (" + new java.util.Date(sinceMs) + ")");

        Cursor cursor = null;
        try {
            cursor = ctx.getContentResolver().query(
                    MediaStore.Files.getContentUri("external"),
                    projection, selection, selectionArgs, sortOrder);

            if (cursor == null) {
                Log.w(TAG, "queryRecentFiles: cursor is null");
                return result;
            }
            Log.d(TAG, "queryRecentFiles: cursor rows=" + cursor.getCount());

            int colData  = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
            int colDate  = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED);
            int colType  = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE);
            int colName  = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);

            int skippedExistence = 0;
            int skippedExtension = 0;
            while (cursor.moveToNext() && result.size() < 100) {
                try {
                    int    mediaType   = cursor.getInt(colType);
                    String path        = cursor.getString(colData);
                    String name        = cursor.getString(colName);
                    long   dateAddedMs = cursor.getLong(colDate) * 1000L;

                    if (path == null || path.isEmpty()) continue;

                    // For MEDIA_TYPE_NONE, only accept files with a known extension.
                    if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_NONE) {
                        if (!hasKnownExtension(name)) { skippedExtension++; continue; }
                    }

                    File f = new File(path);
                    if (f.exists() && f.isFile()) {
                        result.add(new Entry(f, dateAddedMs));
                    } else {
                        skippedExistence++;
                    }
                } catch (Exception rowEx) {
                    Log.w(TAG, "queryRecentFiles row error", rowEx);
                }
            }
            Log.d(TAG, "queryRecentFiles: accepted=" + result.size()
                    + " skippedNotExist=" + skippedExistence
                    + " skippedExt=" + skippedExtension);
        } catch (Exception e) {
            Log.e(TAG, "queryRecentFiles failed", e);
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Exception ignored) {}
            }
        }
        return result;
    }

    /** Returns the lookback duration used by {@link #queryRecentFiles}. */
    public static long getQueryLookbackMs() {
        return QUERY_LOOKBACK_MS;
    }

    private static boolean hasKnownExtension(String filename) {
        if (filename == null || filename.isEmpty()) return false;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return false;
        return KNOWN_EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    // ─── Data class ──────────────────────────────────────────────────────────

    private static class MediaEntry {
        final File file;
        final long dateAddedSec;

        MediaEntry(File file, long dateAddedSec) {
            this.file = file;
            this.dateAddedSec = dateAddedSec;
        }
    }
}
