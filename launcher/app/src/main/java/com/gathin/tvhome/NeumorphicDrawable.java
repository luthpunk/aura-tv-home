package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Renders authentic Soft UI Light (Neumorphism) 3D surfaces:
 * - {@link #MODE_EXTRUDED}: Raised/extruded ("timbul") warm porcelain clay with dual opposing
 *   soft shadows (bright white top-left highlight shadow + warm dark bottom-right drop shadow)
 *   and a convex 3D bevel rim.
 * - {@link #MODE_RECESSED}: Pressed-in/inset ("cekungan") cavity with inner top-left dark shadow
 *   crescent and inner bottom-right bright white highlight lip.
 *
 * Uses multi-pass feathered hardware-accelerated primitives with zero per-frame allocations
 * so it runs at a locked 60fps on Android TV GPUs.
 */
public final class NeumorphicDrawable extends Drawable {

    public static final int MODE_EXTRUDED = 0;
    public static final int MODE_RECESSED = 1;

    /** Warm porcelain surface matching reference image 1793633365104.jpg. */
    public static final int COLOR_SURFACE = 0xFFEDEAE4;
    public static final int COLOR_TEXT_PRIMARY = 0xFF2B3036;
    public static final int COLOR_TEXT_SECONDARY = 0xFF5A626E;

    private final View host;
    private final float radius;
    private final float density;
    private final int mode;
    private final boolean drawOuterShadow;
    private final Glider glider;
    /** True for menu panels (paints glider over the panel); false for top pills (morphs extruded -> recessed as glider covers it). */
    private final boolean paintsShape;

    private final RectF rect = new RectF();
    private final Path clipPath = new Path();
    private final int[] at = new int[2], highlightAt = new int[2];
    private int alpha = 255;

    // Reusable static drawing primitives for zero-allocation rendering
    private static final RectF TMP_RECT = new RectF();
    private static final Path TMP_PATH = new Path();
    private static final Matrix GRAD_MATRIX = new Matrix();

    private static final Paint FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint STROKE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SHADOW_DARK_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SHADOW_LIGHT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Normalized (0,0)->(1,1) unit gradients scaled via local Matrix.
    // Rebuilt by setDarkPalette() when switching between Neumorphism Light and Dark.
    private static LinearGradient EXTRUDED_FILL_GRAD, EXTRUDED_RIM_GRAD, RECESSED_FILL_GRAD,
            RECESSED_RIM_GRAD, TILE_UNFOCUSED_RIM_GRAD, TILE_FOCUS_RIM_GRAD;

    // Shadow palette (RGB only, alpha applied per feather pass) + per-pass alpha multipliers
    private static int sLightRgb = 0xFFFFFF, sDarkRgb = 0x1A1610, sInnerDarkRgb = 0x18140E;
    private static float sOuterLightK = 1f, sOuterDarkK = 1f, sInnerDarkK = 1f, sInnerLightK = 1f;
    private static int sFocusCushion = 0x55FFFFFF;
    private static boolean sDark = false;

    private static LinearGradient unit(int[] colors) {
        return new LinearGradient(0f, 0f, 1f, 1f, colors, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
    }

    private static void buildPalette(boolean dark) {
        sDark = dark;
        if (!dark) {
            // Neumorphism Light: warm porcelain (reference 1793633365104.jpg)
            EXTRUDED_FILL_GRAD = unit(new int[]{0xFFF6F4EF, 0xFFEDEAE4, 0xFFE3DFD7});
            EXTRUDED_RIM_GRAD = unit(new int[]{0xFFFFFFFF, 0x70FFFFFF, 0x321A1610});
            RECESSED_FILL_GRAD = unit(new int[]{0xFFD6D1C7, 0xFFE1DED6, 0xFFEAE7E0});
            RECESSED_RIM_GRAD = unit(new int[]{0x4E1A1610, 0x141A1610, 0xF8FFFFFF});
            TILE_UNFOCUSED_RIM_GRAD = unit(new int[]{0xD9FFFFFF, 0x33FFFFFF, 0x281A1610});
            TILE_FOCUS_RIM_GRAD = unit(new int[]{0xFFFFFFFF, 0xB3FFFFFF, 0x451A1610});
            sLightRgb = 0xFFFFFF; sDarkRgb = 0x1A1610; sInnerDarkRgb = 0x18140E;
            sOuterLightK = 1f; sOuterDarkK = 1f; sInnerDarkK = 1f; sInnerLightK = 1f;
            sFocusCushion = 0x55FFFFFF;
        } else {
            // Neumorphism Dark: charcoal-navy canvas (reference 1794194557112.jpg)
            // base #171A22, raised highlight #242939, deep shadow #07080C
            EXTRUDED_FILL_GRAD = unit(new int[]{0xFF1E212B, 0xFF191C24, 0xFF15171E});
            EXTRUDED_RIM_GRAD = unit(new int[]{0xB3343A4D, 0x40282D3B, 0x8C05060A});
            RECESSED_FILL_GRAD = unit(new int[]{0xFF0E0F15, 0xFF12141A, 0xFF171920});
            RECESSED_RIM_GRAD = unit(new int[]{0xB305060A, 0x4005060A, 0x8C2F3546});
            TILE_UNFOCUSED_RIM_GRAD = unit(new int[]{0x8C343A4D, 0x26282D3B, 0x8005060A});
            TILE_FOCUS_RIM_GRAD = unit(new int[]{0xFF4C5570, 0xA6343A4D, 0xA605060A});
            sLightRgb = 0x2A3042; sDarkRgb = 0x000000; sInnerDarkRgb = 0x000000;
            sOuterLightK = 0.9f; sOuterDarkK = 3.2f; sInnerDarkK = 2.6f; sInnerLightK = 0.75f;
            sFocusCushion = 0x404C5570;
        }
    }

    /** Called by ThemeManager when the Neumorphism variant changes. */
    public static void setDarkPalette(boolean dark) {
        if (dark != sDark || EXTRUDED_FILL_GRAD == null) buildPalette(dark);
    }

    public static boolean isDarkPalette() {
        return sDark;
    }

    static {
        STROKE_PAINT.setStyle(Paint.Style.STROKE);
        buildPalette(false);
    }

    public NeumorphicDrawable(float radius, float density, int mode) {
        this(null, radius, density, mode, true, null, false);
    }

    public NeumorphicDrawable(float radius, float density, int mode, boolean drawOuterShadow) {
        this(null, radius, density, mode, drawOuterShadow, null, false);
    }

    public NeumorphicDrawable(@Nullable View host, float radius, float density, int mode,
                              boolean drawOuterShadow, @Nullable Glider glider, boolean paintsShape) {
        this.host = host;
        this.radius = radius;
        this.density = density;
        this.mode = mode;
        this.drawOuterShadow = drawOuterShadow;
        this.glider = glider;
        this.paintsShape = paintsShape;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty() || alpha == 0) return;
        float a = alpha / 255f;
        rect.set(b);

        if (mode == MODE_RECESSED) {
            drawRecessed(canvas, rect, radius, density, a);
            return;
        }

        // For top-bar pills (paintsShape == false with a Glider companion):
        // When unfocused (covered == 0), draw extruded ("timbul").
        // As the glider arrives on the pill (covered -> 1), fade the extruded dome out so the
        // recessed ("cekungan") cavity shows cleanly.
        float covered = 0f;
        if (!paintsShape && host != null) {
            if (glider != null) {
                if (glider.isShowing()) {
                    host.getLocationInWindow(at);
                    glider.getLocationInWindow(highlightAt);
                    covered = glider.coverage(highlightAt[0] - at[0], highlightAt[1] - at[1], rect.width(), rect.height());
                }
            } else if (host.isFocused()) {
                covered = 1f;
            }
        }

        float extrudedAlpha = (1f - covered) * a;
        if (extrudedAlpha > 0f) {
            drawExtruded(canvas, rect, radius, density, extrudedAlpha, drawOuterShadow);
        }
        if (!paintsShape && covered > 0f && glider == null) {
            drawRecessed(canvas, rect, radius, density, covered * a);
        }

        // For dropdown menu panels (paintsShape == true): paint the gliding recessed row over the panel
        if (paintsShape && glider != null && glider.isShowing() && host != null) {
            host.getLocationInWindow(at);
            glider.getLocationInWindow(highlightAt);
            clipPath.reset();
            clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clipPath);
            glider.drawShape(canvas, highlightAt[0] - at[0], highlightAt[1] - at[1], host);
            canvas.restoreToCount(save);
        }
    }

    /**
     * Draws an extruded ("timbul") 3D Soft UI shape with top-left bright white highlight shadow,
     * bottom-right warm dark drop shadow, convex porcelain fill, and dual-lighting bevel rim.
     */
    public static void drawExtruded(@NonNull Canvas c, @NonNull RectF bounds, float r,
                                    float density, float alpha, boolean withOuterShadow) {
        if (bounds.isEmpty() || alpha <= 0f) return;

        if (withOuterShadow) {
            drawOuterDualShadows(c, bounds, r, density, alpha, 1.0f);
        }

        // Convex warm porcelain body fill
        GRAD_MATRIX.reset();
        GRAD_MATRIX.setScale(Math.max(1f, bounds.width()), Math.max(1f, bounds.height()));
        GRAD_MATRIX.postTranslate(bounds.left, bounds.top);
        EXTRUDED_FILL_GRAD.setLocalMatrix(GRAD_MATRIX);

        FILL_PAINT.setShader(EXTRUDED_FILL_GRAD);
        FILL_PAINT.setAlpha(Math.round(255 * alpha));
        c.drawRoundRect(bounds, r, r, FILL_PAINT);
        FILL_PAINT.setShader(null);

        // 3D top-left white highlight & bottom-right shade bevel rim
        float strokeW = 1.5f * density;
        float inset = strokeW * 0.5f;
        TMP_RECT.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
        GRAD_MATRIX.reset();
        GRAD_MATRIX.setScale(Math.max(1f, TMP_RECT.width()), Math.max(1f, TMP_RECT.height()));
        GRAD_MATRIX.postTranslate(TMP_RECT.left, TMP_RECT.top);
        EXTRUDED_RIM_GRAD.setLocalMatrix(GRAD_MATRIX);

        STROKE_PAINT.setStrokeWidth(strokeW);
        STROKE_PAINT.setShader(EXTRUDED_RIM_GRAD);
        STROKE_PAINT.setAlpha(Math.round(255 * alpha));
        float rr = Math.max(0f, r - inset);
        c.drawRoundRect(TMP_RECT, rr, rr, STROKE_PAINT);
        STROKE_PAINT.setShader(null);
    }

    /**
     * Draws only the dual opposing Soft UI outer shadows (bright white top-left highlight glow +
     * warm dark bottom-right drop shadow) behind a card or tray.
     */
    public static void drawOuterDualShadows(@NonNull Canvas c, @NonNull RectF bounds, float r,
                                            float density, float alpha, float intensity) {
        if (bounds.isEmpty() || alpha <= 0f) return;

        // 1. Top-Left Bright White Highlight Shadow (Timbul atas-kiri)
        SHADOW_LIGHT_PAINT.setStyle(Paint.Style.FILL);
        for (int i = 5; i >= 1; i--) {
            float shift = i * 1.05f * density * intensity;
            float spread = i * 0.5f * density * intensity;
            TMP_RECT.set(
                    bounds.left - shift - spread,
                    bounds.top - shift - spread,
                    bounds.right - shift * 0.15f + spread * 0.25f,
                    bounds.bottom - shift * 0.15f + spread * 0.25f);
            int passAlpha = Math.min(255, Math.round(44 * sOuterLightK * alpha * ((6 - i) / 5f)));
            SHADOW_LIGHT_PAINT.setColor((passAlpha << 24) | sLightRgb);
            float sr = r + spread;
            c.drawRoundRect(TMP_RECT, sr, sr, SHADOW_LIGHT_PAINT);
        }

        // 2. Bottom-Right Warm Dark Drop Shadow (Timbul bawah-kanan)
        SHADOW_DARK_PAINT.setStyle(Paint.Style.FILL);
        for (int i = 6; i >= 1; i--) {
            float dx = i * 1.25f * density * intensity;
            float dy = i * 1.45f * density * intensity;
            float spread = i * 0.6f * density * intensity;
            TMP_RECT.set(
                    bounds.left + dx * 0.15f - spread * 0.25f,
                    bounds.top + dy * 0.15f - spread * 0.25f,
                    bounds.right + dx + spread,
                    bounds.bottom + dy + spread);
            int passAlpha = Math.min(255, Math.round(28 * sOuterDarkK * alpha * ((7 - i) / 6f)));
            SHADOW_DARK_PAINT.setColor((passAlpha << 24) | sDarkRgb);
            float sr = r + spread;
            c.drawRoundRect(TMP_RECT, sr, sr, SHADOW_DARK_PAINT);
        }
    }

    /**
     * Optimized 2-pass ambient dual shadow for unfocused tiles and background elements.
     * Replaces 11 feathered passes with 2 pre-blended passes, reducing draw calls by 82%
     * while retaining the authentic top-left highlight and bottom-right dark depth.
     */
    public static void drawAmbientDualShadows(@NonNull Canvas c, @NonNull RectF bounds, float r,
                                              float density, float alpha) {
        if (bounds.isEmpty() || alpha <= 0f) return;

        // Top-Left Light Highlight Shadow (Single blended pass)
        SHADOW_LIGHT_PAINT.setStyle(Paint.Style.FILL);
        float shiftLight = 2.0f * density;
        float spreadLight = 1.5f * density;
        TMP_RECT.set(
                bounds.left - shiftLight - spreadLight,
                bounds.top - shiftLight - spreadLight,
                bounds.right + spreadLight * 0.2f,
                bounds.bottom + spreadLight * 0.2f);
        int lightAlpha = Math.min(255, Math.round(58 * sOuterLightK * alpha));
        SHADOW_LIGHT_PAINT.setColor((lightAlpha << 24) | sLightRgb);
        c.drawRoundRect(TMP_RECT, r + spreadLight, r + spreadLight, SHADOW_LIGHT_PAINT);

        // Bottom-Right Dark Drop Shadow (Single blended pass)
        SHADOW_DARK_PAINT.setStyle(Paint.Style.FILL);
        float dx = 2.4f * density;
        float dy = 2.8f * density;
        float spreadDark = 2.0f * density;
        TMP_RECT.set(
                bounds.left - spreadDark * 0.2f,
                bounds.top - spreadDark * 0.2f,
                bounds.right + dx + spreadDark,
                bounds.bottom + dy + spreadDark);
        int darkAlpha = Math.min(255, Math.round(48 * sOuterDarkK * alpha));
        SHADOW_DARK_PAINT.setColor((darkAlpha << 24) | sDarkRgb);
        c.drawRoundRect(TMP_RECT, r + spreadDark, r + spreadDark, SHADOW_DARK_PAINT);
    }

    /**
     * Draws a recessed / pressed-in ("efek cekungan") Soft UI cavity with inner top-left dark
     * shadow and inner bottom-right bright white highlight lip, matching the Weather pill and
     * focused menu row in reference image 1793633365104.jpg.
     */
    public static void drawRecessed(@NonNull Canvas c, @NonNull RectF bounds, float r,
                                    float density, float alpha) {
        if (bounds.isEmpty() || alpha <= 0f) return;

        // 1. Concave cavity base fill
        GRAD_MATRIX.reset();
        GRAD_MATRIX.setScale(Math.max(1f, bounds.width()), Math.max(1f, bounds.height()));
        GRAD_MATRIX.postTranslate(bounds.left, bounds.top);
        RECESSED_FILL_GRAD.setLocalMatrix(GRAD_MATRIX);

        FILL_PAINT.setShader(RECESSED_FILL_GRAD);
        FILL_PAINT.setAlpha(Math.round(255 * alpha));
        c.drawRoundRect(bounds, r, r, FILL_PAINT);
        FILL_PAINT.setShader(null);

        // 2. Clip inside the cavity to render authentic inner top-left dark shadow & bottom-right white glow
        TMP_PATH.reset();
        TMP_PATH.addRoundRect(bounds, r, r, Path.Direction.CW);
        int save = c.save();
        c.clipPath(TMP_PATH);

        // Inner top-left dark shadow crescent (2 feathered passes - optimized from 6)
        SHADOW_DARK_PAINT.setStyle(Paint.Style.STROKE);
        float darkStrokeW = 3.0f * density;
        SHADOW_DARK_PAINT.setStrokeWidth(darkStrokeW);
        for (int i = 1; i <= 2; i++) {
            float d = i * 1.8f * density;
            TMP_RECT.set(
                    bounds.left + d * 0.45f,
                    bounds.top + d * 0.55f,
                    bounds.right + d * 2.5f,
                    bounds.bottom + d * 2.5f);
            int passAlpha = Math.min(255, Math.round(52 * sInnerDarkK * alpha * (i == 1 ? 1.0f : 0.65f)));
            SHADOW_DARK_PAINT.setColor((passAlpha << 24) | sInnerDarkRgb);
            c.drawRoundRect(TMP_RECT, r, r, SHADOW_DARK_PAINT);
        }

        // Inner bottom-right bright white highlight crescent (2 feathered passes - optimized from 5)
        SHADOW_LIGHT_PAINT.setStyle(Paint.Style.STROKE);
        float lightStrokeW = 2.6f * density;
        SHADOW_LIGHT_PAINT.setStrokeWidth(lightStrokeW);
        for (int i = 1; i <= 2; i++) {
            float d = i * 1.6f * density;
            TMP_RECT.set(
                    bounds.left - d * 2.5f,
                    bounds.top - d * 2.5f,
                    bounds.right - d * 0.45f,
                    bounds.bottom - d * 0.55f);
            int passAlpha = Math.min(255, Math.round(78 * sInnerLightK * alpha * (i == 1 ? 1.0f : 0.65f)));
            SHADOW_LIGHT_PAINT.setColor((passAlpha << 24) | sLightRgb);
            c.drawRoundRect(TMP_RECT, r, r, SHADOW_LIGHT_PAINT);
        }

        c.restoreToCount(save);

        // 3. Crisp inner cavity rim (dark top-left lip -> bright white bottom-right lip)
        float rimW = 1.35f * density;
        float inset = rimW * 0.5f;
        TMP_RECT.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
        GRAD_MATRIX.reset();
        GRAD_MATRIX.setScale(Math.max(1f, TMP_RECT.width()), Math.max(1f, TMP_RECT.height()));
        GRAD_MATRIX.postTranslate(TMP_RECT.left, TMP_RECT.top);
        RECESSED_RIM_GRAD.setLocalMatrix(GRAD_MATRIX);

        STROKE_PAINT.setStrokeWidth(rimW);
        STROKE_PAINT.setShader(RECESSED_RIM_GRAD);
        STROKE_PAINT.setAlpha(Math.round(255 * alpha));
        float rr = Math.max(0f, r - inset);
        c.drawRoundRect(TMP_RECT, rr, rr, STROKE_PAINT);
        STROKE_PAINT.setShader(null);
    }

    /**
     * Draws a subtle 3D Soft UI convex bevel rim on an unfocused app tile over its banner.
     */
    public static void drawTileUnfocusedRim(@NonNull Canvas c, @NonNull RectF bounds, float r,
                                            float density, float alpha) {
        if (bounds.isEmpty() || alpha <= 0f) return;
        float strokeW = 1.2f * density;
        float inset = strokeW * 0.5f;
        TMP_RECT.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
        GRAD_MATRIX.reset();
        GRAD_MATRIX.setScale(Math.max(1f, TMP_RECT.width()), Math.max(1f, TMP_RECT.height()));
        GRAD_MATRIX.postTranslate(TMP_RECT.left, TMP_RECT.top);
        TILE_UNFOCUSED_RIM_GRAD.setLocalMatrix(GRAD_MATRIX);

        STROKE_PAINT.setStrokeWidth(strokeW);
        STROKE_PAINT.setShader(TILE_UNFOCUSED_RIM_GRAD);
        STROKE_PAINT.setAlpha(Math.round(255 * alpha));
        float rr = Math.max(0f, r - inset);
        c.drawRoundRect(TMP_RECT, rr, rr, STROKE_PAINT);
        STROKE_PAINT.setShader(null);
    }

    /**
     * Draws a tactile 3D Soft UI focus bevel rim on a focused app tile (replaces Frosted Glass rim).
     */
    public static void drawTileFocusRim(@NonNull Canvas c, @NonNull RectF bounds, float r,
                                        float density, float alpha) {
        if (bounds.isEmpty() || alpha <= 0f) return;

        // Soft inner white luminous cushion just inside the bevel
        float innerW = 2.8f * density;
        float innerInset = innerW * 0.5f;
        TMP_RECT.set(bounds.left + innerInset, bounds.top + innerInset, bounds.right - innerInset, bounds.bottom - innerInset);
        STROKE_PAINT.setShader(null);
        STROKE_PAINT.setStrokeWidth(innerW);
        STROKE_PAINT.setColor((Math.round(((sFocusCushion >>> 24) & 0xFF) * alpha) << 24) | (sFocusCushion & 0x00FFFFFF));
        float rInner = Math.max(0f, r - innerInset);
        c.drawRoundRect(TMP_RECT, rInner, rInner, STROKE_PAINT);

        // Crisp 3D top-left white highlight to bottom-right warm shade bevel rim
        float strokeW = 1.8f * density;
        float inset = strokeW * 0.5f;
        TMP_RECT.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
        GRAD_MATRIX.reset();
        GRAD_MATRIX.setScale(Math.max(1f, TMP_RECT.width()), Math.max(1f, TMP_RECT.height()));
        GRAD_MATRIX.postTranslate(TMP_RECT.left, TMP_RECT.top);
        TILE_FOCUS_RIM_GRAD.setLocalMatrix(GRAD_MATRIX);

        STROKE_PAINT.setStrokeWidth(strokeW);
        STROKE_PAINT.setShader(TILE_FOCUS_RIM_GRAD);
        STROKE_PAINT.setAlpha(Math.round(255 * alpha));
        float rr = Math.max(0f, r - inset);
        c.drawRoundRect(TMP_RECT, rr, rr, STROKE_PAINT);
        STROKE_PAINT.setShader(null);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
