package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * The selection: a light rim around the focused tile, in the style of Apple's Liquid Glass, with
 * no fill, no brighter top and no outer glow, so the app underneath keeps its own brightness. One instance floats over the grid and is moved
 * with spring animations between tiles.
 */
public class GlassView extends View {

    private final GlassRim rim;
    private final Paint neonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        // The same shining edge as the top-bar pills (GlassDrawable), at the same width.
        rim = new GlassRim(1.5f * density, 0xD9FFFFFF, 0x1AFFFFFF);
        neonPaint.setStyle(Paint.Style.STROKE);
        neonPaint.setStrokeWidth(1.25f * density);
        neonPaint.setColor(0xFF00F0FF);
        // Cache the slab as a texture. The spring only changes translation, so the GPU can
        // move it without replaying clipPath and the glow on every frame. That path is
        // software-rendered on the MT5895 and is what makes the highlight crawl.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // The slab keeps its old size: this inset once made room for an outer glow, and the tile
        // positions and the spring are measured around it.
        float inset = 6f * density;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        if (ThemeManager.isDarkGlow(getContext())) {
            canvas.drawRoundRect(rect, radius, radius, neonPaint);
            return;
        }
        if (ThemeManager.isNeumorphism(getContext())) {
            NeumorphicDrawable.drawTileFocusRim(canvas, rect, radius, density, 1f);
            return;
        }
        // A rim only: the slab sits on top of the tile, so any fill would wash the app out.
        rim.fit(rect);
        canvas.drawRoundRect(rect, radius, radius, rim.paint);
    }
}
