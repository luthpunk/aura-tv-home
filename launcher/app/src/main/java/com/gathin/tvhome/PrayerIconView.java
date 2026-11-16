package com.gathin.tvhome;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Draws crisp vector-style Canvas artwork for the Prayer Times widget:
 * mosque silhouette with golden crescent halo, clock badge, and the 6 time-of-day prayer icons.
 */
public class PrayerIconView extends View {

    public static final int ICON_SUBUH = 0;
    public static final int ICON_SYURUQ = 1;
    public static final int ICON_DZUHUR = 2;
    public static final int ICON_ASHAR = 3;
    public static final int ICON_MAGHRIB = 4;
    public static final int ICON_ISYA = 5;
    public static final int ICON_MOSQUE = 10;
    public static final int ICON_CLOCK = 11;

    private int iconType = ICON_SUBUH;
    private int foregroundColor = 0xFFFFFFFF;
    private int cutoutColor = 0xFF3A3F4B;
    private int goldColor = 0xFFFACC15;
    private int goldWarmColor = 0xFFF59E0B;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tmpPath = new Path();
    private final RectF tmpRect = new RectF();

    public PrayerIconView(Context context) {
        this(context, null);
    }

    public PrayerIconView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PrayerIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.tag});
            String tag = ta.getString(0);
            ta.recycle();
            if (tag != null) {
                parseTag(tag);
            }
        }
    }

    private void parseTag(String tag) {
        switch (tag) {
            case "mosque":
                iconType = ICON_MOSQUE;
                break;
            case "clock":
                iconType = ICON_CLOCK;
                break;
            case "0":
                iconType = ICON_SUBUH;
                break;
            case "1":
                iconType = ICON_SYURUQ;
                break;
            case "2":
                iconType = ICON_DZUHUR;
                break;
            case "3":
                iconType = ICON_ASHAR;
                break;
            case "4":
                iconType = ICON_MAGHRIB;
                break;
            case "5":
                iconType = ICON_ISYA;
                break;
            default:
                break;
        }
    }

    public void setIconType(int type) {
        if (this.iconType != type) {
            this.iconType = type;
            invalidate();
        }
    }

    public void setThemeColors(int foreground, int cutout, int gold) {
        if (this.foregroundColor != foreground || this.cutoutColor != cutout || this.goldColor != gold) {
            this.foregroundColor = foreground;
            this.cutoutColor = cutout;
            this.goldColor = gold;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        switch (iconType) {
            case ICON_MOSQUE:
                drawMosqueWithHalo(canvas, w, h);
                break;
            case ICON_CLOCK:
                drawClock(canvas, w, h);
                break;
            case ICON_SUBUH:
                drawSunrise(canvas, w, h, false);
                break;
            case ICON_SYURUQ:
                drawSunrise(canvas, w, h, true);
                break;
            case ICON_DZUHUR:
                drawSun(canvas, w, h);
                break;
            case ICON_ASHAR:
                drawSunCloud(canvas, w, h);
                break;
            case ICON_MAGHRIB:
                drawSunset(canvas, w, h);
                break;
            case ICON_ISYA:
                drawMoonStars(canvas, w, h);
                break;
            default:
                break;
        }
    }

    private void drawMosqueWithHalo(Canvas canvas, int w, int h) {
        float s = Math.min(w, h);
        float ox = (w - s) * 0.5f;
        float oy = (h - s) * 0.5f;
        float cx = ox + s * 0.48f;
        float cy = oy + s * 0.46f;
        float haloR = s * 0.42f;

        // Soft golden ambient glow behind the mosque dome
        glowPaint.setShader(new RadialGradient(
                cx, cy, haloR,
                new int[]{0x99FACC15, 0x55F59E0B, 0x00F59E0B},
                new float[]{0.15f, 0.72f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, haloR, glowPaint);
        glowPaint.setShader(null);

        // Bright golden crescent arc framing the mosque
        strokePaint.setColor(goldColor);
        strokePaint.setStrokeWidth(Math.max(2f, s * 0.045f));
        tmpRect.set(cx - haloR * 0.92f, cy - haloR * 0.92f, cx + haloR * 0.92f, cy + haloR * 0.92f);
        canvas.drawArc(tmpRect, 165f, 210f, false, strokePaint);

        // Mosque silhouette (dome + minaret + colonnade)
        fillPaint.setColor(foregroundColor);
        float baseBottom = oy + s * 0.89f;
        float roofY = oy + s * 0.66f;
        float leftWall = ox + s * 0.13f;
        float rightWall = ox + s * 0.69f;

        // Ground plinth
        tmpRect.set(ox + s * 0.09f, baseBottom - s * 0.045f, ox + s * 0.91f, baseBottom);
        canvas.drawRoundRect(tmpRect, s * 0.02f, s * 0.02f, fillPaint);

        // Main prayer hall body
        tmpRect.set(leftWall, roofY, rightWall, baseBottom - s * 0.03f);
        canvas.drawRect(tmpRect, fillPaint);

        // Central large onion dome
        float domeCx = ox + s * 0.41f;
        float domeW = s * 0.20f;
        float domeTop = oy + s * 0.30f;
        tmpPath.reset();
        tmpPath.moveTo(domeCx - domeW, roofY + s * 0.01f);
        tmpPath.cubicTo(domeCx - domeW * 1.08f, roofY - s * 0.17f,
                domeCx - domeW * 0.35f, domeTop + s * 0.05f,
                domeCx, domeTop);
        tmpPath.cubicTo(domeCx + domeW * 0.35f, domeTop + s * 0.05f,
                domeCx + domeW * 1.08f, roofY - s * 0.17f,
                domeCx + domeW, roofY + s * 0.01f);
        tmpPath.close();
        canvas.drawPath(tmpPath, fillPaint);

        // Central spire
        strokePaint.setColor(foregroundColor);
        strokePaint.setStrokeWidth(Math.max(1.5f, s * 0.024f));
        canvas.drawLine(domeCx, domeTop, domeCx, domeTop - s * 0.09f, strokePaint);
        canvas.drawCircle(domeCx, domeTop - s * 0.045f, s * 0.016f, fillPaint);

        // Two small flanking domes (left & mid-right)
        drawSmallDome(canvas, ox + s * 0.19f, roofY, s * 0.068f, s * 0.095f);
        drawSmallDome(canvas, ox + s * 0.63f, roofY, s * 0.068f, s * 0.095f);

        // Tall right minaret
        float minCx = ox + s * 0.785f;
        float minHalf = s * 0.046f;
        float minTop = oy + s * 0.37f;
        tmpRect.set(minCx - minHalf, minTop, minCx + minHalf, baseBottom - s * 0.03f);
        canvas.drawRect(tmpRect, fillPaint);
        // Minaret upper balcony
        tmpRect.set(minCx - minHalf * 1.45f, minTop + s * 0.09f, minCx + minHalf * 1.45f, minTop + s * 0.125f);
        canvas.drawRoundRect(tmpRect, s * 0.015f, s * 0.015f, fillPaint);
        // Minaret pointed dome + finial
        drawSmallDome(canvas, minCx, minTop + s * 0.005f, minHalf * 1.08f, s * 0.085f);
        strokePaint.setStrokeWidth(Math.max(1.3f, s * 0.02f));
        canvas.drawLine(minCx, minTop - s * 0.08f, minCx, minTop - s * 0.145f, strokePaint);

        // Arched windows/doors cut out along the base
        fillPaint.setColor(cutoutColor);
        float archBottom = baseBottom - s * 0.045f;
        float archTop = roofY + s * 0.055f;
        float archW = s * 0.055f;
        float[] archCenters = {ox + s * 0.19f, ox + s * 0.295f, ox + s * 0.41f, ox + s * 0.525f, ox + s * 0.63f};
        for (int i = 0; i < archCenters.length; i++) {
            float ax = archCenters[i];
            float aw = (i == 2) ? archW * 1.25f : archW;
            float at = (i == 2) ? archTop - s * 0.02f : archTop;
            tmpPath.reset();
            tmpPath.moveTo(ax - aw * 0.5f, archBottom);
            tmpPath.lineTo(ax - aw * 0.5f, at + aw * 0.6f);
            tmpRect.set(ax - aw * 0.5f, at, ax + aw * 0.5f, at + aw);
            tmpPath.arcTo(tmpRect, 180f, 180f);
            tmpPath.lineTo(ax + aw * 0.5f, archBottom);
            tmpPath.close();
            canvas.drawPath(tmpPath, fillPaint);
        }
        // Small minaret window cutout
        tmpRect.set(minCx - s * 0.016f, minTop + s * 0.17f, minCx + s * 0.016f, minTop + s * 0.25f);
        canvas.drawRoundRect(tmpRect, s * 0.016f, s * 0.016f, fillPaint);
    }

    private void drawSmallDome(Canvas canvas, float cx, float baseY, float halfW, float height) {
        tmpPath.reset();
        tmpPath.moveTo(cx - halfW, baseY + height * 0.06f);
        tmpPath.cubicTo(cx - halfW * 1.05f, baseY - height * 0.65f,
                cx - halfW * 0.35f, baseY - height * 0.85f,
                cx, baseY - height);
        tmpPath.cubicTo(cx + halfW * 0.35f, baseY - height * 0.85f,
                cx + halfW * 1.05f, baseY - height * 0.65f,
                cx + halfW, baseY + height * 0.06f);
        tmpPath.close();
        canvas.drawPath(tmpPath, fillPaint);
    }

    private void drawClock(Canvas canvas, int w, int h) {
        float s = Math.min(w, h);
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float r = s * 0.40f;
        strokePaint.setColor(goldColor);
        strokePaint.setStrokeWidth(Math.max(1.5f, s * 0.13f));
        canvas.drawCircle(cx, cy, r, strokePaint);
        canvas.drawLine(cx, cy, cx, cy - r * 0.55f, strokePaint);
        canvas.drawLine(cx, cy, cx + r * 0.45f, cy + r * 0.22f, strokePaint);
    }

    private void drawSunrise(Canvas canvas, int w, int h, boolean highRays) {
        float s = Math.min(w, h);
        float cx = w * 0.5f;
        float horizonY = h * 0.78f;
        float r = s * (highRays ? 0.26f : 0.25f);
        float sunCy = horizonY - (highRays ? s * 0.04f : 0f);

        // Sun semicircle
        fillPaint.setColor(goldColor);
        tmpRect.set(cx - r, sunCy - r, cx + r, sunCy + r);
        canvas.drawArc(tmpRect, 180f, 180f, true, fillPaint);

        // Rays
        strokePaint.setColor(goldColor);
        strokePaint.setStrokeWidth(Math.max(1.3f, s * 0.075f));
        int count = highRays ? 9 : 7;
        float innerR = r * 1.32f;
        float outerR = r * (highRays ? 2.05f : 1.78f);
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(190.0 + (160.0 * i) / (count - 1));
            float x1 = cx + (float) Math.cos(angle) * innerR;
            float y1 = sunCy + (float) Math.sin(angle) * innerR;
            float x2 = cx + (float) Math.cos(angle) * outerR;
            float y2 = sunCy + (float) Math.sin(angle) * outerR;
            canvas.drawLine(x1, y1, x2, y2, strokePaint);
        }

        // Horizon bar
        strokePaint.setStrokeWidth(Math.max(1.4f, s * 0.085f));
        canvas.drawLine(cx - s * 0.52f, horizonY, cx + s * 0.52f, horizonY, strokePaint);
    }

    private void drawSun(Canvas canvas, int w, int h) {
        float s = Math.min(w, h);
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float r = s * 0.23f;

        fillPaint.setColor(goldColor);
        canvas.drawCircle(cx, cy, r, fillPaint);

        strokePaint.setColor(goldColor);
        strokePaint.setStrokeWidth(Math.max(1.3f, s * 0.075f));
        float innerR = r * 1.38f;
        float outerR = r * 1.98f;
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30.0);
            canvas.drawLine(
                    cx + (float) Math.cos(angle) * innerR,
                    cy + (float) Math.sin(angle) * innerR,
                    cx + (float) Math.cos(angle) * outerR,
                    cy + (float) Math.sin(angle) * outerR,
                    strokePaint
            );
        }
    }

    private void drawSunCloud(Canvas canvas, int w, int h) {
        float s = Math.min(w, h);
        float sunCx = w * 0.42f;
        float sunCy = h * 0.46f;
        float r = s * 0.22f;

        fillPaint.setColor(goldColor);
        canvas.drawCircle(sunCx, sunCy, r, fillPaint);

        strokePaint.setColor(goldColor);
        strokePaint.setStrokeWidth(Math.max(1.2f, s * 0.07f));
        float innerR = r * 1.35f;
        float outerR = r * 1.85f;
        for (int i = 0; i < 9; i++) {
            double angle = Math.toRadians(150.0 + i * 25.0);
            canvas.drawLine(
                    sunCx + (float) Math.cos(angle) * innerR,
                    sunCy + (float) Math.sin(angle) * innerR,
                    sunCx + (float) Math.cos(angle) * outerR,
                    sunCy + (float) Math.sin(angle) * outerR,
                    strokePaint
            );
        }

        // Crisp foreground cloud
        fillPaint.setColor(foregroundColor);
        float cBaseY = h * 0.78f;
        tmpRect.set(w * 0.38f, h * 0.56f, w * 0.88f, cBaseY);
        canvas.drawRoundRect(tmpRect, s * 0.14f, s * 0.14f, fillPaint);
        canvas.drawCircle(w * 0.56f, h * 0.56f, s * 0.16f, fillPaint);
        canvas.drawCircle(w * 0.74f, h * 0.62f, s * 0.12f, fillPaint);
    }

    private void drawSunset(Canvas canvas, int w, int h) {
        float s = Math.min(w, h);
        float cx = w * 0.5f;
        float horizonY = h * 0.62f;
        float r = s * 0.25f;

        fillPaint.setColor(goldColor);
        tmpRect.set(cx - r, horizonY - r, cx + r, horizonY + r);
        canvas.drawArc(tmpRect, 180f, 180f, true, fillPaint);

        strokePaint.setColor(goldColor);
        strokePaint.setStrokeWidth(Math.max(1.2f, s * 0.07f));
        float innerR = r * 1.32f;
        float outerR = r * 1.75f;
        for (int i = 0; i < 7; i++) {
            double angle = Math.toRadians(190.0 + (160.0 * i) / 6.0);
            canvas.drawLine(
                    cx + (float) Math.cos(angle) * innerR,
                    horizonY + (float) Math.sin(angle) * innerR,
                    cx + (float) Math.cos(angle) * outerR,
                    horizonY + (float) Math.sin(angle) * outerR,
                    strokePaint
            );
        }

        // Water reflection horizon bars
        strokePaint.setStrokeWidth(Math.max(1.4f, s * 0.08f));
        canvas.drawLine(cx - s * 0.52f, horizonY + s * 0.03f, cx + s * 0.52f, horizonY + s * 0.03f, strokePaint);
        strokePaint.setColor(goldWarmColor);
        canvas.drawLine(cx - s * 0.34f, horizonY + s * 0.19f, cx + s * 0.34f, horizonY + s * 0.19f, strokePaint);
    }

    private void drawMoonStars(Canvas canvas, int w, int h) {
        float s = Math.min(w, h);
        float cx = w * 0.45f;
        float cy = h * 0.52f;
        float r = s * 0.34f;

        // Crescent built by two circles via Path EVEN_ODD or two intersecting arcs
        tmpPath.reset();
        tmpPath.setFillType(Path.FillType.EVEN_ODD);
        tmpPath.addCircle(cx, cy, r, Path.Direction.CW);
        tmpPath.addCircle(cx + r * 0.42f, cy - r * 0.28f, r * 0.80f, Path.Direction.CW);
        fillPaint.setColor(foregroundColor);
        canvas.drawPath(tmpPath, fillPaint);

        // Two small 4-point stars
        drawSparkleStar(canvas, w * 0.66f, h * 0.30f, s * 0.11f);
        drawSparkleStar(canvas, w * 0.77f, h * 0.46f, s * 0.07f);
    }

    private void drawSparkleStar(Canvas canvas, float cx, float cy, float r) {
        tmpPath.reset();
        tmpPath.moveTo(cx, cy - r);
        tmpPath.lineTo(cx + r * 0.28f, cy - r * 0.28f);
        tmpPath.lineTo(cx + r, cy);
        tmpPath.lineTo(cx + r * 0.28f, cy + r * 0.28f);
        tmpPath.lineTo(cx, cy + r);
        tmpPath.lineTo(cx - r * 0.28f, cy + r * 0.28f);
        tmpPath.lineTo(cx - r, cy);
        tmpPath.lineTo(cx - r * 0.28f, cy - r * 0.28f);
        tmpPath.close();
        canvas.drawPath(tmpPath, fillPaint);
    }
}
