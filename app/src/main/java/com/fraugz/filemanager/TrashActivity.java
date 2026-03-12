package com.fraugz.filemanager;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrashActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private View emptyView;
    private TextView trashSizeSummary;
    private List<TrashManager.TrashEntry> trashFiles;
    private boolean isDark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trash);

        isDark = ThemeManager.getTheme(this) == ThemeManager.THEME_DARK;

        recycler = findViewById(R.id.recycler_trash);
        emptyView = findViewById(R.id.empty_trash_view);
        trashSizeSummary = findViewById(R.id.trash_size_summary);

        applyThemeColors();

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_empty_trash).setOnClickListener(v -> confirmEmptyTrash());

        loadTrash();
    }

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
    }

    private void loadTrash() {
        TrashManager.purgeExpired(this);
        trashFiles = TrashManager.getTrashFiles(this);
        updateTrashSizeSummary();
        if (trashFiles.isEmpty()) {
            recycler.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recycler.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            recycler.setAdapter(new TrashAdapter());
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
        new AlertDialog.Builder(this)
            .setTitle(R.string.empty_trash_title)
            .setMessage(getString(R.string.empty_trash_message, trashFiles.size()))
            .setPositiveButton(R.string.empty_trash, (d, w) -> {
                runDeleteTrashEntriesWithProgress(new ArrayList<>(trashFiles), true);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
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

    // Simple inline adapter
    class TrashAdapter extends RecyclerView.Adapter<TrashAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView name, size;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.name);
                size = v.findViewById(R.id.meta);
                // Reuse item_file layout
            }
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_file, p, false);
            v.findViewById(R.id.btn_more).setVisibility(View.VISIBLE);
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            TrashManager.TrashEntry entry = trashFiles.get(pos);
            File f = entry.getTrashFile();
            FileItem fi = new FileItem(f);
            h.name.setText(entry.getOriginalName());
            h.size.setText(fi.getFormattedSize(TrashActivity.this) + "  ·  " + fi.getFormattedDate());
            h.name.setTextColor(isDark ? 0xFFFFFFFF : 0xFF1C1C1E);
            h.size.setTextColor(isDark ? 0xFF888888 : 0xFF6B6B6B);
            h.itemView.setBackgroundColor(isDark ? 0xFF000000 : 0xFFFFFFFF);
            h.itemView.findViewById(R.id.icon).setVisibility(View.GONE);
            h.itemView.findViewById(R.id.btn_more).setOnClickListener(v -> {
                new AlertDialog.Builder(TrashActivity.this)
                    .setTitle(entry.getOriginalName())
                    .setItems(new String[]{getString(R.string.restore), getString(R.string.delete_forever)}, (d, w) -> {
                        if (w == 0) {
                            if (TrashManager.restore(TrashActivity.this, entry)) {
                                android.widget.Toast.makeText(TrashActivity.this, R.string.item_restored, android.widget.Toast.LENGTH_SHORT).show();
                            } else {
                                String reason = TrashManager.getLastError();
                                if (reason == null || reason.trim().isEmpty()) reason = getString(R.string.unknown_reason);
                                android.widget.Toast.makeText(TrashActivity.this, getString(R.string.restore) + ": " + reason, android.widget.Toast.LENGTH_LONG).show();
                            }
                            loadTrash();
                        } else {
                            showDeleteForeverWarning(entry);
                        }
                    }).show();
            });
        }
        @Override public int getItemCount() { return trashFiles.size(); }
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
}

