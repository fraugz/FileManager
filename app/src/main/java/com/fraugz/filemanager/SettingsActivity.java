package com.fraugz.filemanager;

import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.webkit.MimeTypeMap;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private static final String PROJECT_GITHUB_URL = "https://github.com/fraugz/FileManager";
    private static final String QUICK_GUIDE_EN_URL = "https://github.com/fraugz/FileManager/blob/main/QUICK_GUIDE.md";
    private static final String QUICK_GUIDE_ES_URL = "https://github.com/fraugz/FileManager/blob/main/QUICK_GUIDE.es.md";

    private TextView themeSubtitle;
    private TextView uiScaleSubtitle;
    private TextView languageSubtitle;
    private TextView defaultAppsSubtitle;
    private TextView versionValue;

    private static class AppChoice {
        final String packageName;
        final String label;
        final Drawable icon;

        AppChoice(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private interface OnAppChoiceSelected {
        void onSelected(AppChoice selected);
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
        findViewById(R.id.row_changelog).setOnClickListener(v -> showChangelogDialog());
        findViewById(R.id.row_quick_guide).setOnClickListener(v -> openQuickGuideLink());
        findViewById(R.id.row_github).setOnClickListener(v -> openUrl(PROJECT_GITHUB_URL));

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
        TextView changelogTitle = findViewById(R.id.changelog_title);
        TextView changelogSubtitle = findViewById(R.id.changelog_subtitle);
        TextView quickGuideTitle = findViewById(R.id.quick_guide_title);
        TextView quickGuideSubtitle = findViewById(R.id.quick_guide_subtitle);
        TextView githubTitle = findViewById(R.id.github_title);
        TextView githubSubtitle = findViewById(R.id.github_subtitle);

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
        if (changelogTitle != null) changelogTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale);
        if (changelogSubtitle != null) changelogSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale);
        if (quickGuideTitle != null) quickGuideTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale);
        if (quickGuideSubtitle != null) quickGuideSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale);
        if (githubTitle != null) githubTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale);
        if (githubSubtitle != null) githubSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale);
        if (versionValue != null) versionValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale);

        setSquareSize(findViewById(R.id.btn_back), dp(48f * scale));
        setSquareSize(findViewById(R.id.theme_icon), dp(28f * scale));
        setSquareSize(findViewById(R.id.ui_scale_icon), dp(28f * scale));
        setSquareSize(findViewById(R.id.language_icon), dp(28f * scale));
        setSquareSize(findViewById(R.id.default_apps_icon), dp(28f * scale));
        setSquareSize(findViewById(R.id.changelog_icon), dp(28f * scale));
        setSquareSize(findViewById(R.id.quick_guide_icon), dp(28f * scale));
        setSquareSize(findViewById(R.id.github_icon), dp(28f * scale));
    }

    private void showChangelogDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.changelog_title)
                .setMessage(getString(R.string.changelog_content))
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void openQuickGuideLink() {
        String lang = LocaleManager.resolveLanguage(this);
        String url = LocaleManager.LANG_ES.equals(lang) ? QUICK_GUIDE_ES_URL : QUICK_GUIDE_EN_URL;
        openUrl(url);
    }

    private void openUrl(String url) {
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browser);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_opening_link), Toast.LENGTH_SHORT).show();
        }
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
        List<DefaultAppsManager.Entry> entries = DefaultAppsManager.getEntries(this);
        if (entries.isEmpty()) {
                new AlertDialog.Builder(this)
                    .setTitle(R.string.default_apps_title)
                    .setMessage(R.string.default_apps_empty_message)
                    .setNeutralButton(R.string.add_extension_app, (d, w) -> showAddExtensionDefaultAppDialog())
                    .setPositiveButton(R.string.close, null)
                    .show();
            return;
        }

        List<String> labels = new ArrayList<>();
        for (DefaultAppsManager.Entry row : entries) {
            labels.add(row.extension + "\n" + row.packageName);
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.default_apps_title)
                .setItems(labels.toArray(new String[0]), (d, which) -> showDefaultAppItemActions(entries.get(which)))
            .setNeutralButton(R.string.clear_all, (d, w) -> confirmClearAllDefaultApps())
            .setNegativeButton(R.string.add_extension_app, (d, w) -> showAddExtensionDefaultAppDialog())
            .setPositiveButton(R.string.close, null)
                .show();
    }

    private void showAddExtensionDefaultAppDialog() {
        List<String> unresolved = getUnresolvedCommonExtensions();
        List<String> options = new ArrayList<>(unresolved);
        options.add(getString(R.string.custom_extension_option));

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_extension_app)
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    if (which < unresolved.size()) {
                        showPickAppForExtension(unresolved.get(which));
                    } else {
                        showCustomExtensionInputDialog();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCustomExtensionInputDialog() {
        final EditText extInput = new EditText(this);
        extInput.setSingleLine(true);
        extInput.setHint(getString(R.string.extension_hint));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        wrap.setPadding(p, dp(8), p, 0);
        wrap.addView(extInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_extension_app)
                .setView(wrap)
                .setPositiveButton(R.string.accept, (d, w) -> {
                    String ext = normalizeExtensionInput(extInput.getText() == null ? "" : extInput.getText().toString());
                    if (ext == null) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.add_extension_app)
                                .setMessage(R.string.invalid_extension)
                                .setPositiveButton(R.string.close, null)
                                .show();
                        return;
                    }
                    showPickAppForExtension(ext);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private List<String> getUnresolvedCommonExtensions() {
        String[] common = {
                ".pdf", ".txt", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
                ".jpg", ".png", ".mp3", ".mp4", ".zip", ".apk"
        };

        Set<String> alreadyMapped = new HashSet<>();
        for (DefaultAppsManager.Entry entry : DefaultAppsManager.getEntries(this)) {
            if (entry != null && entry.extension != null) {
                alreadyMapped.add(entry.extension.trim().toLowerCase());
            }
        }

        List<String> unresolved = new ArrayList<>();
        for (String ext : common) {
            String normalized = normalizeExtensionInput(ext);
            if (normalized != null && !alreadyMapped.contains(normalized)) {
                unresolved.add(normalized);
            }
        }

        return unresolved;
    }

    private String normalizeExtensionInput(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase();
        if (value.isEmpty()) return null;
        if ("*".equals(value)) return "*";
        if (!value.startsWith(".")) value = "." + value;
        if (value.length() < 2) return null;
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '+')) {
                return null;
            }
        }
        return value;
    }

    private void showPickAppForExtension(String extension) {
        List<AppChoice> candidates = getAllInstalledApps();
        if (candidates.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.add_extension_app)
                    .setMessage(R.string.no_apps_found)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return;
        }

        showAppChoiceDialog(extension, candidates, null, selected -> {
            DefaultAppsManager.add(this, extension, selected.packageName, selected.label);
            updateDefaultAppsSubtitle();
            Toast.makeText(this, getString(R.string.default_app_saved), Toast.LENGTH_SHORT).show();
            showDefaultAppsDialog();
        });
    }

    private List<AppChoice> getAllInstalledApps() {
        PackageManager pm = getPackageManager();
        List<AppChoice> out = new ArrayList<>();
        Map<String, AppChoice> dedup = new LinkedHashMap<>();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(launcherIntent, 0);

        for (ResolveInfo info : launcherApps) {
            if (info == null || info.activityInfo == null) continue;
            ActivityInfo activityInfo = info.activityInfo;
            ApplicationInfo appInfo = activityInfo.applicationInfo;
            if (appInfo == null) continue;

            String pkg = activityInfo.packageName;
            if (pkg == null || pkg.trim().isEmpty()) continue;
            if (pkg.equals(getPackageName())) continue;

            CharSequence rawLabel = info.loadLabel(pm);
            String label = rawLabel == null ? pkg : rawLabel.toString().trim();
            if (label.isEmpty()) label = pkg;

            Drawable icon;
            try {
                icon = info.loadIcon(pm);
            } catch (Exception ignored) {
                icon = null;
            }

            dedup.put(pkg, new AppChoice(pkg, label, icon));
        }

        out.addAll(dedup.values());

        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
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

    private void showDefaultAppItemActions(DefaultAppsManager.Entry entry) {
        String extension = entry.extension;

        String[] options = {getString(R.string.change_app), getString(R.string.delete)};
        new AlertDialog.Builder(this)
                .setTitle(extension)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        showReplaceDefaultAppDialog(entry);
                    } else if (which == 1) {
                        DefaultAppsManager.removeByExtension(this, extension);
                        updateDefaultAppsSubtitle();
                        showDefaultAppsDialog();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showReplaceDefaultAppDialog(DefaultAppsManager.Entry entry) {
        List<AppChoice> candidates = getCandidateDefaultApps(entry.extension, entry.packageName, entry.appLabel);
        if (candidates.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.change_app)
                    .setMessage(R.string.no_apps_found)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return;
        }

        showAppChoiceDialog(getString(R.string.change_app), candidates, entry.packageName, selected -> {
            DefaultAppsManager.replacePackage(this, entry.extension, selected.packageName, selected.label);
            updateDefaultAppsSubtitle();
            showDefaultAppsDialog();
        });
    }

    private List<AppChoice> getCandidateDefaultApps(String extension, String oldPackageName, String currentLabel) {
        PackageManager pm = getPackageManager();
        Map<String, AppChoice> dedup = new LinkedHashMap<>();

        // Use the same source as "add extension": all launcher-visible apps.
        for (AppChoice app : getAllInstalledApps()) {
            if (app == null || app.packageName == null || app.packageName.trim().isEmpty()) continue;
            if (!dedup.containsKey(app.packageName)) {
                dedup.put(app.packageName, app);
            }
        }

        // Ensure current mapped app stays selectable even if hidden by package visibility.
        if (oldPackageName != null && !oldPackageName.trim().isEmpty() && !dedup.containsKey(oldPackageName)) {
            String fallbackLabel = (currentLabel == null || currentLabel.trim().isEmpty()) ? oldPackageName : currentLabel.trim();
            Drawable fallbackIcon = null;
            try {
                fallbackIcon = pm.getApplicationIcon(oldPackageName);
            } catch (Exception ignored) {
            }
            dedup.put(oldPackageName, new AppChoice(oldPackageName, fallbackLabel, fallbackIcon));
        }

        List<AppChoice> out = new ArrayList<>(dedup.values());
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    private void showAppChoiceDialog(String title,
                                     List<AppChoice> candidates,
                                     String selectedPackage,
                                     OnAppChoiceSelected onSelected) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int hp = dp(16);
        root.setPadding(hp, dp(10), hp, 0);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(getString(R.string.search_apps_hint));
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView listView = new ListView(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(420));
        listParams.topMargin = dp(8);
        root.addView(listView, listParams);

        AppChoiceAdapter adapter = new AppChoiceAdapter(candidates, selectedPackage);
        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(root)
                .setNegativeButton(R.string.cancel, null)
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppChoice selected = adapter.getItem(position);
            if (selected != null) {
                dialog.dismiss();
                onSelected.onSelected(selected);
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dialog.show();
    }

    private class AppChoiceAdapter extends BaseAdapter {
        private final List<AppChoice> all;
        private final List<AppChoice> filtered;
        private final String selectedPackage;

        AppChoiceAdapter(List<AppChoice> initial, String selectedPackage) {
            this.all = new ArrayList<>(initial);
            this.filtered = new ArrayList<>(initial);
            this.selectedPackage = selectedPackage;
        }

        void filter(String query) {
            String q = query == null ? "" : query.trim().toLowerCase();
            filtered.clear();
            if (q.isEmpty()) {
                filtered.addAll(all);
            } else {
                for (AppChoice c : all) {
                    String label = c.label == null ? "" : c.label.toLowerCase();
                    String pkg = c.packageName == null ? "" : c.packageName.toLowerCase();
                    if (label.contains(q) || pkg.contains(q)) {
                        filtered.add(c);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return filtered.size();
        }

        @Override
        public AppChoice getItem(int position) {
            return (position >= 0 && position < filtered.size()) ? filtered.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(SettingsActivity.this).inflate(R.layout.item_app_choice, parent, false);
            }

            AppChoice item = getItem(position);
            if (item == null) return v;

            ImageView icon = v.findViewById(R.id.app_icon);
            TextView label = v.findViewById(R.id.app_label);
            TextView pkg = v.findViewById(R.id.app_package);

            if (item.icon != null) icon.setImageDrawable(item.icon);
            else icon.setImageDrawable(null);

            boolean selected = selectedPackage != null && selectedPackage.equals(item.packageName);
            label.setText(selected ? ("✓ " + item.label) : item.label);
            pkg.setText(item.packageName);

            return v;
        }
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

