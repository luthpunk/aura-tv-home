package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Random;

/**
 * Screensaver (DreamService): Theme-Aware StandBy Screensaver with large 110sp digits,
 * Gregorian date capsule, Islamic Hijri date, next prayer countdown capsule, and subtle
 * drift to prevent burn-in over long sessions.
 *
 * <p>Automatically adapts its visual style to the active Aura TV Home theme:
 * <ul>
 *   <li><b>Frosted Glass:</b> Translucent Apple TV glass plaque & capsule over a deep atmospheric backdrop (shifts to ruby glass in Wind Down mode 22:00–06:00).</li>
 *   <li><b>Neumorphism (Light):</b> Warm porcelain Soft UI canvas (#EDEAE4) with 3D extruded clock plaque & recessed date cavity.</li>
 *   <li><b>Neumorphism Dark:</b> Deep charcoal-navy Soft UI canvas (#171A22) with 3D extruded dark clock plaque & recessed date cavity.</li>
 *   <li><b>Dark Glow:</b> Pitch-black OLED (#000000) with cyber neon cyan (#00F0FF) glowing rim and digits.</li>
 * </ul>
 */
public class ClockDream extends DreamService {

    private static final String TAG = "TvHome.ClockDream";
    private static final long MOVE_MS = 60_000L;
    private static final long DREAM_SLEEP_TIMEOUT_MS = 2L * 60 * 60 * 1000L; // 2 hours

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View rootView;
    private View content;
    private long dreamStartTime = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (dreamStartTime > 0 && android.os.SystemClock.elapsedRealtime() - dreamStartTime >= DREAM_SLEEP_TIMEOUT_MS) {
                Log.i(TAG, "Screensaver ran for 2 hours: turning off TV display");
                HomeKeyService.sleepScreen();
                try {
                    Runtime.getRuntime().exec("input keyevent 223");
                } catch (Exception ignored) {
                }
                finish();
                return;
            }
            updateNightstand();
            moveClock();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // Any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.view_nightstand);

        rootView = findViewById(R.id.nightstandRoot);
        if (rootView != null) {
            rootView.setOnClickListener(v -> finish());
        }
        content = findViewById(R.id.nightstandContent);

        updateNightstand();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dreamStartTime = android.os.SystemClock.elapsedRealtime();
        if (content != null) {
            content.setAlpha(0f);
            content.post(() -> {
                updateNightstand();
                placeClock();
                content.animate().alpha(1f).setDuration(1000).start();
            });
        }
        handler.postDelayed(tick, MOVE_MS);
    }

    @Override
    public void onDreamingStopped() {
        dreamStartTime = 0;
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        dreamStartTime = 0;
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void updateNightstand() {
        if (rootView == null) return;

        TextView hijri = rootView.findViewById(R.id.nightstandHijri);
        if (hijri != null) {
            hijri.setText("🌙 " + PrayerTimes.getHijriDate());
        }

        TextView prayer = rootView.findViewById(R.id.nightstandPrayer);
        if (prayer != null) {
            prayer.setText("🕌 " + PrayerTimes.getNextPrayerSummary(this));
        }

        applyThemeStyle(this, rootView);
    }

    /**
     * Applies the active theme's visual identity (Frosted Glass, Neumorphism Light,
     * Neumorphism Dark, or Dark Glow) to a nightstand screensaver view hierarchy.
     */
    public static void applyThemeStyle(Context context, View root) {
        if (context == null || root == null) return;

        // Ensure ThemeManager & NeumorphicDrawable palette state is synced
        ThemeManager.getActiveTheme(context);
        boolean isNeu = ThemeManager.isNeumorphism(context);
        boolean isNeuDark = ThemeManager.isNeumorphismDark(context);
        boolean isDarkGlow = ThemeManager.isDarkGlow(context);

        float density = context.getResources().getDisplayMetrics().density;

        View clockCard = root.findViewById(R.id.nightstandClockCard);
        TextView clock = root.findViewById(R.id.nightstandClock);
        TextView ampm = root.findViewById(R.id.nightstandAmPm);
        TextView date = root.findViewById(R.id.nightstandDate);
        TextView hijri = root.findViewById(R.id.nightstandHijri);
        TextView prayer = root.findViewById(R.id.nightstandPrayer);
        TextView hint = root.findViewById(R.id.nightstandHint);

        if (isNeu) {
            // Neumorphism Light (#EDEAE4) or Neumorphism Dark (#171A22)
            root.setBackgroundColor(isNeuDark ? 0xFF171A22 : 0xFFEDEAE4);

            if (clockCard != null) {
                clockCard.setBackground(new NeumorphicDrawable(34 * density, density, NeumorphicDrawable.MODE_EXTRUDED, true));
            }
            if (clock != null) {
                clock.setTextColor(ThemeManager.neu(0xFF232830));
                clock.setShadowLayer(0, 0, 0, 0);
            }
            if (ampm != null) {
                ampm.setTextColor(ThemeManager.neu(0xFF4A5360));
                ampm.setShadowLayer(0, 0, 0, 0);
            }
            if (date != null) {
                date.setTextColor(ThemeManager.neu(0xFF363E4A));
                date.setShadowLayer(0, 0, 0, 0);
                date.setBackground(new NeumorphicDrawable(22 * density, density, NeumorphicDrawable.MODE_RECESSED, false));
            }
            if (hijri != null) {
                hijri.setTextColor(ThemeManager.neu(0xFF5A626E));
                hijri.setShadowLayer(0, 0, 0, 0);
            }
            if (prayer != null) {
                prayer.setTextColor(ThemeManager.neu(0xFF2B3036));
                prayer.setShadowLayer(0, 0, 0, 0);
                prayer.setBackground(new NeumorphicDrawable(21 * density, density, NeumorphicDrawable.MODE_EXTRUDED, false));
            }
            if (hint != null) {
                hint.setTextColor(ThemeManager.neu(0xFF7A828E));
            }
            return;
        }

        if (isDarkGlow) {
            // Dark Glow: Pure pitch-black OLED + vibrant cyber neon cyan glow
            root.setBackgroundColor(0xFF000000);

            if (clockCard != null) {
                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setShape(GradientDrawable.RECTANGLE);
                cardBg.setColor(0xF2060911);
                cardBg.setCornerRadius(34 * density);
                cardBg.setStroke(Math.max(1, Math.round(1.5f * density)), 0xFF00F0FF);
                clockCard.setBackground(cardBg);
            }
            if (clock != null) {
                clock.setTextColor(0xFFFFFFFF);
                clock.setShadowLayer(16 * density, 0, 0, 0xCC00F0FF);
            }
            if (ampm != null) {
                ampm.setTextColor(0xFF00F0FF);
                ampm.setShadowLayer(8 * density, 0, 0, 0x9900F0FF);
            }
            if (date != null) {
                date.setTextColor(0xFF00F0FF);
                date.setShadowLayer(6 * density, 0, 0, 0x6600F0FF);
                GradientDrawable dateBg = new GradientDrawable();
                dateBg.setShape(GradientDrawable.RECTANGLE);
                dateBg.setColor(0xE6080C16);
                dateBg.setCornerRadius(22 * density);
                dateBg.setStroke(Math.max(1, Math.round(1.2f * density)), 0x9900F0FF);
                date.setBackground(dateBg);
            }
            if (hijri != null) {
                hijri.setTextColor(0xCC9EAFC2);
                hijri.setShadowLayer(4 * density, 0, 1 * density, 0xCC000000);
            }
            if (prayer != null) {
                prayer.setTextColor(0xFFFFFFFF);
                prayer.setShadowLayer(4 * density, 0, 1 * density, 0xCC000000);
                GradientDrawable prayerBg = new GradientDrawable();
                prayerBg.setShape(GradientDrawable.RECTANGLE);
                prayerBg.setColor(0xE60A101D);
                prayerBg.setCornerRadius(21 * density);
                prayerBg.setStroke(Math.max(1, Math.round(1.0f * density)), 0x8000F0FF);
                prayer.setBackground(prayerBg);
            }
            if (hint != null) {
                hint.setTextColor(0x7300F0FF);
            }
            return;
        }

        // Default: Frosted Glass (Apple TV Glassmorphism with Wind Down mode between 22:00 and 06:00)
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        boolean isNightMode = (hour >= 22 || hour < 6);

        GradientDrawable rootBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                isNightMode
                        ? new int[]{0xF4120608, 0xF8080304, 0xFF000000}
                        : new int[]{0xF20D1526, 0xF6070B14, 0xFA030509}
        );
        root.setBackground(rootBg);

        if (isNightMode) {
            int red = 0xFFFF453A;
            if (clockCard != null) {
                GradientDrawable cardBg = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{0x24FF453A, 0x0EFF453A}
                );
                cardBg.setCornerRadius(34 * density);
                cardBg.setStroke(Math.max(1, Math.round(1.4f * density)), 0x59FF453A);
                clockCard.setBackground(cardBg);
            }
            if (clock != null) {
                clock.setTextColor(red);
                clock.setShadowLayer(10 * density, 0, 3 * density, 0x80000000);
            }
            if (ampm != null) ampm.setTextColor(0xCCFF453A);
            if (date != null) {
                date.setTextColor(0xE6FF453A);
                date.setShadowLayer(5 * density, 0, 2 * density, 0x80000000);
                GradientDrawable datePill = new GradientDrawable();
                datePill.setShape(GradientDrawable.RECTANGLE);
                datePill.setColor(0x1FFF453A);
                datePill.setStroke(Math.max(1, Math.round(1.0f * density)), 0x4DFF453A);
                datePill.setCornerRadius(22 * density);
                date.setBackground(datePill);
            }
            if (hijri != null) hijri.setTextColor(0xE6FF453A);
            if (prayer != null) {
                prayer.setTextColor(red);
                GradientDrawable redPill = new GradientDrawable();
                redPill.setShape(GradientDrawable.RECTANGLE);
                redPill.setColor(0x26FF453A);
                redPill.setStroke(Math.max(1, Math.round(1.0f * density)), 0x59FF453A);
                redPill.setCornerRadius(21 * density);
                prayer.setBackground(redPill);
            }
            if (hint != null) hint.setTextColor(0x66FF453A);
        } else {
            if (clockCard != null) {
                GradientDrawable cardBg = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{0x2EFFFFFF, 0x12FFFFFF}
                );
                cardBg.setCornerRadius(34 * density);
                cardBg.setStroke(Math.max(1, Math.round(1.5f * density)), 0x66FFFFFF);
                clockCard.setBackground(cardBg);
            }
            if (clock != null) {
                clock.setTextColor(0xFFFFFFFF);
                clock.setShadowLayer(10 * density, 0, 3 * density, 0x80000000);
            }
            if (ampm != null) ampm.setTextColor(0xB3FFFFFF);
            if (date != null) {
                date.setTextColor(0xF2FFFFFF);
                date.setShadowLayer(5 * density, 0, 2 * density, 0x80000000);
                GradientDrawable datePill = new GradientDrawable();
                datePill.setShape(GradientDrawable.RECTANGLE);
                datePill.setColor(0x22FFFFFF);
                datePill.setStroke(Math.max(1, Math.round(1.1f * density)), 0x4DFFFFFF);
                datePill.setCornerRadius(22 * density);
                date.setBackground(datePill);
            }
            if (hijri != null) hijri.setTextColor(0xD9E6E0D2);
            if (prayer != null) {
                prayer.setTextColor(0xFFFFFFFF);
                prayer.setBackgroundResource(R.drawable.pill_bg);
            }
            if (hint != null) hint.setTextColor(0x59FFFFFF);
        }
    }

    private void moveClock() {
        if (content == null) return;
        content.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(800).start();
        }).start();
    }

    /** Subtle drift to prevent OLED burn-in: nudged up to 1.5% around the center. */
    private void placeClock() {
        if (content == null) return;
        View parent = (View) content.getParent();
        if (parent == null) return;
        int pw = parent.getWidth();
        int ph = parent.getHeight();
        if (pw <= 0 || ph <= 0) return;

        float nudgeX = pw * 0.015f * (2f * random.nextFloat() - 1f);
        float nudgeY = ph * 0.015f * (2f * random.nextFloat() - 1f);
        content.setTranslationX(nudgeX);
        content.setTranslationY(nudgeY);
    }
}
