package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * Clean floating HUD toast for Android TV.
 * Prevents OS from appending the app icon alongside warning icons,
 * giving a sleek, unified, Apple TV-like translucent pill HUD.
 */
public final class TvToast {
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static View sCurrentToastView = null;
    private static Runnable sDismissRunnable = null;
    private static WeakReference<Activity> sTopActivity = null;

    private TvToast() {}

    public static void setCurrentActivity(Activity activity) {
        if (activity != null) {
            sTopActivity = new WeakReference<>(activity);
        }
    }

    public static void show(Context context, int resId) {
        if (context == null) return;
        show(context, context.getString(resId), false);
    }

    public static void show(Context context, int resId, boolean isLong) {
        if (context == null) return;
        show(context, context.getString(resId), isLong);
    }

    public static void show(Context context, CharSequence text) {
        show(context, text, false);
    }

    public static void show(Context context, CharSequence text, boolean isLong) {
        if (text == null || text.length() == 0) return;

        sHandler.post(() -> {
            Activity activity = findActivity(context);
            if (activity == null && sTopActivity != null) {
                activity = sTopActivity.get();
            }

            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                if (context != null) {
                    try {
                        Toast.makeText(context, text, isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
                }
                return;
            }

            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            if (decor == null) {
                if (context != null) {
                    try {
                        Toast.makeText(context, text, isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
                }
                return;
            }

            float density = activity.getResources().getDisplayMetrics().density;

            // Remove existing toast if visible
            if (sCurrentToastView != null) {
                if (sDismissRunnable != null) sHandler.removeCallbacks(sDismissRunnable);
                try {
                    decor.removeView(sCurrentToastView);
                } catch (Exception ignored) {}
                sCurrentToastView = null;
            }

            // Create Apple TV-style floating HUD pill
            TextView tv = new TextView(activity);
            tv.setText(text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            tv.setTextColor(0xFFFFFFFF);

            try {
                Typeface outfit = activity.getResources().getFont(R.font.outfit);
                tv.setTypeface(outfit, Typeface.BOLD);
                tv.setFontVariationSettings("'wght' 650");
            } catch (Exception ignored) {}

            tv.setGravity(Gravity.CENTER);
            tv.setIncludeFontPadding(false);

            int padH = Math.round(18 * density);
            int padV = Math.round(9 * density);
            tv.setPadding(padH, padV, padH, padV);

            // Sleek frosted glass / dark glow pill background
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xEE11141C); // Deep dark sleek translucent
            bg.setCornerRadius(22 * density);
            int strokeColor = ThemeManager.isDarkGlow(activity) ? 0x6600F0FF : 0x3DFFFFFF;
            bg.setStroke(Math.max(1, Math.round(1.0f * density)), strokeColor); // Delicate translucent rim
            tv.setBackground(bg);
            tv.setElevation(16 * density);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = Math.round(42 * density);

            tv.setLayoutParams(lp);
            tv.setAlpha(0f);
            tv.setTranslationY(16 * density);
            tv.setScaleX(0.95f);
            tv.setScaleY(0.95f);

            decor.addView(tv);
            sCurrentToastView = tv;

            // Smooth entrance animation
            tv.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                    .start();

            // Auto dismiss
            long durationMs = isLong ? 3200 : 2000;
            sDismissRunnable = () -> {
                if (sCurrentToastView == tv) {
                    tv.animate()
                            .alpha(0f)
                            .translationY(10 * density)
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .setDuration(160)
                            .withEndAction(() -> {
                                try {
                                    decor.removeView(tv);
                                } catch (Exception ignored) {}
                                if (sCurrentToastView == tv) sCurrentToastView = null;
                            })
                            .start();
                }
            };
            sHandler.postDelayed(sDismissRunnable, durationMs);
        });
    }

    private static Activity findActivity(Context context) {
        if (context == null) return null;
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
