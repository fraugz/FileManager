package com.fraugz.filemanager;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class RecentManager {

    private static final String PREFS = "recent_files";
    private static final String KEY = "paths_ordered";
    private static final int MAX = 50;

    public static void add(Context ctx, String path) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> list = parseOrderedPaths(prefs.getString(KEY, ""));
        list.remove(path);
        list.add(0, path);
        if (list.size() > MAX) list = list.subList(0, MAX);
        prefs.edit().putString(KEY, joinOrderedPaths(list)).apply();
    }

    public static List<String> get(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String ordered = prefs.getString(KEY, null);
        if (ordered != null) {
            return parseOrderedPaths(ordered);
        }

        // Legacy migration from StringSet based storage.
        List<String> migrated = new ArrayList<>(prefs.getStringSet("paths", java.util.Collections.emptySet()));
        if (!migrated.isEmpty()) {
            prefs.edit().putString(KEY, joinOrderedPaths(migrated)).remove("paths").apply();
        }
        return migrated;
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static List<String> parseOrderedPaths(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            String p = line.trim();
            if (!p.isEmpty() && !out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private static String joinOrderedPaths(List<String> paths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(paths.get(i));
        }
        return sb.toString();
    }
}

