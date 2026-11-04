package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Draws the dock tray behind the first (dock) row and adds a gap below it.
 * Dynamically adapts to the active theme:
 * - Frosted Glass: blurred wallpaper backdrop, translucent veil and specular glass rim.
 * - Neumorphism: soft tactile dark clay tray with dual-lighting bevel and rich soft shadow.
 * - Dark Glow: deep pitch-black OLED tray with cyber neon cyan rim.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trayShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlassRim edge;
    private Glider glider;
    private final int[] at = new int[2], gliderAt = new int[2];
    private final Backdrop.Fill blur = new Backdrop.Fill();

    // Theme-specific paints
    private final Paint darkTrayFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkTrayRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkTrayShadow = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint neuTrayFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint neuTrayRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint neuTrayShadow = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rect = new RectF();
    private final RectF tileBounds = new RectF();
    /** The tray as last drawn, in the grid's coordinates: where the focus shape shows on it. */
    final RectF area = new RectF();

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;

        // Frosted Glass
        band.setColor(0x12FFFFFF);
        trayShadowPaint.setColor(0x38000000);
        trayShadowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(14f * density, android.graphics.BlurMaskFilter.Blur.NORMAL));
        edge = new GlassRim(1.5f * density, 0xD9FFFFFF, 0x1AFFFFFF, 0.5f);

        // Dark Glow: Deep OLED black tray with subtle thin cyber cyan outline and OLED shadow
        darkTrayFill.setColor(0xEE08080C);
        darkTrayRim.setStyle(Paint.Style.STROKE);
        darkTrayRim.setStrokeWidth(1.1f * density);
        darkTrayRim.setColor(0x3300F0FF); // Sleek thin cyber neon cyan rim
        darkTrayShadow.setColor(0x80000000);
        darkTrayShadow.setMaskFilter(new android.graphics.BlurMaskFilter(16f * density, android.graphics.BlurMaskFilter.Blur.NORMAL));

        // Neumorphism Light: Soft extruded warm porcelain clay tray
        neuTrayFill.setColor(0xFFEDEAE4);
        neuTrayRim.setStyle(Paint.Style.STROKE);
        neuTrayRim.setStrokeWidth(1.6f * density);
        neuTrayShadow.setColor(0x28000000);
        neuTrayShadow.setMaskFilter(new android.graphics.BlurMaskFilter(16f * density, android.graphics.BlurMaskFilter.Blur.NORMAL));
    }

    /** The focus shape: the tray's glass would hide it, so the tray paints it over the glass itself. */
    void setGlider(Glider glider) {
        this.glider = glider;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        Context ctx = parent.getContext();
        boolean isDark = ThemeManager.isDarkGlow(ctx);
        boolean isNeu = ThemeManager.isNeumorphism(ctx);

        if (!any) {
            if (isNeu) {
                drawNeumorphicTileShadows(c, parent);
            }
            return;
        }
        float pad = 14 * density;
        rect.inset(-pad, -pad);
        area.set(rect);
        float radius = 26 * density;

        if (isDark) {
            // Dark Glow: Deep OLED black dock tray with cyber cyan subtle rim
            RectF trayShadow = new RectF(rect);
            trayShadow.offset(0, 4f * density);
            c.drawRoundRect(trayShadow, radius, radius, darkTrayShadow);

            c.drawRoundRect(rect, radius, radius, darkTrayFill);

            drawGlider(c, parent);

            float half = darkTrayRim.getStrokeWidth() / 2f;
            rect.inset(half, half);
            c.drawRoundRect(rect, radius - half, radius - half, darkTrayRim);
        } else if (isNeu) {
            // Neumorphism Light: True 3D extruded ("timbul") warm porcelain dock tray with dual opposing shadows
            NeumorphicDrawable.drawExtruded(c, rect, radius, density, 1f, true);

            drawGlider(c, parent);

            // Re-stroke tray bevel rim so it stays crisp over the glider edge
            float half = neuTrayRim.getStrokeWidth() / 2f;
            rect.inset(half, half);
            neuTrayRim.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{0xFFFFFFFF, 0x70FFFFFF, 0x321A1610},
                    new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, radius - half, radius - half, neuTrayRim);

            // Draw authentic 3D Soft UI dual outer shadows (top-left bright white highlight +
            // bottom-right warm dark drop shadow) behind EVERY visible app tile on the home screen!
            drawNeumorphicTileShadows(c, parent);
        } else {
            // Default: Frosted Glass
            // Soft drop shadow under the glass dock tray
            RectF trayShadow = new RectF(rect);
            trayShadow.offset(0, 4f * density);
            c.drawRoundRect(trayShadow, radius, radius, trayShadowPaint);

            blur.draw(c, parent, rect, radius, 1f);
            c.drawRoundRect(rect, radius, radius, band);

            drawGlider(c, parent);

            // Drawn just inside the tray, as the pills draw theirs
            float half = edge.paint.getStrokeWidth() / 2f;
            rect.inset(half, half);
            edge.fit(rect);
            c.drawRoundRect(rect, radius - half, radius - half, edge.paint);
        }
    }

    private void drawNeumorphicTileShadows(Canvas c, RecyclerView parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            View card = child.findViewById(R.id.card);
            if (card == null || card.getWidth() <= 0) continue;
            float cx = child.getLeft() + card.getLeft() + card.getWidth() * 0.5f;
            float cy = child.getTop() + card.getTop() + child.getTranslationY() + card.getHeight() * 0.5f;
            float sx = card.getScaleX();
            float sy = card.getScaleY();
            float hw = card.getWidth() * sx * 0.5f;
            float hh = card.getHeight() * sy * 0.5f;
            tileBounds.set(cx - hw, cy - hh, cx + hw, cy + hh);
            float tileR = AppAdapter.cornerRadius(card.getWidth(), density) * sx;
            NeumorphicDrawable.drawOuterDualShadows(c, tileBounds, tileR, density, 1f,
                    card.isFocused() ? 1.35f : 1.0f);
        }
    }

    private void drawGlider(Canvas c, RecyclerView parent) {
        if (glider != null && glider.isShowing()) {
            parent.getLocationInWindow(at);
            glider.getLocationInWindow(gliderAt);
            c.save();
            RectF clipArea = new RectF(rect);
            float shadowBleed = 36f * density;
            clipArea.inset(-shadowBleed, -shadowBleed);
            c.clipRect(clipArea);
            glider.drawShape(c, gliderAt[0] - at[0], gliderAt[1] - at[1], parent);
            c.restore();
        }
    }
}
