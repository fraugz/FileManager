package com.fraugz.filemanager;

import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private TextView themeSubtitle;
    private TextView uiScaleSubtitle;
    private TextView languageSubtitle;
    private TextView defaultAppsSubtitle;
    private TextView versionValue;

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
        languageSubtitle = findViewById(R.id.language_subtitle);
        defaultAppsSubtitle = findViewById(R.id.default_apps_subtitle);
        versionValue = findViewById(R.id.version_value);

        themeSubtitle.setText(ThemeManager.getThemeName(this));
        uiScaleSubtitle.setText(ThemeManager.getUiScaleName(this));
        languageSubtitle.setText(LocaleManager.getLanguageDisplayName(this));
        if (versionValue != null) versionValue.setText(getAppVersionName());
        updateDefaultAppsSubtitle();
        applyUiScalePreview();

        // Back
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Theme selector
        findViewById(R.id.row_theme).setOnClickListener(v -> {
            String[] options = {getString(R.string.theme_dark), getString(R.string.theme_light)};
            int current = ThemeManager.getTheme(this);
            new AlertDialog.Builder(this)
                .setTitle(R.string.theme_title)
                .setSingleChoiceItems(options, current, (d, which) -> {
                    ThemeManager.setTheme(this, which);
                    d.dismiss();
                    // Restart all activities to apply theme
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        });

        findViewById(R.id.row_ui_scale).setOnClickListener(v -> showUiScaleDialog());
            findViewById(R.id.row_language).setOnClickListener(v -> showLanguageDialog());
        findViewById(R.id.row_default_apps).setOnClickListener(v -> showDefaultAppsDialog());

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (languageSubtitle != null) {
            languageSubtitle.setText(LocaleManager.getLanguageDisplayName(this));
        }
        updateDefaultAppsSubtitle();
    }

    private void showUiScaleDialog() {
        String[] labels = {
            getString(R.string.ui_scale_small),
            getString(R.string.ui_scale_normal),
            getString(R.string.ui_scale_large),
            getString(R.string.ui_scale_xlarge)
        };
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
                .setTitle(R.string.ui_scale_title)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    ThemeManager.setUiScale(this, scales[which]);
                    dialog.dismiss();
                    restartToApplyUiScale();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showLanguageDialog() {
        String[] labels = {getString(R.string.language_spanish), getString(R.string.language_english)};
        String current = LocaleManager.resolveLanguage(this);
        int selected = LocaleManager.LANG_ES.equals(current) ? 0 : 1;

        new AlertDialog.Builder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    String target = which == 0 ? LocaleManager.LANG_ES : LocaleManager.LANG_EN;
                    LocaleManager.setLanguage(this, target);
                    dialog.dismiss();
                    restartToApplyUiScale();
                })
                .setNegativeButton(R.string.cancel, null)
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
        TextView languageTitle = findViewById(R.id.language_title);
        TextView defaultAppsTitle = findViewById(R.id.default_apps_title);

        if (title != null) title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * scale);
        if (badge != null) badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * scale);
        if (appearance != null) appearance.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scale);
        if (about != null) about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scale);
        if (themeTitle != null) themeTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale);
        if (themeSubtitle != null) themeSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale);
        if (uiScaleTitle != null) uiScaleTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale);
        if (uiScaleSubtitle != null) uiScaleSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale);
        if (languageTitle != null) languageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale);
        if (languageSubtitle != null) languageSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale);
        if (defaultAppsTitle != null) defaultAppsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale);
        if (defaultAppsSubtitle != null) defaultAppsSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale);
        if (versionValue != null) versionValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale);

        setSquareSize(findViewById(R.id.btn_back), dp(48f * scale));
        setSquareSize(findViewById(R.id.theme_icon), dp(28f * scale));
        setSquareSize(findViewById(R.id.ui_scale_icon), dp(28f * scale));
        setSquareSize(findViewById(R.id.language_icon), dp(28f * scale));
        setSquareSize(findViewById(R.id.default_apps_icon), dp(28f * scale));
    }

    private void updateDefaultAppsSubtitle() {
        if (defaultAppsSubtitle == null) return;
        int count = DefaultAppsManager.getEntries(this).size();
        if (count == 0) {
            defaultAppsSubtitle.setText(R.string.default_apps_none);
        } else {
            defaultAppsSubtitle.setText(getString(R.string.default_apps_count, count));
        }
    }

    private void showDefaultAppsDialog() {
        List<String> entries = DefaultAppsManager.getEntries(this);
        if (entries.isEmpty()) {
                new AlertDialog.Builder(this)
                    .setTitle(R.string.default_apps_title)
                    .setMessage(R.string.default_apps_empty_message)
                    .setPositiveButton(R.string.close, null)
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
            .setTitle(R.string.default_apps_title)
                .setItems(labels.toArray(new String[0]), (d, which) -> showDefaultAppItemActions(entries.get(which)))
            .setNeutralButton(R.string.clear_all, (d, w) -> confirmClearAllDefaultApps())
            .setPositiveButton(R.string.close, null)
                .show();
    }

    private void confirmClearAllDefaultApps() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_clear_title)
                .setMessage(R.string.confirm_clear_message)
                .setPositiveButton(R.string.clear_all, (d, w) -> {
                    DefaultAppsManager.clear(this);
                    updateDefaultAppsSubtitle();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDefaultAppItemActions(String entry) {
        int sep = entry.indexOf('|');
        String pkg = sep >= 0 ? entry.substring(0, sep) : entry;
        String label = (sep >= 0 && sep < entry.length() - 1) ? entry.substring(sep + 1) : pkg;

        String[] options = {getString(R.string.change_app), getString(R.string.delete)};
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
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showReplaceDefaultAppDialog(String oldPackageName, String currentLabel) {
        List<AppChoice> candidates = getCandidateDefaultApps(oldPackageName, currentLabel);
        if (candidates.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.change_app)
                    .setMessage(R.string.no_apps_found)
                    .setPositiveButton(R.string.close, null)
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
                .setTitle(R.string.change_app)
                .setSingleChoiceItems(labels, currentIndex, (d, which) -> {
                    AppChoice selected = candidates.get(which);
                    DefaultAppsManager.replacePackage(this, oldPackageName, selected.packageName, selected.label);
                    updateDefaultAppsSubtitle();
                    d.dismiss();
                    showDefaultAppsDialog();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private List<AppChoice> getCandidateDefaultApps(String oldPackageName, String currentLabel) {
        PackageManager pm = getPackageManager();
        Map<String, AppChoice> dedup = new LinkedHashMap<>();

        // Prioritize apps that can actually open files via ACTION_VIEW.
        List<String> mimeCandidates = Arrays.asList(
                "*/*", "text/plain", "application/pdf", "image/*", "video/*", "audio/*"
        );
        for (String mime : mimeCandidates) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setType(mime);

            List<ResolveInfo> handlers = pm.queryIntentActivities(intent, 0);
            for (ResolveInfo info : handlers) {
                if (info == null || info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg == null || pkg.trim().isEmpty()) continue;
                if (pkg.equals(getPackageName())) continue;

                CharSequence rawLabel = info.loadLabel(pm);
                String label = rawLabel == null ? pkg : rawLabel.toString().trim();
                if (label.isEmpty()) label = pkg;

                if (!dedup.containsKey(pkg)) {
                    dedup.put(pkg, new AppChoice(pkg, label));
                }
            }
        }

        // Fallback: include launchable apps to avoid an empty/too-short list on restrictive devices.
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo info : apps) {
            Intent launchIntent = pm.getLaunchIntentForPackage(info.packageName);
            if (launchIntent == null) continue;
            if (info.packageName.equals(getPackageName())) continue;

            CharSequence rawLabel = pm.getApplicationLabel(info);
            String label = rawLabel == null ? info.packageName : rawLabel.toString().trim();
            if (label.isEmpty()) label = info.packageName;

            if (!dedup.containsKey(info.packageName)) {
                dedup.put(info.packageName, new AppChoice(info.packageName, label));
            }
        }

        // Ensure current mapped app stays selectable even if hidden by package visibility.
        if (oldPackageName != null && !oldPackageName.trim().isEmpty() && !dedup.containsKey(oldPackageName)) {
            String fallbackLabel = (currentLabel == null || currentLabel.trim().isEmpty()) ? oldPackageName : currentLabel.trim();
            dedup.put(oldPackageName, new AppChoice(oldPackageName, fallbackLabel));
        }

        List<AppChoice> out = new ArrayList<>(dedup.values());
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

    private String getAppVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = info.versionName;
            return (version == null || version.trim().isEmpty()) ? "-" : version;
        } catch (PackageManager.NameNotFoundException e) {
            return "-";
        }
    }
}

