package com.fraugz.filemanager;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RecentManager {

    private static final String PREFS = "recent_files";
    private static final String KEY = "paths_ordered";
    private static final int MAX = 50;

    public static class RecentEntry {
        public final String path;
        public final long accessedAt;

        public RecentEntry(String path, long accessedAt) {
            this.path = path;
            this.accessedAt = accessedAt;
        }
    }

    public static void add(Context ctx, String path) {
        if (path == null || path.trim().isEmpty()) return;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cleanPath = path.trim();
        List<RecentEntry> entries = parseOrderedEntries(prefs.getString(KEY, ""));

        removePath(entries, cleanPath);
        entries.add(0, new RecentEntry(cleanPath, System.currentTimeMillis()));
        if (entries.size() > MAX) entries = new ArrayList<>(entries.subList(0, MAX));

        prefs.edit().putString(KEY, joinOrderedEntries(entries)).apply();
    }

    public static List<RecentEntry> getEntries(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, null);
        if (raw != null) {
            List<RecentEntry> parsed = parseOrderedEntries(raw);
            sortByAccessDesc(parsed);
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
                out.add(new RecentEntry(clean, now - i));
            }
            sortByAccessDesc(out);
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
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static void remove(Context ctx, String path) {
        if (path == null || path.trim().isEmpty()) return;
        String target = path.trim();

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<RecentEntry> entries = parseOrderedEntries(prefs.getString(KEY, ""));
        removePath(entries, target);
        sortByAccessDesc(entries);
        prefs.edit().putString(KEY, joinOrderedEntries(entries)).apply();
    }

    private static void removePath(List<RecentEntry> entries, String path) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (path.equals(entries.get(i).path)) {
                entries.remove(i);
            }
        }
    }

    private static void sortByAccessDesc(List<RecentEntry> entries) {
        Collections.sort(entries, Comparator.comparingLong((RecentEntry e) -> e.accessedAt).reversed());
    }

    private static List<RecentEntry> parseOrderedEntries(String raw) {
        List<RecentEntry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;

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
                out.add(new RecentEntry(path, accessedAt));
                legacyIndex++;
            }
        }
        return out;
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
}

