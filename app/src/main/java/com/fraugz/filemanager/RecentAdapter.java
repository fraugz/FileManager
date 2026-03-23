package com.fraugz.filemanager;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.LruCache;

public class RecentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_FILE = 1;

    public interface OnFileClick {
        void onClick(File file);
        void onLongPress(File file, View anchor);
    }

    private final Context ctx;
    private final OnFileClick listener;
    private List<Object> items = new ArrayList<>(); // String (header) or File
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> previewCache = new LruCache<String, Bitmap>(32) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };
    private boolean darkTheme = true;
    private float uiScale = 1.0f;
    private Map<String, Boolean> pinnedByPath = new HashMap<>();

    public RecentAdapter(Context ctx, List<File> files, OnFileClick listener) {
        this.ctx = ctx;
        this.listener = listener;
        setFiles(files);
    }

    public void setFiles(List<File> files) {
        setFiles(files, null, null);
    }

    public void setFiles(List<File> files, Map<String, Long> accessByPath) {
        setFiles(files, accessByPath, null);
    }

    public void setFiles(List<File> files, Map<String, Long> accessByPath, Map<String, Boolean> pinnedByPath) {
        items.clear();
        if (pinnedByPath != null) {
            this.pinnedByPath = pinnedByPath;
        } else {
            this.pinnedByPath.clear();
        }

        List<File> orderedFiles = new ArrayList<>(files);
        Collections.sort(orderedFiles, Comparator.comparingLong((File f) -> getAccessTime(f, accessByPath)).reversed());

        // Group by access date.
        Map<String, List<File>> groups = new LinkedHashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        SimpleDateFormat today = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayStr = today.format(new Date());

        for (File f : orderedFiles) {
            long accessedAt = getAccessTime(f, accessByPath);
            String d = today.format(new Date(accessedAt));
            String label;
            if (d.equals(todayStr)) label = ctx.getString(R.string.today);
            else label = sdf.format(new Date(accessedAt));
            if (!groups.containsKey(label)) groups.put(label, new ArrayList<>());
            groups.get(label).add(f);
        }

        for (Map.Entry<String, List<File>> e : groups.entrySet()) {
            String key = e.getKey();
            List<File> group = e.getValue();
            items.add(key + "  |  " + ctx.getString(R.string.items_count, group.size()));
            items.addAll(group);
        }
        notifyDataSetChanged();
    }

    private long getAccessTime(File file, Map<String, Long> accessByPath) {
        if (accessByPath == null) return file.lastModified();
        Long ts = accessByPath.get(file.getAbsolutePath());
        return (ts != null && ts > 0L) ? ts : file.lastModified();
    }

    public void setDarkTheme(boolean darkTheme) {
        this.darkTheme = darkTheme;
        notifyDataSetChanged();
    }

    public void setUiScale(float scale) {
        this.uiScale = Math.max(0.90f, Math.min(1.30f, scale));
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int pos) {
        return items.get(pos) instanceof String ? TYPE_HEADER : TYPE_FILE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(ctx);
        if (viewType == TYPE_HEADER) {
            View v = inf.inflate(R.layout.item_recent_header, parent, false);
            return new HeaderVH(v);
        } else {
            View v = inf.inflate(R.layout.item_recent, parent, false);
            return new FileVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        if (holder instanceof HeaderVH) {
            HeaderVH h = (HeaderVH) holder;
            h.text.setText((String) items.get(pos));
            h.text.setTextColor(darkTheme ? 0xFF888888 : 0xFF4E5A68);
            h.text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * uiScale);
            h.collapse.setColorFilter(darkTheme ? 0xFF666666 : 0xFF5E6C7A);
            setSquareSize(h.collapse, dp(32f * uiScale));
        } else {
            File file = (File) items.get(pos);
            FileVH h = (FileVH) holder;
            h.name.setText(file.getName());
            h.name.setSingleLine(false);
            h.name.setEllipsize(null);
            h.name.setMaxLines(Integer.MAX_VALUE);
            h.name.setTextColor(darkTheme ? 0xFFFFFFFF : 0xFF1C1C1E);
            h.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * uiScale);
            FileItem fi = new FileItem(file);
            h.size.setText(fi.getFormattedSize(ctx));
            h.size.setTextColor(darkTheme ? 0xFF666666 : 0xFF5E6C7A);
            h.size.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * uiScale);
            String parent = file.getParentFile() != null ? file.getParentFile().getName() : "";
            h.path.setText(parent);
            h.path.setTextColor(darkTheme ? 0xFF555555 : 0xFF768394);
            h.path.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * uiScale);
            String ext = fi.getExtension().toLowerCase(Locale.ROOT);
            h.thumbnail.setImageResource(getIconRes(ext));
            h.thumbnail.setBackgroundColor(darkTheme ? 0xFF1C1C1E : 0xFFE7EDF4);
            int thumbPadding = dp(8f * uiScale);
            h.thumbnail.setPadding(thumbPadding, thumbPadding, thumbPadding, thumbPadding);
            h.thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            setSquareSize(h.thumbnail, dp(88f * uiScale));
            
            // Show pin indicator if file is pinned
            Boolean isPinned = pinnedByPath.get(file.getAbsolutePath());
            if (h.pinIndicator != null) {
                h.pinIndicator.setVisibility(isPinned != null && isPinned ? View.VISIBLE : View.GONE);
                if (isPinned != null && isPinned) {
                    h.pinIndicator.setColorFilter(darkTheme ? 0xFFFFD700 : 0xFFFFA500);
                }
            }
            
            h.itemView.setOnClickListener(v -> listener.onClick(file));

            final float[] down = new float[]{0f, 0f};
            final boolean[] longPressTriggered = new boolean[]{false};
            final Runnable[] longPressTask = new Runnable[1];
            h.itemView.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        down[0] = event.getX();
                        down[1] = event.getY();
                        longPressTriggered[0] = false;
                        longPressTask[0] = () -> {
                            longPressTriggered[0] = true;
                            listener.onLongPress(file, h.itemView);
                        };
                        v.postDelayed(longPressTask[0], 550L);
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getX() - down[0]) > dp(10) || Math.abs(event.getY() - down[1]) > dp(10)) {
                            if (longPressTask[0] != null) v.removeCallbacks(longPressTask[0]);
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (longPressTask[0] != null) v.removeCallbacks(longPressTask[0]);
                        return longPressTriggered[0];
                    default:
                        return false;
                }
            });

            // Load rich previews and fallbacks.
            if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("webp") || ext.equals("bmp") || ext.equals("gif")) {
                executor.execute(() -> {
                    try {
                        String key = "rimg#" + file.getAbsolutePath() + "#" + file.lastModified();
                        mainHandler.post(() -> h.thumbnail.setTag(key));

                        Bitmap cached = previewCache.get(key);
                        if (cached != null) {
                            mainHandler.post(() -> {
                                Object tag = h.thumbnail.getTag();
                                if (tag != null && key.equals(tag)) {
                                    setBitmapPreview(h.thumbnail, cached);
                                }
                            });
                            return;
                        }

                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = 4;
                        Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                        if (bmp != null) {
                            previewCache.put(key, bmp);
                            mainHandler.post(() -> {
                                Object tag = h.thumbnail.getTag();
                                if (tag != null && key.equals(tag)) {
                                    setBitmapPreview(h.thumbnail, bmp);
                                }
                            });
                        } else {
                            mainHandler.post(() -> applyConfiguredAppIconFallback(file, h.thumbnail));
                        }
                    } catch (Exception ignored) {
                        mainHandler.post(() -> applyConfiguredAppIconFallback(file, h.thumbnail));
                    }
                });
            } else if (ext.equals("mp4") || ext.equals("3gp") || ext.equals("mkv")) {
                executor.execute(() -> {
                    try {
                        String key = "rvid#" + file.getAbsolutePath() + "#" + file.lastModified();
                        mainHandler.post(() -> h.thumbnail.setTag(key));

                        Bitmap cached = previewCache.get(key);
                        if (cached != null) {
                            mainHandler.post(() -> {
                                Object tag = h.thumbnail.getTag();
                                if (tag != null && key.equals(tag)) {
                                    setBitmapPreview(h.thumbnail, cached);
                                }
                            });
                            return;
                        }

                        Bitmap bmp = ThumbnailUtils.createVideoThumbnail(
                                file.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
                        if (bmp != null) {
                            previewCache.put(key, bmp);
                            mainHandler.post(() -> {
                                Object tag = h.thumbnail.getTag();
                                if (tag != null && key.equals(tag)) {
                                    setBitmapPreview(h.thumbnail, bmp);
                                }
                            });
                        } else {
                            mainHandler.post(() -> applyConfiguredAppIconFallback(file, h.thumbnail));
                        }
                    } catch (Exception ignored) {
                        mainHandler.post(() -> applyConfiguredAppIconFallback(file, h.thumbnail));
                    }
                });
            } else if (ext.equals("mp3") || ext.equals("wav") || ext.equals("flac") || ext.equals("aac") || ext.equals("ogg") || ext.equals("m4a")) {
                executor.execute(() -> {
                    String key = "raud#" + file.getAbsolutePath() + "#" + file.lastModified();
                    mainHandler.post(() -> h.thumbnail.setTag(key));

                    Bitmap cached = previewCache.get(key);
                    if (cached != null) {
                        mainHandler.post(() -> {
                            Object tag = h.thumbnail.getTag();
                            if (tag != null && key.equals(tag)) {
                                setBitmapPreview(h.thumbnail, cached);
                            }
                        });
                        return;
                    }

                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    try {
                        retriever.setDataSource(file.getAbsolutePath());
                        byte[] art = retriever.getEmbeddedPicture();
                        if (art == null || art.length == 0) throw new IllegalStateException("no-audio-art");
                        Bitmap bmp = BitmapFactory.decodeByteArray(art, 0, art.length);
                        if (bmp != null) {
                            previewCache.put(key, bmp);
                            mainHandler.post(() -> {
                                Object tag = h.thumbnail.getTag();
                                if (tag != null && key.equals(tag)) {
                                    setBitmapPreview(h.thumbnail, bmp);
                                }
                            });
                        } else {
                            mainHandler.post(() -> applyConfiguredAppIconFallback(file, h.thumbnail));
                        }
                    } catch (Exception ignored) {
                        mainHandler.post(() -> applyConfiguredAppIconFallback(file, h.thumbnail));
                    } finally {
                        try { retriever.release(); } catch (Exception ignored) {}
                    }
                });
            } else if (ext.equals("apk")) {
                executor.execute(() -> {
                    String key = "rapk#" + file.getAbsolutePath() + "#" + file.lastModified();
                    mainHandler.post(() -> h.thumbnail.setTag(key));

                    Bitmap cached = previewCache.get(key);
                    if (cached != null) {
                        mainHandler.post(() -> {
                            Object tag = h.thumbnail.getTag();
                            if (tag != null && key.equals(tag)) {
                                setBitmapPreview(h.thumbnail, cached);
                            }
                        });
                        return;
                    }

                    try {
                        PackageManager pm = ctx.getPackageManager();
                        PackageInfo pkgInfo = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
                        if (pkgInfo == null || pkgInfo.applicationInfo == null) throw new IllegalStateException("invalid-apk");

                        ApplicationInfo appInfo = pkgInfo.applicationInfo;
                        appInfo.sourceDir = file.getAbsolutePath();
                        appInfo.publicSourceDir = file.getAbsolutePath();
                        Drawable drawable = appInfo.loadIcon(pm);
                        Bitmap bmp = drawableToBitmap(drawable, dp(88f * uiScale));

                        if (bmp != null) {
                            previewCache.put(key, bmp);
                            mainHandler.post(() -> {
                                Object tag = h.thumbnail.getTag();
                                if (tag != null && key.equals(tag)) {
                                    setBitmapPreview(h.thumbnail, bmp);
                                }
                            });
                        } else {
                            mainHandler.post(() -> applyConfiguredAppIconFallback(file, h.thumbnail));
                        }
                    } catch (Exception ignored) {
                        mainHandler.post(() -> applyConfiguredAppIconFallback(file, h.thumbnail));
                    }
                });
            } else {
                applyConfiguredAppIconFallback(file, h.thumbnail);
            }
        }
    }

    private void applyConfiguredAppIconFallback(File file, ImageView imageView) {
        try {
            String extensionKey = getExtensionKey(file);
            String preferredPkg = DefaultAppsManager.getPackageForExtension(ctx, extensionKey);
            if (preferredPkg == null || preferredPkg.trim().isEmpty()) return;

            PackageManager pm = ctx.getPackageManager();
            Drawable appIcon = pm.getApplicationIcon(preferredPkg);
            if (appIcon == null) return;

            int iconPadding = dp(8f * uiScale);
            imageView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setImageDrawable(appIcon);
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

    private void setBitmapPreview(ImageView imageView, Bitmap bmp) {
        if (bmp == null) return;
        imageView.setPadding(0, 0, 0, 0);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setImageBitmap(bmp);
    }

    private int getIconRes(String ext) {
        switch (ext.toLowerCase()) {
            case "jpg": case "jpeg": case "png": case "gif": case "webp": return R.drawable.ic_image;
            case "mp4": case "avi": case "mov": case "mkv": return R.drawable.ic_video;
            case "mp3": case "wav": case "flac": case "aac": return R.drawable.ic_audio;
            case "pdf": return R.drawable.ic_pdf;
            case "apk": return R.drawable.ic_apk;
            case "zip": case "rar": case "tar": return R.drawable.ic_zip;
            default: return R.drawable.ic_file;
        }
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

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView text;
        ImageView collapse;
        HeaderVH(View v) {
            super(v);
            text = v.findViewById(R.id.header_text);
            collapse = v.findViewById(R.id.btn_collapse);
        }
    }

    static class FileVH extends RecyclerView.ViewHolder {
        ImageView thumbnail, pinIndicator;
        TextView name, size, path;
        FileVH(View v) {
            super(v);
            thumbnail = v.findViewById(R.id.thumbnail);
            pinIndicator = v.findViewById(R.id.pin_indicator);
            name = v.findViewById(R.id.name);
            size = v.findViewById(R.id.size);
            path = v.findViewById(R.id.path);
        }
    }
}

