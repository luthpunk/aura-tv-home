package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/** Smooth hourly forecast curve that draws itself from left to right. */
public class HourlyChartView extends View {

    private int[] temps = new int[]{25, 25, 25, 27, 29, 29, 28, 25};
    private String unit = "°";
    private String[] labels = new String[]{"00:00", "03:00", "06:00", "09:00", "12:00", "15:00", "18:00", "21:00"};

    private float reveal = 0f;
    private ValueAnimator animator;

    private static final int ACCENT = 0xFF4FC3F7;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotOuter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotInner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tempText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hourText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint basePaint = new Paint();

    private final Path path = new Path();
    private final Path segment = new Path();
    private final PathMeasure pathMeasure = new PathMeasure();
    private float[] xs = new float[8];
    private float[] ys = new float[8];

    public HourlyChartView(Context context) {
        this(context, null);
    }

    public HourlyChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(3f));
        linePaint.setColor(ACCENT);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(10f));
        glowPaint.setColor(ACCENT);
        glowPaint.setAlpha(50);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);

        dotOuter.setColor(ACCENT);
        dotInner.setColor(Color.WHITE);

        tempText.setColor(Color.WHITE);
        tempText.setTextSize(sp(16f));
        tempText.setTextAlign(Paint.Align.CENTER);
        tempText.setFakeBoldText(true);

        hourText.setColor(0xB3FFFFFF);
        hourText.setTextSize(sp(13f));
        hourText.setTextAlign(Paint.Align.CENTER);

        basePaint.setColor(0x22FFFFFF);
        basePaint.setStrokeWidth(dp(1f));
    }

    public void setData(int[] values, String unit, String[] labels) {
        if (values != null) this.temps = values;
        if (unit != null) this.unit = unit;
        if (labels != null) this.labels = labels;
        invalidate();
    }

    public void animateIn(long delayMs) {
        if (animator != null) animator.cancel();
        reveal = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1400L);
        animator.setStartDelay(delayMs);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            reveal = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = temps != null ? temps.length : 0;
        if (n < 2 || getWidth() <= 0 || getHeight() <= 0) return;

        if (xs.length < n) {
            xs = new float[n];
            ys = new float[n];
        }

        float padX = dp(30f);
        float top = dp(34f);
        float bottom = getHeight() - dp(34f);
        float left = padX;
        float right = getWidth() - padX;

        int rawMin = temps[0], rawMax = temps[0];
        for (int i = 1; i < n; i++) {
            if (temps[i] < rawMin) rawMin = temps[i];
            if (temps[i] > rawMax) rawMax = temps[i];
        }
        int minT = "%".equals(unit) ? 0 : (rawMin - 1);
        int maxT = "%".equals(unit) ? (Math.max(30, rawMax) + 5) : (rawMax + 1);
        int range = Math.max(1, maxT - minT);

        for (int i = 0; i < n; i++) {
            xs[i] = left + i * (right - left) / (n - 1);
            ys[i] = bottom - (temps[i] - minT) / (float) range * (bottom - top);
        }

        path.reset();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            float midX = (xs[i - 1] + xs[i]) / 2f;
            path.cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i]);
        }

        canvas.drawLine(left, bottom + dp(4f), right, bottom + dp(4f), basePaint);

        pathMeasure.setPath(path, false);
        segment.reset();
        pathMeasure.getSegment(0f, pathMeasure.getLength() * reveal, segment, true);
        canvas.drawPath(segment, glowPaint);
        canvas.drawPath(segment, linePaint);

        float revealX = left + (right - left) * reveal;
        for (int i = 0; i < n; i++) {
            if (labels != null && i < labels.length) {
                canvas.drawText(labels[i], xs[i], getHeight() - dp(8f), hourText);
            }
            if (xs[i] <= revealX + 1f) {
                canvas.drawCircle(xs[i], ys[i], dp(6f), dotOuter);
                canvas.drawCircle(xs[i], ys[i], dp(3f), dotInner);
                canvas.drawText(temps[i] + unit, xs[i], ys[i] - dp(14f), tempText);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
