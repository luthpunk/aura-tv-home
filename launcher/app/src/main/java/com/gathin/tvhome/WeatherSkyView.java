package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Animated sky background:
 * STORM -> dark storm clouds, slanted rain, lightning
 * SUNNY -> rich deep blue sky, soft clouds, warm sun glow (high contrast for white text)
 * NIGHT -> deep midnight sky, twinkling stars, soft moon glow, dark indigo clouds
 */
public class WeatherSkyView extends View {

    public enum Condition {STORM, SUNNY, NIGHT}

    private Condition currentCondition = Condition.STORM;

    private float blend = 0f;       // 0 = storm, 1 = clear
    private float nightBlend = 0f;  // 0 = day, 1 = night
    private float lastBlendShader = -1f;
    private float lastNightShader = -1f;
    private int lastShaderH = -1;
    private int lastColorFilterBlend = 0;
    private boolean hasColorFilter = false;
    private ValueAnimator blendAnimator;
    private float time = 0f;
    private long lastFrameNs = 0L;
    private float flash = 0f;

    private static final int STORM_TOP = 0xFF141A24;
    private static final int STORM_BOTTOM = 0xFF333C4A;
    private static final int SUNNY_TOP = 0xFF1F427A;
    private static final int SUNNY_BOTTOM = 0xFF4774B2;
    private static final int NIGHT_TOP = 0xFF090F20;
    private static final int NIGHT_BOTTOM = 0xFF1A2947;

    private static final int STORM_CLOUD = 0xFF282D36;
    private static final int SUNNY_CLOUD = 0xFFDDE9FA;
    private static final int NIGHT_CLOUD = 0xFF212D44;

    private final Paint skyPaint = new Paint();
    private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint();
    private final Paint scrimPaint = new Paint();

    private final Bitmap puff;
    private final Random random = new Random();

    private static final class Puff {
        final float dx, dy, r;

        Puff(float dx, float dy, float r) {
            this.dx = dx;
            this.dy = dy;
            this.r = r;
        }
    }

    private static final class Cloud {
        float x;
        final float y, size, speed, alpha;
        final List<Puff> puffs;

        Cloud(float x, float y, float size, float speed, float alpha, List<Puff> puffs) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.speed = speed;
            this.alpha = alpha;
            this.puffs = puffs;
        }
    }

    private static final class Drop {
        float x, y;
        final float len, speed;

        Drop(float x, float y, float len, float speed) {
            this.x = x;
            this.y = y;
            this.len = len;
            this.speed = speed;
        }
    }

    private final List<Cloud> clouds = new ArrayList<>();
    private final List<Drop> drops = new ArrayList<>(160);
    private final float[][] stars = new float[28][3]; // x, y, phase
    private final RectF rect = new RectF();

    public WeatherSkyView(Context context) {
        this(context, null);
    }

    public WeatherSkyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        rainPaint.setColor(Color.WHITE);
        rainPaint.setStrokeWidth(2f);
        rainPaint.setStrokeCap(Paint.Cap.ROUND);
        flashPaint.setColor(Color.WHITE);
        starPaint.setColor(0xFFEAF3FF);

        for (int i = 0; i < stars.length; i++) {
            stars[i][0] = random.nextFloat();
            stars[i][1] = random.nextFloat() * 0.58f;
            stars[i][2] = random.nextFloat() * 6.28f;
        }

        puff = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(puff);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(
                128f, 128f, 128f,
                new int[]{0xFFFFFFFF, 0xAAFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(128f, 128f, 128f, p);

        for (int i = 0; i < 6; i++) {
            clouds.add(makeCloud(random.nextFloat() * 1.4f - 0.2f, 0.08f + random.nextFloat() * 0.3f, 0.25f, 0.006f, 0.45f));
        }
        clouds.add(makeCloud(0.55f, 0.38f, 0.55f, 0.004f, 0.95f));
        for (int i = 0; i < 4; i++) {
            clouds.add(makeCloud(random.nextFloat() * 1.4f - 0.2f, 0.65f + random.nextFloat() * 0.25f, 0.45f, 0.012f, 0.6f));
        }

        for (int i = 0; i < 160; i++) {
            drops.add(new Drop(random.nextFloat(), random.nextFloat(),
                    18f + random.nextFloat() * 22f, 0.9f + random.nextFloat() * 0.8f));
        }
    }

    public Condition getCurrentCondition() {
        return currentCondition;
    }

    private Cloud makeCloud(float x, float y, float size, float speed, float alpha) {
        List<Puff> puffs = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            puffs.add(new Puff(
                    (random.nextFloat() - 0.5f) * 1.6f,
                    (random.nextFloat() - 0.5f) * 0.5f,
                    0.35f + random.nextFloat() * 0.35f));
        }
        return new Cloud(x, y, size * (0.8f + random.nextFloat() * 0.4f),
                speed * (0.7f + random.nextFloat() * 0.6f), alpha, puffs);
    }

    public void setCondition(Condition condition, boolean animate) {
        setCondition(condition, animate, 2500L);
    }

    public void setCondition(Condition condition, boolean animate, long durationMs) {
        currentCondition = condition;
        final float startBlend = blend;
        final float startNight = nightBlend;
        final float targetBlend = (condition == Condition.STORM) ? 0f : 1f;
        final float targetNight = (condition == Condition.NIGHT) ? 1f : 0f;
        if (blendAnimator != null) blendAnimator.cancel();
        if (!animate) {
            blend = targetBlend;
            nightBlend = targetNight;
            invalidate();
            return;
        }
        blendAnimator = ValueAnimator.ofFloat(0f, 1f);
        blendAnimator.setDuration(durationMs);
        blendAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        blendAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            blend = startBlend + (targetBlend - startBlend) * t;
            nightBlend = startNight + (targetNight - startNight) * t;
        });
        blendAnimator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        sunPaint.setShader(new RadialGradient(
                w * 0.68f, h * 0.24f, h * 0.48f,
                new int[]{0xAAFFF0C0, 0x48FFE08A, 0x00FFE08A},
                new float[]{0f, 0.35f, 1f},
                Shader.TileMode.CLAMP));
        moonPaint.setShader(new RadialGradient(
                w * 0.72f, h * 0.22f, h * 0.34f,
                new int[]{0x88E2EEFF, 0x33BBD6FF, 0x00BBD6FF},
                new float[]{0f, 0.38f, 1f},
                Shader.TileMode.CLAMP));
        // Soft full-height readability scrim keeping hero text and glass cards crisp in every weather
        scrimPaint.setShader(new LinearGradient(
                0f, 0f, 0f, h,
                new int[]{0x80000000, 0x3E000000, 0x36000000, 0x92000000},
                new float[]{0f, 0.32f, 0.58f, 1f},
                Shader.TileMode.CLAMP));
        lastShaderH = -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;

        long now = System.nanoTime();
        float dt = (lastFrameNs == 0L) ? 0f : Math.min(0.05f, (now - lastFrameNs) / 1_000_000_000f);
        lastFrameNs = now;
        time += dt;

        // Rebuild sky gradient only when blend, nightBlend, or view height changes
        int hInt = (int) h;
        if (Math.abs(blend - lastBlendShader) > 0.002f
                || Math.abs(nightBlend - lastNightShader) > 0.002f
                || lastShaderH != hInt) {
            int clearTop = lerpColor(SUNNY_TOP, NIGHT_TOP, nightBlend);
            int clearBottom = lerpColor(SUNNY_BOTTOM, NIGHT_BOTTOM, nightBlend);
            skyPaint.setShader(new LinearGradient(
                    0f, 0f, 0f, h,
                    lerpColor(STORM_TOP, clearTop, blend),
                    lerpColor(STORM_BOTTOM, clearBottom, blend),
                    Shader.TileMode.CLAMP));
            lastBlendShader = blend;
            lastNightShader = nightBlend;
            lastShaderH = hInt;
        }
        canvas.drawRect(0f, 0f, w, h, skyPaint);

        float sunStrength = blend * (1f - nightBlend);
        if (sunStrength > 0.01f) {
            int alpha = Math.max(0, Math.min(255, Math.round(255f * sunStrength * (0.85f + 0.15f * (float) Math.sin(time * 1.2f)))));
            sunPaint.setAlpha(alpha);
            canvas.drawRect(0f, 0f, w, h, sunPaint);
        }

        float nightStrength = blend * nightBlend;
        if (nightStrength > 0.01f) {
            for (int i = 0; i < stars.length; i++) {
                float tw = 0.45f + 0.55f * (float) Math.sin(time * 1.6f + stars[i][2]);
                starPaint.setAlpha(Math.max(0, Math.min(255, Math.round(190f * nightStrength * tw))));
                canvas.drawCircle(stars[i][0] * w, stars[i][1] * h, 1.8f, starPaint);
            }
            moonPaint.setAlpha(Math.max(0, Math.min(255, Math.round(255f * nightStrength))));
            canvas.drawRect(0f, 0f, w, h, moonPaint);
        }

        int clearCloud = lerpColor(SUNNY_CLOUD, NIGHT_CLOUD, nightBlend);
        int cloudTint = lerpColor(STORM_CLOUD, clearCloud, blend);
        if (!hasColorFilter || cloudTint != lastColorFilterBlend) {
            cloudPaint.setColorFilter(new PorterDuffColorFilter(cloudTint, PorterDuff.Mode.SRC_IN));
            lastColorFilterBlend = cloudTint;
            hasColorFilter = true;
        }
        float cloudDensity = 1f - 0.48f * blend;
        for (int ci = 0; ci < clouds.size(); ci++) {
            Cloud c = clouds.get(ci);
            c.x += c.speed * dt * (1.6f - blend);
            if (c.x - c.size > 1.4f) c.x = -0.6f;
            float cx = c.x * w;
            float cy = c.y * h + (float) Math.sin(time * 0.3f + c.y * 10f) * h * 0.006f;
            float s = c.size * h;
            cloudPaint.setAlpha(Math.max(0, Math.min(255, Math.round(255f * c.alpha * cloudDensity))));
            for (int pi = 0; pi < c.puffs.size(); pi++) {
                Puff p = c.puffs.get(pi);
                float px = cx + p.dx * s;
                float py = cy + p.dy * s;
                float r = p.r * s;
                rect.set(px - r, py - r, px + r, py + r);
                canvas.drawBitmap(puff, null, rect, cloudPaint);
            }
        }

        float rainAlpha = 1f - blend;
        if (rainAlpha > 0.02f) {
            rainPaint.setAlpha(Math.round(110f * rainAlpha));
            for (int i = 0; i < drops.size(); i++) {
                Drop d = drops.get(i);
                d.y += d.speed * dt;
                d.x += 0.12f * d.speed * dt;
                if (d.y > 1.05f) {
                    d.y = -0.05f;
                    d.x = random.nextFloat();
                }
                if (d.x > 1.05f) d.x -= 1.1f;
                float x = d.x * w;
                float y = d.y * h;
                canvas.drawLine(x, y, x - d.len * 0.25f, y - d.len, rainPaint);
            }
        }

        if (blend < 0.3f && random.nextFloat() < dt * 0.18f) flash = 1f;
        if (flash > 0f) {
            flashPaint.setAlpha(Math.max(0, Math.min(255, Math.round(90f * flash * (1f - blend)))));
            canvas.drawRect(0f, 0f, w, h, flashPaint);
            flash -= dt * 3.5f;
        }

        canvas.drawRect(0f, 0f, w, h, scrimPaint);

        if (isAttachedToWindow() && getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrameNs = 0L;
        if (getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (blendAnimator != null) blendAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private static int lerpColor(int a, int b, float t) {
        float inv = 1f - t;
        return Color.argb(
                Math.round(Color.alpha(a) * inv + Color.alpha(b) * t),
                Math.round(Color.red(a) * inv + Color.red(b) * t),
                Math.round(Color.green(a) * inv + Color.green(b) * t),
                Math.round(Color.blue(a) * inv + Color.blue(b) * t));
    }
}
