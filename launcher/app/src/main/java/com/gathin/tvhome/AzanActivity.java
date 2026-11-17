package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
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
        final float progressThreshold;

        PhraseEntry(String arabic, float progressThreshold) {
            this.arabic = arabic;
            this.progressThreshold = progressThreshold;
        }
    }

    private static final PhraseEntry[] STANDARD_PHRASES = {
            new PhraseEntry("ٱللَّهُ أَكْبَرُ", 0.00f),
            new PhraseEntry("أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا ٱللَّهُ", 0.16f),
            new PhraseEntry("أَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ ٱللَّهِ", 0.33f),
            new PhraseEntry("حَيَّ عَلَى ٱلصَّلَاةِ", 0.50f),
            new PhraseEntry("حَيَّ عَلَى ٱلْفَلَاحِ", 0.67f),
            new PhraseEntry("ٱللَّهُ أَكْبَرُ", 0.82f),
            new PhraseEntry("لَا إِلَٰهَ إِلَّا ٱللَّهُ", 0.91f)
    };

    private static final PhraseEntry[] SUBUH_PHRASES = {
            new PhraseEntry("ٱللَّهُ أَكْبَرُ", 0.00f),
            new PhraseEntry("أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا ٱللَّهُ", 0.14f),
            new PhraseEntry("أَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ ٱللَّهِ", 0.29f),
            new PhraseEntry("حَيَّ عَلَى ٱلصَّلَاةِ", 0.44f),
            new PhraseEntry("حَيَّ عَلَى ٱلْفَلَاحِ", 0.59f),
            new PhraseEntry("ٱلصَّلَاةُ خَيْرٌ مِنَ ٱلنَّوْمِ", 0.72f),
            new PhraseEntry("ٱللَّهُ أَكْبَرُ", 0.85f),
            new PhraseEntry("لَا إِلَٰهَ إِلَّا ٱللَّهُ", 0.93f)
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private float density;
    private TextView takbir;
    private TextView prayerNameView;
    private TextView timeView;
    private TextView hint;
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
        w.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.y = dp(24);
        w.setAttributes(lp);
        boolean blur = false;
        if (!isNeu && !isDarkGlow && Build.VERSION.SDK_INT >= 31) {
            WindowManager wm = getSystemService(WindowManager.class);
            if (wm != null && wm.isCrossWindowBlurEnabled()) {
                w.setBackgroundBlurRadius(dp(30));
                blur = true;
            }
        }

        int accentColor = (isNeu && !isNeuDark) ? 0xFFD97706 : 0xFFFFA726;
        int primaryTextColor = isNeu ? ThemeManager.neu(0xFF2B3036) : 0xFFFFFFFF;
        int labelTextColor = isNeu ? ThemeManager.neu(0xFF4A525D) : 0xB3FFFFFF;
        int timeTextColor = isNeu ? ThemeManager.neu(0xFF5A626E) : 0xCCFFFFFF;
        int hintTextColor = isNeu ? ThemeManager.neu(0xFF5A626E) : 0x99FFFFFF;
        int dividerColor = isNeu ? ThemeManager.neu(0x1F000000) : (isDarkGlow ? 0x3800F0FF : 0x33FFFFFF);

        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setClipChildren(false);
        wrapper.setClipToPadding(false);
        int outerPad = dp(isNeu ? 18 : 8);
        wrapper.setPadding(outerPad, outerPad, outerPad, outerPad);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(30), dp(22), dp(34), dp(22));
        if (isNeu) {
            card.setBackground(new NeumorphicDrawable(dp(26), density, NeumorphicDrawable.MODE_EXTRUDED, true));
            card.setElevation(0f);
        } else if (isDarkGlow) {
            GradientDrawable dgBg = new GradientDrawable();
            dgBg.setColor(0xF008090D);
            dgBg.setCornerRadius(dp(26));
            dgBg.setStroke(Math.max(1, dp(1.5f)), 0xFF00F0FF);
            card.setBackground(dgBg);
            card.setClipToOutline(true);
            card.setElevation(dp(16));
        } else {
            GradientDrawable glass = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    blur ? new int[]{0xB3161E2E, 0xB30F1622} : new int[]{0xE6222938, 0xE6161C27});
            glass.setCornerRadius(dp(26));
            glass.setStroke(Math.max(1, dp(1.5f)), 0x59FFFFFF);
            card.setBackground(glass);
            card.setClipToOutline(true);
            card.setElevation(dp(16));
        }

        activePhrases = (prayer == 0) ? SUBUH_PHRASES : STANDARD_PHRASES;
        currentPhraseIdx = 0;

        takbir = text(activePhrases[0].arabic, amiri, 38, accentColor, 400);
        takbir.setSingleLine(false);
        takbir.setMaxLines(2);
        takbir.setIncludeFontPadding(false);
        takbir.setLineSpacing(0f, 1.05f);
        card.addView(takbir, new LinearLayout.LayoutParams(dp(270), LinearLayout.LayoutParams.WRAP_CONTENT));

        View line = new View(this);
        line.setBackgroundColor(dividerColor);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(70));
        llp.leftMargin = dp(26);
        llp.rightMargin = dp(26);
        card.addView(line, llp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        String town = AppLocationManager.getLocationName(this);
        if (town == null || town.isEmpty()) {
            town = Location.prefs(this).getString("now_town", "");
        }
        TextView label = text(town.isEmpty() ? "ADZAN" : "ADZAN  ·  " + town.toUpperCase(Locale.ROOT), outfit, 12, labelTextColor, 600);
        label.setLetterSpacing(0.22f);
        label.setGravity(Gravity.START);
        words.addView(label);
        String when = new SimpleDateFormat("HH.mm", Locale.getDefault()).format(new Date());
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.BOTTOM);
        nameRow.setPadding(0, dp(2), 0, dp(2));
        prayerNameView = text(PrayerTimes.NAMES[Math.max(0, Math.min(prayer, PrayerTimes.NAMES.length - 1))], outfit, 28, primaryTextColor, 500);
        prayerNameView.setSingleLine(true);
        nameRow.addView(prayerNameView);
        timeView = text(when, outfit, 20, timeTextColor, 300);
        timeView.setSingleLine(true);
        timeView.setPadding(dp(14), 0, 0, dp(3));
        nameRow.addView(timeView);
        words.addView(nameRow);
        hint = text("Tekan Back / OK untuk tutup", outfit, 12, hintTextColor, 400);
        hint.setGravity(Gravity.START);
        words.addView(hint);
        card.addView(words);
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(v -> finish());
        wrapper.addView(card, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        setContentView(wrapper);

        String soundChoice = Azan.getSoundChoice(this);
        if (Azan.SOUND_SILENT.equals(soundChoice)) {
            hint.setText("Mode Senyap • Tekan Back untuk tutup");
            handler.postDelayed(fallbackCycleRunnable, 2800);
            handler.postDelayed(this::finish, 15_000);
        } else if (Azan.SOUND_CHIME.equals(soundChoice)) {
            hint.setText("Nada Pengingat Singkat • Tekan Back untuk tutup");
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int prayer = intent.getIntExtra("prayer", 4);
        activePhrases = (prayer == 0) ? SUBUH_PHRASES : STANDARD_PHRASES;
        currentPhraseIdx = 0;
        if (prayerNameView != null) {
            prayerNameView.setText(PrayerTimes.NAMES[Math.max(0, Math.min(prayer, PrayerTimes.NAMES.length - 1))]);
        }
        if (timeView != null) {
            timeView.setText(new SimpleDateFormat("HH.mm", Locale.getDefault()).format(new Date()));
        }
        applyPhraseText(activePhrases[0].arabic);
    }

    private void showPhrase(int index) {
        if (index < 0 || index >= activePhrases.length) return;
        currentPhraseIdx = index;
        final String arabic = activePhrases[index].arabic;
        if (takbir == null) return;
        takbir.animate().cancel();
        takbir.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction(() -> {
                    if (isFinishing() || isDestroyed() || takbir == null) return;
                    applyPhraseText(arabic);
                    takbir.animate().alpha(1f).setDuration(260).start();
                })
                .start();
    }

    private void applyPhraseText(String arabic) {
        if (takbir == null) return;
        takbir.setText(arabic);
        takbir.setTextSize(TypedValue.COMPLEX_UNIT_SP, arabic.length() > 18 ? 25f : 38f);
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
