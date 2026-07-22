package com.fraugz.filemanager;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
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

    private static final String PREFS_NAME           = "update_checker";
    private static final String KEY_LAST_CHECK       = "last_check_ms";
    private static final String KEY_DISMISSED_VERSION = "dismissed_version";
    private static final long   CHECK_INTERVAL       = 24L * 60L * 60L * 1000L; // 24 h
    private static final String RELEASES_API_URL =
            "https://api.github.com/repos/fraugz/FileManager/releases/latest";
    private static final String RELEASES_PAGE_URL =
            "https://github.com/fraugz/FileManager/releases/latest";
    private static final String APK_DIRECT_URL_TEMPLATE =
            "https://github.com/fraugz/FileManager/releases/download/v%s/app-release.apk";

    /** Call once from Activity.onCreate — runs off the main thread, safe to ignore errors. */
    public static void checkOnce(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L);
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL) return;
        runCheck(ctx, prefs);
    }

    /**
     * Forces an immediate update check regardless of the 24h interval.
     * Shows a "no updates available" toast when the app is already up to date.
     */
    public static void checkNow(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        runCheck(ctx, prefs, true);
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private static void runCheck(Context ctx, SharedPreferences prefs) {
        runCheck(ctx, prefs, false);
    }

    private static void runCheck(Context ctx, SharedPreferences prefs, boolean showUpToDate) {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        exec.execute(() -> {
            try {
                String latestTag = fetchLatestTag();
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();

                String current;
                try {
                    current = ctx.getPackageManager()
                            .getPackageInfo(ctx.getPackageName(), 0).versionName;
                } catch (Exception e) { return; }

                if (latestTag != null && isNewer(latestTag, current)) {
                    String dismissed = prefs.getString(KEY_DISMISSED_VERSION, "");
                    if (latestTag.equals(dismissed)) return; // user dismissed this version
                    main.post(() -> showUpdateDialog(ctx, latestTag, prefs));
                } else if (showUpToDate) {
                    main.post(() -> android.widget.Toast.makeText(ctx,
                            ctx.getString(R.string.update_up_to_date),
                            android.widget.Toast.LENGTH_SHORT).show());
                }
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

    private static void showUpdateDialog(Context ctx, String latestVersion, SharedPreferences prefs) {
        if (ctx == null) return;
        String apkUrl = String.format(APK_DIRECT_URL_TEMPLATE, latestVersion);
        new AlertDialog.Builder(ctx)
                .setTitle(ctx.getString(R.string.update_available_title))
                .setMessage(ctx.getString(R.string.update_available_message, latestVersion))
        .setPositiveButton(R.string.update_download, (d, w) -> {
                    DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl))
                            .setTitle(ctx.getString(R.string.app_name))
                            .setDescription(latestVersion)
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setMimeType("application/vnd.android.package-archive")
                            .setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    "filemanager-" + latestVersion + ".apk");
                    long downloadId = dm.enqueue(request);
                    BroadcastReceiver receiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                            if (id == downloadId) {
                                context.unregisterReceiver(this);
                                Uri apkUri = dm.getUriForDownloadedFile(downloadId);
                                if (apkUri != null) {
                                    Intent install = new Intent(Intent.ACTION_VIEW);
                                    install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                                    install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    context.startActivity(install);
                                }
                            }
                        }
                    };
                    ctx.registerReceiver(receiver,
                            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                            android.os.Build.VERSION.SDK_INT >= 33
                                    ? android.content.Context.RECEIVER_EXPORTED
                                    : 0);
                })
                .setNeutralButton(R.string.update_later, null)
                .setNegativeButton(R.string.update_dismiss, (d, w) ->
                        prefs.edit().putString(KEY_DISMISSED_VERSION, latestVersion).apply())
                .show();
    }
}
