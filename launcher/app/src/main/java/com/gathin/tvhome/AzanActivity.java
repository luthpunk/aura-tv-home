package com.gathin.tvhome;

import android.app.Activity;
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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

/**
 * The azan, as a fixed-width top-center floating glass card over whatever was on screen.
 * Displays the dynamic prayer & location header, audio-synced rotating Arabic phrase +
 * Indonesian translation, and a compact dismiss hint separated by clean hairline dividers.
 */
public class AzanActivity extends Activity {

    private static final class AdzanPhrase {
        final String arabic;
        final String meaning;
        final float untilProgress;

        AdzanPhrase(String arabic, String meaning, float untilProgress) {
            this.arabic = arabic;
            this.meaning = meaning;
            this.untilProgress = untilProgress;
        }
    }

    private static final AdzanPhrase[] STANDARD_PHRASES = {
            new AdzanPhrase("ٱللَّهُ أَكْبَرُ", "Allah Maha Besar", 0.16f),
            new AdzanPhrase("أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا ٱللَّهُ", "Tiada Tuhan selain Allah", 0.34f),
            new AdzanPhrase("أَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ ٱللَّهِ", "Muhammad utusan Allah", 0.52f),
            new AdzanPhrase("حَيَّ عَلَى ٱلصَّلَاةِ", "Mari mendirikan sholat", 0.68f),
            new AdzanPhrase("حَيَّ عَلَى ٱلْفَلَاحِ", "Mari menuju kemenangan", 0.84f),
            new AdzanPhrase("ٱللَّهُ أَكْبَرُ", "Allah Maha Besar", 0.94f),
            new AdzanPhrase("لَا إِلَٰهَ إِلَّا ٱللَّهُ", "Tiada Tuhan selain Allah", 1.00f),
    };

    private static final AdzanPhrase[] SUBUH_PHRASES = {
            new AdzanPhrase("ٱللَّهُ أَكْبَرُ", "Allah Maha Besar", 0.15f),
            new AdzanPhrase("أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا ٱللَّهُ", "Tiada Tuhan selain Allah", 0.31f),
            new AdzanPhrase("أَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ ٱللَّهِ", "Muhammad utusan Allah", 0.48f),
            new AdzanPhrase("حَيَّ عَلَى ٱلصَّلَاةِ", "Mari mendirikan sholat", 0.63f),
            new AdzanPhrase("حَيَّ عَلَى ٱلْفَلَاحِ", "Mari menuju kemenangan", 0.76f),
            new AdzanPhrase("ٱلصَّلَاةُ خَيْرٌ مِنَ ٱلنَّوْمِ", "Sholat lebih baik daripada tidur", 0.89f),
            new AdzanPhrase("ٱللَّهُ أَكْبَرُ", "Allah Maha Besar", 0.95f),
            new AdzanPhrase("لَا إِلَٰهَ إِلَّا ٱللَّهُ", "Tiada Tuhan selain Allah", 1.00f),
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private float density;
    private TextView takbir;
    private TextView takbirMeaning;
    private TextView hint;
    private AdzanPhrase[] activePhrases;
    private int currentPhraseIndex = 0;
    private int audioStartOffsetMs = 0;
    /** True once the card has been on screen: until then a stop is the screensaver going, not the owner. */
    private boolean shown;

    private final Runnable audioPhraseSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || player == null) return;
            try {
                if (player.isPlaying()) {
                    int duration = player.getDuration();
                    int effectiveDuration = duration - audioStartOffsetMs;
                    if (effectiveDuration > 2000) {
                        int pos = Math.max(0, player.getCurrentPosition() - audioStartOffsetMs);
                        float progress = Math.min(1f, Math.max(0f, pos / (float) effectiveDuration));
                        int targetIdx = activePhrases.length - 1;
                        for (int i = 0; i < activePhrases.length; i++) {
                            if (progress <= activePhrases[i].untilProgress) {
                                targetIdx = i;
                                break;
                            }
                        }
                        if (targetIdx != currentPhraseIndex) {
                            showPhrase(targetIdx);
                        }
                    }
                }
            } catch (IllegalStateException ignored) {
                return;
            }
            handler.postDelayed(this, 250L);
        }
    };

    private final Runnable fallbackCycleRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || activePhrases == null || activePhrases.length == 0) return;
            int nextIdx = (currentPhraseIndex + 1) % activePhrases.length;
            showPhrase(nextIdx);
            handler.postDelayed(this, 3500L);
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
        activePhrases = (prayer == 0) ? SUBUH_PHRASES : STANDARD_PHRASES;

        Typeface outfit = getResources().getFont(R.font.outfit);
        Typeface amiri = getResources().getFont(R.font.amiri_quran);

        // Ensure ThemeManager palette is initialized before reading theme colors
        ThemeManager.getActiveTheme(this);
        boolean isNeu = ThemeManager.isNeumorphism(this);
        boolean isNeuDark = ThemeManager.isNeumorphismDark(this);
        boolean isDarkGlow = ThemeManager.isDarkGlow(this);

        // Top-center floating glass card over whatever app is open.
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        w.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
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

        int accentColor = (isNeu && !isNeuDark) ? 0xFFD97706 : 0xFFFFA726;
        int primaryTextColor = isNeu ? ThemeManager.neu(0xFF2B3036) : 0xFFFFFFFF;
        int meaningTextColor = isNeu ? ThemeManager.neu(0xFF4A525D) : 0xD9FFFFFF;
        int hintTextColor = isNeu ? ThemeManager.neu(0xFF5A626E) : 0x99FFFFFF;
        int dividerColor = isNeu ? ThemeManager.neu(0x1F000000) : (isDarkGlow ? 0x3800F0FF : 0x2BFFFFFF);

        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setClipChildren(false);
        wrapper.setClipToPadding(false);
        int outerPad = dp(isNeu ? 18 : 8);
        wrapper.setPadding(outerPad, outerPad, outerPad, outerPad);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int fixedCardWidth = Math.min(dp(460), screenWidth - outerPad * 2 - dp(32));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(26), dp(14), dp(26), dp(12));
        if (isNeu) {
            card.setBackground(new NeumorphicDrawable(dp(22), density, NeumorphicDrawable.MODE_EXTRUDED, true));
            card.setElevation(0f);
        } else if (isDarkGlow) {
            GradientDrawable dgBg = new GradientDrawable();
            dgBg.setColor(0xF008090D);
            dgBg.setCornerRadius(dp(22));
            dgBg.setStroke(Math.max(1, dp(1.5f)), 0xFF00F0FF);
            card.setBackground(dgBg);
            card.setClipToOutline(true);
            card.setElevation(dp(16));
        } else {
            GradientDrawable glass = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    blur ? new int[]{0xB3161E2E, 0xB30F1622} : new int[]{0xE6222938, 0xE6161C27});
            glass.setCornerRadius(dp(22));
            glass.setStroke(Math.max(1, dp(1.5f)), 0x59FFFFFF);
            card.setBackground(glass);
            card.setClipToOutline(true);
            card.setElevation(dp(16));
        }

        // 1. Dynamic Prayer + Location Header
        String prayerName = PrayerTimes.NAMES[Math.max(0, Math.min(prayer, PrayerTimes.NAMES.length - 1))];
        String town = AppLocationManager.getLocationName(this);
        if (town == null || town.isEmpty()) {
            town = Location.prefs(this).getString("now_town", "");
        }
        String headerStr = town.isEmpty()
                ? "Saatnya Adzan " + prayerName
                : "Saatnya Adzan " + prayerName + " di wilayah " + town.trim();
        TextView headerText = text(headerStr, outfit, 14.5f, primaryTextColor, 600);
        headerText.setIncludeFontPadding(false);
        headerText.setMaxLines(2);
        card.addView(headerText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 2. Top Divider
        card.addView(makeDivider(dividerColor, dp(10), dp(8)));

        // 3. Rotating Arabic Phrase + Tight Indonesian Meaning
        takbir = text(activePhrases[0].arabic, amiri, 26f, accentColor, 400);
        takbir.setIncludeFontPadding(false);
        takbir.setLineSpacing(0f, 0.92f);
        takbir.setMaxLines(2);
        card.addView(takbir, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        takbirMeaning = text(activePhrases[0].meaning, outfit, 12f, meaningTextColor, 400);
        takbirMeaning.setIncludeFontPadding(false);
        takbirMeaning.setMaxLines(1);
        LinearLayout.LayoutParams meaningLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        meaningLp.topMargin = dp(4);
        card.addView(takbirMeaning, meaningLp);

        // 4. Bottom Divider
        card.addView(makeDivider(dividerColor, dp(10), dp(8)));

        // 5. Compact Dismiss Hint
        hint = text("Tekan Back / OK untuk menutup", outfit, 10.5f, hintTextColor, 400);
        hint.setIncludeFontPadding(false);
        hint.setSingleLine(true);
        card.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(v -> finish());
        wrapper.addView(card, new FrameLayout.LayoutParams(
                fixedCardWidth, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        setContentView(wrapper);

        card.setAlpha(0f);
        card.setTranslationY(-dp(20));
        card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(320L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        String soundChoice = Azan.getSoundChoice(this);
        if (Azan.SOUND_SILENT.equals(soundChoice)) {
            hint.setText("Mode Senyap • Tekan Back / OK untuk menutup");
            handler.postDelayed(fallbackCycleRunnable, 3500L);
            handler.postDelayed(this::finish, 18_000);
        } else if (Azan.SOUND_CHIME.equals(soundChoice)) {
            hint.setText("Nada Singkat • Tekan Back / OK untuk menutup");
            handler.postDelayed(fallbackCycleRunnable, 3500L);
            playChime();
        } else {
            File file = Azan.recording(this, soundChoice);
            if (file == null) {
                hint.setText("Mengunduh audio " + Azan.soundLabel(soundChoice) + "...");
                Azan.fetch(this, soundChoice);
                handler.postDelayed(fallbackCycleRunnable, 3500L);
                playChime();
                handler.postDelayed(this::finish, 8000);
                return;
            }
            play(file, soundChoice);
        }
    }

    private View makeDivider(int color, int topMarginPx, int bottomMarginPx) {
        View line = new View(this);
        line.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = topMarginPx;
        lp.bottomMargin = bottomMarginPx;
        line.setLayoutParams(lp);
        return line;
    }

    private void showPhrase(int index) {
        if (activePhrases == null || index < 0 || index >= activePhrases.length) return;
        currentPhraseIndex = index;
        final AdzanPhrase next = activePhrases[index];
        if (takbir != null) {
            takbir.animate().alpha(0f).setDuration(160L).withEndAction(() -> {
                if (isFinishing() || isDestroyed()) return;
                takbir.setText(next.arabic);
                takbir.animate().alpha(1f).setDuration(240L).start();
            }).start();
        }
        if (takbirMeaning != null) {
            takbirMeaning.animate().alpha(0f).setDuration(160L).withEndAction(() -> {
                if (isFinishing() || isDestroyed()) return;
                takbirMeaning.setText(next.meaning);
                takbirMeaning.animate().alpha(1f).setDuration(240L).start();
            }).start();
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
            audioStartOffsetMs = (Azan.SOUND_KLCC.equals(soundChoice) && file.getName().contains("klcc"))
                    ? Azan.KLCC_START_MS
                    : 0;
            if (audioStartOffsetMs > 0) {
                player.seekTo(audioStartOffsetMs);
            }
            player.start();
            handler.postDelayed(audioPhraseSyncRunnable, 250L);
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
