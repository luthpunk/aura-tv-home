package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages the visual themes of Glass TV Home.
 * Supports:
 * 1. Frosted Glass (Default Apple TV-inspired glassmorphism)
 * 2. Neumorphism (Soft UI tactile extruded design with soft shadows)
 * 3. Dark Glow (Pitch black OLED banners with vibrant cyber neon glow)
 */
public final class ThemeManager {
    public static final String THEME_FROSTED_GLASS = "frosted_glass";
    public static final String THEME_NEUMORPHISM = "neumorphism";
    public static final String THEME_DARK_GLOW = "dark_glow";

    private static final String PREFS_NAME = "tvhome_theme_prefs";
    private static final String KEY_ACTIVE_THEME = "active_theme";

    private ThemeManager() {}

    /**
     * Gets the currently active theme identifier.
     */
    public static String getActiveTheme(Context context) {
        if (context == null) return THEME_FROSTED_GLASS;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_ACTIVE_THEME, THEME_FROSTED_GLASS);
    }

    /**
     * Sets and persists the new active theme.
     */
    public static void setActiveTheme(Context context, String theme) {
        if (context == null || theme == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_ACTIVE_THEME, theme).apply();
    }

    /**
     * Returns true if the active theme is Dark Glow (all black banners + neon glow).
     */
    public static boolean isDarkGlow(Context context) {
        return THEME_DARK_GLOW.equalsIgnoreCase(getActiveTheme(context));
    }

    /**
     * Returns true if the active theme is Neumorphism (Soft UI).
     */
    public static boolean isNeumorphism(Context context) {
        return THEME_NEUMORPHISM.equalsIgnoreCase(getActiveTheme(context));
    }

    /**
     * Returns true if the active theme is Frosted Glass (Default).
     */
    public static boolean isFrostedGlass(Context context) {
        String theme = getActiveTheme(context);
        return THEME_FROSTED_GLASS.equalsIgnoreCase(theme) || (!isDarkGlow(context) && !isNeumorphism(context));
    }

    /**
     * User-facing display title for each theme.
     */
    public static String getThemeDisplayName(String theme) {
        if (theme == null) return "Frosted Glass";
        switch (theme) {
            case THEME_NEUMORPHISM:
                return "Neumorphism";
            case THEME_DARK_GLOW:
                return "Dark Glow";
            case THEME_FROSTED_GLASS:
            default:
                return "Frosted Glass";
        }
    }

    /**
     * Descriptive subtitle explaining each theme's visual style.
     */
    public static String getThemeSubtitle(String theme) {
        if (theme == null) return "";
        switch (theme) {
            case THEME_NEUMORPHISM:
                return "Tampilan cerah Soft UI taktil dengan bayangan lembut dan kanvas porselen elegan";
            case THEME_DARK_GLOW:
                return "Wallpaper ekstra gelap OLED, top menu hitam cyber, dan pendaran neon cyan terang";
            case THEME_FROSTED_GLASS:
            default:
                return "Efek kaca transparan bawaan Apple TV dengan wallpaper dinamis";
        }
    }
}
