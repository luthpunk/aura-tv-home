package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages the visual themes of Glass TV Home.
 * Supports:
 * 1. Frosted Glass (Default Apple TV-inspired glassmorphism)
 * 2. Neumorphism (Soft UI tactile extruded design with soft shadows)
 * 3. Dark Glow (Pitch black OLED banners with vibrant cyber neon glow)
 * 4. Neumorphism Dark (Soft UI on a deep charcoal-navy canvas, same 3D shapes as Neumorphism)
 */
public final class ThemeManager {
    public static final String THEME_FROSTED_GLASS = "frosted_glass";
    public static final String THEME_NEUMORPHISM = "neumorphism";
    public static final String THEME_DARK_GLOW = "dark_glow";
    public static final String THEME_NEUMORPHISM_DARK = "neumorphism_dark";

    /** Cached so static drawers / color mapping know the variant without a Context. */
    private static volatile boolean sNeuDark = false;

    private static final String PREFS_NAME = "tvhome_theme_prefs";
    private static final String KEY_ACTIVE_THEME = "active_theme";

    private ThemeManager() {}

    /**
     * Gets the currently active theme identifier.
     */
    public static String getActiveTheme(Context context) {
        if (context == null) return THEME_FROSTED_GLASS;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String theme = sp.getString(KEY_ACTIVE_THEME, THEME_FROSTED_GLASS);
        updateNeuDark(THEME_NEUMORPHISM_DARK.equalsIgnoreCase(theme));
        return theme;
    }

    private static void updateNeuDark(boolean dark) {
        if (sNeuDark != dark) {
            sNeuDark = dark;
            NeumorphicDrawable.setDarkPalette(dark);
        }
    }

    /**
     * Sets and persists the new active theme.
     */
    public static void setActiveTheme(Context context, String theme) {
        if (context == null || theme == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_ACTIVE_THEME, theme).apply();
        updateNeuDark(THEME_NEUMORPHISM_DARK.equalsIgnoreCase(theme));
    }

    /**
     * Returns true if the active theme is Dark Glow (all black banners + neon glow).
     */
    public static boolean isDarkGlow(Context context) {
        return THEME_DARK_GLOW.equalsIgnoreCase(getActiveTheme(context));
    }

    /**
     * Returns true if the active theme is Neumorphism (Soft UI), Light OR Dark variant.
     * Both variants share every 3D shape/layout path; only the palette differs.
     */
    public static boolean isNeumorphism(Context context) {
        String t = getActiveTheme(context);
        return THEME_NEUMORPHISM.equalsIgnoreCase(t) || THEME_NEUMORPHISM_DARK.equalsIgnoreCase(t);
    }

    /** Returns true if the active theme is the Neumorphism Dark variant. */
    public static boolean isNeumorphismDark(Context context) {
        return THEME_NEUMORPHISM_DARK.equalsIgnoreCase(getActiveTheme(context));
    }

    /** Last-known Neumorphism Dark state (no Context needed). */
    public static boolean isNeuDarkCached() {
        return sNeuDark;
    }

    /**
     * Maps a Neumorphism LIGHT palette color to its Neumorphism DARK counterpart.
     * Returns the color unchanged when the light variant is active, so every existing
     * {@code isNeu ? color : ...} site works for both variants.
     * Reference palette (1794194557112.jpg): canvas #171A22, raised highlight #242939,
     * deep shadow #0B0C12, primary text near-white.
     */
    public static int neu(int c) {
        if (!sNeuDark) return c;
        int a = (c >>> 24) & 0xFF;
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 510f;

        // Saturated accent (blue link, green status, AQI colors...): keep hue, lift if too dark
        if (max - min > 60) {
            if (l < 0.42f) {
                r = r + (int) ((255 - r) * 0.35f);
                g = g + (int) ((255 - g) * 0.35f);
                b = b + (int) ((255 - b) * 0.35f);
            }
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        if (a < 0xFF) {
            if (l >= 0.97f) {
                // White highlight glaze -> faint cool highlight
                return (Math.round(a * 0.55f) << 24) | 0x2E3446;
            }
            if (l >= 0.6f) {
                // Porcelain translucent wash/overlay -> deep charcoal wash
                return (a << 24) | 0x0E1016;
            }
            if (l <= 0.25f) {
                // Hairline divider / faint dark tint on light -> faint light hairline on dark
                return (Math.min(255, Math.round(a * 0.9f)) << 24) | 0xFFFFFF;
            }
            int v = Math.round((1f - l) * 255f);
            return (a << 24) | (v << 16) | (v << 8) | Math.min(255, v + 8);
        }

        if (l >= 0.6f) {
            // Opaque light surface -> dark surface (brighter source = base, slightly darker = deeper)
            float nl = 0.09f + (0.93f - l) * 0.35f;
            nl = Math.max(0.045f, Math.min(0.2f, nl));
            int v = Math.round(nl * 255f);
            return 0xFF000000 | (Math.max(0, v - 2) << 16) | (v << 8) | Math.min(255, v + 9);
        }
        // Opaque dark text/icon -> light text/icon
        float nl = 1f - l * 0.6f;
        int v = Math.round(Math.max(0.45f, Math.min(0.97f, nl)) * 255f);
        return 0xFF000000 | (Math.max(0, v - 4) << 16) | (Math.max(0, v - 2) << 8) | Math.min(255, v + 4);
    }

    /** Array helper for gradients. */
    public static int[] neu(int[] colors) {
        if (!sNeuDark || colors == null) return colors;
        int[] out = new int[colors.length];
        for (int i = 0; i < colors.length; i++) out[i] = neu(colors[i]);
        return out;
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
            case THEME_NEUMORPHISM_DARK:
                return "Neumorphism Dark";
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
            case THEME_NEUMORPHISM_DARK:
                return "Soft UI taktil versi gelap: kanvas arang-navy, tombol timbul dengan bayangan pekat";
            case THEME_DARK_GLOW:
                return "Wallpaper ekstra gelap OLED, top menu hitam cyber, dan pendaran neon cyan terang";
            case THEME_FROSTED_GLASS:
            default:
                return "Efek kaca transparan bawaan Apple TV dengan wallpaper dinamis";
        }
    }
}
