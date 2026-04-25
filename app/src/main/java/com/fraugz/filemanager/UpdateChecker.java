package com.fraugz.filemanager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Checks GitHub Releases for a newer version once per day.
 * Shows an AlertDialog when an update is available.
 */
public class UpdateChecker {

    private static final String PREFS_NAME       = "update_checker";
    private static final String KEY_LAST_CHECK   = "last_check_ms";
    private static final long   CHECK_INTERVAL   = 24L * 60L * 60L * 1000L; // 24 h
    private static final String RELEASES_API_URL =
            "https://api.github.com/repos/fraugz/FileManager/releases/latest";
    private static final String RELEASES_PAGE_URL =
            "https://github.com/fraugz/FileManager/releases/latest";

    /** Call once from Activity.onCreate — runs off the main thread, safe to ignore errors. */
    public static void checkOnce(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L);
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL) return;

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        exec.execute(() -> {
            try {
                String latestTag = fetchLatestTag();
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
                if (latestTag == null) return;

                String current;
                try {
                    current = ctx.getPackageManager()
                            .getPackageInfo(ctx.getPackageName(), 0).versionName;
                } catch (Exception e) { return; }
                if (!isNewer(latestTag, current)) return;

                main.post(() -> showUpdateDialog(ctx, latestTag));
            } catch (Exception ignored) {
                // Network unavailable or any error — silently skip
            } finally {
                exec.shutdown();
            }
        });
    }

    // -------------------------------------------------------------------------

    private static String fetchLatestTag() throws Exception {
        URL url = new URL(RELEASES_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(8_000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

        int code = conn.getResponseCode();
        if (code != 200) return null;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        conn.disconnect();

        // Minimal JSON parse: find "tag_name":"v1.2.3"
        String json = sb.toString();
        int idx = json.indexOf("\"tag_name\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int open = json.indexOf('"', colon + 1);
        if (open < 0) return null;
        int close = json.indexOf('"', open + 1);
        if (close < 0) return null;
        return json.substring(open + 1, close).replace("v", "").trim();
    }

    /**
     * Returns true if {@code remote} is strictly greater than {@code local}.
     * Compares semantic version segments numerically (1.10.0 > 1.9.0).
     */
    static boolean isNewer(String remote, String local) {
        int[] r = parseVersion(remote);
        int[] l = parseVersion(local);
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv != lv) return rv > lv;
        }
        return false;
    }

    private static int[] parseVersion(String v) {
        String[] parts = v.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { nums[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", "")); }
            catch (NumberFormatException e) { nums[i] = 0; }
        }
        return nums;
    }

    private static void showUpdateDialog(Context ctx, String latestVersion) {
        if (ctx == null) return;
        new AlertDialog.Builder(ctx)
                .setTitle(ctx.getString(R.string.update_available_title))
                .setMessage(ctx.getString(R.string.update_available_message, latestVersion))
                .setPositiveButton(R.string.update_download, (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(RELEASES_PAGE_URL));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(intent);
                })
                .setNegativeButton(R.string.update_later, null)
                .show();
    }
}
