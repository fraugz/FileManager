package com.fraugz.filemanager;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrashActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private View emptyView;
    private TextView trashSizeSummary;
    private List<TrashManager.TrashEntry> trashFiles;
    private List<SystemTrashEntry> systemTrashFiles = new ArrayList<>();
    private List<Object> allItems = new ArrayList<>();
    private boolean isDark;

    // Bottom action bar
    private LinearLayout bottomBar;
    private final java.util.Set<Object> selectedItems = new java.util.LinkedHashSet<>();

    // Thumbnail support
    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> previewCache = new LruCache<String, Bitmap>(20) {
        @Override protected int sizeOf(String key, Bitmap v) {
            return Math.max(1, v.getByteCount() / 1024);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trash);

        isDark = ThemeManager.getTheme(this) == ThemeManager.THEME_DARK;

        recycler = findViewById(R.id.recycler_trash);
        emptyView = findViewById(R.id.empty_trash_view);
        trashSizeSummary = findViewById(R.id.trash_size_summary);

        buildBottomBar();
        applyThemeColors();

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        // Dismiss bottom bar when user scrolls
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy != 0) hideBottomBar();
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_empty_trash).setOnClickListener(v -> confirmEmptyTrash());

        loadTrash();
    }

    @Override
    public void onBackPressed() {
        if (bottomBar != null && bottomBar.getVisibility() == View.VISIBLE) {
            hideBottomBar();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        thumbExecutor.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Bottom action bar
    // -------------------------------------------------------------------------

    private void buildBottomBar() {
        LinearLayout root = findViewById(R.id.trash_root);
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setVisibility(View.GONE);
        bottomBar.setElevation(dp(8));
        bottomBar.setBackgroundColor(0xFF111111);

        // [icon drawable res, label string res]
        int[] icons  = { R.drawable.ic_action_move, -1, R.drawable.ic_trash };
        int[] labels = { R.string.restore, R.string.info, R.string.delete_forever };
        int[] tints  = { 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFF453A };  // delete gets red tint

        for (int i = 0; i < labels.length; i++) {
            final int action = i;

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER);
            cell.setPadding(0, dp(10), 0, dp(10));
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cell.setLayoutParams(cellLp);
            // ripple
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            cell.setBackgroundResource(tv.resourceId);
            cell.setOnClickListener(v -> onBottomBarAction(action));

            ImageView iconView = new ImageView(this);
            iconView.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
            if (icons[i] == -1) {
                iconView.setImageDrawable(
                        androidx.core.content.ContextCompat.getDrawable(
                                this, android.R.drawable.ic_menu_info_details));
            } else {
                iconView.setImageResource(icons[i]);
            }
            iconView.setColorFilter(tints[i]);
            cell.addView(iconView);

            TextView labelView = new TextView(this);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(4);
            labelView.setLayoutParams(tlp);
            labelView.setText(labels[i]);
            labelView.setTextSize(11f);
            labelView.setTextColor(tints[i]);
            labelView.setMaxLines(1);
            labelView.setSingleLine(true);
            labelView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(labelView);

            bottomBar.addView(cell);
        }
        root.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void toggleSelection(Object item) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
        notifySelectionChanged(item);
        if (selectedItems.isEmpty()) {
            bottomBar.setVisibility(View.GONE);
        } else {
            bottomBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideBottomBar() {
        List<Object> prev = new ArrayList<>(selectedItems);
        selectedItems.clear();
        bottomBar.setVisibility(View.GONE);
        for (Object o : prev) notifySelectionChanged(o);
    }

    private void notifySelectionChanged(Object item) {
        if (item == null || recycler.getAdapter() == null) return;
        int idx = allItems.indexOf(item);
        if (idx >= 0) recycler.getAdapter().notifyItemChanged(idx);
    }

    private void onBottomBarAction(int action) {
        List<Object> items = new ArrayList<>(selectedItems);
        hideBottomBar();
        if (items.isEmpty()) return;

        if (action == 0) {
            // Restore all selected
            List<TrashManager.TrashEntry> appEntries = new ArrayList<>();
            List<SystemTrashEntry> sysEntries = new ArrayList<>();
            for (Object o : items) {
                if (o instanceof TrashManager.TrashEntry) appEntries.add((TrashManager.TrashEntry) o);
                else if (o instanceof SystemTrashEntry) sysEntries.add((SystemTrashEntry) o);
            }
            int restored = 0;
            String lastErr = null;
            for (TrashManager.TrashEntry e : appEntries) {
                if (TrashManager.restore(this, e)) restored++;
                else lastErr = TrashManager.getLastError();
            }
            for (SystemTrashEntry e : sysEntries) {
                restoreSystemEntry(e);
                restored++;
            }
            if (lastErr != null && !lastErr.trim().isEmpty()) {
                Toast.makeText(this, getString(R.string.restore) + ": " + lastErr, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.item_restored, Toast.LENGTH_SHORT).show();
            }
            loadTrash();

        } else if (action == 1) {
            // Info: only show for single selection
            if (items.size() == 1) showItemInfo(items.get(0));

        } else {
            // Delete forever all selected
            List<TrashManager.TrashEntry> appEntries = new ArrayList<>();
            List<SystemTrashEntry> sysEntries = new ArrayList<>();
            for (Object o : items) {
                if (o instanceof TrashManager.TrashEntry) appEntries.add((TrashManager.TrashEntry) o);
                else if (o instanceof SystemTrashEntry) sysEntries.add((SystemTrashEntry) o);
            }
            int total = items.size();
            String names = items.size() == 1
                    ? (items.get(0) instanceof TrashManager.TrashEntry
                        ? ((TrashManager.TrashEntry) items.get(0)).getOriginalName()
                        : ((SystemTrashEntry) items.get(0)).originalName)
                    : getString(R.string.items_count, total);
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.warning_title)
                    .setMessage(getString(R.string.warning_delete_forever_single_message, names))
                    .setPositiveButton(R.string.delete_forever, (d, w) -> {
                        for (SystemTrashEntry e : sysEntries) e.file.delete();
                        if (!appEntries.isEmpty()) runDeleteTrashEntriesWithProgress(appEntries, false);
                        else loadTrash();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    private void showItemInfo(Object item) {
        File file;
        String displayName;
        String path;
        String timeLeftLine = "";
        if (item instanceof TrashManager.TrashEntry) {
            TrashManager.TrashEntry e = (TrashManager.TrashEntry) item;
            file = e.getTrashFile();
            displayName = e.getOriginalName();
            path = e.getOriginalPath();
            timeLeftLine = "\n" + getString(R.string.trash_expires) + ": " + formatDaysLeft(e.getDeletedAt());
        } else {
            SystemTrashEntry e = (SystemTrashEntry) item;
            file = e.file;
            displayName = e.originalName;
            path = e.file.getParent() + "/" + e.originalName;
        }
        FileItem fi = new FileItem(file);
        String message = getString(R.string.size) + ": " + fi.getFormattedSize(this) + "\n"
                + getString(R.string.date) + ": " + fi.getFormattedDate() + "\n"
                + getString(R.string.path) + ": " + path
                + timeLeftLine;
        new AlertDialog.Builder(this)
                .setTitle(displayName)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Theme
    // -------------------------------------------------------------------------

    private void applyThemeColors() {
        int bgMain = isDark ? 0xFF000000 : 0xFFF2F2F7;
        int textPrimary = isDark ? 0xFFFFFFFF : 0xFF1C1C1E;
        int textSecondary = isDark ? 0xFF888888 : 0xFF6B6B6B;

        View root = findViewById(R.id.trash_root);
        View topBar = findViewById(R.id.trash_top_bar);
        TextView title = findViewById(R.id.trash_title);
        if (root != null) root.setBackgroundColor(bgMain);
        if (topBar != null) topBar.setBackgroundColor(bgMain);
        if (title != null) title.setTextColor(textPrimary);

        if (recycler != null) recycler.setBackgroundColor(bgMain);
        if (trashSizeSummary != null) trashSizeSummary.setTextColor(textSecondary);
        if (emptyView != null) {
            emptyView.setBackgroundColor(bgMain);
            View child = ((ViewGroup) emptyView).getChildAt(1);
            if (child instanceof TextView) ((TextView) child).setTextColor(textSecondary);
        }
        View back = findViewById(R.id.btn_back);
        if (back instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) back).setColorFilter(textPrimary);
        }
        // Bottom bar keeps its own dark fixed theme (#111111 set in buildBottomBar)
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    private void loadTrash() {
        TrashManager.purgeExpired(this);
        trashFiles = TrashManager.getTrashFiles(this);
        systemTrashFiles = scanSystemTrash();
        rebuildAllItems();
        updateTrashSizeSummary();

        boolean bothEmpty = trashFiles.isEmpty() && systemTrashFiles.isEmpty();
        if (bothEmpty) {
            recycler.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recycler.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            recycler.setAdapter(new TrashAdapter());
        }
    }

    private void rebuildAllItems() {
        allItems.clear();

        // App trash section — always shown (point 6)
        allItems.add(new SectionHeader(
                getString(R.string.app_trash_section),
                getString(R.string.trash_info_app)));
        if (trashFiles.isEmpty()) {
            allItems.add(new EmptySection());
        } else {
            allItems.addAll(trashFiles);
        }

        // System trash section — always shown (point 6)
        allItems.add(new SectionHeader(
                getString(R.string.system_trash_section),
                getString(R.string.trash_info_system)));
        if (systemTrashFiles.isEmpty()) {
            allItems.add(new EmptySection());
        } else {
            allItems.addAll(systemTrashFiles);
        }
    }

    private List<SystemTrashEntry> scanSystemTrash() {
        List<SystemTrashEntry> result = new ArrayList<>();
        String[] publicDirs = {
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DOCUMENTS,
                Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_MUSIC,
        };
        for (String d : publicDirs) {
            scanDirForTrashed(Environment.getExternalStoragePublicDirectory(d), result, 0);
        }
        return result;
    }

    private void scanDirForTrashed(File dir, List<SystemTrashEntry> out, int depth) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || depth > 1) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.getName().startsWith(".trashed-")) {
                out.add(new SystemTrashEntry(child));
            } else if (child.isDirectory() && depth == 0) {
                scanDirForTrashed(child, out, 1);
            }
        }
    }

    private void updateTrashSizeSummary() {
        if (trashSizeSummary == null) return;
        long totalBytes = 0L;
        if (trashFiles != null) {
            for (TrashManager.TrashEntry e : trashFiles) {
                if (e == null || e.getTrashFile() == null) continue;
                totalBytes += computeSize(e.getTrashFile());
            }
        }
        if (systemTrashFiles != null) {
            for (SystemTrashEntry e : systemTrashFiles) {
                if (e == null || e.file == null) continue;
                totalBytes += computeSize(e.file);
            }
        }
        trashSizeSummary.setText(getString(R.string.trash_total_size, formatSize(totalBytes)));
    }

    private long computeSize(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children == null) return 0L;
        for (File child : children) {
            total += computeSize(child);
        }
        return total;
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024L * 1024L) return String.format(java.util.Locale.getDefault(), "%.1f KB", size / 1024.0);
        if (size < 1024L * 1024L * 1024L) return String.format(java.util.Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
        return String.format(java.util.Locale.getDefault(), "%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    private void confirmEmptyTrash() {
        int appCount = trashFiles.size();
        int sysCount = systemTrashFiles.size();
        if (appCount == 0 && sysCount == 0) {
            Toast.makeText(this, R.string.trash_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String message;
        if (appCount > 0 && sysCount > 0) {
            message = getString(R.string.empty_trash_message_both, appCount, sysCount);
        } else if (appCount > 0) {
            message = getString(R.string.empty_trash_message, appCount);
        } else {
            message = getString(R.string.empty_trash_message_system, sysCount);
        }
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.empty_trash_title)
            .setMessage(message)
            .setPositiveButton(R.string.empty_trash, (d, w) -> emptyBothTrashes(appCount, sysCount))
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void emptyBothTrashes(int appCount, int sysCount) {
        int total = appCount + sysCount;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final ProgressDialogHolder holder = showProgressDialog(R.string.deleting_files, total, cancelled);
        final List<TrashManager.TrashEntry> appEntries = new ArrayList<>(trashFiles);
        final List<SystemTrashEntry> sysEntries = new ArrayList<>(systemTrashFiles);

        new Thread(() -> {
            int deleted = 0;
            for (TrashManager.TrashEntry e : appEntries) {
                if (cancelled.get()) break;
                TrashManager.deleteEntry(e);
                deleted++;
                final int d = deleted;
                runOnUiThread(() -> updateProgressDialog(holder, d));
            }
            for (SystemTrashEntry e : sysEntries) {
                if (cancelled.get()) break;
                e.file.delete();
                deleted++;
                final int d = deleted;
                runOnUiThread(() -> updateProgressDialog(holder, d));
            }
            final boolean wasCancelled = cancelled.get();
            runOnUiThread(() -> {
                dismissProgressDialog(holder);
                loadTrash();
                if (wasCancelled) {
                    Toast.makeText(this, R.string.operation_cancelled, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.trash_emptied, Toast.LENGTH_SHORT).show();
                }
            });
        }, "trash-empty").start();
    }

    private static class ProgressDialogHolder {
        final AlertDialog dialog;
        final TextView status;
        final ProgressBar progress;
        final int total;

        ProgressDialogHolder(AlertDialog dialog, TextView status, ProgressBar progress, int total) {
            this.dialog = dialog;
            this.status = status;
            this.progress = progress;
            this.total = total;
        }
    }

    private ProgressDialogHolder showProgressDialog(int titleRes, int total, AtomicBoolean cancelled) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, dp(12), p, dp(4));

        TextView status = new TextView(this);
        status.setText(getString(R.string.operation_progress, 0, Math.max(1, total)));
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(Math.max(1, total));
        bar.setProgress(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        root.addView(bar, lp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(root)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    cancelled.set(true);
                    status.setText(getString(R.string.cancelling));
                })
                .create();
        dialog.show();
        return new ProgressDialogHolder(dialog, status, bar, Math.max(1, total));
    }

    private void updateProgressDialog(ProgressDialogHolder holder, int done) {
        if (holder == null) return;
        int clamped = Math.max(0, Math.min(done, holder.total));
        holder.progress.setProgress(clamped);
        holder.status.setText(getString(R.string.operation_progress, clamped, holder.total));
    }

    private void dismissProgressDialog(ProgressDialogHolder holder) {
        if (holder == null || holder.dialog == null) return;
        if (holder.dialog.isShowing()) holder.dialog.dismiss();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void runDeleteTrashEntriesWithProgress(List<TrashManager.TrashEntry> entries, boolean showEmptiedMessage) {
        if (entries == null || entries.isEmpty()) return;

        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final ProgressDialogHolder holder = showProgressDialog(R.string.deleting_files, entries.size(), cancelled);

        new Thread(() -> {
            int deleted = 0;
            for (TrashManager.TrashEntry entry : entries) {
                if (cancelled.get()) break;
                TrashManager.deleteEntry(entry);
                deleted++;
                final int done = deleted;
                runOnUiThread(() -> updateProgressDialog(holder, done));
            }

            final int deletedFinal = deleted;
            runOnUiThread(() -> {
                dismissProgressDialog(holder);
                loadTrash();
                if (cancelled.get()) {
                    android.widget.Toast.makeText(this, R.string.operation_cancelled, android.widget.Toast.LENGTH_SHORT).show();
                } else if (showEmptiedMessage) {
                    android.widget.Toast.makeText(this, R.string.trash_emptied, android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    android.widget.Toast.makeText(this, getString(R.string.deleted_count, deletedFinal), android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }, "trash-delete").start();
    }

    // -------------------------------------------------------------------------
    // Model classes
    // -------------------------------------------------------------------------

    static class SectionHeader {
        final String title;
        final String info;
        SectionHeader(String title, String info) {
            this.title = title;
            this.info = info;
        }
    }

    static class EmptySection {}

    static class SystemTrashEntry {
        final File file;
        final String originalName;

        SystemTrashEntry(File file) {
            this.file = file;
            this.originalName = extractOriginalName(file.getName());
        }

        static String extractOriginalName(String name) {
            // Android pattern: .trashed-<unix_timestamp_seconds>-<original_name>
            if (name.startsWith(".trashed-")) {
                String rest = name.substring(9);
                int dash = rest.indexOf('-');
                if (dash >= 0 && dash < rest.length() - 1) {
                    return rest.substring(dash + 1);
                }
            }
            return name;
        }
    }

    // -------------------------------------------------------------------------
    // Adapter
    // -------------------------------------------------------------------------

    class TrashAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER  = 0;
        private static final int TYPE_APP     = 1;
        private static final int TYPE_SYSTEM  = 2;
        private static final int TYPE_EMPTY   = 3;

        // ---- Header VH ----
        class HeaderVH extends RecyclerView.ViewHolder {
            TextView label;
            TextView infoBtn;
            String info;
            HeaderVH(View v) {
                super(v);
                label   = v.findViewWithTag("header_label");
                infoBtn = v.findViewWithTag("header_info");
            }
        }

        // ---- Entry VH ----
        class EntryVH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name, meta;
            EntryVH(View v) {
                super(v);
                icon = v.findViewById(R.id.icon);
                name = v.findViewById(R.id.name);
                meta = v.findViewById(R.id.meta);
            }
        }

        // ---- Empty placeholder VH ----
        class EmptyVH extends RecyclerView.ViewHolder {
            EmptyVH(View v) { super(v); }
        }

        @Override public int getItemViewType(int pos) {
            Object item = allItems.get(pos);
            if (item instanceof SectionHeader)           return TYPE_HEADER;
            if (item instanceof TrashManager.TrashEntry) return TYPE_APP;
            if (item instanceof SystemTrashEntry)        return TYPE_SYSTEM;
            return TYPE_EMPTY;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == TYPE_HEADER) {
                return new HeaderVH(buildHeaderView(parent));
            }
            if (type == TYPE_EMPTY) {
                return new EmptyVH(buildEmptyView(parent));
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
            // Remove the 3-dots button (point 2)
            View more = v.findViewById(R.id.btn_more);
            if (more != null) more.setVisibility(View.GONE);
            return new EntryVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int pos) {
            int textPrimary   = isDark ? 0xFFFFFFFF : 0xFF1C1C1E;
            int textSecondary = isDark ? 0xFF888888 : 0xFF6B6B6B;
            int bgColor       = isDark ? 0xFF000000 : 0xFFFFFFFF;
            int headerBg      = isDark ? 0xFF1C1C1E : 0xFFF2F2F7;
            Object item = allItems.get(pos);

            if (vh instanceof HeaderVH) {
                HeaderVH h = (HeaderVH) vh;
                SectionHeader sec = (SectionHeader) item;
                h.label.setText(sec.title);
                h.label.setTextColor(textSecondary);
                h.info = sec.info;
                h.infoBtn.setTextColor(0xFFFFFFFF);
                // Re-tint the ? badge background for the current theme
                android.graphics.drawable.GradientDrawable badgeBg =
                        new android.graphics.drawable.GradientDrawable();
                badgeBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                badgeBg.setColor(isDark ? 0xFF636366 : 0xFF8E8E93);
                badgeBg.setSize(dp(20), dp(20));
                h.infoBtn.setBackground(badgeBg);
                h.infoBtn.setOnClickListener(v -> new AlertDialog.Builder(TrashActivity.this)
                        .setTitle(sec.title)
                        .setMessage(sec.info)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
                h.itemView.setBackgroundColor(headerBg);
                return;
            }

            if (vh instanceof EmptyVH) {
                TextView tv = (TextView) ((EmptyVH) vh).itemView;
                tv.setTextColor(textSecondary);
                tv.setBackgroundColor(bgColor);
                return;
            }

            EntryVH h = (EntryVH) vh;
            h.name.setTextColor(textPrimary);
            h.meta.setTextColor(textSecondary);
            boolean isSelected = selectedItems.contains(item);
            int selectedBg = isDark ? 0xFF1A3A5C : 0xFFD0E8FF;
            h.itemView.setBackgroundColor(isSelected ? selectedBg : bgColor);
            h.icon.setVisibility(View.VISIBLE);
            // Override the hardcoded dark bg_icon_circle to match the current theme
            android.graphics.drawable.GradientDrawable iconBg =
                    new android.graphics.drawable.GradientDrawable();
            iconBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            iconBg.setCornerRadius(dp(12));
            iconBg.setColor(isDark ? 0xFF2C2C2E : 0xFFE5E5EA);
            h.icon.setBackground(iconBg);

            if (item instanceof TrashManager.TrashEntry) {
                TrashManager.TrashEntry entry = (TrashManager.TrashEntry) item;
                File file = entry.getTrashFile();
                FileItem fi = new FileItem(file);
                h.name.setText(entry.getOriginalName());
                h.meta.setText(fi.getFormattedSize(TrashActivity.this) + "  \u00b7  "
                        + fi.getFormattedDate() + "  \u00b7  " + formatDaysLeft(entry.getDeletedAt()));
                bindPreview(file, entry.getOriginalName(), h.icon);

                h.itemView.setOnClickListener(v -> {
                    if (!selectedItems.isEmpty()) { toggleSelection(entry); return; }
                    openFile(file, entry.getOriginalName());
                });
                h.itemView.setOnLongClickListener(v -> {
                    toggleSelection(entry);
                    return true;
                });

            } else if (item instanceof SystemTrashEntry) {
                SystemTrashEntry entry = (SystemTrashEntry) item;
                FileItem fi = new FileItem(entry.file);
                h.name.setText(entry.originalName);
                h.meta.setText(fi.getFormattedSize(TrashActivity.this) + "  ·  " + fi.getFormattedDate());
                bindPreview(entry.file, h.icon);

                h.itemView.setOnClickListener(v -> {
                    if (!selectedItems.isEmpty()) { toggleSelection(entry); return; }
                    openFile(entry.file, entry.originalName);
                });
                h.itemView.setOnLongClickListener(v -> {
                    toggleSelection(entry);
                    return true;
                });
            }
        }

        @Override public int getItemCount() { return allItems.size(); }

        // ---- View builders ----

        private View buildHeaderView(ViewGroup parent) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            int padH = dp(24), padV = dp(10);
            row.setPadding(padH, padV, padH, padV / 2);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView label = new TextView(parent.getContext());
            label.setTag("header_label");
            label.setTextSize(12f);
            label.setAllCaps(true);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(label, lp);

            TextView infoBtn = new TextView(parent.getContext());
            infoBtn.setTag("header_info");
            infoBtn.setText("?");
            infoBtn.setTextSize(13f);
            infoBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            infoBtn.setTextColor(0xFFFFFFFF);
            infoBtn.setGravity(android.view.Gravity.CENTER);
            // Circular badge background
            android.graphics.drawable.GradientDrawable badge =
                    new android.graphics.drawable.GradientDrawable();
            badge.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            badge.setColor(0xFF636366);  // system-grey, visible on both light/dark bg
            int badgeSize = dp(20);
            badge.setSize(badgeSize, badgeSize);
            infoBtn.setBackground(badge);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
            infoLp.setMarginStart(dp(8));
            infoBtn.setLayoutParams(infoLp);
            row.addView(infoBtn);

            android.widget.FrameLayout container = new android.widget.FrameLayout(parent.getContext());
            container.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            container.addView(row);

            // Wire ℹ click (needs access to HeaderVH — use tag trick)
            container.setTag("header_info_clickable");
            return container;
        }

        private View buildEmptyView(ViewGroup parent) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            int pad = dp(16);
            tv.setPadding(dp(72), pad, pad, pad);
            tv.setTextSize(14f);
            tv.setText(R.string.trash_empty_section);
            return tv;
        }
    }

    // -------------------------------------------------------------------------
    // File open (point 2)
    // -------------------------------------------------------------------------

    /**
     * @param file         Physical file to open (may have .fichero ext for app trash)
     * @param originalName Original filename — used to infer the correct MIME type and look up
     *                     the preferred app stored by DefaultAppsManager.
     */
    private void openFile(File file, String originalName) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String displayName = originalName != null ? originalName : file.getName();
            String mime = getMimeTypeFromName(displayName);
            String ext = extensionKey(displayName);

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Use the app previously chosen by the user (stored by DefaultAppsManager)
            String preferredPkg = DefaultAppsManager.getPackageForExtension(this, ext);
            if (preferredPkg != null && !preferredPkg.trim().isEmpty()) {
                Intent preferred = new Intent(intent);
                preferred.setPackage(preferredPkg);
                try {
                    startActivity(preferred);
                    return;
                } catch (android.content.ActivityNotFoundException ignored) {
                    // Stored app no longer available — fall through to standard chooser
                }
            }

            // No stored preference: let Android resolve (opens directly if only one handler,
            // shows the "Open with" sheet if multiple)
            startActivity(intent);

        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.cannot_open_file,
                    originalName != null ? originalName : file.getName()), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cannot_open_file, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String extensionKey(String name) {
        if (name == null) return "*";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "*";
    }

    private String getMimeTypeFromName(String name) {
        name = name.toLowerCase(java.util.Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String ext = name.substring(dot + 1);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "*/*";
    }

    // -------------------------------------------------------------------------
    // Preview (point 1)
    // -------------------------------------------------------------------------

    private void bindPreview(File file, ImageView iv) {
        bindPreview(file, file != null ? file.getName() : "", iv);
    }

    private void bindPreview(File file, String nameHint, ImageView iv) {
        if (file == null || !file.exists()) {
            setDefaultIcon(nameHint, iv);
            return;
        }
        String key = file.getAbsolutePath() + "@" + file.lastModified();
        Bitmap cached = previewCache.get(key);
        if (cached != null) {
            iv.setImageBitmap(cached);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return;
        }
        setDefaultIcon(nameHint, iv);
        iv.setTag(key);
        thumbExecutor.execute(() -> {
            Bitmap bmp = generateThumbnail(file, nameHint);
            if (bmp == null) return;
            previewCache.put(key, bmp);
            mainHandler.post(() -> {
                if (key.equals(iv.getTag())) {
                    iv.setImageBitmap(bmp);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }
            });
        });
    }

    private Bitmap generateThumbnail(File file) {
        return generateThumbnail(file, file != null ? file.getName() : "");
    }

    private Bitmap generateThumbnail(File file, String nameHint) {
        String name = nameHint.toLowerCase(java.util.Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        if (isImageExt(ext)) return decodeImageThumb(file);
        if (isVideoExt(ext)) return decodeVideoThumb(file);
        if (isAudioExt(ext)) return decodeAudioCover(file);
        if ("apk".equals(ext)) return decodeApkIcon(file);
        return null;
    }

    private Bitmap decodeImageThumb(File file) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            opts.inSampleSize = Math.max(1, Math.min(opts.outWidth, opts.outHeight) / 128);
            opts.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Exception e) { return null; }
    }

    private Bitmap decodeVideoThumb(File file) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());
            return mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception e) { return null; }
        finally { try { mmr.release(); } catch (Exception ignored) {} }
    }

    private Bitmap decodeAudioCover(File file) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());
            byte[] art = mmr.getEmbeddedPicture();
            if (art != null) return BitmapFactory.decodeByteArray(art, 0, art.length);
        } catch (Exception ignored) {}
        finally { try { mmr.release(); } catch (Exception ignored) {} }
        return null;
    }

    private Bitmap decodeApkIcon(File file) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
            if (pi == null) return null;
            pi.applicationInfo.sourceDir = file.getAbsolutePath();
            pi.applicationInfo.publicSourceDir = file.getAbsolutePath();
            Drawable icon = pi.applicationInfo.loadIcon(pm);
            Bitmap bmp = Bitmap.createBitmap(icon.getIntrinsicWidth(),
                    icon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            icon.draw(canvas);
            return bmp;
        } catch (Exception e) { return null; }
    }

    private void setDefaultIcon(File file, ImageView iv) {
        setDefaultIcon(file != null ? file.getName() : "", iv);
        if (file != null && file.isDirectory()) iv.setImageResource(R.drawable.ic_folder);
    }

    private void setDefaultIcon(String nameHint, ImageView iv) {
        String name = nameHint.toLowerCase(java.util.Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (isImageExt(ext)) iv.setImageResource(R.drawable.ic_image);
        else if (isVideoExt(ext)) iv.setImageResource(R.drawable.ic_video);
        else if (isAudioExt(ext)) iv.setImageResource(R.drawable.ic_audio);
        else if ("pdf".equals(ext)) iv.setImageResource(R.drawable.ic_pdf);
        else if ("apk".equals(ext)) iv.setImageResource(R.drawable.ic_apk);
        else if ("zip".equals(ext) || "rar".equals(ext) || "7z".equals(ext) || "tar".equals(ext) || "gz".equals(ext)) iv.setImageResource(R.drawable.ic_zip);
        else iv.setImageResource(R.drawable.ic_file);
    }

    private boolean isImageExt(String e) {
        return e.equals("jpg") || e.equals("jpeg") || e.equals("png") || e.equals("gif") ||
               e.equals("webp") || e.equals("bmp") || e.equals("heic") || e.equals("heif");
    }

    private boolean isVideoExt(String e) {
        return e.equals("mp4") || e.equals("mkv") || e.equals("avi") || e.equals("mov") ||
               e.equals("webm") || e.equals("3gp") || e.equals("flv") || e.equals("ts");
    }

    private boolean isAudioExt(String e) {
        return e.equals("mp3") || e.equals("aac") || e.equals("ogg") || e.equals("flac") ||
               e.equals("wav") || e.equals("m4a") || e.equals("opus") || e.equals("wma");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static final long RETENTION_MS = 30L * 24 * 60 * 60 * 1000;

    private String formatDaysLeft(long deletedAt) {
        long remainMs = (deletedAt + RETENTION_MS) - System.currentTimeMillis();
        if (remainMs <= 0) return getString(R.string.trash_expires_today);
        long days = remainMs / (24L * 60 * 60 * 1000);
        if (days == 0) return getString(R.string.trash_expires_today);
        return getString(R.string.trash_days_left, days);
    }

    private void showDeleteForeverWarning(TrashManager.TrashEntry entry) {
        if (entry == null) return;
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.warning_title)
                .setMessage(getString(R.string.warning_delete_forever_single_message, entry.getOriginalName()))
                .setPositiveButton(R.string.delete_forever, (d, w) -> {
                    List<TrashManager.TrashEntry> single = new ArrayList<>();
                    single.add(entry);
                    runDeleteTrashEntriesWithProgress(single, false);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void restoreSystemEntry(SystemTrashEntry entry) {
        File target = new File(entry.file.getParent(), entry.originalName);
        if (target.exists()) {
            String baseName = entry.originalName;
            String ext = "";
            int dot = baseName.lastIndexOf('.');
            if (dot > 0) { ext = baseName.substring(dot); baseName = baseName.substring(0, dot); }
            int i = 1;
            while (target.exists()) {
                target = new File(entry.file.getParent(), baseName + " (" + i + ")" + ext);
                i++;
            }
        }
        if (entry.file.renameTo(target)) {
            android.widget.Toast.makeText(this, R.string.item_restored, android.widget.Toast.LENGTH_SHORT).show();
        } else {
            android.widget.Toast.makeText(this, R.string.system_trash_restore_failed, android.widget.Toast.LENGTH_LONG).show();
        }
        loadTrash();
    }

    private void showDeleteSystemEntryWarning(SystemTrashEntry entry) {
        if (entry == null) return;
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.warning_title)
                .setMessage(getString(R.string.warning_delete_forever_single_message, entry.originalName))
                .setPositiveButton(R.string.delete_forever, (d, w) -> {
                    if (entry.file.delete()) {
                        loadTrash();
                    } else {
                        android.widget.Toast.makeText(this, R.string.delete_failed, android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}

