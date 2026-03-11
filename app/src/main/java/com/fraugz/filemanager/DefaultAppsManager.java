package com.fraugz.filemanager;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class DefaultAppsManager {

    private static final String PREFS = "default_apps";
    private static final String KEY = "apps_ordered";
    private static final int MAX = 30;

    public static class Entry {
        public final String extension;
        public final String packageName;
        public final String appLabel;

        Entry(String extension, String packageName, String appLabel) {
            this.extension = extension;
            this.packageName = packageName;
            this.appLabel = appLabel;
        }
    }

    private DefaultAppsManager() {
    }

    public static void add(Context ctx, String extension, String packageName, String appLabel) {
        if (extension == null || extension.trim().isEmpty()) return;
        if (packageName == null || packageName.trim().isEmpty()) return;

        String cleanExt = normalizeExtension(extension);
        String cleanPackage = packageName.trim();
        String cleanLabel = (appLabel == null || appLabel.trim().isEmpty()) ? cleanPackage : appLabel.trim();

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<Entry> list = parseEntries(prefs.getString(KEY, ""));

        removeExtension(list, cleanExt);
        list.add(0, new Entry(cleanExt, cleanPackage, cleanLabel));
        if (list.size() > MAX) list = new ArrayList<>(list.subList(0, MAX));

        prefs.edit().putString(KEY, joinEntries(list)).apply();
    }

    public static List<Entry> getEntries(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return parseEntries(prefs.getString(KEY, ""));
    }

    public static String getPackageForExtension(Context ctx, String extension) {
        String ext = normalizeExtension(extension == null ? "*" : extension);
        List<Entry> entries = getEntries(ctx);
        for (Entry e : entries) {
            if (ext.equals(e.extension)) return e.packageName;
        }
        for (Entry e : entries) {
            if ("*".equals(e.extension)) return e.packageName;
        }
        return null;
    }

    public static void removeByExtension(Context ctx, String extension) {
        if (extension == null || extension.trim().isEmpty()) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<Entry> list = parseEntries(prefs.getString(KEY, ""));
        removeExtension(list, normalizeExtension(extension));
        prefs.edit().putString(KEY, joinEntries(list)).apply();
    }

    public static void replacePackage(Context ctx, String extension, String newPackageName, String newLabel) {
        if (extension == null || extension.trim().isEmpty()) return;
        if (newPackageName == null || newPackageName.trim().isEmpty()) return;

        String ext = normalizeExtension(extension);
        String newPkg = newPackageName.trim();
        String label = (newLabel == null || newLabel.trim().isEmpty()) ? newPkg : newLabel.trim();

        add(ctx, ext, newPkg, label);
    }

    private static String normalizeExtension(String extension) {
        String ext = extension.trim().toLowerCase();
        if (ext.isEmpty()) return "*";
        if ("*".equals(ext)) return ext;
        if (!ext.startsWith(".")) ext = "." + ext;
        return ext;
    }

    private static void removeExtension(List<Entry> list, String extension) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (extension.equals(list.get(i).extension)) {
                list.remove(i);
            }
        }
    }

    private static List<Entry> parseEntries(String raw) {
        List<Entry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;

        String[] lines = raw.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String extension;
            String pkg;
            String label;

            String[] parts = trimmed.split("\\t", 3);
            if (parts.length == 3) {
                extension = normalizeExtension(parts[0]);
                pkg = parts[1].trim();
                label = parts[2].trim();
            } else {
                // Legacy fallback format: package|label
                int sep = trimmed.indexOf('|');
                if (sep <= 0) continue;
                extension = "*";
                pkg = trimmed.substring(0, sep).trim();
                label = trimmed.substring(sep + 1).trim();
            }

            if (pkg.isEmpty()) continue;
            if (label.isEmpty()) label = pkg;

            removeExtension(out, extension);
            out.add(new Entry(extension, pkg, label));
        }

        return out;
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static String joinEntries(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append('\n');
            Entry e = entries.get(i);
            sb.append(e.extension).append('\t').append(e.packageName).append('\t').append(e.appLabel);
        }
        return sb.toString();
    }
}

