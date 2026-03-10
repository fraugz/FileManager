package com.fraugz.filemanager;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class DefaultAppsManager {

    private static final String PREFS = "default_apps";
    private static final String KEY = "apps_ordered";
    private static final int MAX = 30;

    private DefaultAppsManager() {
    }

    public static void add(Context ctx, String packageName, String appLabel) {
        if (packageName == null || packageName.trim().isEmpty()) return;

        String cleanPackage = packageName.trim();
        String cleanLabel = (appLabel == null || appLabel.trim().isEmpty()) ? cleanPackage : appLabel.trim();
        String entry = cleanPackage + "|" + cleanLabel;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> list = parseEntries(prefs.getString(KEY, ""));

        removePackage(list, cleanPackage);
        list.add(0, entry);
        if (list.size() > MAX) list = list.subList(0, MAX);

        prefs.edit().putString(KEY, joinEntries(list)).apply();
    }

    public static List<String> getEntries(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return parseEntries(prefs.getString(KEY, ""));
    }

    public static void remove(Context ctx, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> list = parseEntries(prefs.getString(KEY, ""));
        removePackage(list, packageName.trim());
        prefs.edit().putString(KEY, joinEntries(list)).apply();
    }

    public static void updateLabel(Context ctx, String packageName, String newLabel) {
        if (packageName == null || packageName.trim().isEmpty()) return;
        String pkg = packageName.trim();
        String label = (newLabel == null || newLabel.trim().isEmpty()) ? pkg : newLabel.trim();

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> list = parseEntries(prefs.getString(KEY, ""));

        for (int i = 0; i < list.size(); i++) {
            String row = list.get(i);
            int sep = row.indexOf('|');
            String rowPkg = sep >= 0 ? row.substring(0, sep) : row;
            if (pkg.equals(rowPkg)) {
                list.set(i, pkg + "|" + label);
                prefs.edit().putString(KEY, joinEntries(list)).apply();
                return;
            }
        }

        // If entry does not exist, create it.
        list.add(0, pkg + "|" + label);
        if (list.size() > MAX) list = list.subList(0, MAX);
        prefs.edit().putString(KEY, joinEntries(list)).apply();
    }

    public static void replacePackage(Context ctx, String oldPackageName, String newPackageName, String newLabel) {
        if (oldPackageName == null || oldPackageName.trim().isEmpty()) return;
        if (newPackageName == null || newPackageName.trim().isEmpty()) return;

        String oldPkg = oldPackageName.trim();
        String newPkg = newPackageName.trim();
        String label = (newLabel == null || newLabel.trim().isEmpty()) ? newPkg : newLabel.trim();

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> list = parseEntries(prefs.getString(KEY, ""));

        int oldIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            String row = list.get(i);
            int sep = row.indexOf('|');
            String rowPkg = sep >= 0 ? row.substring(0, sep) : row;
            if (oldPkg.equals(rowPkg)) {
                oldIndex = i;
                break;
            }
        }

        removePackage(list, oldPkg);
        removePackage(list, newPkg);
        String replacement = newPkg + "|" + label;

        if (oldIndex >= 0) {
            int targetIndex = Math.min(oldIndex, list.size());
            list.add(targetIndex, replacement);
        } else {
            list.add(0, replacement);
        }

        if (list.size() > MAX) list = list.subList(0, MAX);
        prefs.edit().putString(KEY, joinEntries(list)).apply();
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static void removePackage(List<String> list, String packageName) {
        for (int i = list.size() - 1; i >= 0; i--) {
            String row = list.get(i);
            String pkg = row;
            int sep = row.indexOf('|');
            if (sep >= 0) pkg = row.substring(0, sep);
            if (packageName.equals(pkg)) {
                list.remove(i);
            }
        }
    }

    private static List<String> parseEntries(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;

        String[] lines = raw.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static String joinEntries(List<String> entries) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(entries.get(i));
        }
        return sb.toString();
    }
}

