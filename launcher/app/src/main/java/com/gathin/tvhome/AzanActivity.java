package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The azan, as a floating glass card at the top-center of the screen over whatever was on (the app
 * or Home stays in view behind it, blurred through the card where the TV can blur). Video and music
 * pause because the azan takes the sound (audio focus). When it ends, or on Back, it closes and
 * the app underneath is where it was.
 */
public class AzanActivity extends Activity {
    private static final class PhraseEntry {
        final String arabic;
        final String meaning;
        final float progressThreshold;
        final float textSizeSp;

        PhraseEntry(String arabic, String meaning, float progressThreshold, float textSizeSp) {
            this.arabic = arabic;
            this.meaning = meaning;
            this.progressThreshold = progressThreshold;
            this.textSizeSp = textSizeSp;
        }
    }

    private static final PhraseEntry[] STANDARD_PHRASES = {
            new PhraseEntry("ٱللَّهُ أَكْبَرُ ٱللَّهُ أَكْبَرُ", "Allah Maha Besar, Allah Maha Besar", 0.00f, 29f),
            new PhraseEntry("أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا ٱللَّهُ", "Aku bersaksi tiada Tuhan selain Allah", 0.16f, 24f),
            new PhraseEntry("أَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ ٱللَّهِ", "Aku bersaksi Nabi Muhammad utusan Allah", 0.33f, 23f),
            new PhraseEntry("حَيَّ عَلَى ٱلصَّلَاةِ", "Mari mendirikan sholat", 0.50f, 29f),
            new PhraseEntry("حَيَّ عَلَى ٱلْفَلَاحِ", "Mari menuju kemenangan", 0.67f, 29f),
            new PhraseEntry("ٱللَّهُ أَكْبَرُ ٱللَّهُ أَكْبَرُ", "Allah Maha Besar, Allah Maha Besar", 0.82f, 29f),
            new PhraseEntry("لَا إِلَٰهَ إِلَّا ٱللَّهُ", "Tiada Tuhan selain Allah", 0.91f, 30f)
    };

    private static final PhraseEntry[] SUBUH_PHRASES = {
            new PhraseEntry("ٱللَّهُ أَكْبَرُ ٱللَّهُ أَكْبَرُ", "Allah Maha Besar, Allah Maha Besar", 0.00f, 29f),
            new PhraseEntry("أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا ٱللَّهُ", "Aku bersaksi tiada Tuhan selain Allah", 0.14f, 24f),
            new PhraseEntry("أَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ ٱللَّهِ", "Aku bersaksi Nabi Muhammad utusan Allah", 0.29f, 23f),
            new PhraseEntry("حَيَّ عَلَى ٱلصَّلَاةِ", "Mari mendirikan sholat", 0.44f, 29f),
            new PhraseEntry("حَيَّ عَلَى ٱلْفَلَاحِ", "Mari menuju kemenangan", 0.59f, 29f),
            new PhraseEntry("ٱلصَّلَاةُ خَيْرٌ مِنَ ٱلنَّوْمِ", "Sholat lebih baik daripada tidur", 0.72f, 25f),
            new PhraseEntry("ٱللَّهُ أَكْبَرُ ٱللَّهُ أَكْبَرُ", "Allah Maha Besar, Allah Maha Besar", 0.85f, 29f),
            new PhraseEntry("لَا إِلَٰهَ إِلَّا ٱللَّهُ", "Tiada Tuhan selain Allah", 0.93f, 30f)
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private float density;
    private LinearLayout phraseContainer;
    private TextView takbir;
    private TextView translationView;
    private TextView headlinePrayerView;
    private TextView headlineLocationView;
    private TextView timeView;
    private TextView hint;
    private String locationName = "";
    private PhraseEntry[] activePhrases = STANDARD_PHRASES;
    private int currentPhraseIdx = 0;
    private int audioStartOffsetMs = 0;
    /** True once the card has been on screen: until then a stop is the screensaver going, not the owner. */
    private boolean shown;

    private final Runnable audioPhraseSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || player == null) return;
            try {
                if (player.isPlaying()) {
                    int dur = player.getDuration();
                    int pos = player.getCurrentPosition();
                    if (dur > audioStartOffsetMs + 500) {
                        int effectiveDur = dur - audioStartOffsetMs;
                        int effectivePos = Math.max(0, pos - audioStartOffsetMs);
                        float ratio = Math.min(1f, effectivePos / (float) effectiveDur);
                        int targetIdx = 0;
                        for (int i = 0; i < activePhrases.length; i++) {
                            if (ratio >= activePhrases[i].progressThreshold) {
                                targetIdx = i;
                            }
                        }
                        if (targetIdx != currentPhraseIdx) {
                            showPhrase(targetIdx);
                        }
                    }
                }
            } catch (IllegalStateException ignored) {
            }
            handler.postDelayed(this, 250);
        }
    };

    private final Runnable fallbackCycleRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) return;
            int nextIdx = (currentPhraseIdx + 1) % activePhrases.length;
            showPhrase(nextIdx);
            handler.postDelayed(this, 2800);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Over the screensaver too: turn the screen on and come in front of it.
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        density = getResources().getDisplayMetrics().density;
        int prayer = getIntent().getIntExtra("prayer", 4);
        Typeface outfit = getResources().getFont(R.font.outfit);
        Typeface amiri = getResources().getFont(R.font.amiri_quran);

        // Ensure ThemeManager palette is initialized before reading theme colors
        ThemeManager.getActiveTheme(this);
        boolean isNeu = ThemeManager.isNeumorphism(this);
        boolean isNeuDark = ThemeManager.isNeumorphismDark(this);
        boolean isDarkGlow = ThemeManager.isDarkGlow(this);

        // Top-center floating card matching active theme over whatever app is open.
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.y = dp(20);
        w.setAttributes(lp);
        boolean blur = false;
        if (!isNeu && !isDarkGlow && Build.VERSION.SDK_INT >= 31) {
            WindowManager wm = getSystemService(WindowManager.class);
            if (wm != null && wm.isCrossWindowBlurEnabled()) {
                w.setBackgroundBlurRadius(dp(30));
                blur = true;
            }
        }

        int goldColor = (isNeu && !isNeuDark) ? 0xFFD97706 : 0xFFF5B935;
        int headlineAccentColor = isDarkGlow ? 0xFF00F0FF : goldColor;
        int primaryTextColor = isNeu ? ThemeManager.neu(0xFF2B3036) : 0xFFFFFFFF;
        int topLabelColor = isNeu ? ThemeManager.neu(0xFF5A626E) : (isDarkGlow ? 0xB300F0FF : 0xB8FFFFFF);
        int meaningTextColor = isNeu ? ThemeManager.neu(0xFF4A525D) : 0xD9FFFFFF;
        int hintTextColor = isNeu ? ThemeManager.neu(0xFF6B7280) : 0x8CFFFFFF;
        int timeBadgeBgColor = isNeu ? ThemeManager.neu(0x16000000) : (isDarkGlow ? 0x2200F0FF : 0x52000000);
        int timeBadgeTextColor = isNeu ? ThemeManager.neu(0xFF2B3036) : 0xFFFFFFFF;
        int clockIconColor = isDarkGlow ? 0xFF00F0FF : goldColor;

        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setClipChildren(false);
        wrapper.setClipToPadding(false);
        int outerPad = dp(isNeu ? 18 : 10);
        wrapper.setPadding(outerPad, outerPad, outerPad, outerPad);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int fixedCardWidth = Math.min(dp(440), screenW - outerPad * 2 - dp(32));
        // Lock window width to compact card size so it fits snugly with no excess empty space.
        w.setLayout(fixedCardWidth + outerPad * 2, WindowManager.LayoutParams.WRAP_CONTENT);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setPadding(dp(22), dp(14), dp(22), dp(14));
        if (isNeu) {
            card.setBackground(new NeumorphicDrawable(dp(24), density, NeumorphicDrawable.MODE_EXTRUDED, true));
            card.setElevation(0f);
        } else if (isDarkGlow) {
            GradientDrawable dgBg = new GradientDrawable();
            dgBg.setColor(0xF008090D);
            dgBg.setCornerRadius(dp(24));
            dgBg.setStroke(Math.max(1, dp(1.5f)), 0xFF00F0FF);
            card.setBackground(dgBg);
            card.setClipToOutline(true);
            card.setElevation(dp(16));
        } else {
            GradientDrawable glass = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    blur ? new int[]{0xB81E2430, 0xB8131822} : new int[]{0xEB222834, 0xEB151A24});
            glass.setCornerRadius(dp(24));
            glass.setStroke(Math.max(1, dp(1.2f)), 0x52FFFFFF);
            card.setBackground(glass);
            card.setClipToOutline(true);
            card.setElevation(dp(16));
        }

        String town = AppLocationManager.getLocationName(this);
        if (town == null || town.isEmpty()) {
            town = Location.prefs(this).getString("now_town", "");
        }
        locationName = town == null ? "" : town.trim();

        // 1. Top row: centered "PENGINGAT ADZAN" + top-right "[clock HH.mm]" pill
        FrameLayout topHeader = new FrameLayout(this);

        TextView badgeLabel = text("PENGINGAT ADZAN", outfit, 11.5f, topLabelColor, 600);
        badgeLabel.setLetterSpacing(0.14f);
        badgeLabel.setSingleLine(true);
        topHeader.addView(badgeLabel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        LinearLayout timePill = new LinearLayout(this);
        timePill.setOrientation(LinearLayout.HORIZONTAL);
        timePill.setGravity(Gravity.CENTER_VERTICAL);
        timePill.setPadding(dp(9), dp(3), dp(10), dp(3));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(timeBadgeBgColor);
        pillBg.setCornerRadius(dp(100));
        if (isDarkGlow) {
            pillBg.setStroke(Math.max(1, dp(0.8f)), 0x6600F0FF);
        }
        timePill.setBackground(pillBg);

        ImageView clockIcon = new ImageView(this);
        clockIcon.setImageDrawable(createClockDrawable(clockIconColor));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(13), dp(13));
        iconLp.setMarginEnd(dp(5));
        timePill.addView(clockIcon, iconLp);

        String when = new SimpleDateFormat("HH.mm", Locale.getDefault()).format(new Date());
        timeView = text(when, outfit, 12.5f, timeBadgeTextColor, 600);
        timeView.setSingleLine(true);
        timePill.addView(timeView);

        topHeader.addView(timePill, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));

        card.addView(topHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 2. Two-line Announcement: "Saatnya Adzan <Sholat>" + "di <Wilayah> dan sekitarnya"
        String prayerName = PrayerTimes.NAMES[Math.max(0, Math.min(prayer, PrayerTimes.NAMES.length - 1))];
        headlinePrayerView = text("Saatnya Adzan " + prayerName, outfit, 21f, headlineAccentColor, 600);
        headlinePrayerView.setSingleLine(true);
        headlinePrayerView.setEllipsize(TextUtils.TruncateAt.END);
        if (!isNeu) headlinePrayerView.setShadowLayer(4 * density, 0, 1 * density, 0x80000000);
        LinearLayout.LayoutParams hpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hpLp.topMargin = dp(8);
        card.addView(headlinePrayerView, hpLp);

        headlineLocationView = text(formatLocationLine(locationName), outfit, 18.5f, primaryTextColor, 600);
        headlineLocationView.setSingleLine(true);
        headlineLocationView.setEllipsize(TextUtils.TruncateAt.END);
        if (!isNeu) headlineLocationView.setShadowLayer(4 * density, 0, 1 * density, 0x80000000);
        if (locationName.isEmpty()) headlineLocationView.setVisibility(View.GONE);
        LinearLayout.LayoutParams hlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlLp.topMargin = dp(1);
        card.addView(headlineLocationView, hlLp);

        // 3. Dynamic Arabic + Indonesian Translation Container (unclipped, WRAP_CONTENT height)
        activePhrases = (prayer == 0) ? SUBUH_PHRASES : STANDARD_PHRASES;
        currentPhraseIdx = 0;

        phraseContainer = new LinearLayout(this);
        phraseContainer.setOrientation(LinearLayout.VERTICAL);
        phraseContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        phraseContainer.setClipChildren(false);
        phraseContainer.setClipToPadding(false);

        takbir = text(activePhrases[0].arabic, amiri, activePhrases[0].textSizeSp, goldColor, 400);
        takbir.setSingleLine(false);
        takbir.setMaxLines(2);
        takbir.setPadding(0, dp(2), 0, dp(2));
        if (!isNeu) takbir.setShadowLayer(4 * density, 0, 1 * density, 0x70000000);
        phraseContainer.addView(takbir, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        translationView = text(activePhrases[0].meaning, outfit, 13.5f, meaningTextColor, 400);
        translationView.setSingleLine(true);
        translationView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        trLp.topMargin = dp(2);
        phraseContainer.addView(translationView, trLp);

        applyPhrase(activePhrases[0]);

        LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pcLp.topMargin = dp(10);
        card.addView(phraseContainer, pcLp);

        // 4. Compact footer hint
        hint = text("Tekan Back / OK untuk menutup", outfit, 11.5f, hintTextColor, 400);
        hint.setSingleLine(true);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(10);
        card.addView(hint, hintLp);

        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(v -> finish());
        wrapper.addView(card, new FrameLayout.LayoutParams(
                fixedCardWidth, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        setContentView(wrapper);

        String soundChoice = Azan.getSoundChoice(this);
        if (Azan.SOUND_SILENT.equals(soundChoice)) {
            hint.setText("Mode Senyap  •  Tekan Back / OK untuk menutup");
            handler.postDelayed(fallbackCycleRunnable, 2800);
            handler.postDelayed(this::finish, 15_000);
        } else if (Azan.SOUND_CHIME.equals(soundChoice)) {
            hint.setText("Nada Pengingat  •  Tekan Back / OK untuk menutup");
            handler.postDelayed(fallbackCycleRunnable, 2800);
            playChime();
        } else {
            File file = Azan.recording(this, soundChoice);
            if (file == null) {
                hint.setText("Mengunduh audio " + Azan.soundLabel(soundChoice) + "...");
                Azan.fetch(this, soundChoice);
                handler.postDelayed(fallbackCycleRunnable, 2800);
                playChime();
                handler.postDelayed(this::finish, 8000);
                return;
            }
            play(file, soundChoice);
        }
    }

    private String formatLocationLine(String town) {
        if (town == null || town.isEmpty()) return "";
        return "di " + town + " dan sekitarnya";
    }

    private Drawable createClockDrawable(final int color) {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas canvas) {
                float w = getBounds().width();
                float h = getBounds().height();
                float cx = w * 0.5f;
                float cy = h * 0.5f;
                float r = Math.min(w, h) * 0.42f;
                p.setColor(color);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(Math.max(1.5f, w * 0.11f));
                canvas.drawCircle(cx, cy, r, p);
                canvas.drawLine(cx, cy - r * 0.52f, cx, cy, p);
                canvas.drawLine(cx, cy, cx + r * 0.42f, cy + r * 0.25f, p);
            }

            @Override
            public void setAlpha(int alpha) {
                p.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
                p.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int prayer = intent.getIntExtra("prayer", 4);
        activePhrases = (prayer == 0) ? SUBUH_PHRASES : STANDARD_PHRASES;
        currentPhraseIdx = 0;
        String prayerName = PrayerTimes.NAMES[Math.max(0, Math.min(prayer, PrayerTimes.NAMES.length - 1))];
        if (headlinePrayerView != null) {
            headlinePrayerView.setText("Saatnya Adzan " + prayerName);
        }
        if (timeView != null) {
            timeView.setText(new SimpleDateFormat("HH.mm", Locale.getDefault()).format(new Date()));
        }
        applyPhrase(activePhrases[0]);
    }

    private void showPhrase(int index) {
        if (index < 0 || index >= activePhrases.length) return;
        currentPhraseIdx = index;
        final PhraseEntry entry = activePhrases[index];
        if (phraseContainer == null) return;
        phraseContainer.animate().cancel();
        phraseContainer.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction(() -> {
                    if (isFinishing() || isDestroyed() || phraseContainer == null) return;
                    applyPhrase(entry);
                    phraseContainer.animate().alpha(1f).setDuration(260).start();
                })
                .start();
    }

    private void applyPhrase(PhraseEntry entry) {
        if (takbir != null) {
            takbir.setText(entry.arabic);
            takbir.setTextSize(TypedValue.COMPLEX_UNIT_SP, entry.textSizeSp);
        }
        if (translationView != null) {
            translationView.setText(entry.meaning);
        }
    }

    private void playChime() {
        audio = getSystemService(AudioManager.class);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        audio.requestAudioFocus(focus);
        try {
            player = MediaPlayer.create(this, R.raw.ps5_select);
            if (player != null) {
                player.setOnCompletionListener(mp -> handler.postDelayed(this::finish, 5000));
                player.start();
            } else {
                handler.postDelayed(this::finish, 8000);
            }
        } catch (Exception e) {
            handler.postDelayed(this::finish, 8000);
        }
    }

    private void play(File file, String soundChoice) {
        audio = getSystemService(AudioManager.class);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        // Transient focus: Spotify and video players pause now and carry on when the azan is over.
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        audio.requestAudioFocus(focus);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(file.getPath());
            player.setOnCompletionListener(mp -> finish());
            player.setOnErrorListener((mp, what, extra) -> {
                finish();
                return true;
            });
            player.prepare();
            audioStartOffsetMs = 0;
            if (Azan.SOUND_KLCC.equals(soundChoice) && file.getName().contains("klcc")) {
                audioStartOffsetMs = Azan.KLCC_START_MS;
                player.seekTo(audioStartOffsetMs);
            }
            player.start();
            handler.post(audioPhraseSyncRunnable);
        } catch (Exception e) {
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        shown = true;
    }

    /**
     * Left in any way (Home, another app): the azan stops with it. Not before it has been shown, though:
     * started while the screensaver was ending, it is stopped once on the way in, and must keep playing.
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (shown && !isChangingConfigurations()) finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
    }

    private TextView text(String s, Typeface font, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(font);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (font != null) t.setFontVariationSettings("'wght' " + weight);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
