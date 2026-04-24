package com.fraugz.filemanager;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.res.Resources;
import android.util.TypedValue;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(FileItem item);
        void onItemLongClick(FileItem item);
        void onMoreClick(FileItem item, View anchor);
    }

    private final List<FileItem> items;
    private boolean selectionMode = false;
    private boolean darkTheme = true;
    private float uiScale = 1.0f;
    private boolean recentMode = false;
    private final Map<String, Long> recentAccessByPath = new HashMap<>();
    private final Map<String, Boolean> recentPinnedByPath = new HashMap<>();
    private final Listener listener;
    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> previewCache = new LruCache<String, Bitmap>(20) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    public FileAdapter(List<FileItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setSelectionMode(boolean mode) { this.selectionMode = mode; notifyDataSetChanged(); }
    public boolean isSelectionMode() { return selectionMode; }
    public void setDarkTheme(boolean dark) { this.darkTheme = dark; }
    public void setUiScale(float scale) { this.uiScale = Math.max(0.90f, Math.min(1.30f, scale)); notifyDataSetChanged(); }
    public void setRecentMode(boolean mode) { this.recentMode = mode; }

    public void setRecentMetadata(Map<String, Long> accessByPath, Map<String, Boolean> pinnedByPath) {
        recentAccessByPath.clear();
        recentPinnedByPath.clear();
        if (accessByPath != null) recentAccessByPath.putAll(accessByPath);
        if (pinnedByPath != null) recentPinnedByPath.putAll(pinnedByPath);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        FileItem item = items.get(pos);
        File file = item.getFile();
        String ext = item.getExtension().toLowerCase(Locale.ROOT);

        // Theme colors
        int colorBg        = darkTheme ? 0xFF000000 : 0xFFFFFFFF;
        int colorBgSel     = darkTheme ? 0xFF1A2744 : 0xFFE3EDFF;
        int colorText      = darkTheme ? 0xFFFFFFFF : 0xFF1C1C1E;
        int colorMeta      = darkTheme ? 0xFF888888 : 0xFF6B6B6B;
        int colorIcon      = darkTheme ? 0xFF2C2C2E : 0xFFF0F2F5;
        int colorMoreTint  = darkTheme ? 0xFF555555 : 0xFFAAAAAA;

        // Background
        h.itemRoot.setBackgroundColor(item.isSelected() ? colorBgSel : colorBg);

        // Texts
        h.name.setTextColor(colorText);
        h.meta.setTextColor(colorMeta);
        h.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * uiScale);
        h.meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * uiScale);
        long accessTs = getAccessTimestamp(file);
        h.meta.setText(formatDateTime(accessTs) + "  ·  " + item.getFormattedSize(h.itemView.getContext()));

        if (recentMode && h.recentDayHeader != null) {
            String currentDayKey = formatDayKey(accessTs);
            String previousDayKey = null;
            if (pos > 0) {
                File prev = items.get(pos - 1).getFile();
                previousDayKey = formatDayKey(getAccessTimestamp(prev));
            }
            boolean showHeader = pos == 0 || !currentDayKey.equals(previousDayKey);
            h.recentDayHeader.setVisibility(showHeader ? View.VISIBLE : View.GONE);
            if (showHeader) {
                h.recentDayHeader.setText(formatDayLabel(accessTs, h.itemView.getContext()));
                h.recentDayHeader.setTextColor(darkTheme ? 0xFF8FA3B8 : 0xFF5E6C7A);
                h.recentDayHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * uiScale);
            }
        } else if (h.recentDayHeader != null) {
            h.recentDayHeader.setVisibility(View.GONE);
        }

        if (h.pinBadge != null) {
            boolean isPinned = recentMode && Boolean.TRUE.equals(recentPinnedByPath.get(file.getAbsolutePath()));
            h.pinBadge.setVisibility(isPinned ? View.VISIBLE : View.GONE);
            if (isPinned) {
                h.pinBadge.setColorFilter(darkTheme ? 0xFFFFD54F : 0xFFFFA000);
            }
        }

        // Icon background
        h.icon.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(colorIcon));
        h.btnMore.setColorFilter(colorMoreTint);
        h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconPadding = dp(8f * uiScale);
        h.icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);

        // File name
        h.name.setText(item.getName());
        h.name.setSingleLine(false);
        h.name.setEllipsize(null);
        h.name.setMaxLines(Integer.MAX_VALUE);

        // Icon resource
        if (item.isDirectory()) {
            h.icon.setTag(null); // cancel any pending async thumbnail for this recycled view
            h.icon.setImageResource(R.drawable.ic_folder);
        } else {
            switch (ext) {
                case "jpg": case "jpeg": case "png": case "gif": case "webp": case "bmp":
                    h.icon.setImageResource(R.drawable.ic_image); break;
                case "mp4": case "avi": case "mov": case "mkv": case "3gp":
                    h.icon.setImageResource(R.drawable.ic_video); break;
                case "mp3": case "wav": case "flac": case "aac": case "ogg":
                    h.icon.setImageResource(R.drawable.ic_audio); break;
                case "pdf":
                    h.icon.setImageResource(R.drawable.ic_pdf); break;
                case "apk":
                    h.icon.setImageResource(R.drawable.ic_apk); break;
                case "zip": case "rar": case "tar": case "gz": case "7z":
                    h.icon.setImageResource(R.drawable.ic_zip); break;
                default:
                    h.icon.setImageResource(R.drawable.ic_file); break;
            }

            if (isImageExtension(ext)) {
                bindImagePreview(file, h.icon);
            } else if (isVideoExtension(ext)) {
                bindVideoPreview(file, h.icon);
            } else if (isAudioExtension(ext)) {
                bindAudioCoverPreview(file, h.icon);
            } else if ("apk".equals(ext)) {
                bindApkIconPreview(file, h.icon);
            } else {
                applyConfiguredAppIconFallback(file, h.icon);
            }
        }

        // Selection indicator is the row highlight only.
        h.itemView.setAlpha(selectionMode && !item.isSelected() ? 0.65f : 1.0f);

        h.btnMore.setVisibility(View.GONE);
        h.btnMore.setOnClickListener(null);

        setSquareSize(h.icon, dp(44f * uiScale));
        setSquareSize(h.btnMore, dp(40f * uiScale));

        h.itemView.setOnClickListener(v -> listener.onItemClick(item));
        h.itemView.setOnLongClickListener(v -> { listener.onItemLongClick(item); return true; });
    }

    @Override
    public int getItemCount() { return items.size(); }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                Resources.getSystem().getDisplayMetrics()));
    }

    private void setSquareSize(View view, int sizePx) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) return;
        if (lp.width != sizePx || lp.height != sizePx) {
            lp.width = sizePx;
            lp.height = sizePx;
            view.setLayoutParams(lp);
        }
    }

    private boolean isImageExtension(String ext) {
        String e = ext == null ? "" : ext.toLowerCase();
        return e.equals("jpg") || e.equals("jpeg") || e.equals("png") || e.equals("webp") || e.equals("bmp") || e.equals("gif");
    }

    private boolean isVideoExtension(String ext) {
        String e = ext == null ? "" : ext.toLowerCase();
        return e.equals("mp4") || e.equals("avi") || e.equals("mov") || e.equals("mkv") || e.equals("3gp") || e.equals("webm");
    }

    private boolean isAudioExtension(String ext) {
        String e = ext == null ? "" : ext.toLowerCase();
        return e.equals("mp3") || e.equals("wav") || e.equals("flac") || e.equals("aac") || e.equals("ogg") || e.equals("m4a");
    }

    private long getAccessTimestamp(File file) {
        if (!recentMode || file == null) return file != null ? file.lastModified() : 0L;
        Long access = recentAccessByPath.get(file.getAbsolutePath());
        if (access == null || access <= 0L) return file.lastModified();
        return access;
    }

    private String formatDateTime(long timestamp) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    private String formatDayKey(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(timestamp));
    }

    private String formatDayLabel(long timestamp, Context context) {
        String today = formatDayKey(System.currentTimeMillis());
        String target = formatDayKey(timestamp);
        if (today.equals(target)) return context.getString(R.string.today);

        long oneDayMs = 24L * 60L * 60L * 1000L;
        String yesterday = formatDayKey(System.currentTimeMillis() - oneDayMs);
        if (yesterday.equals(target)) return context.getString(R.string.yesterday);

        return new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(new Date(timestamp));
    }

    private void applyConfiguredAppIconFallback(File file, ImageView iconView) {
        try {
            Context context = iconView.getContext();
            PackageManager pm = context.getPackageManager();
            String preferredPkg = DefaultAppsManager.getPackageForExtension(context, getExtensionKey(file));
            if (preferredPkg == null || preferredPkg.trim().isEmpty()) return;

            Drawable appIcon = pm.getApplicationIcon(preferredPkg);
            if (appIcon == null) return;

            int iconPadding = dp(8f * uiScale);
            iconView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconView.setImageDrawable(appIcon);
        } catch (Exception ignored) {
        }
    }

    private String getExtensionKey(File file) {
        if (file == null) return "*";
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) return "*";
        return ("." + name.substring(dot + 1)).toLowerCase(Locale.ROOT);
    }

    private void bindVideoPreview(File file, ImageView iconView) {
        String key = "video#" + file.getAbsolutePath() + "#" + file.lastModified();
        iconView.setTag(key);

        Bitmap cached = previewCache.get(key);
        if (cached != null) {
            setBitmapPreview(iconView, cached);
            return;
        }

        thumbExecutor.execute(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(file.getAbsolutePath());
                Bitmap frame = retriever.getFrameAtTime(0);
                if (frame == null) return;
                previewCache.put(key, frame);
                mainHandler.post(() -> {
                    Object tag = iconView.getTag();
                    if (tag != null && key.equals(tag)) {
                        setBitmapPreview(iconView, frame);
                    }
                });
            } catch (Exception ignored) {
                mainHandler.post(() -> {
                    Object tag = iconView.getTag();
                    if (tag != null && key.equals(tag)) {
                        applyConfiguredAppIconFallback(file, iconView);
                    }
                });
            } finally {
                try { retriever.release(); } catch (Exception ignored) {}
            }
        });
    }

    private void bindAudioCoverPreview(File file, ImageView iconView) {
        String key = "audio#" + file.getAbsolutePath() + "#" + file.lastModified();
        iconView.setTag(key);

        Bitmap cached = previewCache.get(key);
        if (cached != null) {
            setBitmapPreview(iconView, cached);
            return;
        }

        thumbExecutor.execute(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(file.getAbsolutePath());
                byte[] art = retriever.getEmbeddedPicture();
                if (art == null || art.length == 0) throw new IllegalStateException("no-embedded-art");
                Bitmap bmp = BitmapFactory.decodeByteArray(art, 0, art.length);
                if (bmp == null) return;
                previewCache.put(key, bmp);
                mainHandler.post(() -> {
                    Object tag = iconView.getTag();
                    if (tag != null && key.equals(tag)) {
                        setBitmapPreview(iconView, bmp);
                    }
                });
            } catch (Exception ignored) {
                mainHandler.post(() -> {
                    Object tag = iconView.getTag();
                    if (tag != null && key.equals(tag)) {
                        applyConfiguredAppIconFallback(file, iconView);
                    }
                });
            } finally {
                try { retriever.release(); } catch (Exception ignored) {}
            }
        });
    }

    private void bindApkIconPreview(File file, ImageView iconView) {
        String key = "apk#" + file.getAbsolutePath() + "#" + file.lastModified();
        iconView.setTag(key);

        Bitmap cached = previewCache.get(key);
        if (cached != null) {
            setBitmapPreview(iconView, cached);
            return;
        }

        thumbExecutor.execute(() -> {
            try {
                Context context = iconView.getContext();
                PackageManager pm = context.getPackageManager();
                PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
                if (info == null || info.applicationInfo == null) throw new IllegalStateException("invalid-apk");

                ApplicationInfo appInfo = info.applicationInfo;
                appInfo.sourceDir = file.getAbsolutePath();
                appInfo.publicSourceDir = file.getAbsolutePath();

                Drawable drawable = appInfo.loadIcon(pm);
                if (drawable == null) throw new IllegalStateException("no-apk-icon");

                Bitmap bmp = drawableToBitmap(drawable, Math.max(96, dp(44f * uiScale)));
                if (bmp == null) return;

                previewCache.put(key, bmp);
                mainHandler.post(() -> {
                    Object tag = iconView.getTag();
                    if (tag != null && key.equals(tag)) {
                        setBitmapPreview(iconView, bmp);
                    }
                });
            } catch (Exception ignored) {
                mainHandler.post(() -> {
                    Object tag = iconView.getTag();
                    if (tag != null && key.equals(tag)) {
                        applyConfiguredAppIconFallback(file, iconView);
                    }
                });
            }
        });
    }

    private Bitmap drawableToBitmap(Drawable drawable, int targetSizePx) {
        if (drawable == null) return null;
        if (drawable instanceof BitmapDrawable) {
            Bitmap bmp = ((BitmapDrawable) drawable).getBitmap();
            if (bmp != null) return bmp;
        }

        int w = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : targetSizePx;
        int h = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : targetSizePx;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private void setBitmapPreview(ImageView iconView, Bitmap bmp) {
        if (bmp == null) return;
        iconView.setPadding(0, 0, 0, 0);
        iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iconView.setImageBitmap(bmp);
    }

    private void bindImagePreview(File file, ImageView iconView) {
        String key = file.getAbsolutePath() + "#" + file.lastModified();
        iconView.setTag(key);

        Bitmap cached = previewCache.get(key);
        if (cached != null) {
            iconView.setPadding(0, 0, 0, 0);
            iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iconView.setImageBitmap(cached);
            return;
        }

        thumbExecutor.execute(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

                int target = Math.max(96, dp(44f * uiScale));
                int sample = 1;
                while ((bounds.outWidth / sample) > target * 2 || (bounds.outHeight / sample) > target * 2) {
                    sample *= 2;
                }

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = Math.max(1, sample);
                Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                if (bmp == null) return;

                previewCache.put(key, bmp);
                mainHandler.post(() -> {
                    Object tag = iconView.getTag();
                    if (tag != null && key.equals(tag)) {
                        setBitmapPreview(iconView, bmp);
                    }
                });
            } catch (Exception ignored) {
                mainHandler.post(() -> {
                    Object tag = iconView.getTag();
                    if (tag != null && key.equals(tag)) {
                        applyConfiguredAppIconFallback(file, iconView);
                    }
                });
            }
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout itemRoot;
        ImageView icon;
        ImageView pinBadge;
        TextView name, meta;
        TextView recentDayHeader;
        ImageButton btnMore;

        ViewHolder(View v) {
            super(v);
            itemRoot  = v.findViewById(R.id.item_root);
            icon      = v.findViewById(R.id.icon);
            pinBadge  = v.findViewById(R.id.pin_badge);
            recentDayHeader = v.findViewById(R.id.recent_day_header);
            name      = v.findViewById(R.id.name);
            meta      = v.findViewById(R.id.meta);
            btnMore   = v.findViewById(R.id.btn_more);
        }
    }
}

