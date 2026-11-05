package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Animated 3-bar music equalizer wave for the Now Playing pill.
 * Oscillates smoothly when playing; rests static when paused.
 */
public class EqualizerWaveView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private ValueAnimator animator;
    private float animPhase = 0f;
    private boolean isPlaying = false;
    private int waveColor = 0xFF1DB954; // Spotify Green default

    public EqualizerWaveView(Context context) {
        super(context);
        init();
    }

    public EqualizerWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EqualizerWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(waveColor);

        animator = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            animPhase = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    public void setWaveColor(int color) {
        this.waveColor = color;
        paint.setColor(color);
        invalidate();
    }

    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;
        this.isPlaying = playing;
        if (playing) {
            if (!animator.isRunning()) {
                animator.start();
            }
        } else {
            if (animator.isRunning()) {
                animator.cancel();
            }
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isPlaying && !animator.isRunning()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator.isRunning()) {
            animator.cancel();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int numBars = 3;
        float barWidth = w / 5f;
        float spacing = barWidth * 0.7f;
        float totalBarsWidth = (numBars * barWidth) + ((numBars - 1) * spacing);
        float startX = (w - totalBarsWidth) / 2f;
        float cornerRadius = barWidth / 2f;

        float[] staticRatios = new float[]{0.35f, 0.75f, 0.45f};

        for (int i = 0; i < numBars; i++) {
            float ratio;
            if (isPlaying) {
                // Harmonic sine waves with phase offsets for fluid jumping motion
                float phaseOffset = i * 1.3f;
                float sinVal = (float) Math.sin(animPhase * 1.8f + phaseOffset);
                ratio = 0.25f + 0.70f * (0.5f + 0.5f * sinVal);
            } else {
                ratio = staticRatios[i % staticRatios.length];
            }

            float barH = Math.max(barWidth, h * ratio);
            float left = startX + i * (barWidth + spacing);
            float top = h - barH;
            float right = left + barWidth;
            float bottom = h;

            rect.set(left, top, right, bottom);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        }
    }
}
