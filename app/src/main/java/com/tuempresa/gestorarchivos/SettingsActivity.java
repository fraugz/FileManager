package com.tuempresa.gestorarchivos;

import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private TextView themeSubtitle;
    private TextView uiScaleSubtitle;
    private TextView defaultAppsSubtitle;

    private static class AppChoice {
        final String packageName;
        final String label;

        AppChoice(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        themeSubtitle = findViewById(R.id.theme_subtitle);
        uiScaleSubtitle = findViewById(R.id.ui_scale_subtitle);
        defaultAppsSubtitle = findViewById(R.id.default_apps_subtitle);

        themeSubtitle.setText(ThemeManager.getThemeName(this));
        uiScaleSubtitle.setText(ThemeManager.getUiScaleName(this));
        updateDefaultAppsSubtitle();
        applyUiScalePreview();

        // Back
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Theme selector
        findViewById(R.id.row_theme).setOnClickListener(v -> {
            String[] options = {"Oscuro", "Claro"};
            int current = ThemeManager.getTheme(this);
            new AlertDialog.Builder(this)
                .setTitle("Tema")
                .setSingleChoiceItems(options, current, (d, which) -> {
                    ThemeManager.setTheme(this, which);
                    d.dismiss();
                    // Restart all activities to apply theme
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancelar", null)
                .show();
        });

        findViewById(R.id.row_ui_scale).setOnClickListener(v -> showUiScaleDialog());
        findViewById(R.id.row_default_apps).setOnClickListener(v -> showDefaultAppsDialog());

    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDefaultAppsSubtitle();
    }

    private void showUiScaleDialog() {
        String[] labels = {"Pequeno", "Normal", "Grande", "Muy grande"};
        float[] scales = {0.90f, 1.00f, 1.15f, 1.30f};
        float currentScale = ThemeManager.getUiScale(this);
        int currentIndex = 1;
        for (int i = 0; i < scales.length; i++) {
            if (Math.abs(scales[i] - currentScale) < 0.04f) {
                currentIndex = i;
                break;
            }
        }

        final int selectedIndex = currentIndex;
        new AlertDialog.Builder(this)
                .setTitle("Tamano de interfaz")
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    ThemeManager.setUiScale(this, scales[which]);
                    dialog.dismiss();
                    restartToApplyUiScale();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void restartToApplyUiScale() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void applyUiScalePreview() {
        float scale = ThemeManager.getUiScale(this);

        TextView title = findViewById(R.id.settings_title);
        TextView badge = findViewById(R.id.settings_badge);
        TextView appearance = findViewById(R.id.label_appearance);
        TextView about = findViewById(R.id.label_about);
        TextView themeTitle = findViewById(R.id.theme_title);
        TextView uiScaleTitle = findViewById(R.id.ui_scale_title);
        TextView defaultAppsTitle = findViewById(R.id.default_apps_title);

        if (title != null) title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * scale);
        if (badge != null) badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * scale);
        if (appearance != null) appearance.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scale);
        if (about != null) about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scale);
        if (themeTitle != null) themeTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale);
        if (themeSubtitle != null) themeSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale);
        if (uiScaleTitle != null) uiScaleTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale);
        if (uiScaleSubtitle != null) uiScaleSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale);
        if (defaultAppsTitle != null) defaultAppsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale);
        if (defaultAppsSubtitle != null) defaultAppsSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale);

        setSquareSize(findViewById(R.id.btn_back), dp(48f * scale));
        setSquareSize(findViewById(R.id.theme_icon), dp(28f * scale));
        setSquareSize(findViewById(R.id.ui_scale_icon), dp(28f * scale));
        setSquareSize(findViewById(R.id.default_apps_icon), dp(28f * scale));
    }

    private void updateDefaultAppsSubtitle() {
        if (defaultAppsSubtitle == null) return;
        int count = DefaultAppsManager.getEntries(this).size();
        if (count == 0) {
            defaultAppsSubtitle.setText("Sin apps registradas");
        } else {
            defaultAppsSubtitle.setText(count + " app" + (count == 1 ? "" : "s") + " guardada" + (count == 1 ? "" : "s"));
        }
    }

    private void showDefaultAppsDialog() {
        List<String> entries = DefaultAppsManager.getEntries(this);
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Apps por defecto")
                    .setMessage("Aun no se han detectado apps abiertas por defecto.")
                    .setPositiveButton("Cerrar", null)
                    .show();
            return;
        }

        List<String> labels = new ArrayList<>();
        for (String row : entries) {
            int sep = row.indexOf('|');
            if (sep > 0 && sep < row.length() - 1) {
                String pkg = row.substring(0, sep);
                String label = row.substring(sep + 1);
                labels.add(label + "\n" + pkg);
            } else {
                labels.add(row);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Apps por defecto")
                .setItems(labels.toArray(new String[0]), (d, which) -> showDefaultAppItemActions(entries.get(which)))
                .setNeutralButton("Limpiar todas", (d, w) -> confirmClearAllDefaultApps())
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void confirmClearAllDefaultApps() {
        new AlertDialog.Builder(this)
                .setTitle("Confirmar limpieza")
                .setMessage("Se eliminaran todas las apps guardadas. Quieres continuar?")
                .setPositiveButton("Limpiar todas", (d, w) -> {
                    DefaultAppsManager.clear(this);
                    updateDefaultAppsSubtitle();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showDefaultAppItemActions(String entry) {
        int sep = entry.indexOf('|');
        String pkg = sep >= 0 ? entry.substring(0, sep) : entry;
        String label = (sep >= 0 && sep < entry.length() - 1) ? entry.substring(sep + 1) : pkg;

        String[] options = {"Cambiar app", "Eliminar"};
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        showReplaceDefaultAppDialog(pkg, label);
                    } else if (which == 1) {
                        DefaultAppsManager.remove(this, pkg);
                        updateDefaultAppsSubtitle();
                        showDefaultAppsDialog();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showReplaceDefaultAppDialog(String oldPackageName, String currentLabel) {
        List<AppChoice> candidates = getInstalledLaunchableApps();
        if (candidates.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Cambiar app")
                    .setMessage("No se encontraron apps instaladas para seleccionar.")
                    .setPositiveButton("Cerrar", null)
                    .show();
            return;
        }

        String[] labels = new String[candidates.size()];
        int preselect = -1;
        for (int i = 0; i < candidates.size(); i++) {
            AppChoice c = candidates.get(i);
            labels[i] = c.label + "\n" + c.packageName;
            if (c.packageName.equals(oldPackageName)) {
                preselect = i;
            }
        }

        final int currentIndex = preselect;
        new AlertDialog.Builder(this)
                .setTitle("Cambiar app")
                .setSingleChoiceItems(labels, currentIndex, (d, which) -> {
                    AppChoice selected = candidates.get(which);
                    DefaultAppsManager.replacePackage(this, oldPackageName, selected.packageName, selected.label);
                    updateDefaultAppsSubtitle();
                    d.dismiss();
                    showDefaultAppsDialog();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private List<AppChoice> getInstalledLaunchableApps() {
        List<AppChoice> out = new ArrayList<>();
        List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
        for (ApplicationInfo info : apps) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(info.packageName);
            if (launchIntent == null) continue;

            CharSequence rawLabel = getPackageManager().getApplicationLabel(info);
            String label = rawLabel == null ? info.packageName : rawLabel.toString().trim();
            if (label.isEmpty()) label = info.packageName;
            out.add(new AppChoice(info.packageName, label));
        }
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()));
    }

    private void setSquareSize(View v, int sizePx) {
        if (v == null) return;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) return;
        if (lp.width != sizePx || lp.height != sizePx) {
            lp.width = sizePx;
            lp.height = sizePx;
            v.setLayoutParams(lp);
        }
    }
}
