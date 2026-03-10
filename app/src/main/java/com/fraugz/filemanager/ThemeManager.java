package com.fraugz.filemanager;

import android.app.Activity;
import android.content.Context;

public class ThemeManager {

    private static final String PREFS = "app_prefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_UI_SCALE = "ui_scale";
    public static final int THEME_DARK  = 0;
    public static final int THEME_LIGHT = 1;
    public static final float UI_SCALE_MIN = 0.90f;
    public static final float UI_SCALE_DEFAULT = 1.00f;
    public static final float UI_SCALE_MAX = 1.30f;

    public static int getTheme(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                  .getInt(KEY_THEME, THEME_DARK);
    }

    public static void setTheme(Context ctx, int theme) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
           .edit().putInt(KEY_THEME, theme).apply();
    }

    /**
     * Call this BEFORE super.onCreate() in every Activity.
     * AppTheme (dark) is already set in the Manifest, so we only
     * need to override when the user chose Light.
     */
    public static void apply(Activity activity) {
        LocaleManager.apply(activity);
        if (getTheme(activity) == THEME_LIGHT) {
            activity.setTheme(R.style.AppTheme_Light);
        }
        // Dark is the default — no setTheme() needed, avoids any timing issue
    }

    public static String getThemeName(Context ctx) {
        return getTheme(ctx) == THEME_LIGHT
            ? ctx.getString(R.string.theme_light)
            : ctx.getString(R.string.theme_dark);
    }

    public static float getUiScale(Context ctx) {
        float value = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getFloat(KEY_UI_SCALE, UI_SCALE_DEFAULT);
        if (value < UI_SCALE_MIN) return UI_SCALE_MIN;
        if (value > UI_SCALE_MAX) return UI_SCALE_MAX;
        return value;
    }

    public static void setUiScale(Context ctx, float scale) {
        float clamped = Math.max(UI_SCALE_MIN, Math.min(UI_SCALE_MAX, scale));
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_UI_SCALE, clamped).apply();
    }

    public static String getUiScaleName(Context ctx) {
        float scale = getUiScale(ctx);
        if (scale <= 0.95f) return ctx.getString(R.string.ui_scale_small);
        if (scale <= 1.07f) return ctx.getString(R.string.ui_scale_normal);
        if (scale <= 1.20f) return ctx.getString(R.string.ui_scale_large);
        return ctx.getString(R.string.ui_scale_xlarge);
    }
}

