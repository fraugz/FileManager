package com.fraugz.filemanager;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RecentManager {

    private static final String PREFS = "recent_files";
    private static final String KEY = "paths_ordered";
    private static final String KEY_PINNED = "pinned_paths";
    private static final String KEY_AUTO_DISCOVERY_SINCE = "auto_discovery_since";
    private static final int MAX = 50;

    public static class RecentEntry {
        public final String path;
        public final long accessedAt;
        public final boolean isPinned;

        public RecentEntry(String path, long accessedAt) {
            this(path, accessedAt, false);
        }

        public RecentEntry(String path, long accessedAt, boolean isPinned) {
            this.path = path;
            this.accessedAt = accessedAt;
            this.isPinned = isPinned;
        }
    }

    public static void add(Context ctx, String path) {
        if (path == null || path.trim().isEmpty()) return;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cleanPath = path.trim();

        List<RecentEntry> entries = parseOrderedEntries(prefs.getString(KEY, ""), prefs);

        removePath(entries, cleanPath);
        
        // Check if it's pinned to preserve pin status
        boolean wasPinned = isPinned(prefs, cleanPath);
        entries.add(0, new RecentEntry(cleanPath, System.currentTimeMillis(), wasPinned));
        if (entries.size() > MAX) entries = new ArrayList<>(entries.subList(0, MAX));

        prefs.edit().putString(KEY, joinOrderedEntries(entries)).apply();
    }

    public static boolean mergeAutoDiscovered(Context ctx, List<File> files) {
        if (files == null || files.isEmpty()) return false;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<RecentEntry> entries = parseOrderedEntries(prefs.getString(KEY, ""), prefs);
        boolean changed = false;
        long autoDiscoverySince = prefs.getLong(KEY_AUTO_DISCOVERY_SINCE, 0L);

        for (File file : files) {
            if (file == null || !file.exists() || !file.isFile()) continue;
            String path = file.getAbsolutePath();
            if (path.trim().isEmpty()) continue;
            if (autoDiscoverySince > 0L && file.lastModified() < autoDiscoverySince) continue;
            if (containsPath(entries, path)) continue;

            boolean isPinned = isPinned(prefs, path);
            long accessedAt = Math.max(1L, file.lastModified());
            entries.add(new RecentEntry(path, accessedAt, isPinned));
            changed = true;
        }

        if (!changed) return false;

        sortByPinnedAndAccessDesc(entries);
        if (entries.size() > MAX) {
            entries = new ArrayList<>(entries.subList(0, MAX));
        }
        prefs.edit().putString(KEY, joinOrderedEntries(entries)).apply();
        return true;
    }

    public static List<RecentEntry> getEntries(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, null);

        if (raw != null) {
            List<RecentEntry> parsed = parseOrderedEntries(raw, prefs);
            sortByPinnedAndAccessDesc(parsed);
            if (!parsed.isEmpty() && !raw.equals(joinOrderedEntries(parsed))) {
                prefs.edit().putString(KEY, joinOrderedEntries(parsed)).apply();
            }
            return parsed;
        }

        // Legacy migration from StringSet based storage.
        List<String> migrated = new ArrayList<>(prefs.getStringSet("paths", java.util.Collections.emptySet()));
        List<RecentEntry> out = new ArrayList<>();
        if (!migrated.isEmpty()) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < migrated.size(); i++) {
                String p = migrated.get(i);
                if (p == null) continue;
                String clean = p.trim();
                if (clean.isEmpty()) continue;
                out.add(new RecentEntry(clean, now - i, false));
            }
            sortByPinnedAndAccessDesc(out);
            prefs.edit().putString(KEY, joinOrderedEntries(out)).remove("paths").apply();
        }
        return out;
    }

    public static List<String> get(Context ctx) {
        List<RecentEntry> entries = getEntries(ctx);
        List<String> paths = new ArrayList<>();
        for (RecentEntry e : entries) {
            paths.add(e.path);
        }
        return paths;
    }

    public static void clear(Context ctx) {
        clearUnpinned(ctx);
    }

    public static void clearUnpinned(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<RecentEntry> entries = parseOrderedEntries(prefs.getString(KEY, ""), prefs);
        List<RecentEntry> pinnedEntries = new ArrayList<>();
        List<String> pinnedPaths = getPinnedPaths(prefs);

        for (RecentEntry entry : entries) {
            if (entry.isPinned) {
                pinnedEntries.add(entry);
            }
        }

        List<String> validPinned = new ArrayList<>();
        for (RecentEntry entry : pinnedEntries) {
            validPinned.add(entry.path);
        }

        // Keep only currently pinned entries in both ordered list and pinned registry.
        prefs.edit()
                .putString(KEY, joinOrderedEntries(pinnedEntries))
                .putString(KEY_PINNED, joinLines(validPinned))
            .putLong(KEY_AUTO_DISCOVERY_SINCE, System.currentTimeMillis())
                .apply();
    }

    public static void remove(Context ctx, String path) {
        if (path == null || path.trim().isEmpty()) return;
        String target = path.trim();

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<RecentEntry> entries = parseOrderedEntries(prefs.getString(KEY, ""), prefs);
        removePath(entries, target);
        List<String> pinned = getPinnedPaths(prefs);
        pinned.remove(target);
        savePinnedPaths(prefs, pinned);
        sortByPinnedAndAccessDesc(entries);

        prefs.edit().putString(KEY, joinOrderedEntries(entries)).apply();
    }

    public static void pin(Context ctx, String path) {
        if (path == null || path.trim().isEmpty()) return;
        String target = path.trim();

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> pinned = getPinnedPaths(prefs);
        if (!pinned.contains(target)) {
            pinned.add(target);
            savePinnedPaths(prefs, pinned);
        }
        
        // Update the entry to reflect pinned status
        List<RecentEntry> entries = parseOrderedEntries(prefs.getString(KEY, ""), prefs);
        prefs.edit().putString(KEY, joinOrderedEntries(entries)).apply();
    }

    public static void unpin(Context ctx, String path) {
        if (path == null || path.trim().isEmpty()) return;
        String target = path.trim();

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> pinned = getPinnedPaths(prefs);
        pinned.remove(target);
        savePinnedPaths(prefs, pinned);
        
        // Update the entry to reflect unpinned status
        List<RecentEntry> entries = parseOrderedEntries(prefs.getString(KEY, ""), prefs);
        prefs.edit().putString(KEY, joinOrderedEntries(entries)).apply();
    }

    public static boolean isPinned(Context ctx, String path) {
        if (path == null || path.trim().isEmpty()) return false;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return isPinned(prefs, path.trim());
    }

    private static void removePath(List<RecentEntry> entries, String path) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (path.equals(entries.get(i).path)) {
                entries.remove(i);
            }
        }
    }

    private static void sortByPinnedAndAccessDesc(List<RecentEntry> entries) {
        Collections.sort(entries, (a, b) -> {
            // Pinned items come first
            if (a.isPinned != b.isPinned) {
                return a.isPinned ? -1 : 1;
            }
            // Within each group, sort by access time descending
            return Long.compare(b.accessedAt, a.accessedAt);
        });
    }

    private static List<RecentEntry> parseOrderedEntries(String raw, SharedPreferences prefs) {
        List<RecentEntry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;

        List<String> pinnedPaths = getPinnedPaths(prefs);
        long now = System.currentTimeMillis();
        int legacyIndex = 0;
        String[] lines = raw.split("\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String path = trimmed;
            long accessedAt = now - legacyIndex;

            int sep = trimmed.lastIndexOf('\t');
            if (sep > 0 && sep < trimmed.length() - 1) {
                String maybePath = trimmed.substring(0, sep).trim();
                String maybeTs = trimmed.substring(sep + 1).trim();
                if (!maybePath.isEmpty()) {
                    path = maybePath;
                    try {
                        accessedAt = Long.parseLong(maybeTs);
                    } catch (NumberFormatException ignored) {
                        // Keep fallback for malformed legacy/new mixed values.
                    }
                }
            }

            if (!containsPath(out, path)) {
                boolean isPinned = pinnedPaths.contains(path);
                out.add(new RecentEntry(path, accessedAt, isPinned));
                legacyIndex++;
            }
        }
        return out;
    }

    private static List<String> getPinnedPaths(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_PINNED, "");
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String line : raw.split("\\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
        }
        return out;
    }

    private static void savePinnedPaths(SharedPreferences prefs, List<String> pinned) {
        prefs.edit().putString(KEY_PINNED, joinLines(pinned)).apply();
    }

    private static boolean isPinned(SharedPreferences prefs, String path) {
        List<String> pinned = getPinnedPaths(prefs);
        return pinned.contains(path);
    }

    private static void sortByAccessDesc(List<RecentEntry> entries) {
        Collections.sort(entries, Comparator.comparingLong((RecentEntry e) -> e.accessedAt).reversed());
    }

    private static boolean containsPath(List<RecentEntry> entries, String path) {
        for (RecentEntry e : entries) {
            if (path.equals(e.path)) return true;
        }
        return false;
    }

    private static String joinOrderedEntries(List<RecentEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append('\n');
            RecentEntry e = entries.get(i);
            sb.append(e.path).append('\t').append(e.accessedAt);
        }
        return sb.toString();
    }

    private static String joinLines(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}

