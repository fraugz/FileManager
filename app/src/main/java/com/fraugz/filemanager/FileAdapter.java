package com.fraugz.filemanager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;
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

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        FileItem item = items.get(pos);

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
        h.meta.setText(item.getFormattedDate() + "  ·  " + item.getFormattedSize());

        // Icon background
        h.icon.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(colorIcon));
        h.btnMore.setColorFilter(colorMoreTint);
        h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconPadding = dp(8f * uiScale);
        h.icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);

        // File name
        h.name.setText(item.getName());

        // Icon resource
        if (item.isDirectory()) {
            h.icon.setImageResource(R.drawable.ic_folder);
        } else {
            switch (item.getExtension()) {
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
        }

        if (!item.isDirectory() && isImageExtension(item.getExtension())) {
            bindImagePreview(item.getFile(), h.icon);
        }

        // Selection
        h.checkbox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        h.checkbox.setChecked(item.isSelected());
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
                        iconView.setPadding(0, 0, 0, 0);
                        iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        iconView.setImageBitmap(bmp);
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout itemRoot;
        ImageView icon;
        TextView name, meta;
        CheckBox checkbox;
        ImageButton btnMore;

        ViewHolder(View v) {
            super(v);
            itemRoot  = v.findViewById(R.id.item_root);
            icon      = v.findViewById(R.id.icon);
            name      = v.findViewById(R.id.name);
            meta      = v.findViewById(R.id.meta);
            checkbox  = v.findViewById(R.id.checkbox);
            btnMore   = v.findViewById(R.id.btn_more);
        }
    }
}

