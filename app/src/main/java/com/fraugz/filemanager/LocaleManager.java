package com.fraugz.filemanager;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

public class LocaleManager {

    private static final String PREFS = "app_prefs";
    private static final String KEY_LANGUAGE = "language";

    public static final String LANG_EN = "en";
    public static final String LANG_ES = "es";

    private LocaleManager() {
    }

    public static void apply(Activity activity) {
        String language = resolveLanguage(activity);
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = activity.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        if (!language.equals(config.locale != null ? config.locale.getLanguage() : "")) {
            config.setLocale(locale);
            resources.updateConfiguration(config, resources.getDisplayMetrics());
        }
    }

    public static void setLanguage(Context ctx, String language) {
        if (!LANG_EN.equals(language) && !LANG_ES.equals(language)) return;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, language)
                .apply();
    }

    public static String resolveLanguage(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_LANGUAGE, "");
        if (LANG_EN.equals(saved) || LANG_ES.equals(saved)) {
            return saved;
        }

        String deviceLanguage = Locale.getDefault().getLanguage();
        return LANG_ES.equals(deviceLanguage) ? LANG_ES : LANG_EN;
    }

    public static String getLanguageDisplayName(Context ctx) {
        String lang = resolveLanguage(ctx);
        return LANG_ES.equals(lang)
                ? ctx.getString(R.string.language_spanish)
                : ctx.getString(R.string.language_english);
    }
}
