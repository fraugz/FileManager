package com.fraugz.filemanager;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;

public class TrashActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private View emptyView;
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
        if (trashFiles.isEmpty()) {
            recycler.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recycler.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            recycler.setAdapter(new TrashAdapter());
        }
    }

    private void confirmEmptyTrash() {
        new AlertDialog.Builder(this)
            .setTitle("Vaciar papelera")
            .setMessage("Se eliminaran permanentemente " + trashFiles.size() + " archivo(s).")
            .setPositiveButton("Vaciar", (d, w) -> {
                TrashManager.emptyTrash(this);
                loadTrash();
                android.widget.Toast.makeText(this, "Papelera vaciada", android.widget.Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancelar", null)
            .show();
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
            h.size.setText(fi.getFormattedSize() + "  ·  " + fi.getFormattedDate());
            h.name.setTextColor(isDark ? 0xFFFFFFFF : 0xFF1C1C1E);
            h.size.setTextColor(isDark ? 0xFF888888 : 0xFF6B6B6B);
            h.itemView.setBackgroundColor(isDark ? 0xFF000000 : 0xFFFFFFFF);
            h.itemView.findViewById(R.id.icon).setVisibility(View.GONE);
            h.itemView.findViewById(R.id.btn_more).setOnClickListener(v -> {
                new AlertDialog.Builder(TrashActivity.this)
                    .setTitle(entry.getOriginalName())
                    .setItems(new String[]{"Restaurar", "Eliminar definitivamente"}, (d, w) -> {
                        if (w == 0) {
                            if (TrashManager.restore(TrashActivity.this, entry)) {
                                android.widget.Toast.makeText(TrashActivity.this, "Elemento restaurado", android.widget.Toast.LENGTH_SHORT).show();
                            } else {
                                String reason = TrashManager.getLastError();
                                if (reason == null || reason.trim().isEmpty()) reason = "motivo no disponible";
                                android.widget.Toast.makeText(TrashActivity.this, "No se pudo restaurar: " + reason, android.widget.Toast.LENGTH_LONG).show();
                            }
                        } else {
                            TrashManager.deleteEntry(entry);
                        }
                        loadTrash();
                    }).show();
            });
        }
        @Override public int getItemCount() { return trashFiles.size(); }
    }
}

