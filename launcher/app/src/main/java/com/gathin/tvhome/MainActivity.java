package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhome.qrcode.QrBitmapUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    private static java.lang.ref.WeakReference<MainActivity> sInstance;

    public static MainActivity getInstance() {
        return sInstance != null ? sInstance.get() : null;
    }

    public static boolean isForeground() {
        MainActivity inst = getInstance();
        return inst != null && !inst.isFinishing() && !inst.isDestroyed() && !inst.hiddenSinceResume;
    }

    public static void openSettingsFromGlobal() {
        MainActivity inst = getInstance();
        if (inst != null) {
            inst.runOnUiThread(() -> {
                if (inst.screensaverActive) inst.dismissScreensaver();
                inst.showSettingsPopup();
            });
        }
    }

    private boolean isHomeLongPressed = false;
    private final Runnable longPressHomeRunnable = new Runnable() {
        @Override
        public void run() {
            isHomeLongPressed = true;
            SoundEffects.playSelect();
            if (screensaverActive) dismissScreensaver();
            showSettingsPopup();
        }
    };

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;
    private String liveTvAction;
    private String inputAction;
    private String sourcePanelService;
    private String sourcePanelAction;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    /** The focus: one dark shape gliding between tiles and pills (see Glider). */
    private Glider glider;
    /** Whether the glider is on the top menu, so a move down travels dark. */
    private boolean gliderOnPill;
    private final int[] pillAt = new int[2], rootAt = new int[2];
    /** The top-right buttons and the two rectangles used to line them up with the dock. */
    private View topRow, topSettings;
    private final Rect alignCard = new Rect(), alignButton = new Rect();
    /** How much of the space between the side paddings the top shelf may use. */
    private static final float HERO_WIDTH_SHARE = 0.40f;
    /** The name is one line, and may run wider than the description, up to this share. */
    private static final float HERO_NAME_WIDTH_SHARE = 0.60f;
    private static final float HERO_TITLE_MAX_SP = 20f;
    /** The breathing room between the focused dock app title and the top of the dock cards. */
    private static final float HERO_GAP_DP = 21f;
    private static final float HERO_TITLE_MIN_SP = 16f;
    /** The shelf's width in pixels, worked out once from the screen. */
    private int heroMaxWidth, heroNameMaxWidth;
    private View heroShelf;
    private TextView heroName;
    private TextView heroDesc;
    private TextView heroWhere;
    /** The place on screen and a few lines about it; null for the user's own wallpapers. */
    private String placeName;
    private String placeText = "", placeCountry = "";
    private int ambientColor = -1;
    private boolean heroShown;
    /** The glow behind the tiles. It follows every app, while the shelf only names dock apps. */
    private boolean ambientShown;

    /** The colour the glass is blending from, how far along it is, and the blend itself. */
    private int glowFrom;
    private float glowMix = 1f;
    private android.animation.ValueAnimator glowBlend;
    private Bitmap ambientShape;
    /** Where the shelf's bottom edge belongs; its top follows once the lines have been measured. */
    private float heroBottom;
    private View btnInputs;
    private TextView btnWifi;
    private View btnAssistant;
    private View btnCandyBox;
    private TextView ampmView, dateView;
    private TextView shamsiDate;
    private TextView prayerTimesView;
    private View weatherAirQualityView;
    private TextView weaHeaderLoc, weaMainTemp, weaMainCond, weaMainSub;
    private View weaHeaderDiv, weaHourlyBox, weaHourDiv1, weaHourDiv2, weaBottomTray;
    private ImageView weaMainIcon, weaHourIcon1, weaHourIcon2, weaHourIcon3;
    private TextView weaHourTime1, weaHourVal1, weaHourTime2, weaHourVal2, weaHourTime3, weaHourVal3;
    private TextView weaPillAqi, weaPillHum, weaPillWind, weaPillRain;
    private View nowPlayingCard;
    private ImageView cardAlbumArt;
    private View cardTrackInfo;
    private TextView cardSubtitle;
    private TextView cardTitle;
    private ImageView cardWaveIcon;
    private boolean nowPlayingDisabledByUser = false;
    private String lastPlayedTrackKey = "";
    private NowPlayingManager.Listener nowPlayingListener;
    private View islamicShelf;
    private View ayahBlock;
    private TextView ayahLabel, ayahArabic, ayahText, prayerHeader;
    private boolean ayahRunning;
    private boolean homeWeatherRunning;
    private TextView lblSubuh, lblSyuruk, lblZohor, lblAsar, lblMaghrib, lblIsyak;
    private TextView timeSubuh, timeSyuruk, timeZohor, timeAsar, timeMaghrib, timeIsyak;
    private boolean shamsi;
    private String vpnApp;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    // How far the grid has scrolled the glass. springY works without it, in grid content space, so a
    // scroll moves the glass in lockstep with its tile instead of becoming a moving spring target.
    private float glassScrollShift;
    private boolean glassShown;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    private boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    private boolean reducedMotion;
    /** hidden_apps as the adapter sees them (config.xml or a theme's config.json): a hidden app gets no card either. */
    private Set<String> hiddenApps = Collections.emptySet();
    private List<Cards.Card> cards = Collections.emptyList();
    /** The grid has been laid out and focused since the last resume; the card prompt waits for this. */
    private boolean tilesReady;
    private boolean tilesAnimatedOnce;
    /** Grid rows left showing under the dock (config rows_below_dock). */
    private int rowsBelowDockSetting;
    /** The longest Home holds its first frame while it puts itself together (see holdFirstFrame). */
    private static final long FIRST_FRAME_WAIT_MS = 1200;
    private static final long RETURN_SETTLE_MS = 6000;
    /** True while Home rebuilds on return; focus passes through the pills then and must not light them. */
    private boolean resuming;
    /** True while Home's first frame is held back (see holdFirstFrame). */
    private boolean firstFrameHeld;
    private Dialog cardPrompt;
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private boolean promptsPaused;
    /** The Inputs or Module cards dropdown while one is open, so onPause can close it. */
    private PopupWindow openPopup;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Rotating wallpaper (Config.wallpapers), the view that shows it and the bitmap on screen. */
    private List<File> wallpapers = Collections.emptyList();
    private long wallpaperIntervalMs;
    /** Decodes the next picture off the main thread, so a switch never stutters the D-pad animations. */
    private final java.util.concurrent.ExecutorService wallDecoder = java.util.concurrent.Executors.newSingleThreadExecutor();
    private long wallShownAt;
    private android.widget.ImageView wallView;
    private android.graphics.Bitmap wallBitmap;
    /** Settings kept for the places refresh, which runs after onCreate. */
    private Config config;
    /** Pictures of places are on (see PlaceWallpapers); they are dimmed behind the tiles. */
    private boolean placesMode;
    private volatile boolean placesRefreshRunning;
    private static final long SCREENSAVER_TIMEOUT_MS = 10L * 60 * 1000L; // 10 minutes idle -> screensaver
    private static final long TV_SLEEP_TIMEOUT_MS = SCREENSAVER_TIMEOUT_MS + (2L * 60 * 60 * 1000L); // 2 hours later -> TV off
    private long lastUserInteraction = android.os.SystemClock.elapsedRealtime();
    private boolean screensaverActive = false;
    private View screensaverView;
    private boolean wallpaperFullscreen = false;
    private View mainContent;
    private static final long DOUBLE_BACK_DELAY_MS = 350L;
    private Runnable singleBackRunnable = null;
    private boolean doubleBackPending = false;
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            updateInputsButton();
        }

        @Override
        public void onInputRemoved(String inputId) {
            updateInputsButton();
        }
    };
    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            reloadApps(false);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateShamsiDate();
            updatePrayerTimes();
            updateDateAndPeriod();
            updateWifiLabel();
            updateWeatherAqi();
            WeatherAirQuality.syncIfDue(MainActivity.this, MainActivity.this::updateWeatherAqi);
            updateIslamicShelf();
            refreshPlacesIfDue();
            refreshOnlineWallpapersIfDue();
            checkInactivity();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    private final Runnable nowPlayingPoll = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                NowPlayingManager.updateCurrentMedia(MainActivity.this);
                handler.postDelayed(this, 3000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = new java.lang.ref.WeakReference<>(this);
        SoundEffects.init(this);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        COLUMNS = cfg.columns;
        reducedMotion = cfg.reducedMotion;
        liveTvPkg = cfg.liveTvPackage;
        liveTvAction = cfg.liveTvAction;
        inputAction = cfg.inputAction;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;

        root = findViewById(R.id.root);
        // The tiles' entrance plays once per TV boot. Android reclaims the launcher during long stays
        // in big apps; starting again then is a return to Home, not a first launch.
        long bootAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        android.content.SharedPreferences launch = getSharedPreferences("launch", MODE_PRIVATE);
        if (Math.abs(launch.getLong("boot_at", 0) - bootAt) < 60_000) tilesAnimatedOnce = true;
        else launch.edit().putLong("boot_at", bootAt).apply();
        ensureSystemScreensaverAndSleepSettings();
        root.setBackgroundColor(cfg.backgroundColor);
        config = cfg;
        wallpapers = cfg.wallpapers;
        wallpaperIntervalMs = cfg.wallpaperIntervalMs;
        placesMode = cfg.places;
        mainContent = findViewById(R.id.mainContent);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        if (ambient != null) {
            ambient.setVisibility(View.GONE);
            ambient.setAlpha(0f);
        }
        Backdrop.setGlow(null, 0, 0, 1f, 0f);
        Backdrop.setWorker(wallDecoder, handler);
        heroShelf = findViewById(R.id.heroShelf);
        heroName = findViewById(R.id.heroName);
        heroDesc = findViewById(R.id.heroDesc);
        heroWhere = findViewById(R.id.heroWhere);
        int usable = getResources().getDisplayMetrics().widthPixels - (int) ((55 + 56) * density);
        heroMaxWidth = Math.round(usable * HERO_WIDTH_SHARE);
        heroNameMaxWidth = Math.round(usable * HERO_NAME_WIDTH_SHARE);
        if (heroName != null) heroName.setMaxWidth(heroNameMaxWidth);
        if (heroDesc != null) heroDesc.setMaxWidth(heroMaxWidth);

        String rawCat = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("active_category", "Indonesia");
        final String activeCat = "Malaysia".equalsIgnoreCase(rawCat) ? "Indonesia" : rawCat;
        if ("Custom".equalsIgnoreCase(activeCat)) {
            placesMode = false;
            File customDir = new File(getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
            boolean singleMode = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getBoolean("custom_single_mode", false);
            String singleFile = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("custom_single_file", null);
            if (singleMode && singleFile != null) {
                File sf = new File(customDir, singleFile);
                if (sf.exists() && sf.length() > 0) {
                    wallpapers = Collections.singletonList(sf);
                } else if (files != null && files.length > 0) {
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    wallpapers = new ArrayList<>(Arrays.asList(files));
                }
            } else if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                wallpapers = new ArrayList<>(Arrays.asList(files));
            } else if (cfg.wallpaperOnline && wallpapers.isEmpty()) {
                wallpapers = OnlineWallpapers.list(this);
            }
        } else {
            placesMode = false;
            List<File> catFiles = OnlineWallpapers.listByCategory(this, activeCat);
            if (!catFiles.isEmpty()) wallpapers = catFiles;
            else if (cfg.wallpaperOnline && wallpapers.isEmpty()) wallpapers = OnlineWallpapers.list(this);
            final boolean wasEmpty = catFiles.isEmpty();
            if (catFiles.size() < OnlineWallpapers.countForCategory(activeCat)) {
                new Thread(() -> {
                    OnlineWallpapers.syncCategory(getApplicationContext(), activeCat);
                    handler.post(() -> {
                        if (isDestroyed()) return;
                        List<File> fresh = OnlineWallpapers.listByCategory(MainActivity.this, activeCat);
                        if (!fresh.isEmpty()) {
                            wallpapers = fresh;
                            if (wasEmpty) showNextWallpaper(false);
                        }
                    });
                }).start();
            }
        }
        if (!wallpapers.isEmpty()) showNextWallpaper(false);
        else if (cfg.wallpaper != null && !placesMode) setWallpaper(decodeWallpaper(cfg.wallpaper), false);
        // The shelf grows and shrinks with the name and the description, so it hangs from its
        // bottom edge: every time it is measured, its top is worked out again. Positioning it by
        // the height it happened to have beforehand is what let a tall block run into the dock.
        heroShelf.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            if (heroBottom > 0) v.setY(heroBottom - v.getHeight());
        });
        ampmView = findViewById(R.id.ampm);
        dateView = findViewById(R.id.date);
        updateDateAndPeriod();
        weatherAirQualityView = findViewById(R.id.weatherAirQuality);
        if (weatherAirQualityView != null) {
            weaHeaderLoc = findViewById(R.id.weaHeaderLoc);
            weaHeaderDiv = findViewById(R.id.weaHeaderDiv);
            weaMainIcon = findViewById(R.id.weaMainIcon);
            weaMainTemp = findViewById(R.id.weaMainTemp);
            weaMainCond = findViewById(R.id.weaMainCond);
            weaHourlyBox = findViewById(R.id.weaHourlyBox);
            weaHourTime1 = findViewById(R.id.weaHourTime1);
            weaHourIcon1 = findViewById(R.id.weaHourIcon1);
            weaHourVal1 = findViewById(R.id.weaHourVal1);
            weaHourDiv1 = findViewById(R.id.weaHourDiv1);
            weaHourTime2 = findViewById(R.id.weaHourTime2);
            weaHourIcon2 = findViewById(R.id.weaHourIcon2);
            weaHourVal2 = findViewById(R.id.weaHourVal2);
            weaHourDiv2 = findViewById(R.id.weaHourDiv2);
            weaHourTime3 = findViewById(R.id.weaHourTime3);
            weaHourIcon3 = findViewById(R.id.weaHourIcon3);
            weaHourVal3 = findViewById(R.id.weaHourVal3);
            weaBottomTray = findViewById(R.id.weaBottomTray);
            weaPillAqi = findViewById(R.id.weaPillAqi);
            weaPillRain = findViewById(R.id.weaPillRain);

            weatherAirQualityView.setOnClickListener(v -> {
                startActivity(new Intent(this, WeatherActivity.class));
            });
            weatherAirQualityView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusTile(lastFocused % COLUMNS);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (nowPlayingCard != null && nowPlayingCard.getVisibility() == View.VISIBLE) {
                        nowPlayingCard.requestFocus();
                        return true;
                    }
                    View target = (btnInputs != null && btnInputs.getVisibility() == View.VISIBLE) ? btnInputs
                            : (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) ? btnWifi
                            : findViewById(R.id.btnSettings);
                    if (target != null) {
                        target.requestFocus();
                        return true;
                    }
                }
                return false;
            });
            weatherAirQualityView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    SoundEffects.playNav();
                    hideShelf();
                    if (glider != null) glider.hide();
                }
                boolean isNeu = ThemeManager.isNeumorphism(MainActivity.this);
                float scale = (hasFocus && !isNeu) ? 1.025f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
            });
        }
        updateWeatherAqi();
        WeatherAirQuality.syncIfDue(this, this::updateWeatherAqi);

        // Dynamic Now Playing Pill (Apple TV Style: Single Focus Glanceable Pill)
        nowPlayingCard = findViewById(R.id.nowPlayingCard);
        if (nowPlayingCard != null) {
            cardAlbumArt = findViewById(R.id.cardAlbumArt);
            cardTrackInfo = findViewById(R.id.cardTrackInfo);
            cardSubtitle = findViewById(R.id.cardSubtitle);
            cardTitle = findViewById(R.id.cardTitle);
            cardWaveIcon = findViewById(R.id.cardWaveIcon);

            nowPlayingCard.setOnClickListener(v -> {
                SoundEffects.playSelect();
                if (!NowPlayingManager.isNotificationAccessGranted(this)) {
                    NowPlayingManager.openNotificationAccessSettings(this);
                    Toast.makeText(this, "Aktifkan 'Aura TV Media Listener' untuk menampilkan info lagu", Toast.LENGTH_LONG).show();
                } else {
                    showNowPlayingDialog();
                }
            });

            nowPlayingCard.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    SoundEffects.playNav();
                    hideShelf();
                    if (glider != null) glider.hide();
                }
                boolean isNeu = ThemeManager.isNeumorphism(MainActivity.this);
                float scale = (hasFocus && !isNeu) ? 1.04f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            });

            nowPlayingCard.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (weatherAirQualityView != null && weatherAirQualityView.getVisibility() == View.VISIBLE) {
                        weatherAirQualityView.requestFocus();
                        return true;
                    }
                    focusTile(lastFocused % COLUMNS);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    View target = (btnInputs != null && btnInputs.getVisibility() == View.VISIBLE) ? btnInputs
                            : (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) ? btnWifi
                            : findViewById(R.id.btnSettings);
                    if (target != null) {
                        target.requestFocus();
                        return true;
                    }
                }
                return false;
            });
        }
        NowPlayingManager.init(this);
        nowPlayingListener = this::updateNowPlayingUi;
        NowPlayingManager.registerListener(nowPlayingListener);

        islamicShelf = findViewById(R.id.islamicShelf);
        if (islamicShelf != null) {
            ayahBlock = findViewById(R.id.ayahBlock);
            ayahLabel = findViewById(R.id.ayahLabel);
            ayahArabic = findViewById(R.id.ayahArabic);
            ayahText = findViewById(R.id.ayahText);
            prayerHeader = findViewById(R.id.prayerHeader);

            lblSubuh = findViewById(R.id.lblSubuh);
            lblSyuruk = findViewById(R.id.lblSyuruk);
            lblZohor = findViewById(R.id.lblZohor);
            lblAsar = findViewById(R.id.lblAsar);
            lblMaghrib = findViewById(R.id.lblMaghrib);
            lblIsyak = findViewById(R.id.lblIsyak);

            timeSubuh = findViewById(R.id.timeSubuh);
            timeSyuruk = findViewById(R.id.timeSyuruk);
            timeZohor = findViewById(R.id.timeZohor);
            timeAsar = findViewById(R.id.timeAsar);
            timeMaghrib = findViewById(R.id.timeMaghrib);
            timeIsyak = findViewById(R.id.timeIsyak);

            islamicShelf.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
                if (heroBottom > 0) v.setY(heroBottom - v.getHeight());
            });
            updateIslamicShelf();
        }

        btnWifi = findViewById(R.id.btnWifi);
        if (!cfg.showNetworkPill) btnWifi.setVisibility(View.GONE);
        shamsiDate = findViewById(R.id.shamsiDate);
        shamsi = cfg.shamsiDate;
        if (shamsi) {
            shamsiDate.setVisibility(View.VISIBLE);
            TextView date = findViewById(R.id.date);
            date.setTextSize(11f);
            date.setPadding(0, (int) (4 * density), 0, 0);
        }
        prayerTimesView = findViewById(R.id.prayerTimes);
        if (prayerTimesView != null) {
            prayerTimesView.setOnClickListener(v -> showPrayerPopup());
            prayerTimesView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    View inputs = findViewById(R.id.btnInputs);
                    View wifi = findViewById(R.id.btnWifi);
                    View settings = findViewById(R.id.btnSettings);
                    if (inputs != null && inputs.getVisibility() == View.VISIBLE) inputs.requestFocus();
                    else if (wifi != null && wifi.getVisibility() == View.VISIBLE) wifi.requestFocus();
                    else if (settings != null) settings.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusTile(lastFocused % COLUMNS);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    showPrayerPopup();
                    return true;
                }
                return false;
            });
        }
        updatePrayerTimes();
        PrayerTimes.syncIfDue(this, this::updatePrayerTimes);
        vpnApp = cfg.vpnApp;
        moduleCards = cfg.moduleCards;
        hiddenApps = new HashSet<>(cfg.hiddenApps);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS) {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                // The dock's line is set before any row is placed. Set afterwards, the grid kept its
                // rows where the first layout had put them, and Home came back with the dock and its
                // tray hundreds of pixels up the screen until focus slid them down.
                parkDock();
                super.onLayoutChildren(recycler, state);
            }
        });
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        DockDecoration tray = new DockDecoration(COLUMNS, density);
        grid.addItemDecoration(tray);
        // The top-right buttons line up with the dock, which is only known once the tiles are laid out.
        grid.getViewTreeObserver().addOnGlobalLayoutListener(this::alignTopButtons);
        // Glass shows the blurred wallpaper behind it: the dock tray (drawn by the grid) and the
        // top-bar pills redraw when a new blurred copy is ready.
        Backdrop.watch(grid);
        setUpGlider();
        holdFirstFrame();
        for (int i = 0; i < grid.getItemDecorationCount(); i++) {
            if (grid.getItemDecorationAt(i) instanceof DockDecoration) {
                ((DockDecoration) grid.getItemDecorationAt(i)).setGlider(glider);
            }
        }
        // The grid only shows the shape on the tray.
        glider.addCompanion(grid, tray.area);
        applyHomeScreenTheme();
        for (int id : new int[]{R.id.btnInputs, R.id.btnWifi, R.id.btnAssistant, R.id.btnCandyBox, R.id.btnSettings}) {
            View pill = findViewById(id);
            if (pill == null) continue;
            glider.addCompanion(pill);
            pill.setOnFocusChangeListener((v, has) -> {
                if (has) moveTopHighlight(v);
            });
        }
        if (prayerTimesView != null) {
            prayerTimesView.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    SoundEffects.playNav();
                    glider.hide();
                }
            });
        }
        // The apps rise into place on the first launch after the TV starts, and only then. Setting
        // the animation arms it for the grid's first draw, so on any later fresh start (Android
        // closing Home while an app was open) it is not set at all: armed, it drew the dock's tray
        // empty while the apps faded in.
        if (!tilesAnimatedOnce) {
            int enterAnim = reducedMotion ? R.anim.tile_enter_reduced : R.anim.tile_enter;
            float enterDelay = reducedMotion ? 0f : 0.06f;
            grid.setLayoutAnimation(new LayoutAnimationController(
                    AnimationUtils.loadAnimation(this, enterAnim), enterDelay));
        }
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row towards the bottom of the screen, leaving room for
        // rows_below_dock grid rows under it; the rest of the grid stays below until you press Down.
        rowsBelowDockSetting = cfg.rowsBelowDock;
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            parkDock();
            updateTopFade();
        });

        // Rows feather out under the top bar instead of being cut off. The fade needs an offscreen
        // layer and draws the grid twice, on every frame of every animation, so it is on only while a
        // row is actually in the band: it switches on in the same frame a row scrolls into it.
        gridWrap = findViewById(R.id.gridWrap);

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateTopFade();
                if (!glassShown) return;
                glassTargetY -= dy;
                glassScrollShift -= dy;
                glider.scrolled(dy);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentY") {
            @Override
            public float getValue(View v) {
                return v.getTranslationY() - glassScrollShift;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationY(value + glassScrollShift);
            }
        });
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            if (reducedMotion) {
                // Stiff and almost critically damped: settles in a few frames instead of oscillating,
                // and stops once the highlight is within 1.5 px of its tile.
                s.setSpring(new SpringForce().setStiffness(1400f).setDampingRatio(0.95f));
                s.setMinimumVisibleChange(1.5f);
            } else {
                // Critically damped: glides onto the tile and stops there, with no bounce back.
                s.setSpring(new SpringForce().setStiffness(900f).setDampingRatio(1.0f));
            }
        }

        btnInputs = findViewById(R.id.btnInputs);
        btnAssistant = findViewById(R.id.btnAssistant);
        btnCandyBox = findViewById(R.id.btnCandyBox);
        View btnSettings = findViewById(R.id.btnSettings);
        if (btnInputs != null) btnInputs.setOnClickListener(v -> showInputs());
        if (btnWifi != null) {
            btnWifi.setOnClickListener(v -> showWifiPopup());
            btnWifi.setOnLongClickListener(v -> {
                open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
                return true;
            });
        }
        if (btnAssistant != null) {
            btnAssistant.setOnClickListener(v -> triggerGoogleAssistant());
        }
        if (btnCandyBox != null) {
            btnCandyBox.setOnClickListener(v -> showCandyBoxPopup());
        }
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsPopup());
            btnSettings.setOnLongClickListener(v -> {
                if (moduleCards) {
                    showModuleCards();
                    return true;
                }
                open(systemIntent(Settings.ACTION_SETTINGS));
                return true;
            });
        }

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                v.performClick();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (v.getId() == R.id.btnWifi || v.getId() == R.id.btnInputs) {
                    if (weatherAirQualityView != null && weatherAirQualityView.getVisibility() == View.VISIBLE) {
                        weatherAirQualityView.requestFocus();
                        return true;
                    }
                }
                // Back to the dock tile Up came from, not wherever the focus finder lands.
                focusTile(lastFocused % COLUMNS);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (v.getId() == R.id.btnSettings) {
                    if (btnCandyBox != null && btnCandyBox.getVisibility() == View.VISIBLE) {
                        btnCandyBox.requestFocus();
                        return true;
                    }
                    if (btnAssistant != null && btnAssistant.getVisibility() == View.VISIBLE) {
                        btnAssistant.requestFocus();
                        return true;
                    }
                    if (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) {
                        btnWifi.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnCandyBox) {
                    if (btnAssistant != null && btnAssistant.getVisibility() == View.VISIBLE) {
                        btnAssistant.requestFocus();
                        return true;
                    }
                    if (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) {
                        btnWifi.requestFocus();
                        return true;
                    }
                    if (btnInputs != null && btnInputs.getVisibility() == View.VISIBLE) {
                        btnInputs.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnAssistant) {
                    if (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) {
                        btnWifi.requestFocus();
                        return true;
                    }
                    if (btnInputs != null && btnInputs.getVisibility() == View.VISIBLE) {
                        btnInputs.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnInputs || (v.getId() == R.id.btnWifi && (btnInputs == null || btnInputs.getVisibility() != View.VISIBLE))) {
                    if (nowPlayingCard != null && nowPlayingCard.getVisibility() == View.VISIBLE) {
                        nowPlayingCard.requestFocus();
                        return true;
                    }
                    if (weatherAirQualityView != null && weatherAirQualityView.getVisibility() == View.VISIBLE) {
                        weatherAirQualityView.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnInputs && prayerTimesView != null && prayerTimesView.getVisibility() == View.VISIBLE) {
                    prayerTimesView.requestFocus();
                    return true;
                }
                return v.getId() == R.id.btnInputs
                        || (v.getId() == R.id.btnWifi && (btnInputs == null || btnInputs.getVisibility() != View.VISIBLE));
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (v.getId() == R.id.btnWifi) {
                    if (btnAssistant != null && btnAssistant.getVisibility() == View.VISIBLE) {
                        btnAssistant.requestFocus();
                        return true;
                    }
                    if (btnCandyBox != null && btnCandyBox.getVisibility() == View.VISIBLE) {
                        btnCandyBox.requestFocus();
                        return true;
                    }
                    if (btnSettings != null) {
                        btnSettings.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnAssistant) {
                    if (btnCandyBox != null && btnCandyBox.getVisibility() == View.VISIBLE) {
                        btnCandyBox.requestFocus();
                        return true;
                    }
                    if (btnSettings != null) {
                        btnSettings.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnCandyBox) {
                    if (btnSettings != null) {
                        btnSettings.requestFocus();
                        return true;
                    }
                }
                return v.getId() == R.id.btnSettings;
            }
            return false;
        };
        if (btnInputs != null) btnInputs.setOnKeyListener(pillKeys);
        if (btnWifi != null) btnWifi.setOnKeyListener(pillKeys);
        if (btnAssistant != null) btnAssistant.setOnKeyListener(pillKeys);
        if (btnCandyBox != null) btnCandyBox.setOnKeyListener(pillKeys);
        if (btnSettings != null) btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
            if (!onTile && (newF == null || !(isTopPill(newF) || inMenu(newF)))) glider.hide();
        });

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }

        // Silent background update check after 5 seconds on launch
        handler.postDelayed(() -> AppUpdateManager.checkForUpdates(this, false), 5000);

        if (getIntent() != null && getIntent().getBooleanExtra("open_settings", false)) {
            handler.postDelayed(() -> {
                if (screensaverActive) dismissScreensaver();
                showSettingsPopup();
            }, 100);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_candy_box", false)) {
            handler.postDelayed(() -> {
                if (screensaverActive) dismissScreensaver();
                showCandyBoxPopup();
            }, 100);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_azan_settings", false)) {
            handler.postDelayed(() -> {
                if (screensaverActive) dismissScreensaver();
                showAzanSettingsDialog();
            }, 100);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_wallpaper", false)) {
            handler.postDelayed(() -> {
                if (screensaverActive) dismissScreensaver();
                showWallpaperDialog();
            }, 100);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_now_playing", false)) {
            handler.postDelayed(() -> {
                if (screensaverActive) dismissScreensaver();
                showNowPlayingDialog();
            }, 100);
        }
    }

    /**
     * A theme's full-screen picture behind everything. Decoded once, downsampled to the screen so a 4K
     * JPEG costs a 1080p bitmap at most (about 8 MB), which is the price of a wallpaper on a 1 GB TV.
     */
    private android.graphics.Bitmap decodeWallpaper(File file) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= dm.widthPixels && opts.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
            return android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            // A bad or huge picture must never take the home screen down; the plain colour stays.
            return null;
        }
    }

    /** Puts a picture behind everything, cross-fading from the previous one when asked. */
    private void setWallpaper(android.graphics.Bitmap bmp, boolean fade) {
        setWallpaper(bmp, fade, null);
    }

    /** Theme-dependent wallpaper filter. Neumorphism Light gets high-key porcelain wash. */
    private void applyWallpaperColorFilter() {
        if (wallView == null) return;
        if (wallpaperFullscreen) {
            wallView.clearColorFilter();
            return;
        }
        if (ThemeManager.isDarkGlow(this)) {
            wallView.setColorFilter(0xFF222222, android.graphics.PorterDuff.Mode.MULTIPLY);
        } else if (ThemeManager.isNeumorphism(this)) {
            wallView.setColorFilter(new android.graphics.PorterDuffColorFilter(0xEAEDEAE4, android.graphics.PorterDuff.Mode.SRC_ATOP));
        } else {
            wallView.setColorFilter(0xFF6E6E6E, android.graphics.PorterDuff.Mode.MULTIPLY);
        }
    }

    private int getWallpaperDimColor() {
        if (ThemeManager.isDarkGlow(this)) {
            return 0xFF222222; // Ultra-dark OLED dimming (~13% brightness, moody cyber dark contrast)
        } else if (ThemeManager.isNeumorphism(this)) {
            return 0xFFEDEAE4; // Warm porcelain light wash matching 1793633365104.jpg
        } else {
            return 0xFF6E6E6E; // Standard glass dimming
        }
    }

    /** With {@code blur} made in advance, the glass changes in step with the picture. */
    private void setWallpaper(android.graphics.Bitmap bmp, boolean fade, android.graphics.Bitmap blur) {
        if (bmp == null) return;
        if (wallView == null) {
            wallView = new android.widget.ImageView(this);
            wallView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            root.addView(wallView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        // Dim or tint wallpapers according to active theme
        applyWallpaperColorFilter();
        android.graphics.Bitmap old = wallBitmap;
        wallBitmap = bmp;
        if (fade && old != null && !reducedMotion) {
            android.graphics.drawable.TransitionDrawable t = new android.graphics.drawable.TransitionDrawable(new android.graphics.drawable.Drawable[]{
                    new android.graphics.drawable.BitmapDrawable(getResources(), old),
                    new android.graphics.drawable.BitmapDrawable(getResources(), bmp)});
            t.setCrossFadeEnabled(true);
            wallView.setImageDrawable(t);
            t.startTransition(800);
            // The glass fades to the new picture over the same 800 ms as the wallpaper itself.
            if (blur != null) Backdrop.present(blur, 800);
            else blurBackdrop(bmp);
            // Drop the old picture once the fade is done, so only one 1080p bitmap stays in memory.
            handler.postDelayed(() -> {
                if (wallBitmap == bmp) wallView.setImageBitmap(bmp);
            }, 900);
        } else {
            wallView.setImageBitmap(bmp);
            if (blur != null) Backdrop.present(blur, 0);
            else blurBackdrop(bmp);
        }
        // An opaque picture covers the whole screen, so the black window and the plain background
        // under it are never seen. Painting them anyway cost two full-screen passes on every frame,
        // and this TV's graphics chip, not the app, is what holds each frame back.
        if (!bmp.hasAlpha()) {
            root.setBackground(null);
            getWindow().setBackgroundDrawable(null);
        }
    }

    /** Hands the picture now on screen to the glass, which blurs it once, off the main thread. */
    private void blurBackdrop(android.graphics.Bitmap bmp) {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        Backdrop.update(bmp, dm.widthPixels, dm.heightPixels, getWallpaperDimColor(), wallDecoder, handler);
    }

    /** Shows the next picture from the wallpapers folder; the position survives restarts. */
    private void showNextWallpaper(boolean fade) {
        android.content.SharedPreferences prefs = getSharedPreferences("wallpaper", MODE_PRIVATE);
        int next = (prefs.getInt("index", -1) + 1) % wallpapers.size();
        prefs.edit().putInt("index", next).apply();
        wallShownAt = android.os.SystemClock.elapsedRealtime();
        final File file = wallpapers.get(next);
        // On a cold start, name the place straight away: waiting for the decode showed the app's name
        // at full size and shrank it a moment later. After that the name changes with the picture,
        // or it would name the next place over the last one's picture while that one decodes.
        if (placeName == null) setPlace(file);
        final boolean firstPicture = wallBitmap == null;
        wallDecoder.execute(() -> {
            android.graphics.Bitmap bmp = decodeWallpaper(file);
            // After the first picture, the blur is made here, before the picture is shown, so the glass
            // changes with the wallpaper rather than after it. The first picture of a fresh start does
            // not wait for its blur: Home is held for it, and the glass catches up a moment later.
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.Bitmap blur = firstPicture ? null
                    : Backdrop.blur(bmp, dm.widthPixels, dm.heightPixels, getWallpaperDimColor());
            handler.post(() -> {
                if (isDestroyed()) return;
                setWallpaper(bmp, fade, blur);
                setPlace(file);
            });
        });
    }

    /** Switches to the next picture every wallpaper interval while Home is in front. */
    private final Runnable wallTick = new Runnable() {
        @Override
        public void run() {
            if (wallpapers.size() >= 2) showNextWallpaper(true);
            handler.postDelayed(this, wallpaperIntervalMs);
        }
    };

    /** Fetches the week's places in the background, then switches the rotation to them. */
    private void refreshPlacesIfDue() {
        if (!placesMode || placesRefreshRunning || !PlaceWallpapers.due(this)) return;
        placesRefreshRunning = true;
        new Thread(() -> {
            boolean changed = PlaceWallpapers.refresh(getApplicationContext());
            handler.post(() -> {
                placesRefreshRunning = false;
                if (!changed || isDestroyed()) return;
                List<File> places = PlaceWallpapers.list(this);
                if (places.isEmpty()) return;
                // Last week's pictures are gone: show one of this week's straight away, so the
                // name on the shelf and the picture behind it always match.
                wallpapers = places;
                showNextWallpaper(wallBitmap != null);
                if (!isFinishing() && hasWindowFocus()) startWallpaperRotation();
            });
        }, "place-wallpapers").start();
    }

    private boolean onlineRefreshRunning;

    /** Fetches fresh 4K online wallpapers in the background when due. */
    private void refreshOnlineWallpapersIfDue() {
        if (config == null || !config.wallpaperOnline || onlineRefreshRunning || !OnlineWallpapers.due(this)) return;
        onlineRefreshRunning = true;
        new Thread(() -> {
            boolean changed = OnlineWallpapers.sync(getApplicationContext(), config.wallpaperFeed);
            handler.post(() -> {
                onlineRefreshRunning = false;
                if (!changed || isDestroyed()) return;
                List<File> fresh = OnlineWallpapers.list(this);
                if (fresh.isEmpty()) return;
                List<File> combined = new ArrayList<>(config.wallpapers);
                for (File f : fresh) {
                    if (!combined.contains(f)) combined.add(f);
                }
                wallpapers = combined;
                if (wallBitmap == null) showNextWallpaper(false);
                if (!isFinishing() && hasWindowFocus()) startWallpaperRotation();
            });
        }, "online-wallpapers").start();
    }

    /** Clears the TV's app caches when due, once Home has sat idle on screen for a while. */
    private final Runnable cacheClean = () -> {
        if (hasWindowFocus() && config != null) CacheCleaner.maybeRun(this, config.cacheCleanDays);
    };

    /** Whenever Home comes back: picks the rotation up where the interval left it. */
    private void startWallpaperRotation() {
        refreshPlacesIfDue();
        refreshOnlineWallpapersIfDue();
        handler.removeCallbacks(wallTick);
        if (wallpapers.size() < 2 || wallpaperIntervalMs <= 0) return;
        long waited = android.os.SystemClock.elapsedRealtime() - wallShownAt;
        // A picture that fell due while you were in an app waits a few seconds: decoding, blurring
        // and fading it the moment you come back competed with Home for the TV's slow CPU.
        handler.postDelayed(wallTick, Math.max(RETURN_SETTLE_MS, wallpaperIntervalMs - waited));
    }

    /**
     * Home pressed while Home is already in front: singleTask delivers it here, then onResume runs.
     * Back to the top with the first dock tile focused, like the stock launcher. Back from an app
     * never comes this way, so returning to the app you left from is unchanged (#118).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("open_settings", false)) {
            if (screensaverActive) dismissScreensaver();
            showSettingsPopup();
            return;
        }
        if (intent != null && intent.getBooleanExtra("open_candy_box", false)) {
            if (screensaverActive) dismissScreensaver();
            showCandyBoxPopup();
            return;
        }
        if (intent != null && intent.getBooleanExtra("open_azan_settings", false)) {
            if (screensaverActive) dismissScreensaver();
            showAzanSettingsDialog();
            return;
        }
        if (intent != null && intent.getBooleanExtra("open_wallpaper", false)) {
            if (screensaverActive) dismissScreensaver();
            showWallpaperDialog();
            return;
        }
        if (intent != null && intent.getBooleanExtra("open_now_playing", false)) {
            if (screensaverActive) dismissScreensaver();
            showNowPlayingDialog();
            return;
        }
        if (intent != null && intent.getBooleanExtra("screensaver", false)) {
            showNightstandScreensaver();
            return;
        }
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        if (hiddenSinceResume) return;
        if (screensaverActive) {
            dismissScreensaver();
            return;
        }
        // Single home pressed on Home
        if (cardPrompt != null) cardPrompt.dismiss();
        if (openPopup != null) openPopup.dismiss();
        closeAppDrawer(false);
        closeMenu(false);
        if (movingPos >= 0) finishMove();
        focusTile(0);
    }

    /**
     * Home went behind another app since it was last resumed. A Home press that brings it back
     * arrives before onResume, so this tells it apart from a Home press on Home itself.
     */
    private boolean hiddenSinceResume;

    @Override
    protected void onStop() {
        hiddenSinceResume = true;
        handler.removeCallbacks(cacheClean);
        handler.removeCallbacks(nowPlayingPoll);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, packageFilter);
        }
        tilesReady = false;
        promptsPaused = false;
        resuming = true;
        reloadApps(true);
        updateShamsiDate();
        updatePrayerTimes();
        updateDateAndPeriod();
        updateWifiLabel();
        updateWeatherAqi();
        WeatherAirQuality.syncIfDue(this, this::updateWeatherAqi);
        updateIslamicShelf();
        updateInputsButton();
        applyHomeScreenTheme();
        Location.Zone chosenPlace = Location.chosen(this);
        String zoneCode = (chosenPlace != null) ? chosenPlace.code : "WLY01";
        Azan.setMasterEnabled(this, false);
        NowPlayingManager.updateCurrentMedia(this);
        startWallpaperRotation();
        handler.postDelayed(minuteTick, MINUTE_MS);
        handler.removeCallbacks(nowPlayingPoll);
        handler.postDelayed(nowPlayingPoll, 2000);
        handler.removeCallbacks(cacheClean);
        handler.postDelayed(cacheClean, CacheCleaner.IDLE_MS);
        hiddenSinceResume = false;
    }

    /** Rebuilds the tiles (and the module cards), then puts focus back where it was. */
    private void reloadApps(boolean animate) {
        // Capture the app before rebinding can move focus and overwrite its key.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final boolean restoreFocus = animate || grid.hasFocus();
        long started = android.os.SystemClock.uptimeMillis();
        boolean rebuilt = reloadTiles();
        // Nothing installed or removed: Home comes back exactly as it was left, with no rebuild and
        // no shelf, glow or selection fading back in.
        if (restoreFocus && rebuilt) {
            glassShown = false;
            glider.hideNow();
            heroShown = false;
            heroShelf.setAlpha(0f);
            ambientShown = false;
            ambient.setAlpha(0f);
            syncGlassGlow();
        }
        android.util.Log.i("TvHome.Perf", "tiles " + (rebuilt ? "rebuilt" : "unchanged") + " in "
                + (android.os.SystemClock.uptimeMillis() - started) + " ms");
        // The tiles rise into place on the first launch only. Replayed on every return from an app,
        // it showed the glass tray empty for a moment and held Home back while the tiles arrived.
        if (animate && !reducedMotion && !tilesAnimatedOnce) grid.scheduleLayoutAnimation();
        if (animate) tilesAnimatedOnce = true;

        if (restoreFocus) {
            // Follow the same app, or the nearest remaining slot when it was removed.
            restoreFocus();
        } else {
            // A package change must not steal focus from a top button or the Inputs popup.
            int pos = adapter.positionOf(returnKey);
            lastFocused = Math.max(0, Math.min(pos >= 0 ? pos : returnSlot, adapter.getItemCount() - 1));
            lastFocusedKey = adapter.keyAt(lastFocused);
            if (pos < 0) hideHero();
            // A newly installed app may have brought a card with it.
            tilesReady = true;
            showCardPrompt();
        }
    }

    /**
     * Rebuilds the tiles; the module cards the owner chose to show stand in for their app's tile.
     * Every step into the card code runs under CardFeature: if it fails, the tiles are the ordinary
     * ones and cards stay off for this process.
     */
    private boolean reloadTiles() {
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final List<Cards.Card> none = Collections.emptyList();
        cards = moduleCards ? CardFeature.run(() -> Cards.discover(this, hiddenApps, Cards.load(Cards.storeFile(this))), none) : none;
        boolean rebuilt = adapter.reload(cards.isEmpty() ? none : CardFeature.run(() -> Cards.shown(cards), none));
        lastFocusedKey = returnKey;
        lastFocused = returnSlot;
        return rebuilt;
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    private void restoreFocus() {
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
            tilesReady = true;
            resuming = false;
            View now = getCurrentFocus();
            if (now != null && isTopPill(now)) moveTopHighlight(now);
            showCardPrompt();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(packageReceiver);
        handler.removeCallbacks(minuteTick);
        handler.removeCallbacks(wallTick);
        handler.removeCallbacks(nowPlayingPoll);
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        if (cardPrompt != null) cardPrompt.dismiss();
        if (openPopup != null) openPopup.dismiss();
        closeAppDrawer(false);
        closeMenu(false);
        finishMove();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) showCardPrompt();
    }

    /**
     * Android is short of memory while Home sits behind another app: give back what can be remade,
     * so the launcher is less likely to be reclaimed and Home comes back without starting over.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) Backdrop.trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sInstance != null && sInstance.get() == this) {
            sInstance = null;
        }
        if (uploadServer != null) {
            uploadServer.stop();
            uploadServer = null;
        }
        if (nowPlayingListener != null) {
            NowPlayingManager.unregisterListener(nowPlayingListener);
        }
        SoundEffects.release();
        wallDecoder.shutdownNow();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback);
            cm.unregisterNetworkCallback(vpnCallback);
        }
    }

    public void refreshLocationDependentData() {
        updateWeatherAqi();
        updatePrayerTimes();
        WeatherAirQuality.sync(this, this::updateWeatherAqi);
        PrayerTimes.sync(this, this::updatePrayerTimes);
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }

    private void handleBackPress() {
        if (addAppOverlay != null) {
            closeAddAppSheet(true);
            return;
        }
        if (appDrawerOverlay != null) {
            closeAppDrawer(true);
            return;
        }
        if (openMenu != null) {
            closeMenu(true);
            return;
        }
        if (screensaverActive) {
            dismissScreensaver();
            return;
        }
        if (wallpaperFullscreen) {
            setWallpaperFullscreen(false);
            return;
        }

        if (doubleBackPending) {
            if (singleBackRunnable != null) {
                handler.removeCallbacks(singleBackRunnable);
                singleBackRunnable = null;
            }
            doubleBackPending = false;
            showNightstandScreensaver();
        } else {
            doubleBackPending = true;
            singleBackRunnable = () -> {
                doubleBackPending = false;
                singleBackRunnable = null;
            };
            handler.postDelayed(singleBackRunnable, DOUBLE_BACK_DELAY_MS);
        }
    }

    private void setWallpaperFullscreen(boolean fullscreen) {
        if (wallpaperFullscreen == fullscreen) return;
        wallpaperFullscreen = fullscreen;
        float alpha = fullscreen ? 0f : 1f;
        int duration = reducedMotion ? 120 : 300;

        if (mainContent != null) {
            mainContent.animate().alpha(alpha).setDuration(duration).start();
        }
        if (heroShelf != null) {
            heroShelf.animate().alpha(alpha).setDuration(duration).start();
        }
        if (islamicShelf != null) {
            islamicShelf.animate().alpha(alpha).setDuration(duration).start();
        }
        if (glass != null) {
            glass.animate().alpha(alpha).setDuration(duration).start();
        }
        if (glider != null) {
            glider.animate().alpha(alpha).setDuration(duration).start();
        }
        if (ambient != null && fullscreen) {
            ambient.animate().alpha(0f).setDuration(duration).start();
        }

        if (wallView != null) {
            if (fullscreen) {
                wallView.clearColorFilter();
            } else {
                applyWallpaperColorFilter();
            }
        }
    }

    // ---- glass highlight -------------------------------------------------------------------------

    /** Reused by every focus move. */
    private final Rect tileRect = new Rect(), showRect = new Rect();

    @Override
    public void onTileFocused(int position, View card) {
        if (!resuming && hasWindowFocus()) SoundEffects.playNav();
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = tileRect;
        r.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            // The rim is drawn inset by pad, right on the zoomed tile, so its corners use the tile's radius as is.
            glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), density) * AppAdapter.FOCUS_SCALE);
        }
        glassTargetX = x;
        glassTargetY = y;
        // Exactly the zoomed card, behind it: at rest the app hides it completely, so it never lies
        // over an app and only shows while it travels between places.
        float cardRadius = AppAdapter.cornerRadius(card.getWidth(), density) * AppAdapter.FOCUS_SCALE;
        if (gliderOnPill) {
            // Down from the top menu it travels dark, and turns to glass once it lands, unseen behind
            // the app.
            glider.moveTo(x + pad, y + pad, x + w - pad, y + h - pad, cardRadius);
            glider.onArrive(() -> {
                if (!gliderOnPill) glider.setStyle(Glider.GLASS);
            });
        } else {
            // From app to app it is a pane of glass gliding behind them.
            glider.setStyle(Glider.GLASS);
            glider.moveTo(x + pad, y + pad, x + w - pad, y + h - pad, cardRadius);
        }
        gliderOnPill = false;
        glassShown = true;
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            if (weatherAirQualityView != null && weatherAirQualityView.getAlpha() < 1f) {
                weatherAirQualityView.animate().alpha(1f).setDuration(reducedMotion ? 100 : 160).start();
            }
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            // A 1px mismatch used to start a scroll on every dock move. Only scroll when the row is
            // actually off; reduced motion does it in one jump so it does not fight the highlight.
            boolean scroll = Math.abs(dy) > (int) (8 * density);
            if (scroll) {
                if (reducedMotion) grid.scrollBy(0, dy);
                else grid.smoothScrollBy(0, dy);
            }
            showHero(position, scroll ? r.top - dy : r.top);
        } else {
            // Below the dock there is no shelf, but the glow still takes this app's colour, so every
            // tile lights the room the same way the dock does.
            hideShelf();
            if (weatherAirQualityView != null && weatherAirQualityView.getAlpha() > 0f) {
                weatherAirQualityView.animate().alpha(0f).setDuration(reducedMotion ? 100 : 160).start();
            }
            showAmbient(adapter.ambientColorAt(position));
            // Scroll just enough so the whole glass (not only the tile) is visible.
            showRect.set(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, showRect, reducedMotion);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private boolean isCustomWallpaper() {
        String activeCat = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("active_category", "Natural");
        if ("Custom".equalsIgnoreCase(activeCat)) return true;
        if (wallpapers != null && !wallpapers.isEmpty()) {
            int idx = getSharedPreferences("wallpaper", MODE_PRIVATE).getInt("index", 0);
            if (idx >= 0 && idx < wallpapers.size()) {
                File f = wallpapers.get(idx);
                if (f != null && f.getAbsolutePath().contains("custom_wallpapers")) return true;
            }
        }
        return false;
    }

    private void showHero(int position, float dockTopInRoot) {
        int color = adapter.ambientColorAt(position);
        heroBottom = dockTopInRoot - HERO_GAP_DP * density;

        if (heroShelf == null || heroName == null || adapter == null || position >= COLUMNS) {
            hideShelf();
            showAmbient(color);
            return;
        }

        final String label = adapter.labelAt(position);
        if (label == null || label.isEmpty()) {
            hideShelf();
            showAmbient(color);
            return;
        }

        if (heroWhere != null) heroWhere.setVisibility(View.GONE);
        if (heroDesc != null) heroDesc.setVisibility(View.GONE);
        heroShelf.setVisibility(View.VISIBLE);
        heroShelf.animate().cancel();
        heroName.setText(label);

        float y = heroBottom - heroShelf.getHeight();
        if (heroShelf.getHeight() > 0) {
            heroShelf.setY(y);
        } else {
            heroShelf.post(() -> {
                if (heroBottom > 0 && heroShelf != null) {
                    heroShelf.setY(heroBottom - heroShelf.getHeight());
                }
            });
        }
        heroShelf.setTranslationX(0f);

        if (!heroShown) {
            if (firstFrameHeld || resuming || reducedMotion) {
                heroShelf.setAlpha(1f);
            } else {
                heroShelf.animate().alpha(1f).setDuration(150).start();
            }
            heroShown = true;
        } else {
            heroShelf.setAlpha(1f);
        }
        showAmbient(color);
    }

    /**
     * Styles the top menu floating pills (Inputs, Wi-Fi, Assistant, CandyBox, Settings)
     * according to the active theme (Dark Glow OLED, Neumorphic Soft UI Light, or Frosted Glass).
     */
    private void applyTopMenuTheme() {
        int[] topPillIds = new int[]{R.id.btnInputs, R.id.btnWifi, R.id.btnAssistant, R.id.btnCandyBox, R.id.btnSettings};
        float radius = 21f * density;
        boolean isDark = ThemeManager.isDarkGlow(this);
        boolean isNeu = ThemeManager.isNeumorphism(this);

        for (int id : topPillIds) {
            View v = findViewById(id);
            if (!(v instanceof TextView)) continue;
            TextView pill = (TextView) v;

            if (isDark) {
                // Dark Glow: Deep pitch-black OLED pill with sleek thin neon cyber stroke on focus
                pill.setTextColor(0xFFFFFFFF);
                pill.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                pill.setShadowLayer(4f, 0, 1f, 0x99000000);

                StateListDrawable sld = new StateListDrawable();
                GradientDrawable focused = new GradientDrawable();
                focused.setColor(0xFF0A0A0E);
                focused.setCornerRadius(radius);
                focused.setStroke(Math.max(1, Math.round(1.25f * density)), 0xFF00F0FF); // Sleek thin Neon Cyan

                GradientDrawable normal = new GradientDrawable();
                normal.setColor(0xEE08080A);
                normal.setCornerRadius(radius);
                normal.setStroke(Math.max(1, Math.round(1.0f * density)), 0x33FFFFFF);

                sld.addState(new int[]{android.R.attr.state_focused}, focused);
                sld.addState(new int[]{}, normal);
                pill.setBackground(sld);
            } else if (isNeu) {
                // Neumorphism Light: Extruded ("timbul") porcelain pill when normal, recessed ("cekungan") when focused!
                pill.setTextColor(0xFF2B3036);
                pill.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(0xFF2B3036));
                pill.setShadowLayer(0, 0, 0, 0);
                pill.setBackground(new NeumorphicDrawable(pill, radius, density, NeumorphicDrawable.MODE_EXTRUDED, true, glider, false));
            } else {
                // Default: Frosted Glass
                pill.setTextColor(0xFFFFFFFF);
                pill.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                pill.setShadowLayer(4f, 0, 1f, 0x99000000);
                pill.setBackground(new GlassDrawable(pill, radius, density, glider));
                Backdrop.watch(pill);
            }
        }
    }

    /**
     * Applies colors, backgrounds, and shadows across all home screen elements
     * (wallpaper filter, top menu, clock, date, weather, and shelf titles)
     * according to the active theme.
     */
    private void applyHomeScreenTheme() {
        applyWallpaperColorFilter();
        applyTopMenuTheme();

        boolean isDark = ThemeManager.isDarkGlow(this);
        boolean isNeu = ThemeManager.isNeumorphism(this);

        // Clock & Date Block
        android.widget.TextClock clock = findViewById(R.id.clock);
        if (clock != null) {
            clock.setTextColor(isNeu ? 0xFF2B3036 : 0xFFFFFFFF);
            if (isNeu) clock.setShadowLayer(0, 0, 0, 0);
            else clock.setShadowLayer(4f, 0, 1f, 0x80000000);
        }

        if (ampmView != null) {
            ampmView.setTextColor(isNeu ? 0xFF383E47 : 0xFFFFFFFF);
            if (isNeu) ampmView.setShadowLayer(0, 0, 0, 0);
            else ampmView.setShadowLayer(3f, 0, 1f, 0x80000000);
        }

        if (dateView != null) {
            dateView.setTextColor(isNeu ? 0xFF383E47 : 0xE6FFFFFF);
            if (isNeu) dateView.setShadowLayer(0, 0, 0, 0);
            else dateView.setShadowLayer(3f, 0, 1f, 0x80000000);
        }

        if (shamsiDate != null) {
            shamsiDate.setTextColor(isNeu ? 0xFF383E47 : 0xE6FFFFFF);
            if (isNeu) shamsiDate.setShadowLayer(0, 0, 0, 0);
            else shamsiDate.setShadowLayer(3f, 0, 1f, 0x80000000);
        }

        // Compact Rectangular Weather & Air Quality Widget Card (matching reference 1793782253828.jpg)
        if (weatherAirQualityView != null) {
            float cardRadius = 20f * density;
            float wellRadius = 12f * density;
            float trayRadius = 13f * density;
            float pillRadius = 10f * density;

            if (isDark) {
                StateListDrawable sld = new StateListDrawable();
                GradientDrawable focused = new GradientDrawable();
                focused.setColor(0xFF0A0A10);
                focused.setCornerRadius(cardRadius);
                focused.setStroke(Math.max(1, Math.round(1.25f * density)), 0xFF00F0FF);
                GradientDrawable normal = new GradientDrawable();
                normal.setColor(0xEE08080C);
                normal.setCornerRadius(cardRadius);
                normal.setStroke(Math.max(1, Math.round(1.0f * density)), 0x28FFFFFF);
                sld.addState(new int[]{android.R.attr.state_focused}, focused);
                sld.addState(new int[]{}, normal);
                weatherAirQualityView.setBackground(sld);

                if (weaHourlyBox != null) {
                    GradientDrawable hBg = new GradientDrawable();
                    hBg.setColor(0xFF10131C);
                    hBg.setCornerRadius(wellRadius);
                    hBg.setStroke(Math.max(1, Math.round(1.0f * density)), 0x2E00F0FF);
                    weaHourlyBox.setBackground(hBg);
                }
                if (weaBottomTray != null) {
                    GradientDrawable tBg = new GradientDrawable();
                    tBg.setColor(0xFF0E1018);
                    tBg.setCornerRadius(trayRadius);
                    tBg.setStroke(Math.max(1, Math.round(1.0f * density)), 0x1E00F0FF);
                    weaBottomTray.setBackground(tBg);
                }
                for (TextView p : new TextView[]{weaPillAqi, weaPillHum, weaPillWind, weaPillRain}) {
                    if (p == null) continue;
                    GradientDrawable pBg = new GradientDrawable();
                    pBg.setColor(0xFF151924);
                    pBg.setCornerRadius(pillRadius);
                    pBg.setStroke(Math.max(1, Math.round(1.0f * density)), 0x3300F0FF);
                    p.setBackground(pBg);
                }
            } else if (isNeu) {
                StateListDrawable sld = new StateListDrawable();
                sld.addState(new int[]{android.R.attr.state_focused},
                        new NeumorphicDrawable(cardRadius, density, NeumorphicDrawable.MODE_RECESSED, false));
                sld.addState(new int[]{},
                        new NeumorphicDrawable(cardRadius, density, NeumorphicDrawable.MODE_EXTRUDED, true));
                weatherAirQualityView.setBackground(sld);

                if (weaHourlyBox != null) {
                    weaHourlyBox.setBackground(new NeumorphicDrawable(wellRadius, density, NeumorphicDrawable.MODE_RECESSED, false));
                }
                if (weaBottomTray != null) {
                    weaBottomTray.setBackground(new NeumorphicDrawable(trayRadius, density, NeumorphicDrawable.MODE_RECESSED, false));
                }
                for (TextView p : new TextView[]{weaPillAqi, weaPillHum, weaPillWind, weaPillRain}) {
                    if (p == null) continue;
                    p.setBackground(new NeumorphicDrawable(pillRadius, density, NeumorphicDrawable.MODE_EXTRUDED, false));
                }
            } else {
                // Pure Frosted Glass
                StateListDrawable sld = new StateListDrawable();
                GradientDrawable focused = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{0x732A3140, 0x731C222E});
                focused.setCornerRadius(cardRadius);
                focused.setStroke(Math.max(2, Math.round(1.5f * density)), 0xF2FFFFFF);
                GradientDrawable normal = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{0x52252B38, 0x52181C26});
                normal.setCornerRadius(cardRadius);
                normal.setStroke(Math.max(1, Math.round(1.0f * density)), 0x45FFFFFF);
                sld.addState(new int[]{android.R.attr.state_focused}, focused);
                sld.addState(new int[]{}, normal);
                weatherAirQualityView.setBackground(sld);

                if (weaHourlyBox != null) {
                    GradientDrawable hBg = new GradientDrawable();
                    hBg.setColor(0x1EFFFFFF);
                    hBg.setCornerRadius(wellRadius);
                    hBg.setStroke(Math.max(1, Math.round(1.0f * density)), 0x33FFFFFF);
                    weaHourlyBox.setBackground(hBg);
                }
                if (weaBottomTray != null) {
                    GradientDrawable tBg = new GradientDrawable();
                    tBg.setColor(0x14FFFFFF);
                    tBg.setCornerRadius(trayRadius);
                    tBg.setStroke(Math.max(1, Math.round(1.0f * density)), 0x26FFFFFF);
                    weaBottomTray.setBackground(tBg);
                }
                for (TextView p : new TextView[]{weaPillAqi, weaPillHum, weaPillWind, weaPillRain}) {
                    if (p == null) continue;
                    GradientDrawable pBg = new GradientDrawable();
                    pBg.setColor(0x24FFFFFF);
                    pBg.setCornerRadius(pillRadius);
                    pBg.setStroke(Math.max(1, Math.round(1.0f * density)), 0x38FFFFFF);
                    p.setBackground(pBg);
                }
            }

            int headerColor = isNeu ? 0xFF363E4A : 0xFFFFFFFF;
            int bigTempColor = isNeu ? 0xFF636C78 : 0xFFFFFFFF;
            int condColor = isNeu ? 0xFF363E4A : 0xFFFFFFFF;
            int subColor = isNeu ? 0xFF4A5360 : 0xDCE2E8F0;
            int hourTimeColor = isNeu ? 0xFF28303D : 0xCCFFFFFF;
            int hourValColor = isNeu ? 0xFF1F2632 : 0xFFFFFFFF;
            int pillTextColor = isNeu ? 0xFF222934 : 0xF2FFFFFF;
            int iconTint = isNeu ? 0xFF636C78 : 0xFFFFFFFF;
            int divColor = isNeu ? 0x1F000000 : (isDark ? 0x2E00F0FF : 0x28FFFFFF);

            if (weaHeaderLoc != null) {
                weaHeaderLoc.setTextColor(headerColor);
                weaHeaderLoc.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(headerColor));
                if (isNeu) weaHeaderLoc.setShadowLayer(0, 0, 0, 0);
                else weaHeaderLoc.setShadowLayer(2.5f, 0, 1f, 0x80000000);
            }
            for (View d : new View[]{weaHeaderDiv, weaHourDiv1, weaHourDiv2}) {
                if (d != null) d.setBackgroundColor(divColor);
            }
            if (weaMainTemp != null) {
                weaMainTemp.setTextColor(bigTempColor);
                if (isNeu) weaMainTemp.setShadowLayer(0, 0, 0, 0);
                else weaMainTemp.setShadowLayer(3f, 0, 1f, 0x80000000);
            }
            if (weaMainCond != null) {
                weaMainCond.setTextColor(condColor);
                if (isNeu) weaMainCond.setShadowLayer(0, 0, 0, 0);
                else weaMainCond.setShadowLayer(2.5f, 0, 1f, 0x80000000);
            }
            if (weaMainSub != null) {
                weaMainSub.setTextColor(subColor);
                if (isNeu) weaMainSub.setShadowLayer(0, 0, 0, 0);
                else weaMainSub.setShadowLayer(2f, 0, 1f, 0x80000000);
            }
            for (TextView ht : new TextView[]{weaHourTime1, weaHourTime2, weaHourTime3}) {
                if (ht == null) continue;
                ht.setTextColor(hourTimeColor);
                if (isNeu) ht.setShadowLayer(0, 0, 0, 0);
                else ht.setShadowLayer(2f, 0, 1f, 0x80000000);
            }
            for (TextView hv : new TextView[]{weaHourVal1, weaHourVal2, weaHourVal3}) {
                if (hv == null) continue;
                hv.setTextColor(hourValColor);
                if (isNeu) hv.setShadowLayer(0, 0, 0, 0);
                else hv.setShadowLayer(2f, 0, 1f, 0x80000000);
            }
            for (TextView hp : new TextView[]{weaPillAqi, weaPillHum, weaPillWind, weaPillRain}) {
                if (hp == null) continue;
                hp.setTextColor(pillTextColor);
                hp.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(iconTint));
                if (isNeu) hp.setShadowLayer(0, 0, 0, 0);
                else hp.setShadowLayer(2f, 0, 1f, 0x80000000);
            }
            updateWeatherAqi();
        }

        // Focused Row-1 App Title (above dock left)
        if (heroName != null) {
            heroName.setTextColor(isNeu ? 0xFF232830 : 0xFFFFFFFF);
            if (isNeu) heroName.setShadowLayer(0, 0, 0, 0);
            else heroName.setShadowLayer(4f, 0, 1f, 0x99000000);
        }
        if (heroWhere != null) heroWhere.setVisibility(View.GONE);
        if (heroDesc != null) heroDesc.setVisibility(View.GONE);

        // Dynamic Now Playing Pill Theme Styling (Apple TV Style: Single Focus Glanceable Pill)
        if (nowPlayingCard != null) {
            float pillRadius = 21f * density;
            if (isNeu) {
                StateListDrawable cardSld = new StateListDrawable();
                cardSld.addState(new int[]{android.R.attr.state_focused},
                        new NeumorphicDrawable(pillRadius, density, NeumorphicDrawable.MODE_RECESSED, false));
                cardSld.addState(new int[]{},
                        new NeumorphicDrawable(pillRadius, density, NeumorphicDrawable.MODE_EXTRUDED, true));
                nowPlayingCard.setBackground(cardSld);

                if (cardTrackInfo != null) cardTrackInfo.setBackground(null);
                if (cardAlbumArt != null) cardAlbumArt.setBackground(null);

                if (cardTitle != null) {
                    cardTitle.setTextColor(0xFF222934);
                    cardTitle.setShadowLayer(0, 0, 0, 0);
                }
                if (cardSubtitle != null) {
                    cardSubtitle.setTextColor(0xFF5A626E);
                    cardSubtitle.setShadowLayer(0, 0, 0, 0);
                }
            } else if (isDark) {
                StateListDrawable cardSld = new StateListDrawable();
                GradientDrawable foc = new GradientDrawable();
                foc.setColor(0xF2101726);
                foc.setCornerRadius(pillRadius);
                foc.setStroke(Math.max(1, Math.round(1.5f * density)), 0xFF00F0FF);

                GradientDrawable norm = new GradientDrawable();
                norm.setColor(0xF20A0A10);
                norm.setCornerRadius(pillRadius);
                norm.setStroke(Math.max(1, Math.round(1.1f * density)), 0x4D00F0FF);

                cardSld.addState(new int[]{android.R.attr.state_focused}, foc);
                cardSld.addState(new int[]{}, norm);
                nowPlayingCard.setBackground(cardSld);

                if (cardTrackInfo != null) cardTrackInfo.setBackground(null);
                if (cardAlbumArt != null) cardAlbumArt.setBackground(null);

                if (cardTitle != null) {
                    cardTitle.setTextColor(0xFFFFFFFF);
                    cardTitle.setShadowLayer(3f, 0, 1f, 0x80000000);
                }
                if (cardSubtitle != null) {
                    cardSubtitle.setTextColor(0xFF00F0FF);
                    cardSubtitle.setShadowLayer(2f, 0, 1f, 0x80000000);
                }
            } else {
                StateListDrawable cardSld = new StateListDrawable();
                GradientDrawable foc = new GradientDrawable();
                foc.setColor(0x80252B38);
                foc.setCornerRadius(pillRadius);
                foc.setStroke(Math.max(1, Math.round(1.5f * density)), 0xFFFFFFFF);

                cardSld.addState(new int[]{android.R.attr.state_focused}, foc);
                cardSld.addState(new int[]{}, new GlassDrawable(nowPlayingCard, pillRadius, density, glider, 0.45f, true));
                nowPlayingCard.setBackground(cardSld);
                Backdrop.watch(nowPlayingCard);

                if (cardTrackInfo != null) cardTrackInfo.setBackground(null);
                if (cardAlbumArt != null) cardAlbumArt.setBackground(null);

                if (cardTitle != null) {
                    cardTitle.setTextColor(0xFFFFFFFF);
                    cardTitle.setShadowLayer(3f, 0, 1f, 0x80000000);
                }
                if (cardSubtitle != null) {
                    cardSubtitle.setTextColor(0xB3FFFFFF);
                    cardSubtitle.setShadowLayer(2f, 0, 1f, 0x80000000);
                }
            }
        }
    }

    /**
     * Lines the top-right buttons up with the dock: the Settings circle's right edge sits on the
     * right edge of the last dock card, and Inputs follows on its left. Card width follows the
     * column count and the screen, so the edge is measured rather than written down.
     */
    private void alignTopButtons() {
        // This runs on every layout pass, which during a grid scroll is every frame, so it keeps
        // its views and rectangles rather than looking them up or allocating each time.
        if (topRow == null) {
            topRow = findViewById(R.id.topButtons);
            topSettings = findViewById(R.id.btnSettings);
        }
        if (topRow == null || topSettings.getWidth() == 0) return;
        RecyclerView.ViewHolder last = grid.findViewHolderForAdapterPosition(COLUMNS - 1);
        if (last == null) return;
        View card = last.itemView.findViewById(R.id.card);
        if (card == null || card.getWidth() == 0) return;
        // Layout positions, not drawn ones: a focused card is scaled up, and the row's own shift
        // must not be counted twice.
        alignCard.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, alignCard);
        alignButton.set(0, 0, topSettings.getWidth(), topSettings.getHeight());
        root.offsetDescendantRectToMyCoords(topSettings, alignButton);
        float shift = alignCard.right - alignButton.right;
        if (Math.abs(topRow.getTranslationX() - shift) >= 1f) topRow.setTranslationX(shift);

        RecyclerView.ViewHolder first = grid.findViewHolderForAdapterPosition(0);
        if (first != null && heroShelf != null) {
            View firstCard = first.itemView.findViewById(R.id.card);
            if (firstCard != null && firstCard.getWidth() > 0) {
                alignCard.set(0, 0, firstCard.getWidth(), firstCard.getHeight());
                root.offsetDescendantRectToMyCoords(firstCard, alignCard);
                if (Math.abs(heroShelf.getX() - alignCard.left) >= 1f) {
                    heroShelf.setX(alignCard.left);
                }
                View lb = findViewById(R.id.leftBlock);
                if (lb != null && gridWrap != null && weatherAirQualityView != null
                        && weatherAirQualityView.getHeight() > 0 && grid.getPaddingTop() > 0) {
                    // Use pure layout coordinates inside mainContent (getTop/getBottom ignore translationY
                    // and gridWrap.getTop() + grid.getPaddingTop() is the stationary parked dock top).
                    float clockBottomInMain = lb.getBottom();
                    float parkedDockTopInMain = gridWrap.getTop() + grid.getPaddingTop();
                    float shelfH = (heroName != null && heroName.getHeight() > 0)
                            ? heroName.getHeight() : (24f * density);
                    float shelfTopInMain = parkedDockTopInMain - (HERO_GAP_DP * density) - shelfH;
                    float targetCenterY = (clockBottomInMain + shelfTopInMain) * 0.5f;
                    float desiredTopInMain = targetCenterY - (weatherAirQualityView.getHeight() * 0.5f);
                    float baseTopInMain = weatherAirQualityView.getTop();
                    float dy = Math.round(desiredTopInMain - baseTopInMain);
                    if (Math.abs(weatherAirQualityView.getTranslationY() - dy) >= 1f) {
                        weatherAirQualityView.setTranslationY(dy);
                    }
                }
            }
        }
    }

    /**
     * The one focus shape (Glider) goes behind everything it marks; the pills' glass makes way for it
     * as it arrives, and the dock tray paints it over its own glass.
     */
    private void setUpGlider() {
        glider = new Glider(this, reducedMotion);
        // Behind the shelf's text, the pills and the tiles, so it never draws over any of them.
        root.addView(glider, root.indexOfChild(heroShelf), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The glider replaces the glass selection outright.
        glass.setVisibility(View.GONE);
    }

    private boolean isTopPill(View v) {
        int id = v.getId();
        return id == R.id.btnInputs || id == R.id.btnWifi || id == R.id.btnAssistant || id == R.id.btnCandyBox || id == R.id.btnSettings;
    }

    /** Sends the focus shape onto a pill: from a tile it glides up and narrows into the pill. */
    private void moveTopHighlight(View pill) {
        if (resuming) return;
        SoundEffects.playNav();
        hideShelf();
        pill.getLocationInWindow(pillAt);
        glider.getLocationInWindow(rootAt);
        float l = pillAt[0] - rootAt[0], t = pillAt[1] - rootAt[1];
        // The pills do not scroll with the grid, so the shape stops following the grid's scroll.
        glassShown = false;
        // Up to the top menu it is dark at once, and travels that way.
        gliderOnPill = true;
        glider.setStyle(Glider.DARK);
        if (!glider.isShowing()) {
            glider.appearAt(l, t, l + pill.getWidth(), t + pill.getHeight(), pill.getHeight() / 2f);
        } else {
            glider.moveTo(l, t, l + pill.getWidth(), t + pill.getHeight(), pill.getHeight() / 2f);
        }
    }

    /** The shelf's two lines. An empty description takes no room, so the title keeps its place. */
    private void setHeroText(String title, String where, String desc) {
        if (heroName == null || heroWhere == null || heroDesc == null) return;
        heroName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, titleSizeSp(title));
        heroName.setText(title);
        heroWhere.setText(where);
        heroWhere.setVisibility(where == null || where.isEmpty() ? View.GONE : View.VISIBLE);
        // The description sits further down under a country line; with none, close under the name.
        ViewGroup.MarginLayoutParams descLp = (ViewGroup.MarginLayoutParams) heroDesc.getLayoutParams();
        if (descLp != null) {
            int top = Math.round(((where == null || where.isEmpty()) ? 6f : 9.5f) * density);
            if (descLp.topMargin != top) {
                descLp.topMargin = top;
                heroDesc.setLayoutParams(descLp);
            }
        }
        heroDesc.setText(desc);
        heroDesc.setVisibility(desc == null || desc.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Shouting titles are calmed down: "THE RIBBON HERO" becomes "The Ribbon Hero". A title that
     * already has lower case is left exactly as its studio wrote it, so "LEGO Star Wars" and
     * "WALL·E" keep their capitals instead of being flattened to "Lego" and "Wall·e".
     */
    private static String titleCase(String title) {
        if (title == null || title.isEmpty()) return title;
        for (int i = 0; i < title.length(); i++) {
            if (Character.isLowerCase(title.charAt(i))) return title;
        }
        StringBuilder out = new StringBuilder(title.length());
        boolean start = true;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            out.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            // A new word begins after a space or any of the marks a title tends to use.
            start = c == ' ' || c == '-' || c == ':' || c == '.' || c == '\'' || c == '(' || c == '/';
        }
        return out.toString();
    }

    /**
     * The largest size at which the name still fits on one line, up to its share of the width.
     * Names run longer than app names ("Wroclaw Market Square"), and a name cut short tells the
     * viewer nothing, so a long one is set smaller rather than clipped.
     */
    private float titleSizeSp(String title) {
        if (title == null || title.isEmpty() || heroNameMaxWidth <= 0) return HERO_TITLE_MAX_SP;
        android.text.TextPaint paint = new android.text.TextPaint(heroName.getPaint());
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        for (float sp = HERO_TITLE_MAX_SP; sp > HERO_TITLE_MIN_SP; sp -= 2f) {
            paint.setTextSize(sp * scaled);
            if (paint.measureText(title) <= heroNameMaxWidth) return sp;
        }
        return HERO_TITLE_MIN_SP;
    }

    /**
     * Follows the rotation: the shelf names whichever place is on screen. Called after the picture
     * changes, so the words and the picture never disagree.
     */
    private void setPlace(File picture) {
        placeName = null;
        placeText = "";
        placeCountry = "";
    }

    /**
     * The glow used to be a full-screen radial GradientDrawable. On Android 10 that drawable is
     * drawn on the CPU, so fading it for half a second redrew 1920×1080 every frame and the
     * highlight looked like it was running at a few frames per second. A small bitmap stretched
     * over the screen is one texture; changing apps only uploads it once.
     */
    private void setAmbient(int color) {
        setAmbient(color, false);
    }

    private FadeTopFrame gridWrap;

    /** Turns the top fade on while any row reaches into its band, with room for a focused tile's zoom. */
    private void updateTopFade() {
        if (gridWrap == null) return;
        float band = 56 * density + 24 * density;
        boolean inBand = false;
        for (int i = 0; i < grid.getChildCount() && !inBand; i++) {
            View child = grid.getChildAt(i);
            inBand = grid.getTop() + child.getTop() + child.getTranslationY() < band;
        }
        gridWrap.setFadeStrength(inBand ? 1f : 0f);
    }

    /**
     * The dock row sits towards the bottom of the screen, leaving room for rows_below_dock grid rows
     * under it; the rest of the grid stays below until you press Down. Sets the grid's top padding
     * for that, and says whether it was already right.
     */
    private boolean parkDock() {
        int h = grid.getHeight();
        if (h <= 0) return false;
        // Item height follows the tile size, which follows the column count (AppAdapter.tileWidthPx).
        int inner = grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight();
        int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(AppAdapter.tileWidthPx(inner, COLUMNS, density)), density);
        int dockRow = itemH + (int) ((6 + 12) * density);   // item height plus DockDecoration offsets
        int want = Math.max(0, h - dockRow - itemH * rowsBelowDockSetting - (int) (30 * density));
        if (grid.getPaddingTop() == want) return true;
        grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
        return false;
    }

    /**
     * Home's first frame waits until Home is whole: tiles bound and laid out, the dock parked at the
     * bottom and the wallpaper in. Drawn as soon as the window was ready, it showed the glass tray
     * floating empty near the top, then the tiles, then the dock dropping into place. This matters
     * most after a long stay in another app, when Android has reclaimed the launcher and Home starts
     * from nothing. The wait is capped, so Home can never hang on a slow picture.
     */
    private void holdFirstFrame() {
        final long start = android.os.SystemClock.uptimeMillis();
        firstFrameHeld = true;
        final boolean expectsWallpaper = !wallpapers.isEmpty() || (config.wallpaper != null && !placesMode) || config.wallpaperOnline;
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // Parked here rather than by the layout listener alone: while frames are held, the
                // second layout pass that listener relies on does not always come.
                boolean parked = parkDock();
                // The place's name and lines come in the same frame as the picture they describe,
                // whenever focus lands in the dock, where the shelf shows.
                boolean whole = adapter.getItemCount() > 0 && grid.getChildCount() > 0 && parked
                        && (wallBitmap != null || !expectsWallpaper) && (heroShown || lastFocused >= COLUMNS);
                if (!whole && android.os.SystemClock.uptimeMillis() - start < FIRST_FRAME_WAIT_MS) {
                    return false;   // skip this frame; the next traversal asks again
                }
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                firstFrameHeld = false;
                android.util.Log.i("TvHome.Perf", "first frame after " + (android.os.SystemClock.uptimeMillis() - start)
                        + " ms" + (whole ? "" : " (gave up waiting: tiles=" + adapter.getItemCount()
                        + " laidOut=" + grid.getChildCount() + " dockParked=" + parked
                        + " wallpaper=" + (wallBitmap != null) + ")"));
                return true;
            }
        });
    }

    /** Tells the glass the glow's colour and strength, so what shows through matches around it. */
    private void syncGlassGlow() {
        Backdrop.setGlow(ambientShape(), glowFrom, ambientColor, glowMix, ambient.getAlpha());
    }

    /** Runs the glass through the same colour blend as the room's TransitionDrawable. */
    private void blendGlassGlow(int from, long ms) {
        if (glowBlend != null) glowBlend.cancel();
        glowFrom = from;
        glowMix = 0f;
        glowBlend = android.animation.ValueAnimator.ofFloat(0f, 1f);
        glowBlend.setDuration(ms);
        glowBlend.addUpdateListener(a -> {
            glowMix = (float) a.getAnimatedValue();
            syncGlassGlow();
        });
        glowBlend.start();
    }

    /** The glow's shape in white, drawn on first use and kept: a tint gives it each app's colour. */
    private Bitmap ambientShape() {
        if (ambientShape != null) return ambientShape;
        int w = 480;
        int h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(
                w * 0.32f, h * 0.42f, w * 0.62f,
                new int[]{0x5CFFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        new Canvas(bmp).drawRect(0, 0, w, h, paint);
        ambientShape = bmp;
        return bmp;
    }

    /** With crossFade, the old glow fades into the new one (two small textures, one blend). */
    private void setAmbient(int color, boolean crossFade) {
        if (color == ambientColor && ambient.getBackground() != null) return;
        android.graphics.drawable.Drawable old = ambient.getBackground();
        if (old instanceof android.graphics.drawable.TransitionDrawable) {
            android.graphics.drawable.TransitionDrawable t = (android.graphics.drawable.TransitionDrawable) old;
            old = t.getDrawable(t.getNumberOfLayers() - 1);   // a blend still running: start from where it was heading
        }
        int previous = ambientColor;
        ambientColor = color;
        // Every app's glow is the same shape in a different colour, so the shape is drawn once and
        // each app only tints it. Drawing a fresh bitmap per move cost a CPU fill, half a megabyte
        // and a texture upload every time focus moved, which is what made moving around feel heavy
        // once every tile, not only the dock, tinted the room.
        BitmapDrawable drawable = new BitmapDrawable(getResources(), ambientShape());
        drawable.setGravity(Gravity.FILL);
        drawable.setColorFilter(new android.graphics.PorterDuffColorFilter(
                0xFF000000 | color, android.graphics.PorterDuff.Mode.SRC_IN));
        if (crossFade && old != null) {
            android.graphics.drawable.TransitionDrawable t = new android.graphics.drawable.TransitionDrawable(
                    new android.graphics.drawable.Drawable[]{old, drawable});
            t.setCrossFadeEnabled(true);
            ambient.setBackground(t);
            t.startTransition(450);
            // The glass blends with the room, over the same 450 ms, instead of jumping ahead of it.
            blendGlassGlow(previous, 450);
        } else {
            ambient.setBackground(drawable);
            if (glowBlend != null) glowBlend.cancel();
            glowFrom = color;
            glowMix = 1f;
            syncGlassGlow();
        }
    }

    /** Focus has left the apps altogether: both the shelf and the glow go. */
    private void hideHero() {
        hideShelf();
        if (!ambientShown) return;
        ambientShown = false;
        ambient.animate().alpha(0f).setDuration(reducedMotion ? 140 : 300)
                .setUpdateListener(a -> syncGlassGlow()).start();
    }

    private void hideShelf() {
        if (!heroShown) return;
        heroShown = false;
        if (heroShelf != null) heroShelf.animate().alpha(0f).setDuration(reducedMotion ? 100 : 160).start();
        if (islamicShelf != null) islamicShelf.animate().alpha(0f).setDuration(reducedMotion ? 100 : 160).start();
    }

    /**
     * Ambient glow disabled: keeps background and frosted glass clean, neutral, and consistent like Apple TV.
     */
    private void showAmbient(int color) {
        // Disabled: do not tint background or frosted glass
    }

    private void hideGlass() {
        glassShown = false;
    }

    // ---- navigation ------------------------------------------------------------------------------

    // ---- arranging the tiles ---------------------------------------------------------------------

    /** The tile being moved (adapter position), or -1; its card wiggles while it is. */
    private int movingPos = -1;
    private View movingCard;
    private android.animation.ObjectAnimator wiggle;
    /** A key whose release is swallowed: the OK or Back that put a moved tile down must not also open it. */
    private int swallowRelease = -1;
    /**
     * Slides the tiles into their new places while an app is moved. The grid has no item animations
     * otherwise: they would replay on every rebuild and cost frames while scrolling.
     */
    private androidx.recyclerview.widget.DefaultItemAnimator moveSlide;

    /** Holding OK on an app: a glass menu to move it, or to open Android's page for it. */
    @Override
    public boolean onTileHeld(int position, View card) {
        if (openMenu != null || movingPos >= 0) return true;
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final String key = adapter.keyAt(position);
        if (adapter.isAddApp(position)) {
            addInputRow(rows, () -> closeMenu(false), R.drawable.ic_input, "Tambah Aplikasi", -1,
                    this::showAddAppSheet);
        } else if (adapter.isAppDrawer(position)) {
            if (adapter.canMove(position)) {
                addInputRow(rows, () -> {
                    glider.hideNow();
                    gliderOnPill = false;
                    closeMenu(true);
                }, R.drawable.ic_move, getString(R.string.tile_move), -1, () -> startMove(key));
            }
            addInputRow(rows, () -> closeMenu(false), R.drawable.ic_input, "Buka Semua Aplikasi", -1,
                    this::showGlassAppDrawer);
        } else {
            if (adapter.canMove(position)) {
                addInputRow(rows, () -> {
                    // No selection gliding back from the menu: it is simply gone, and the app wiggles.
                    glider.hideNow();
                    gliderOnPill = false;
                    closeMenu(true);
                }, R.drawable.ic_move, getString(R.string.tile_move), -1, () -> startMove(key));
            }
            addInputRow(rows, () -> closeMenu(false), R.drawable.ic_info, getString(R.string.tile_app_info), -1,
                    () -> adapter.openAppInfo(adapter.positionOf(key)));
            if (adapter.canRemove(position)) {
                addInputRow(rows, () -> closeMenu(false), R.drawable.ic_close, "Hapus", -1, () -> {
                    int pos = adapter.positionOf(key);
                    String removedLabel = adapter.removeAppAt(pos);
                    if (removedLabel != null) {
                        reloadTiles();
                        int nextFocus = Math.max(0, Math.min(pos, adapter.getItemCount() - 1));
                        focusTile(nextFocus);
                        Toast.makeText(MainActivity.this, removedLabel + " dihapus dari beranda", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = Math.max((int) (190 * density), panel.getMeasuredWidth());
        // Under the app when there is room; over it in the dock, which sits near the bottom.
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        card.getLocationInWindow(menuAt);
        root.getLocationInWindow(menuGliderAt);
        int below = menuAt[1] - menuGliderAt[1] + card.getHeight() + (int) (18 * density) + panel.getMeasuredHeight();
        showMenu(panel, rows, card, width, below > root.getHeight() - (int) (24 * density));
        return true;
    }

    /** Picks the app up: it wiggles, the arrows carry it, and OK or Back puts it down. */
    private void startMove(String key) {
        int pos = adapter.positionOf(key);
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh == null || !adapter.canMove(pos)) return;
        movingPos = pos;
        movingCard = vh.itemView.findViewById(R.id.card);
        movingCard.requestFocus();
        glider.setPaneHidden(true);
        if (moveSlide == null) {
            moveSlide = new androidx.recyclerview.widget.DefaultItemAnimator();
            moveSlide.setSupportsChangeAnimations(false);
            moveSlide.setMoveDuration(reducedMotion ? 140 : 220);
        }
        grid.setItemAnimator(moveSlide);
        float tilt = reducedMotion ? 0.8f : 1.4f;
        wiggle = android.animation.ObjectAnimator.ofFloat(movingCard, View.ROTATION, -tilt, tilt);
        wiggle.setDuration(reducedMotion ? 260 : 140);
        wiggle.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        wiggle.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        wiggle.start();
    }

    /** Puts the moving app down where it is; the order was kept as it moved. */
    private void finishMove() {
        if (movingPos < 0) return;
        if (wiggle != null) wiggle.cancel();
        wiggle = null;
        if (movingCard != null) movingCard.animate().rotation(0f).setDuration(120).start();
        movingCard = null;
        movingPos = -1;
        glider.setPaneHidden(false);
        // Back to no item animations once the last slide has finished.
        RecyclerView.ItemAnimator slide = grid.getItemAnimator();
        if (slide != null) slide.isRunning(() -> {
            if (movingPos < 0 && grid.getItemAnimator() == slide) grid.setItemAnimator(null);
        });
    }

    /** Carries the moving app one step, the other tiles making room, and the selection follows it. */
    private void stepMove(int keyCode) {
        int last = adapter.lastMovable();
        int to = movingPos;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (movingPos % COLUMNS > 0) to = movingPos - 1;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (movingPos % COLUMNS < COLUMNS - 1) to = Math.min(movingPos + 1, last);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (movingPos >= COLUMNS) to = movingPos - COLUMNS;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (movingPos / COLUMNS < last / COLUMNS) to = Math.min(movingPos + COLUMNS, last);
                break;
            default:
                return;
        }
        if (!adapter.move(movingPos, to)) return;
        movingPos = to;
        final View card = movingCard;
        final int pos = to;
        // Once the tiles have shifted, glide the selection (and the shelf, and the scroll) to it.
        grid.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                grid.removeOnLayoutChangeListener(this);
                grid.post(() -> {
                    if (movingPos == pos && card != null && card.isAttachedToWindow()) onTileFocused(pos, card);
                });
            }
        });
        grid.requestLayout();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        lastUserInteraction = android.os.SystemClock.elapsedRealtime();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        lastUserInteraction = android.os.SystemClock.elapsedRealtime();
        if (screensaverActive) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                dismissScreensaver();
            }
            return true;
        }
        int code = event.getKeyCode();
        if (wallpaperFullscreen) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                setWallpaperFullscreen(false);
                if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                    doubleBackPending = true;
                    singleBackRunnable = () -> {
                        doubleBackPending = false;
                        singleBackRunnable = null;
                    };
                    handler.postDelayed(singleBackRunnable, DOUBLE_BACK_DELAY_MS);
                }
            }
            return true;
        }
        if (doubleBackPending && code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE) {
            if (singleBackRunnable != null) {
                handler.removeCallbacks(singleBackRunnable);
                singleBackRunnable = null;
            }
            doubleBackPending = false;
        }
        if (code == swallowRelease && event.getAction() == KeyEvent.ACTION_UP) {
            swallowRelease = -1;
            return true;
        }
        if (code == KeyEvent.KEYCODE_APP_SWITCH) {
            return true; // Live apps disabled per request
        }
        if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_ALL_APPS) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.isLongPress() || (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0 || event.getRepeatCount() > 0) {
                    if (!isHomeLongPressed) {
                        isHomeLongPressed = true;
                        handler.removeCallbacks(longPressHomeRunnable);
                        longPressHomeRunnable.run();
                    }
                    return true;
                }
                if (event.getRepeatCount() == 0) {
                    isHomeLongPressed = false;
                    handler.removeCallbacks(longPressHomeRunnable);
                    handler.postDelayed(longPressHomeRunnable, 450);
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(longPressHomeRunnable);
                if (isHomeLongPressed) {
                    isHomeLongPressed = false;
                    return true;
                }
                if (screensaverActive) {
                    dismissScreensaver();
                    return true;
                }
                if (cardPrompt != null) cardPrompt.dismiss();
                if (openPopup != null) openPopup.dismiss();
                closeAddAppSheet(false);
                closeAppDrawer(false);
                closeMenu(false);
                if (movingPos >= 0) finishMove();
                focusTile(0);
                return true;
            }
            return true;
        }
        if (movingPos < 0) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    handleBackPress();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) stepMove(code);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (down) {
                    finishMove();
                    swallowRelease = code;
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return position % COLUMNS == 0;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return position % COLUMNS == COLUMNS - 1 || position == count - 1;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) {
                if (position <= 2 && weatherAirQualityView != null && weatherAirQualityView.getVisibility() == View.VISIBLE) {
                    weatherAirQualityView.requestFocus();
                    return true;
                }
                return false;          // dock row: let focus move to the top bar
            }
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    /** The tile {@link #focusWhenSettled} focuses once the grid stops scrolling to it. */
    private int pendingFocus;
    private final RecyclerView.OnScrollListener focusWhenSettled = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
            rv.removeOnScrollListener(this);
            RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pendingFocus);
            View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
            if (target != null) target.requestFocus();
        }
    };

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) {
            (btnWifi.getVisibility() == View.VISIBLE ? btnWifi : findViewById(R.id.btnSettings)).requestFocus();
            return;
        }
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles. One listener at most,
        // aimed at the latest target, so asking again mid-scroll cannot pull focus back to an older one.
        pendingFocus = pos;
        grid.removeOnScrollListener(focusWhenSettled);
        grid.addOnScrollListener(focusWhenSettled);
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateShamsiDate() {
        if (shamsi) shamsiDate.setText(Shamsi.today());
    }

    private void updatePrayerTimes() {
        applyPrayerVisibility();
    }

    /** Hides the Waktu Solat, Ayah/Doa and Islamic shelf completely. */
    private void applyPrayerVisibility() {
        if (prayerTimesView != null) prayerTimesView.setVisibility(View.GONE);
        if (prayerHeader != null) prayerHeader.setVisibility(View.GONE);
        if (ayahBlock != null) ayahBlock.setVisibility(View.GONE);
        if (islamicShelf != null) islamicShelf.setVisibility(View.GONE);
        View table = findViewById(R.id.prayerTable);
        if (table != null) table.setVisibility(View.GONE);
    }

    private CharSequence boldSuffix(String prefix, String boldPart, String suffix) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        sb.append(prefix);
        int s = sb.length();
        sb.append(boldPart);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, sb.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (suffix != null && !suffix.isEmpty()) sb.append(suffix);
        return sb;
    }

    private void updateWeatherAqi() {
        if (weatherAirQualityView == null) return;
        WeatherAirQuality.FullWeather fw = WeatherAirQuality.getFullWeather(this);
        if (fw == null) return;

        boolean isNeu = ThemeManager.isNeumorphism(this);
        int iconColor = isNeu ? 0xFF636C78 : 0xFFFFFFFF;
        int nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        if (weaHeaderLoc != null) {
            weaHeaderLoc.setText(fw.location != null && !fw.location.isEmpty() ? fw.location : "JAKARTA, INDONESIA");
        }
        if (weaMainIcon != null) {
            weaMainIcon.setImageDrawable(WeatherAirQuality.createConditionDrawable(
                    fw.weatherCode, nowHour, iconColor, 54f, 42f, density, true));
        }
        if (weaMainTemp != null) weaMainTemp.setText(fw.temp + "°");
        if (weaMainCond != null) weaMainCond.setText(fw.condition);

        List<WeatherAirQuality.HourlyItem> hList = fw.hourly;
        if (hList != null && !hList.isEmpty()) {
            TextView[] tViews = new TextView[]{weaHourTime1, weaHourTime2, weaHourTime3};
            ImageView[] iViews = new ImageView[]{weaHourIcon1, weaHourIcon2, weaHourIcon3};
            TextView[] vViews = new TextView[]{weaHourVal1, weaHourVal2, weaHourVal3};
            for (int i = 0; i < 3; i++) {
                // Jarak 3 jam ke depan per slot: +3 jam (i=0), +6 jam (i=1), +9 jam (i=2)
                int targetHourIndex = 3 * (i + 1);
                int idx = Math.min(targetHourIndex, hList.size() - 1);
                WeatherAirQuality.HourlyItem item = hList.get(idx);
                if (tViews[i] != null) tViews[i].setText(item.time);
                if (iViews[i] != null) {
                    iViews[i].setImageDrawable(WeatherAirQuality.createConditionDrawable(
                            item.weatherCode, item.hour24, iconColor, 22f, 18f, density, false));
                }
                if (vViews[i] != null) vViews[i].setText(item.temp + "°");
            }
        }

        if (weaPillAqi != null) {
            weaPillAqi.setText(boldSuffix("AQI ", fw.aqi + " • " + fw.aqiStatus, ""));
        }
        if (weaPillRain != null) {
            weaPillRain.setText(boldSuffix("Peluang Hujan ", fw.rainChanceToday + "%", ""));
        }
    }

    private void updateHomeWeather() {
    }

    private void showHomeWeather(HomeWeather.Line line) {
        updateWeatherAqi();
    }

    private void updateIslamicShelf() {
        if (islamicShelf != null) islamicShelf.setVisibility(View.GONE);
        if (ayahBlock != null) ayahBlock.setVisibility(View.GONE);
    }

    private void updateAyah() {
    }

    private void showAyah(WeeklyAyah.Ayah a) {
        if (ayahBlock != null) {
            ayahBlock.setVisibility(View.GONE);
        }
    }

    private void updateDateAndPeriod() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        String period;
        if (hour >= 4 && hour < 11) period = "PAGI";
        else if (hour >= 11 && hour < 15) period = "SIANG";
        else if (hour >= 15 && hour < 18) period = "SORE";
        else period = "MALAM";

        int day = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH);
        int year = cal.get(Calendar.YEAR);
        String[] idMonths = {"JAN", "FEB", "MAR", "APR", "MEI", "JUN", "JUL", "AGU", "SEP", "OKT", "NOV", "DES"};
        String mStr = (month >= 0 && month < 12) ? idMonths[month] : "";
        String dateStr = day + " " + mStr + " " + year;

        if (ampmView != null) ampmView.setText(period);
        if (dateView != null) dateView.setText(dateStr);
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        if (btnWifi.getVisibility() == View.GONE) return;   // show_network_pill off
        String text = getString(R.string.wifi);
        boolean vpn = false, online = false;
        int icon = R.drawable.ic_wifi;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
            } else if (wifi) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        btnWifi.setText(text);
        btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
    }

    /** Shows an Apple TV-style frosted glass Wi-Fi control center popup. */
    private void showWifiPopup() {
        if (openMenu != null) return;
        SoundEffects.playSelect();
        View anchor = btnWifi;
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        Runnable close = () -> closeMenu(false);

        String ssid = getString(R.string.wifi);
        String ip = "";
        boolean isConnected = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network activeNet = cm != null ? cm.getActiveNetwork() : null;
            if (activeNet != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                    isConnected = true;
                }
            }
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                WifiInfo info = wm.getConnectionInfo();
                if (info != null && info.getNetworkId() != -1) {
                    String s = info.getSSID();
                    if (s != null && !s.equals("<unknown ssid>") && !s.isEmpty()) {
                        ssid = s.replaceAll("^\"|\"$", "");
                    }
                    int ipVal = info.getIpAddress();
                    if (ipVal != 0) {
                        ip = String.format(Locale.getDefault(), "%d.%d.%d.%d",
                                (ipVal & 0xff), (ipVal >> 8 & 0xff), (ipVal >> 16 & 0xff), (ipVal >> 24 & 0xff));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        int width = (int) (240 * density);

        // Row 1: Current Wi-Fi status & SSID
        addInputRow(rows, close, R.drawable.ic_wifi, ssid, isConnected ? TvInputManager.INPUT_STATE_CONNECTED : TvInputManager.INPUT_STATE_DISCONNECTED, () -> {
            SoundEffects.playSelect();
            open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
        });

        addSeparator(rows);

        // Row 2: Pengaturan Wi-Fi
        addInputRow(rows, close, R.drawable.ic_settings, "Pengaturan Wi-Fi", -1, () -> {
            SoundEffects.playSelect();
            open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
        });

        // Row 3: Tutup
        addInputRow(rows, close, R.drawable.ic_close, "Tutup", -1, () -> {
            SoundEffects.playSelect();
            closeMenu(true);
        });

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        width = Math.max(width, panel.getMeasuredWidth());

        showMenu(panel, rows, anchor, width, false);
    }

    /** Shows an Apple TV-style frosted glass Candy Box dropdown menu containing all launcher customizations & tools. */
    private void showCandyBoxPopup() {
        if (openMenu != null) return;
        SoundEffects.playSelect();
        View anchor = btnCandyBox != null ? btnCandyBox : findViewById(R.id.btnSettings);
        if (anchor == null) return;
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        Runnable close = () -> closeMenu(false);

        // Row 1: Wallpaper
        addInputRow(rows, close, R.drawable.ic_wallpaper, "Wallpaper 4K", -1, () -> {
            SoundEffects.playSelect();
            showWallpaperDialog();
        });

        // Row 2: Gaya Tema
        String currentTheme = ThemeManager.getActiveTheme(this);
        String themeLabel = "Tema: " + ThemeManager.getThemeDisplayName(currentTheme);
        addInputRow(rows, close, R.drawable.ic_theme, themeLabel, -1, () -> {
            SoundEffects.playSelect();
            showThemeDialog();
        });

        // Row 3: Lokasi Cuaca
        String locName = AppLocationManager.getLocationName(this) + " (" + AppLocationManager.getActiveRegion(this) + ")";
        addInputRow(rows, close, R.drawable.ic_location, "Lokasi: " + locName, -1, () -> {
            SoundEffects.playSelect();
            showAsiaCountryDialog();
        });

        // Row 4: Efek Suara
        String currentSoundTheme = SoundEffects.getActiveTheme(this);
        String soundLabel = "Suara: " + SoundEffects.getThemeDisplayName(currentSoundTheme);
        addInputRow(rows, close, R.drawable.ic_sound, soundLabel, -1, () -> {
            SoundEffects.playSelect();
            showSoundEffectsDialog();
        });

        // Row 5: Auto-Update (OTA)
        boolean isAuto = AppUpdateManager.isAutoUpdateEnabled(this);
        String updateLabel = "Update Otomatis: " + (isAuto ? "ON (" + AppUpdateManager.getAutoUpdateTime(this) + ")" : "OFF");
        addInputRow(rows, close, R.drawable.ic_update, updateLabel, -1, () -> {
            SoundEffects.playSelect();
            showAutoUpdateDialog();
        });

        // Row 6: Nightstand / Screensaver
        addInputRow(rows, close, R.drawable.ic_weather_sun, "Screensaver Jam Meja", -1, () -> {
            SoundEffects.playSelect();
            showNightstandScreensaver();
        });

        // Row 7: Smart Boost TV / Bersih Cache
        addInputRow(rows, close, R.drawable.ic_trash, "Bersihkan Cache TV", -1, () -> {
            SoundEffects.playSelect();
            smartBoostTv();
        });

        addSeparator(rows);

        // Row 8: Tutup
        addInputRow(rows, close, R.drawable.ic_close, "Tutup", -1, () -> {
            SoundEffects.playSelect();
            closeMenu(true);
        });

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = Math.max((int) (230 * density), panel.getMeasuredWidth());

        showMenu(panel, rows, anchor, width, false);
    }

    /** Shows an Apple TV-style frosted glass Settings dropdown menu (Restart, Turn Off, All Settings). */
    private void showSettingsPopup() {
        if (openMenu != null) return;
        SoundEffects.playSelect();
        View anchor = findViewById(R.id.btnSettings);
        if (anchor == null) return;
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        Runnable close = () -> closeMenu(false);

        // Row 1: Restart TV
        addInputRow(rows, close, R.drawable.ic_restart, "Mulai Ulang TV", -1, () -> {
            SoundEffects.playSelect();
            showRestartDialog();
        });

        // Row 2: Turn Off TV
        addInputRow(rows, close, R.drawable.ic_power, "Matikan TV", -1, () -> {
            SoundEffects.playSelect();
            showTurnOffDialog();
        });

        addSeparator(rows);

        // Row 3: All Settings (Android Settings)
        addInputRow(rows, close, R.drawable.ic_settings, "Semua Pengaturan", -1, () -> {
            SoundEffects.playSelect();
            open(systemIntent(Settings.ACTION_SETTINGS));
        });

        // Row 4: Music Notification Access Permission
        addInputRow(rows, close, R.drawable.ic_music, "Izin Info Musik (Now Playing)", -1, () -> {
            SoundEffects.playSelect();
            NowPlayingManager.openNotificationAccessSettings(this);
            Toast.makeText(this, "Aktifkan 'Aura TV Media Listener' untuk menampilkan info lagu", Toast.LENGTH_LONG).show();
        });

        // Row 5: Tutup
        addInputRow(rows, close, R.drawable.ic_close, "Tutup", -1, () -> {
            SoundEffects.playSelect();
            closeMenu(true);
        });

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = Math.max((int) (200 * density), panel.getMeasuredWidth());

        showMenu(panel, rows, anchor, width, false);
    }

    public static final class ThemedDialogItem {
        final String title;
        final String subtitle;
        final boolean isActive;
        final String badgeText;
        final Runnable action;

        public ThemedDialogItem(String title, String subtitle, boolean isActive, String badgeText, Runnable action) {
            this.title = title;
            this.subtitle = subtitle;
            this.isActive = isActive;
            this.badgeText = badgeText;
            this.action = action;
        }
    }

    private void showThemedListDialog(String headerTitle, String headerSubtitle,
                                      List<ThemedDialogItem> items, String closeLabel, Runnable onClose) {
        showThemedListDialog(this, headerTitle, headerSubtitle, items, closeLabel, onClose);
    }

    public static void showThemedListDialog(Activity ctx, String headerTitle, String headerSubtitle,
                                            List<ThemedDialogItem> items, String closeLabel, Runnable onClose) {
        if (ctx == null || ctx.isFinishing()) return;
        float density = ctx.getResources().getDisplayMetrics().density;
        boolean isNeu = ThemeManager.isNeumorphism(ctx);
        boolean isDark = ThemeManager.isDarkGlow(ctx);
        Typeface outfit = ctx.getResources().getFont(R.font.outfit);

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int) (20 * density), (int) (16 * density), (int) (20 * density), (int) (16 * density));

        TextView titleView = new TextView(ctx);
        titleView.setText(headerTitle);
        titleView.setTextSize(19);
        titleView.setTextColor(isNeu ? 0xFF2B3036 : 0xFFFFFFFF);
        titleView.setTypeface(outfit, Typeface.BOLD);
        titleView.setFontVariationSettings("'wght' 700");
        if (isNeu) titleView.setShadowLayer(0, 0, 0, 0);
        else titleView.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
        layout.addView(titleView);

        if (headerSubtitle != null && !headerSubtitle.isEmpty()) {
            TextView subView = new TextView(ctx);
            subView.setText(headerSubtitle);
            subView.setTextSize(13.5f);
            subView.setTextColor(isNeu ? 0xFF434B56 : (isDark ? 0xFF00F0FF : 0xF0FFFFFF));
            subView.setTypeface(outfit, Typeface.BOLD);
            subView.setFontVariationSettings("'wght' 650");
            if (isNeu) subView.setShadowLayer(0, 0, 0, 0);
            else subView.setShadowLayer(4 * density, 0, 1.2f * density, 0xCC000000);
            subView.setPadding(0, (int) (3 * density), 0, (int) (12 * density));
            layout.addView(subView);
        }

        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);

        AlertDialog[] dialogHolder = new AlertDialog[1];
        View[] viewToFocus = new View[1];

        for (int i = 0; i < items.size(); i++) {
            final ThemedDialogItem opt = items.get(i);
            final boolean isActive = opt.isActive;

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding((int) (16 * density), (int) (10 * density), (int) (16 * density), (int) (10 * density));

            StateListDrawable bg = new StateListDrawable();
            if (isNeu) {
                NeumorphicDrawable onNeu = new NeumorphicDrawable(10 * density, density, NeumorphicDrawable.MODE_RECESSED, false);
                NeumorphicDrawable offNeu = new NeumorphicDrawable(10 * density, density, NeumorphicDrawable.MODE_EXTRUDED, false);
                bg.addState(new int[]{android.R.attr.state_focused}, onNeu);
                bg.addState(new int[]{}, offNeu);
            } else if (isDark) {
                GradientDrawable on = new GradientDrawable();
                on.setColor(0xFF05070B);
                on.setCornerRadius(10 * density);
                on.setStroke(Math.max(1, Math.round(1.2f * density)), 0xFF00F0FF);

                GradientDrawable off = new GradientDrawable();
                off.setColor(isActive ? 0x2200F0FF : 0xFF10121A);
                off.setCornerRadius(10 * density);
                off.setStroke(Math.max(1, Math.round(1.0f * density)), isActive ? 0x5500F0FF : 0x1EFFFFFF);

                bg.addState(new int[]{android.R.attr.state_focused}, on);
                bg.addState(new int[]{}, off);
            } else {
                // Frosted Glass: translucent white glass highlight with crisp white glass rim
                GradientDrawable on = new GradientDrawable();
                on.setColor(0x42FFFFFF);
                on.setCornerRadius(10 * density);
                on.setStroke(Math.max(1, Math.round(1.5f * density)), 0xF2FFFFFF);

                GradientDrawable off = new GradientDrawable();
                off.setColor(isActive ? 0x28FFFFFF : 0x16FFFFFF);
                off.setCornerRadius(10 * density);
                off.setStroke(Math.max(1, Math.round(1.0f * density)), isActive ? 0x73FFFFFF : 0x2BFFFFFF);

                bg.addState(new int[]{android.R.attr.state_focused}, on);
                bg.addState(new int[]{}, off);
            }
            row.setBackground(bg);

            LinearLayout topRow = new LinearLayout(ctx);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            topRow.setDuplicateParentStateEnabled(true);

            TextView rowTitle = new TextView(ctx);
            rowTitle.setText(opt.title);
            rowTitle.setTextSize(15.5f);
            if (isNeu) {
                rowTitle.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF1F2429, 0xFF2B3036}));
                rowTitle.setShadowLayer(0, 0, 0, 0);
            } else if (isDark) {
                rowTitle.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF00F0FF, 0xFFFFFFFF}));
                rowTitle.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
            } else {
                rowTitle.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFFFFFFFF, 0xFFFFFFFF}));
                rowTitle.setShadowLayer(3.5f * density, 0, 1.2f * density, 0xCC000000);
            }
            rowTitle.setTypeface(outfit, Typeface.BOLD);
            rowTitle.setFontVariationSettings("'wght' 700");
            rowTitle.setDuplicateParentStateEnabled(true);
            topRow.addView(rowTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            String badge = opt.badgeText != null ? opt.badgeText : (isActive ? "AKTIF" : null);
            if (badge != null && !badge.isEmpty()) {
                TextView activeTag = new TextView(ctx);
                activeTag.setText(badge);
                activeTag.setTextSize(11);
                activeTag.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                        isNeu ? new int[]{0xFF2B3036, 0xFF2B3036}
                                : (isDark ? new int[]{0xFF00F0FF, 0xFF00F0FF} : new int[]{0xFFFFFFFF, 0xFFFFFFFF})));
                activeTag.setTypeface(outfit, Typeface.BOLD);
                activeTag.setFontVariationSettings("'wght' 800");
                activeTag.setPadding((int) (8 * density), (int) (2 * density), (int) (8 * density), (int) (2 * density));
                GradientDrawable tagBg = new GradientDrawable();
                tagBg.setColor(isNeu ? 0x16000000 : (isDark ? 0x2600F0FF : 0x36FFFFFF));
                if (!isNeu && !isDark) {
                    tagBg.setStroke(Math.max(1, Math.round(1.0f * density)), 0x73FFFFFF);
                }
                tagBg.setCornerRadius(5 * density);
                activeTag.setBackground(tagBg);
                activeTag.setDuplicateParentStateEnabled(true);
                topRow.addView(activeTag);
            }
            row.addView(topRow);

            if (opt.subtitle != null && !opt.subtitle.isEmpty()) {
                TextView rowSub = new TextView(ctx);
                rowSub.setText(opt.subtitle);
                rowSub.setTextSize(13.2f);
                if (isNeu) {
                    rowSub.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF2B3036, 0xFF434B56}));
                    rowSub.setShadowLayer(0, 0, 0, 0);
                } else if (isDark) {
                    rowSub.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFFFFFFFF, 0xD9FFFFFF}));
                    rowSub.setShadowLayer(3.5f * density, 0, 1.2f * density, 0xCC000000);
                } else {
                    rowSub.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFFFFFFFF, 0xE6FFFFFF}));
                    rowSub.setShadowLayer(3.5f * density, 0, 1.2f * density, 0xCC000000);
                }
                rowSub.setTypeface(outfit, Typeface.BOLD);
                rowSub.setFontVariationSettings("'wght' 600");
                rowSub.setPadding(0, (int) (3 * density), 0, 0);
                rowSub.setDuplicateParentStateEnabled(true);
                row.addView(rowSub);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (7 * density);
            list.addView(row, lp);

            if (isActive && viewToFocus[0] == null) {
                viewToFocus[0] = row;
            }

            row.setOnClickListener(v -> {
                SoundEffects.playSelect();
                if (dialogHolder[0] != null) {
                    dialogHolder[0].dismiss();
                }
                if (opt.action != null) {
                    opt.action.run();
                }
            });

            row.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    SoundEffects.playNav();
                }
            });
        }

        scroll.addView(list);
        layout.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx, R.style.Theme_TvHome_GlassDialog);
        builder.setView(layout);
        if (closeLabel != null && !closeLabel.isEmpty()) {
            builder.setNegativeButton(closeLabel, (d, w) -> {
                d.dismiss();
                if (onClose != null) onClose.run();
            });
        }
        AlertDialog dialog = builder.create();
        dialogHolder[0] = dialog;
        dialog.show();
        styleGlassDialog(ctx, dialog);

        if (dialog.getWindow() != null) {
            int w = Math.min((int) (520 * density), ctx.getResources().getDisplayMetrics().widthPixels - (int) (120 * density));
            int h = Math.min((int) (430 * density), (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.74f));
            dialog.getWindow().setLayout(w, h);
        }

        if (viewToFocus[0] != null) {
            viewToFocus[0].requestFocus();
        } else if (list.getChildCount() > 0) {
            list.getChildAt(0).requestFocus();
        }
    }

    private void showSoundEffectsDialog() {
        final String[] soundOptions = new String[]{
                "🎮 PlayStation 5 (Bawaan)",
                "🕹️ Nintendo Switch",
                "🟩 Xbox Series X/S",
                "🔇 Matikan Suara (Bisu)"
        };
        final String[] soundSubs = new String[]{
                "Nada kristal ambient khas antarmuka konsol PS5",
                "Suara klik & snap pop taktil yang responsif",
                "Harmoni akustik hangat khas konsol Xbox Series",
                "Nonaktifkan seluruh efek suara navigasi remote"
        };
        final String[] soundKeys = new String[]{
                SoundEffects.THEME_PS5,
                SoundEffects.THEME_SWITCH,
                SoundEffects.THEME_XBOX,
                SoundEffects.THEME_OFF
        };

        String currentTheme = SoundEffects.getActiveTheme(this);
        List<ThemedDialogItem> items = new ArrayList<>();
        for (int i = 0; i < soundKeys.length; i++) {
            final String chosenKey = soundKeys[i];
            boolean active = chosenKey.equalsIgnoreCase(currentTheme);
            items.add(new ThemedDialogItem(soundOptions[i], soundSubs[i], active, active ? "AKTIF" : null, () -> {
                SoundEffects.setTheme(MainActivity.this, chosenKey);
                String displayName = SoundEffects.getThemeDisplayName(chosenKey);
                if (SoundEffects.THEME_OFF.equalsIgnoreCase(chosenKey)) {
                    Toast.makeText(MainActivity.this, "🔇 Efek Suara Dimatikan", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "🔊 Efek Suara: " + displayName, Toast.LENGTH_SHORT).show();
                    handler.postDelayed(SoundEffects::playSelect, 120);
                }
            }));
        }

        showThemedListDialog(
                "🔊 Pilih Efek Suara Navigasi",
                "Sesuaikan respons audio saat berpindah menu menggunakan remote TV",
                items,
                null,
                null
        );
    }

    private void showAzanSettingsDialog() {
        SoundEffects.playSelect();
        Location.Zone chosenPlace = Location.chosen(this);
        String zoneCode = (chosenPlace != null) ? chosenPlace.code : "WLY01";
        java.util.Calendar cal = java.util.Calendar.getInstance();
        PrayerTimes.Day todayTimes = PrayerTimes.day(this, zoneCode, cal);
        boolean isNeu = ThemeManager.isNeumorphism(this);
        boolean isDark = ThemeManager.isDarkGlow(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        AlertDialog[] dialogHolder = new AlertDialog[1];

        LinearLayout rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        rootContainer.setPadding((int) (22 * density), (int) (16 * density), (int) (22 * density), (int) (16 * density));

        // Header Title Row
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText("🕌 Tetapan & Pilihan Azan");
        titleView.setTextSize(19);
        titleView.setTextColor(isNeu ? 0xFF2B3036 : 0xFFFFFFFF);
        titleView.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        titleView.setFontVariationSettings("'wght' 700");
        if (isNeu) titleView.setShadowLayer(0, 0, 0, 0);
        else titleView.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
        titleCol.addView(titleView);

        TextView subView = new TextView(this);
        subView.setText("Notifikasi popup kad kaca terapung di atas semua aplikasi & tetapan audio azan");
        subView.setTextSize(12.5f);
        subView.setTextColor(isNeu ? 0xFF5A626E : 0xD9FFFFFF);
        subView.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        subView.setFontVariationSettings("'wght' 600");
        if (isNeu) subView.setShadowLayer(0, 0, 0, 0);
        else subView.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        subView.setPadding(0, (int) (2 * density), 0, 0);
        titleCol.addView(subView);

        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        rootContainer.addView(headerRow);

        // Gap below header
        View headerDivider = new View(this);
        headerDivider.setBackgroundColor(isNeu ? 0x1F000000 : 0x22FFFFFF);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) (1 * density)));
        divLp.topMargin = (int) (8 * density);
        divLp.bottomMargin = (int) (8 * density);
        rootContainer.addView(headerDivider, divLp);

        // Content ScrollView with 2 balanced columns (16:9 widescreen layout)
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);

        LinearLayout twoCols = new LinearLayout(this);
        twoCols.setOrientation(LinearLayout.HORIZONTAL);

        // --- LEFT COLUMN: A (Suis Utama) & B (Pilihan Waktu Solat) ---
        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f);
        leftLp.rightMargin = (int) (12 * density);
        leftCol.setLayoutParams(leftLp);

        // Bahagian A: Suis Utama
        leftCol.addView(createDialogSectionHeader("A. STATUS UTAMA POPUP AZAN"));
        final TextView masterStatusTag = new TextView(this);
        LinearLayout masterRow = createDialogRow(
                "🔔 Notifikasi Popup Azan TV",
                "Papar kad waktu solat terapung di atas Netflix, YouTube & semua app",
                masterStatusTag,
                null,
                () -> {
                    boolean nowOn = !Azan.isMasterEnabled(MainActivity.this);
                    Azan.setMasterEnabled(MainActivity.this, nowOn);
                    Azan.schedule(MainActivity.this, zoneCode);
                    SoundEffects.playSelect();
                    updateMasterTag(masterStatusTag, nowOn);
                    Toast.makeText(MainActivity.this, nowOn ? "✅ Popup Azan TV Diaktifkan" : "⭕ Popup Azan TV Dimatikan", Toast.LENGTH_SHORT).show();
                }
        );
        updateMasterTag(masterStatusTag, Azan.isMasterEnabled(this));
        leftCol.addView(masterRow);

        // Bahagian B: Pilihan Waktu Solat
        leftCol.addView(createDialogSectionHeader("B. PILIHAN WAKTU SOLAT (ON / OFF)"));
        int[] prayers = {0, 2, 3, 4, 5};
        String[] prayerLabels = {"🌅 Subuh", "☀️ Zohor", "🌤️ Asar", "🌇 Maghrib", "🌙 Isyak"};

        for (int i = 0; i < prayers.length; i++) {
            final int pIdx = prayers[i];
            String pName = prayerLabels[i];
            String pTimeStr = "";
            if (todayTimes != null && pIdx < todayTimes.minutes.length) {
                int min = todayTimes.minutes[pIdx];
                pTimeStr = String.format(Locale.ROOT, " • %02d:%02d", min / 60, min % 60);
            }

            final TextView prayerTag = new TextView(this);
            LinearLayout prayerRow = createDialogRow(
                    pName + pTimeStr,
                    null,
                    prayerTag,
                    null,
                    () -> {
                        boolean current = Azan.on(MainActivity.this, pIdx);
                        Azan.set(MainActivity.this, pIdx, !current);
                        Azan.schedule(MainActivity.this, zoneCode);
                        SoundEffects.playSelect();
                        updatePrayerTag(prayerTag, !current);
                    }
            );
            updatePrayerTag(prayerTag, Azan.on(this, pIdx));
            leftCol.addView(prayerRow);
        }
        twoCols.addView(leftCol);

        // Subtle vertical divider between columns
        View colDivider = new View(this);
        colDivider.setBackgroundColor(isNeu ? 0x1A000000 : 0x1FFFFFFF);
        LinearLayout.LayoutParams colDivLp = new LinearLayout.LayoutParams(Math.max(1, (int) (1 * density)), ViewGroup.LayoutParams.MATCH_PARENT);
        colDivLp.leftMargin = (int) (4 * density);
        colDivLp.rightMargin = (int) (12 * density);
        twoCols.addView(colDivider, colDivLp);

        // --- RIGHT COLUMN: C (Audio), D (Uji Popup), Panduan & Tutup ---
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        rightCol.setLayoutParams(rightLp);

        // Bahagian C: Audio
        rightCol.addView(createDialogSectionHeader("C. PILIHAN BUNYI AZAN"));
        final TextView soundTag = new TextView(this);
        final TextView[] soundSubHolder = new TextView[1];
        LinearLayout soundRow = createDialogRow(
                "🔊 Bunyi Azan Semasa",
                Azan.soundLabel(Azan.getSoundChoice(this)),
                soundTag,
                soundSubHolder,
                () -> {
                    SoundEffects.playSelect();
                    showAzanSoundChoiceSubDialog(() -> {
                        if (soundSubHolder[0] != null) {
                            soundSubHolder[0].setText(Azan.soundLabel(Azan.getSoundChoice(MainActivity.this)));
                        }
                    });
                }
        );
        soundTag.setText("TUKAR ❯");
        soundTag.setTextSize(12.5f);
        soundTag.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        soundTag.setFontVariationSettings("'wght' 700");
        soundTag.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                isNeu ? new int[]{0xFF1F2429, 0xFF0284C7}
                        : (isDark ? new int[]{0xFF00F0FF, 0xFF00F0FF} : new int[]{0xFF000000, 0xFF38BDF8})));
        rightCol.addView(soundRow);

        // Bahagian D: Ujian Azan
        rightCol.addView(createDialogSectionHeader("D. UJI POPUP AZAN"));
        final TextView testTag = new TextView(this);
        LinearLayout testRow = createDialogRow(
                "▶️ Uji Popup Azan Sekarang",
                "Mencetuskan kad terapung & main audio pilihan",
                testTag,
                null,
                () -> {
                    SoundEffects.playSelect();
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                    Intent azanIntent = new Intent(MainActivity.this, AzanActivity.class);
                    azanIntent.putExtra("prayer", 4);
                    azanIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(azanIntent);
                }
        );
        testTag.setText("UJI ▶");
        testTag.setTextSize(13);
        testTag.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        testTag.setFontVariationSettings("'wght' 700");
        testTag.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                isNeu ? new int[]{0xFF1F2429, 0xFF0284C7}
                        : (isDark ? new int[]{0xFF00F0FF, 0xFF00F0FF} : new int[]{0xFF000000, 0xFF38BDF8})));
        rightCol.addView(testRow);

        // Info Card
        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
        if (isNeu) {
            infoCard.setBackground(new NeumorphicDrawable(9 * density, density, NeumorphicDrawable.MODE_RECESSED, false));
        } else {
            GradientDrawable infoBg = new GradientDrawable();
            infoBg.setColor(isDark ? 0xFF0A0C12 : 0x12FFFFFF);
            infoBg.setCornerRadius(9 * density);
            infoBg.setStroke(Math.max(1, (int) (1 * density)), isDark ? 0x3300F0FF : 0x22FFFFFF);
            infoCard.setBackground(infoBg);
        }

        TextView infoText = new TextView(this);
        infoText.setText("💡 Waktu solat diselaraskan secara automatik mengikut zon JAKIM. Kad azan tetap muncul di atas Netflix, YouTube & Perfect TV.");
        infoText.setTextSize(11f);
        infoText.setTextColor(isNeu ? 0xFF4A5568 : 0xB3FFFFFF);
        infoText.setTypeface(getResources().getFont(R.font.outfit), Typeface.NORMAL);
        infoText.setFontVariationSettings("'wght' 500");
        infoText.setLineSpacing(0, 1.25f);
        infoCard.addView(infoText);

        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = (int) (4 * density);
        infoLp.bottomMargin = (int) (5 * density);
        infoCard.setLayoutParams(infoLp);
        rightCol.addView(infoCard);

        // Butang Tutup / Selesai
        final TextView closeTag = new TextView(this);
        LinearLayout closeRow = createDialogRow(
                "✕ Tutup Tetapan",
                null,
                closeTag,
                null,
                () -> {
                    SoundEffects.playSelect();
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                }
        );
        closeTag.setText("TUTUP");
        closeTag.setTextSize(12.5f);
        closeTag.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        closeTag.setFontVariationSettings("'wght' 700");
        closeTag.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                isNeu ? new int[]{0xFF1F2429, 0xFF5A626E}
                        : (isDark ? new int[]{0xFF00F0FF, 0x88FFFFFF} : new int[]{0xFF000000, 0x88FFFFFF})));
        rightCol.addView(closeRow);

        twoCols.addView(rightCol);
        scroll.addView(twoCols);
        rootContainer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        builder.setView(rootContainer);
        AlertDialog dialog = builder.create();
        dialogHolder[0] = dialog;
        dialog.show();
        styleGlassDialog(dialog);

        // Set true 16:9 popup layout sizing with generous top & bottom margins
        Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenH = dm.heightPixels;
            int screenW = dm.widthPixels;

            int targetH = Math.round(screenH * 0.70f / 9f) * 9;
            int targetW = targetH * 16 / 9;
            if (targetW > (int) (screenW * 0.85f)) {
                targetW = Math.round((screenW * 0.85f) / 16f) * 16;
                targetH = targetW * 9 / 16;
            }
            window.setLayout(targetW, targetH);
            window.setGravity(Gravity.CENTER);
        }
    }

    private void showAzanSoundChoiceSubDialog(Runnable onUpdated) {
        final String[] soundKeys = new String[]{
                Azan.SOUND_MAKKAH,
                Azan.SOUND_MADINAH,
                Azan.SOUND_KLCC,
                Azan.SOUND_CHIME,
                Azan.SOUND_SILENT
        };

        final String[] soundOptions = new String[]{
                "🕋 Azan Masjidil Haram Makkah (Penuh)",
                "🕌 Azan Masjid Nabawi Madinah (Penuh)",
                "🇲🇾 Azan Masjid As-Syakirin KLCC",
                "🔔 Nada Chime Lembut (Ringkas ~5 saat)",
                "🔕 Mod Senyap (Visual Sahaja Tanpa Audio)"
        };

        String currentSound = Azan.getSoundChoice(this);
        List<ThemedDialogItem> items = new ArrayList<>();
        for (int i = 0; i < soundKeys.length; i++) {
            final String chosen = soundKeys[i];
            boolean isActive = chosen.equalsIgnoreCase(currentSound);
            items.add(new ThemedDialogItem(
                    soundOptions[i],
                    null,
                    isActive,
                    isActive ? "AKTIF" : null,
                    () -> {
                        Azan.setSoundChoice(MainActivity.this, chosen);
                        Azan.fetch(MainActivity.this, chosen);
                        Toast.makeText(MainActivity.this, "Audio Dipilih: " + Azan.soundLabel(chosen), Toast.LENGTH_SHORT).show();
                        if (onUpdated != null) onUpdated.run();
                    }
            ));
        }

        showThemedListDialog(
                "🔊 Pilih Audio Azan",
                "Pilih lantunan azan atau nada peringatan waktu solat",
                items,
                null,
                null
        );
    }

    private void updateMasterTag(TextView tag, boolean on) {
        boolean isNeu = ThemeManager.isNeumorphism(this);
        boolean isDark = ThemeManager.isDarkGlow(this);
        tag.setText(on ? "ON" : "OFF");
        tag.setTextSize(13);
        tag.setTypeface(getResources().getFont(R.font.outfit), Typeface.NORMAL);
        tag.setFontVariationSettings(on ? "'wght' 700" : "'wght' 600");
        int color = isNeu ? (on ? 0xFF0284C7 : 0xFF5A626E) : (on ? (isDark ? 0xFF00F0FF : 0xFFFFFFFF) : 0x99FFFFFF);
        int focusedColor = isNeu ? 0xFF1F2429 : (isDark ? 0xFF00F0FF : 0xFFFFFFFF);
        tag.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{focusedColor, color}));
    }

    private void updatePrayerTag(TextView tag, boolean on) {
        boolean isNeu = ThemeManager.isNeumorphism(this);
        boolean isDark = ThemeManager.isDarkGlow(this);
        tag.setText(on ? "ON" : "OFF");
        tag.setTextSize(13);
        tag.setTypeface(getResources().getFont(R.font.outfit), Typeface.NORMAL);
        tag.setFontVariationSettings(on ? "'wght' 700" : "'wght' 600");
        int color = isNeu ? (on ? 0xFF0284C7 : 0xFF5A626E) : (on ? (isDark ? 0xFF00F0FF : 0xFFFFFFFF) : 0x99FFFFFF);
        int focusedColor = isNeu ? 0xFF1F2429 : (isDark ? 0xFF00F0FF : 0xFFFFFFFF);
        tag.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{focusedColor, color}));
    }

    private TextView createDialogSectionHeader(String title) {
        boolean isNeu = ThemeManager.isNeumorphism(this);
        boolean isDark = ThemeManager.isDarkGlow(this);
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(11.5f);
        tv.setTextColor(isNeu ? 0xFF4A5568 : (isDark ? 0xFF00F0FF : 0xE6FFFFFF));
        tv.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        tv.setFontVariationSettings("'wght' 700");
        tv.setLetterSpacing(0.08f);
        tv.setPadding(0, (int) (6 * density), 0, (int) (3 * density));
        return tv;
    }

    private LinearLayout createDialogRow(String title, String subtitle, TextView tagView, TextView[] subHolderOut, Runnable onClick) {
        boolean isNeu = ThemeManager.isNeumorphism(this);
        boolean isDark = ThemeManager.isDarkGlow(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        boolean hasSub = (subtitle != null && !subtitle.isEmpty());
        int vPad = hasSub ? (int) (8 * density) : (int) (5 * density);
        row.setPadding((int) (14 * density), vPad, (int) (14 * density), vPad);

        StateListDrawable bg = new StateListDrawable();
        if (isNeu) {
            NeumorphicDrawable onNeu = new NeumorphicDrawable(9 * density, density, NeumorphicDrawable.MODE_RECESSED, false);
            NeumorphicDrawable offNeu = new NeumorphicDrawable(9 * density, density, NeumorphicDrawable.MODE_EXTRUDED, false);
            bg.addState(new int[]{android.R.attr.state_focused}, onNeu);
            bg.addState(new int[]{}, offNeu);
        } else if (isDark) {
            GradientDrawable on = new GradientDrawable();
            on.setColor(0xFF05070B);
            on.setCornerRadius(9 * density);
            on.setStroke(Math.max(1, Math.round(1.2f * density)), 0xFF00F0FF);
            GradientDrawable off = new GradientDrawable();
            off.setColor(0xFF10121A);
            off.setCornerRadius(9 * density);
            off.setStroke(Math.max(1, Math.round(1.0f * density)), 0x1EFFFFFF);
            bg.addState(new int[]{android.R.attr.state_focused}, on);
            bg.addState(new int[]{}, off);
        } else {
            GradientDrawable on = new GradientDrawable();
            on.setColor(0x42FFFFFF);
            on.setCornerRadius(9 * density);
            on.setStroke(Math.max(1, Math.round(1.5f * density)), 0xF2FFFFFF);
            GradientDrawable off = new GradientDrawable();
            off.setColor(0x16FFFFFF);
            off.setCornerRadius(9 * density);
            off.setStroke(Math.max(1, Math.round(1.0f * density)), 0x2BFFFFFF);
            bg.addState(new int[]{android.R.attr.state_focused}, on);
            bg.addState(new int[]{}, off);
        }
        row.setBackground(bg);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setDuplicateParentStateEnabled(true);

        TextView rowTitle = new TextView(this);
        rowTitle.setText(title);
        rowTitle.setTextSize(15);
        rowTitle.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                isNeu ? new int[]{0xFF1F2429, 0xFF2B3036}
                        : (isDark ? new int[]{0xFF00F0FF, 0xFFFFFFFF} : new int[]{0xFFFFFFFF, 0xFFFFFFFF})));
        rowTitle.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        rowTitle.setFontVariationSettings("'wght' 700");
        if (isNeu) rowTitle.setShadowLayer(0, 0, 0, 0);
        else rowTitle.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        rowTitle.setDuplicateParentStateEnabled(true);
        topRow.addView(rowTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (tagView != null) {
            tagView.setDuplicateParentStateEnabled(true);
            topRow.addView(tagView);
        }
        row.addView(topRow);

        final TextView finalRowSub;
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView rowSub = new TextView(this);
            rowSub.setText(subtitle);
            rowSub.setTextSize(12.5f);
            rowSub.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                    isNeu ? new int[]{0xFF2B3036, 0xFF434B56}
                            : (isDark ? new int[]{0xFFFFFFFF, 0xD9FFFFFF} : new int[]{0xFFFFFFFF, 0xE6FFFFFF})));
            rowSub.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
            rowSub.setFontVariationSettings("'wght' 600");
            if (isNeu) rowSub.setShadowLayer(0, 0, 0, 0);
            else rowSub.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
            rowSub.setPadding(0, (int) (2 * density), 0, 0);
            rowSub.setDuplicateParentStateEnabled(true);
            row.addView(rowSub);
            finalRowSub = rowSub;
            if (subHolderOut != null) {
                subHolderOut[0] = rowSub;
            }
        } else {
            finalRowSub = null;
        }

        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                SoundEffects.playNav();
                rowTitle.setShadowLayer(0, 0, 0, 0);
                if (finalRowSub != null) finalRowSub.setShadowLayer(0, 0, 0, 0);
            } else if (!isNeu) {
                rowTitle.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
                if (finalRowSub != null) finalRowSub.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
            }
        });

        row.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = hasSub ? (int) (5 * density) : (int) (3 * density);
        row.setLayoutParams(lp);

        return row;
    }

    private void showRestartDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("🔄 Mulai Ulang TV (Restart)");
        builder.setMessage("Apakah Anda yakin ingin memulai ulang (restart) TV ini?");
        builder.setPositiveButton("Ya, Mulai Ulang", (dialog, which) -> {
            dialog.dismiss();
            Toast.makeText(MainActivity.this, "Memulai ulang TV...", Toast.LENGTH_SHORT).show();
            handler.postDelayed(() -> HomeKeyService.restartTv(MainActivity.this), 300);
        });
        builder.setNegativeButton("Batal", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
        styleGlassDialog(dialog);
    }

    private void showTurnOffDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("🔌 Matikan TV");
        builder.setMessage("Apakah Anda ingin mematikan / mengistirahatkan TV?");
        builder.setPositiveButton("Matikan TV", (dialog, which) -> {
            dialog.dismiss();
            HomeKeyService.turnOffTv(MainActivity.this);
        });
        builder.setNegativeButton("Batal", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
        styleGlassDialog(dialog);
    }

    private void showAutoUpdateDialog() {
        boolean isAutoEnabled = AppUpdateManager.isAutoUpdateEnabled(this);
        String schedTime = AppUpdateManager.getAutoUpdateTime(this);

        List<ThemedDialogItem> items = new ArrayList<>();
        items.add(new ThemedDialogItem(
                "🔄 Periksa Pembaruan Sekarang",
                "Hubungi server GitHub untuk memeriksa versi APK terbaru",
                false,
                "CEK OTA",
                () -> AppUpdateManager.checkForUpdates(MainActivity.this, true)
        ));
        items.add(new ThemedDialogItem(
                isAutoEnabled ? "✅ Update Otomatis Harian" : "⭕ Update Otomatis Harian",
                isAutoEnabled ? ("Pemeriksaan otomatis aktif setiap hari pukul " + schedTime) : "Pemeriksaan pembaruan otomatis sedang dimatikan",
                isAutoEnabled,
                isAutoEnabled ? "AKTIF" : "NONAKTIF",
                () -> {
                    boolean newState = !isAutoEnabled;
                    AppUpdateManager.setAutoUpdateEnabled(MainActivity.this, newState);
                    Toast.makeText(MainActivity.this, newState ? "✅ Update Otomatis Diaktifkan (" + schedTime + ")" : "⭕ Update Otomatis Dimatikan", Toast.LENGTH_SHORT).show();
                }
        ));
        items.add(new ThemedDialogItem(
                "⏱️ Ubah Waktu Update Otomatis",
                "Jadwal saat ini: " + schedTime + " • Disarankan pukul 03:30 AM",
                false,
                schedTime,
                this::showUpdateTimePickerDialog
        ));
        items.add(new ThemedDialogItem(
                isAutoEnabled ? "❌ Matikan Update Otomatis" : "⚡ Aktifkan Update Otomatis",
                isAutoEnabled ? "Nonaktifkan pemeriksaan pembaruan latar belakang" : "Aktifkan kembali pemeriksaan pembaruan otomatis",
                false,
                null,
                () -> {
                    boolean newState = !isAutoEnabled;
                    AppUpdateManager.setAutoUpdateEnabled(MainActivity.this, newState);
                    Toast.makeText(MainActivity.this, newState ? "✅ Update Otomatis Diaktifkan (" + schedTime + ")" : "⭕ Update Otomatis Dimatikan", Toast.LENGTH_SHORT).show();
                }
        ));

        showThemedListDialog(
                "🔄 Pengaturan Update Otomatis (OTA)",
                "Kelola pemeriksaan versi terbaru dan jadwal pembaruan otomatis",
                items,
                null,
                null
        );
    }

    private void showUpdateTimePickerDialog() {
        final String[] times = new String[]{
                "🌙 03:30 AM (Saran Terbaik)",
                "🌙 04:00 AM (Dini Hari)",
                "🌅 05:00 AM (Pagi)",
                "☀️ 12:00 PM (Siang Hari)",
                "🌃 02:00 AM (Tengah Malam)"
        };
        final String[] timeSubs = new String[]{
                "Waktu dini hari saat TV sedang tidak digunakan",
                "Pemeriksaan pembaruan pukul 04:00 pagi",
                "Pemeriksaan pembaruan pukul 05:00 pagi",
                "Pemeriksaan pembaruan pukul 12:00 siang",
                "Pemeriksaan pembaruan pukul 02:00 tengah malam"
        };
        final String[] rawTimes = new String[]{
                "03:30 AM", "04:00 AM", "05:00 AM", "12:00 PM", "02:00 AM"
        };
        String current = AppUpdateManager.getAutoUpdateTime(this);
        List<ThemedDialogItem> items = new ArrayList<>();
        for (int i = 0; i < rawTimes.length; i++) {
            final String chosen = rawTimes[i];
            boolean active = chosen.equalsIgnoreCase(current);
            items.add(new ThemedDialogItem(times[i], timeSubs[i], active, active ? "AKTIF" : null, () -> {
                AppUpdateManager.setAutoUpdateTime(MainActivity.this, chosen);
                AppUpdateManager.setAutoUpdateEnabled(MainActivity.this, true);
                Toast.makeText(MainActivity.this, "Waktu Update Otomatis diatur ke: " + chosen, Toast.LENGTH_SHORT).show();
            }));
        }

        showThemedListDialog(
                "⏱️ Pilih Waktu Update Otomatis Harian",
                "Pilih jadwal pemeriksaan pembaruan otomatis setiap hari",
                items,
                null,
                null
        );
    }

    private void updateNowPlayingUi(NowPlayingManager.MediaInfo info) {
        if (nowPlayingCard == null || cardTitle == null || cardSubtitle == null) return;

        boolean hasActiveMusic = info != null && info.hasMusic();

        if (!hasActiveMusic) {
            hideNowPlayingCard();
            nowPlayingDisabledByUser = false;
            lastPlayedTrackKey = "";
            return;
        }

        String currentTrackKey = info.packageName + ":" + info.title;
        if (!currentTrackKey.equals(lastPlayedTrackKey)) {
            lastPlayedTrackKey = currentTrackKey;
            nowPlayingDisabledByUser = false; // New song started playing -> show
        }

        if (nowPlayingDisabledByUser) {
            hideNowPlayingCard();
            return;
        }

        if (ayahBlock != null) {
            ayahBlock.setVisibility(View.GONE);
        }

        // Active playback from any media player
        cardTitle.setText(info.title.isEmpty() ? "Lagu Dimainkan" : info.title);
        cardTitle.setSelected(true);
        if (!info.artist.isEmpty()) {
            cardSubtitle.setText(info.artist + (info.appName.isEmpty() ? "" : " • " + info.appName.toUpperCase(Locale.ROOT)));
        } else {
            cardSubtitle.setText(info.appName.isEmpty() ? "SEDANG DIPUTAR" : info.appName.toUpperCase(Locale.ROOT));
        }

        if (info.albumArt != null && cardAlbumArt != null) {
            cardAlbumArt.setImageBitmap(info.albumArt);
            cardAlbumArt.setPadding(0, 0, 0, 0);
        } else if (cardAlbumArt != null) {
            try {
                Drawable appIcon = getPackageManager().getApplicationIcon(info.packageName);
                cardAlbumArt.setImageDrawable(appIcon);
                cardAlbumArt.setPadding(6, 6, 6, 6);
            } catch (Exception e) {
                cardAlbumArt.setImageResource(R.drawable.ic_music);
                cardAlbumArt.setPadding(8, 8, 8, 8);
            }
        }

        if (cardWaveIcon != null) {
            cardWaveIcon.setImageResource(info.isPlaying ? R.drawable.ic_sound : R.drawable.ic_pause);
            if (info.packageName.contains("spotify")) {
                cardWaveIcon.setColorFilter(0xFF1DB954);
            } else if (info.packageName.contains("youtube")) {
                cardWaveIcon.setColorFilter(0xFFFF3344);
            } else {
                cardWaveIcon.setColorFilter(0xFF00F0FF);
            }
        }

        if (nowPlayingCard.getVisibility() != View.VISIBLE) {
            nowPlayingCard.setVisibility(View.VISIBLE);
            nowPlayingCard.setEnabled(true);
            nowPlayingCard.setAlpha(0f);
            nowPlayingCard.setScaleX(0.9f);
            nowPlayingCard.setScaleY(0.9f);
            nowPlayingCard.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withLayer()
                    .start();
        }
    }

    private void disableNowPlaying() {
        nowPlayingDisabledByUser = true;
        NowPlayingManager.pausePlayback();
        NowPlayingManager.clearMedia();
        hideNowPlayingCard();
    }

    private void hideNowPlayingCard() {
        if (nowPlayingCard == null) return;
        if (nowPlayingCard.getVisibility() != View.GONE) {
            nowPlayingCard.animate()
                    .alpha(0f)
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withLayer()
                    .withEndAction(() -> {
                        nowPlayingCard.setVisibility(View.GONE);
                        nowPlayingCard.setEnabled(false);
                        if (weatherAirQualityView != null) {
                            weatherAirQualityView.setNextFocusDownId(View.NO_ID);
                        }
                        showAyah(WeeklyAyah.stored(MainActivity.this) != null ? WeeklyAyah.stored(MainActivity.this) : WeeklyAyah.last(MainActivity.this));
                    })
                    .start();
        } else {
            nowPlayingCard.setVisibility(View.GONE);
            nowPlayingCard.setEnabled(false);
            if (weatherAirQualityView != null) {
                weatherAirQualityView.setNextFocusDownId(View.NO_ID);
            }
            showAyah(WeeklyAyah.stored(this) != null ? WeeklyAyah.stored(this) : WeeklyAyah.last(this));
        }
    }

    private void showNowPlayingDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View panel = getLayoutInflater().inflate(R.layout.dialog_media_hub, root, false);
        dialog.setContentView(panel);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (460 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.65f);
            w.setFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        ImageView headerIcon = panel.findViewById(R.id.headerIcon);
        TextView headerTitle = panel.findViewById(R.id.headerTitle);
        TextView badgeSource = panel.findViewById(R.id.badgeSource);
        TextView txtTrackTitle = panel.findViewById(R.id.txtTrackTitle);
        TextView txtTrackArtist = panel.findViewById(R.id.txtTrackArtist);
        TextView txtPlaybackStatus = panel.findViewById(R.id.txtPlaybackStatus);
        ImageView btnPopupPrev = panel.findViewById(R.id.btnPopupPrev);
        ImageView btnPlayPause = panel.findViewById(R.id.btnPlayPause);
        ImageView btnPopupNext = panel.findViewById(R.id.btnPopupNext);
        TextView btnOpenApp = panel.findViewById(R.id.btnOpenApp);
        TextView btnCloseDialog = panel.findViewById(R.id.btnCloseDialog);

        boolean isNeu = ThemeManager.isNeumorphism(this);
        boolean isDark = ThemeManager.isDarkGlow(this);

        // Apply theme styles to dialog panel and controls
        if (isNeu) {
            panel.setBackground(new NeumorphicDrawable(22 * density, density, NeumorphicDrawable.MODE_EXTRUDED, false));
            if (headerTitle != null) {
                headerTitle.setTextColor(0xFF1F2429);
                headerTitle.setShadowLayer(0, 0, 0, 0);
            }
            if (txtTrackTitle != null) {
                txtTrackTitle.setTextColor(0xFF1F2429);
                txtTrackTitle.setShadowLayer(0, 0, 0, 0);
            }
            if (txtTrackArtist != null) {
                txtTrackArtist.setTextColor(0xFF5A626E);
                txtTrackArtist.setShadowLayer(0, 0, 0, 0);
            }
            if (badgeSource != null) {
                badgeSource.setBackground(new NeumorphicDrawable(13 * density, density, NeumorphicDrawable.MODE_RECESSED, false));
            }

            for (ImageView btn : new ImageView[]{btnPopupPrev, btnPlayPause, btnPopupNext}) {
                if (btn == null) continue;
                float r = (btn == btnPlayPause) ? 26 * density : 20 * density;
                StateListDrawable sld = new StateListDrawable();
                sld.addState(new int[]{android.R.attr.state_focused},
                        new NeumorphicDrawable(r, density, NeumorphicDrawable.MODE_RECESSED, false));
                sld.addState(new int[]{},
                        new NeumorphicDrawable(r, density, NeumorphicDrawable.MODE_EXTRUDED, false));
                btn.setBackground(sld);
                btn.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF1F2429));
            }

            for (TextView btn : new TextView[]{btnOpenApp, btnCloseDialog}) {
                if (btn == null) continue;
                StateListDrawable sld = new StateListDrawable();
                sld.addState(new int[]{android.R.attr.state_focused},
                        new NeumorphicDrawable(12 * density, density, NeumorphicDrawable.MODE_RECESSED, false));
                sld.addState(new int[]{},
                        new NeumorphicDrawable(12 * density, density, NeumorphicDrawable.MODE_EXTRUDED, false));
                btn.setBackground(sld);
                btn.setTextColor(0xFF1F2429);
            }
        } else if (isDark) {
            GradientDrawable panelBg = new GradientDrawable();
            panelBg.setColor(0xF80B0F19);
            panelBg.setCornerRadius(22 * density);
            panelBg.setStroke(Math.max(1, Math.round(1.2f * density)), 0x4D00F0FF);
            panel.setBackground(panelBg);

            if (headerTitle != null) {
                headerTitle.setTextColor(0xFFFFFFFF);
                headerTitle.setShadowLayer(3f, 0, 1f, 0x80000000);
            }
            if (txtTrackTitle != null) {
                txtTrackTitle.setTextColor(0xFFFFFFFF);
                txtTrackTitle.setShadowLayer(3f, 0, 1f, 0x80000000);
            }
            if (txtTrackArtist != null) {
                txtTrackArtist.setTextColor(0xFF9EAFC2);
                txtTrackArtist.setShadowLayer(2f, 0, 1f, 0x80000000);
            }
            if (badgeSource != null) {
                GradientDrawable bbg = new GradientDrawable();
                bbg.setColor(0xFF121826);
                bbg.setCornerRadius(13 * density);
                bbg.setStroke(Math.max(1, Math.round(1f * density)), 0x3300F0FF);
                badgeSource.setBackground(bbg);
            }

            for (ImageView btn : new ImageView[]{btnPopupPrev, btnPlayPause, btnPopupNext}) {
                if (btn == null) continue;
                btn.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF00F0FF));
                StateListDrawable sld = new StateListDrawable();
                GradientDrawable foc = new GradientDrawable();
                foc.setShape(GradientDrawable.OVAL);
                foc.setColor(0x2B00F0FF);
                foc.setStroke(Math.max(1, Math.round(1.5f * density)), 0xFF00F0FF);

                GradientDrawable norm = new GradientDrawable();
                norm.setShape(GradientDrawable.OVAL);
                norm.setColor(0xFF141824);
                norm.setStroke(Math.max(1, Math.round(1f * density)), 0x3300F0FF);

                sld.addState(new int[]{android.R.attr.state_focused}, foc);
                sld.addState(new int[]{}, norm);
                btn.setBackground(sld);
            }

            if (btnOpenApp != null) {
                StateListDrawable sld = new StateListDrawable();
                GradientDrawable foc = new GradientDrawable();
                foc.setColor(0x3300F0FF);
                foc.setCornerRadius(12 * density);
                foc.setStroke(Math.max(1, Math.round(1.5f * density)), 0xFF00F0FF);

                GradientDrawable norm = new GradientDrawable();
                norm.setColor(0xFF141D2C);
                norm.setCornerRadius(12 * density);
                norm.setStroke(Math.max(1, Math.round(1f * density)), 0x4D00F0FF);

                sld.addState(new int[]{android.R.attr.state_focused}, foc);
                sld.addState(new int[]{}, norm);
                btnOpenApp.setBackground(sld);
                btnOpenApp.setTextColor(0xFF00F0FF);
            }

            if (btnCloseDialog != null) {
                StateListDrawable sld = new StateListDrawable();
                GradientDrawable foc = new GradientDrawable();
                foc.setColor(0x26FFFFFF);
                foc.setCornerRadius(12 * density);
                foc.setStroke(Math.max(1, Math.round(1.5f * density)), 0x88FFFFFF);

                GradientDrawable norm = new GradientDrawable();
                norm.setColor(0xFF141824);
                norm.setCornerRadius(12 * density);
                norm.setStroke(Math.max(1, Math.round(1f * density)), 0x26FFFFFF);

                sld.addState(new int[]{android.R.attr.state_focused}, foc);
                sld.addState(new int[]{}, norm);
                btnCloseDialog.setBackground(sld);
                btnCloseDialog.setTextColor(0xB3FFFFFF);
            }
        } else {
            // Frosted Glass
            GradientDrawable panelBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xEE222836, 0xEE161B26}
            );
            panelBg.setCornerRadius(22 * density);
            panelBg.setStroke(Math.max(1, Math.round(1.2f * density)), 0x4DFFFFFF);
            panel.setBackground(panelBg);
        }

        updatePopupMediaInfo(panel, dialog);

        if (btnPopupPrev != null) {
            btnPopupPrev.setOnClickListener(v -> {
                SoundEffects.playSelect();
                NowPlayingManager.skipToPrevious();
                handler.postDelayed(() -> {
                    NowPlayingManager.updateCurrentMedia(MainActivity.this);
                    updatePopupMediaInfo(panel, dialog);
                }, 300);
            });
        }

        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(v -> {
                SoundEffects.playSelect();
                NowPlayingManager.togglePlayPause();
                handler.postDelayed(() -> {
                    NowPlayingManager.updateCurrentMedia(MainActivity.this);
                    updatePopupMediaInfo(panel, dialog);
                }, 300);
            });
        }

        if (btnPopupNext != null) {
            btnPopupNext.setOnClickListener(v -> {
                SoundEffects.playSelect();
                NowPlayingManager.skipToNext();
                handler.postDelayed(() -> {
                    NowPlayingManager.updateCurrentMedia(MainActivity.this);
                    updatePopupMediaInfo(panel, dialog);
                }, 300);
            });
        }

        if (btnOpenApp != null) {
            btnOpenApp.setOnClickListener(v -> {
                SoundEffects.playSelect();
                dialog.dismiss();
                NowPlayingManager.openMediaApp(MainActivity.this);
            });
        }

        if (btnCloseDialog != null) {
            btnCloseDialog.setOnClickListener(v -> {
                SoundEffects.playSelect();
                dialog.dismiss();
                disableNowPlaying();
            });
        }

        View.OnFocusChangeListener btnFocus = (v, hasFocus) -> {
            if (hasFocus) SoundEffects.playNav();
            float scale = (hasFocus && !isNeu) ? 1.08f : 1.0f;
            v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(120)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withLayer()
                    .start();
        };

        if (btnPopupPrev != null) btnPopupPrev.setOnFocusChangeListener(btnFocus);
        if (btnPlayPause != null) btnPlayPause.setOnFocusChangeListener(btnFocus);
        if (btnPopupNext != null) btnPopupNext.setOnFocusChangeListener(btnFocus);
        if (btnOpenApp != null) btnOpenApp.setOnFocusChangeListener(btnFocus);
        if (btnCloseDialog != null) btnCloseDialog.setOnFocusChangeListener(btnFocus);

        dialog.show();
        if (btnPlayPause != null) {
            btnPlayPause.requestFocus();
        }
    }

    private void updatePopupMediaInfo(View panel, Dialog dialog) {
        if (panel == null || (dialog != null && !dialog.isShowing())) return;
        NowPlayingManager.MediaInfo info = NowPlayingManager.getCurrentInfo();
        boolean hasMedia = info != null && info.hasMusic() && NowPlayingManager.isPackageInstalled(this, info.packageName);

        ImageView headerIcon = panel.findViewById(R.id.headerIcon);
        TextView badgeSource = panel.findViewById(R.id.badgeSource);
        ImageView imgAlbumArt = panel.findViewById(R.id.imgAlbumArt);
        TextView txtTrackTitle = panel.findViewById(R.id.txtTrackTitle);
        TextView txtTrackArtist = panel.findViewById(R.id.txtTrackArtist);
        TextView txtPlaybackStatus = panel.findViewById(R.id.txtPlaybackStatus);
        ImageView btnPlayPause = panel.findViewById(R.id.btnPlayPause);
        TextView btnOpenApp = panel.findViewById(R.id.btnOpenApp);

        if (hasMedia) {
            if (txtTrackTitle != null) {
                txtTrackTitle.setText(info.title.isEmpty() ? "Lagu Diputar" : info.title);
                txtTrackTitle.setSelected(true);
            }
            if (txtTrackArtist != null) {
                txtTrackArtist.setText(info.artist.isEmpty() ? (info.appName.isEmpty() ? "Musik" : info.appName) : info.artist);
            }
            if (txtPlaybackStatus != null) {
                txtPlaybackStatus.setText(info.isPlaying ? "▶ Sedang Diputar" : "⏸ Jeda");
                txtPlaybackStatus.setTextColor(info.isPlaying ? Color.parseColor("#1DB954") : Color.parseColor("#FFC107"));
            }

            if (imgAlbumArt != null) {
                if (info.albumArt != null) {
                    imgAlbumArt.setImageBitmap(info.albumArt);
                    imgAlbumArt.setPadding(0, 0, 0, 0);
                } else if (!info.packageName.isEmpty()) {
                    try {
                        Drawable appIcon = getPackageManager().getApplicationIcon(info.packageName);
                        imgAlbumArt.setImageDrawable(appIcon);
                        imgAlbumArt.setPadding(16, 16, 16, 16);
                    } catch (Exception e) {
                        imgAlbumArt.setImageResource(R.drawable.ic_music);
                        imgAlbumArt.setPadding(18, 18, 18, 18);
                    }
                }
            }

            if (badgeSource != null) {
                badgeSource.setText(info.appName.isEmpty() ? "NOW PLAYING" : info.appName.toUpperCase(Locale.ROOT));
                if (info.packageName.contains("spotify")) {
                    badgeSource.setTextColor(Color.parseColor("#1DB954"));
                    if (headerIcon != null) headerIcon.setColorFilter(Color.parseColor("#1DB954"));
                } else if (info.packageName.contains("youtube")) {
                    badgeSource.setTextColor(Color.parseColor("#FF3344"));
                    if (headerIcon != null) headerIcon.setColorFilter(Color.parseColor("#FF3344"));
                } else {
                    badgeSource.setTextColor(Color.parseColor("#00F0FF"));
                    if (headerIcon != null) headerIcon.setColorFilter(Color.parseColor("#00F0FF"));
                }
            }

            if (btnPlayPause != null) {
                btnPlayPause.setImageResource(info.isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            }

            if (btnOpenApp != null) {
                btnOpenApp.setVisibility(View.VISIBLE);
                btnOpenApp.setText("Buka " + (info.appName.isEmpty() ? "Aplikasi" : info.appName));
            }
        } else {
            if (txtTrackTitle != null) txtTrackTitle.setText("Tidak Ada Lagu Diputar");
            if (txtTrackArtist != null) txtTrackArtist.setText("Buka YouTube atau Spotify untuk mulai mendengarkan");
            if (txtPlaybackStatus != null) {
                txtPlaybackStatus.setText("⏸ Tidak Ada Pemutaran");
                txtPlaybackStatus.setTextColor(Color.parseColor("#80FFFFFF"));
            }
            if (badgeSource != null) {
                badgeSource.setText("NOW PLAYING");
                badgeSource.setTextColor(Color.parseColor("#80FFFFFF"));
                if (headerIcon != null) headerIcon.setColorFilter(Color.parseColor("#80FFFFFF"));
            }
            if (btnPlayPause != null) {
                btnPlayPause.setImageResource(R.drawable.ic_play);
            }
            if (btnOpenApp != null) {
                btnOpenApp.setVisibility(View.GONE);
            }
        }
    }

    private Dialog qrUploadDialog;
    private WallpaperUploadServer uploadServer;
    private ImageView qrImageView;
    private TextView qrStatusText;
    private TextView qrUrlText;

    private void showThemeDialog() {
        final String activeTheme = ThemeManager.getActiveTheme(this);

        class ThemeOption {
            final String key;
            final String title;
            final String subtitle;
            ThemeOption(String key, String title, String subtitle) {
                this.key = key;
                this.title = title;
                this.subtitle = subtitle;
            }
        }

        List<ThemeOption> options = new ArrayList<>();
        options.add(new ThemeOption(ThemeManager.THEME_FROSTED_GLASS,
                "💎 Frosted Glass",
                "Efek kaca transparan bawaan Apple TV dengan wallpaper dinamis"));
        options.add(new ThemeOption(ThemeManager.THEME_NEUMORPHISM,
                "⚪ Neumorphism",
                "Tampilan cerah Soft UI taktil dengan bayangan lembut dan kanvas porselen elegan"));
        options.add(new ThemeOption(ThemeManager.THEME_DARK_GLOW,
                "⚡ Dark Glow",
                "Semua banner hitam pekat OLED dengan pendaran cahaya neon tegas"));

        List<ThemedDialogItem> items = new ArrayList<>();
        for (ThemeOption opt : options) {
            boolean isActive = opt.key.equalsIgnoreCase(activeTheme);
            items.add(new ThemedDialogItem(
                    opt.title,
                    opt.subtitle,
                    isActive,
                    isActive ? "AKTIF" : null,
                    () -> {
                        ThemeManager.setActiveTheme(MainActivity.this, opt.key);
                        applyHomeScreenTheme();
                        if (wallBitmap != null) {
                            blurBackdrop(wallBitmap);
                        }
                        if (adapter != null) {
                            adapter.invalidateTheme();
                        }
                        reloadTiles();
                        if (grid != null) {
                            for (int cIdx = 0; cIdx < grid.getChildCount(); cIdx++) {
                                View child = grid.getChildAt(cIdx);
                                RecyclerView.ViewHolder vh = grid.getChildViewHolder(child);
                                if (vh instanceof AppAdapter.Holder) {
                                    AppAdapter.Holder holder = (AppAdapter.Holder) vh;
                                    holder.syncFocusRim(holder.card.isFocused(), true);
                                }
                            }
                            grid.invalidateItemDecorations();
                            grid.invalidate();
                        }
                        if (glider != null) glider.invalidate();
                        if (glass != null) glass.invalidate();
                        Toast.makeText(MainActivity.this, "Tema diaktifkan: " + opt.title, Toast.LENGTH_SHORT).show();
                    }
            ));
        }

        showThemedListDialog(
                "🎨 Pilih Gaya Tema Tampilan",
                "Personalisasi estetika kartu, banner, dan pencahayaan TV",
                items,
                null,
                null
        );
    }

    private void showWallpaperDialog() {
        File customDir = new File(getFilesDir(), "custom_wallpapers");
        File[] customFiles = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
        int customCount = (customFiles != null) ? customFiles.length : 0;

        String rawKey = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("active_category", "Indonesia");
        final String currentKey = "Malaysia".equalsIgnoreCase(rawKey) ? "Indonesia" : rawKey;

        class WallpaperOption {
            final String key;
            final String title;
            final String subtitle;
            WallpaperOption(String key, String title, String subtitle) {
                this.key = key;
                this.title = title;
                this.subtitle = subtitle;
            }
        }

        List<WallpaperOption> options = new ArrayList<>();
        options.add(new WallpaperOption("Indonesia", "🇮🇩 Pesona Nusantara (Indonesia)", "Koleksi 4K Alam & Candi Ikonik Indonesia"));
        options.add(new WallpaperOption("Anime", "🎌 Anime HD", "Ilustrasi Sinematik, Langit Senja & Pemandangan Anime"));
        options.add(new WallpaperOption("Natural", "🌿 Alam & Pemandangan", "Pegunungan, Hutan & Aurora 4K"));
        options.add(new WallpaperOption("Skyscraper", "🏙️ Gedung Pencakar Langit & Kota", "Metropolis Modern & Arsitektur"));
        options.add(new WallpaperOption("Space", "🌌 Luar Angkasa & Kosmos", "Galaksi, Nebula & Bintang (Deep Space)"));
        options.add(new WallpaperOption("Ocean", "🌊 Lautan & Pesisir Pantai", "Air Jernih, Terumbu Karang & Pantai"));
        options.add(new WallpaperOption("Minimalist", "🔲 Minimalis Gelap (OLED)", "Gelap Murni, Kurva Abstrak 3D & Siluet Malam"));
        options.add(new WallpaperOption("MANAGE_CUSTOM", "📁 Kelola Gambar Unggahan",
                customCount > 0 ? (customCount + " foto diunggah • QR / Slideshow") : "Unggah foto pribadi lewat HP"));
        options.add(new WallpaperOption("NEXT", "🔀 Ganti Gambar Berikutnya", "Putar gambar berikutnya di kategori ini"));

        List<ThemedDialogItem> items = new ArrayList<>();
        for (WallpaperOption opt : options) {
            boolean isActive = opt.key.equalsIgnoreCase(currentKey)
                    || (opt.key.equals("Indonesia") && (currentKey.startsWith("Indonesia") || currentKey.startsWith("Malaysia")));
            final String chosenKey = opt.key;
            items.add(new ThemedDialogItem(
                    opt.title,
                    opt.subtitle,
                    isActive,
                    isActive ? "AKTIF" : null,
                    () -> {
                        if ("NEXT".equals(chosenKey)) {
                            if (wallpapers != null && !wallpapers.isEmpty()) {
                                showNextWallpaper(true);
                                Toast.makeText(MainActivity.this, "Mengganti ke gambar berikutnya...", Toast.LENGTH_SHORT).show();
                            }
                        } else if ("MANAGE_CUSTOM".equals(chosenKey)) {
                            showCustomWallpaperManagerDialog();
                        } else {
                            switchWallpaperCategory(chosenKey);
                        }
                    }
            ));
        }

        showThemedListDialog(
                "🖼️ Pilih Koleksi Wallpaper 4K",
                "Pexels & Unsplash CDN • Resolusi Ultra HD 3840×2160",
                items,
                null,
                null
        );
    }

    private void triggerGoogleAssistant() {
        SoundEffects.playSelect();
        // 1. Primary: System Google Assistant (same as Assistant key on remote)
        try {
            Intent intent = new Intent(Intent.ACTION_ASSIST);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // 2. Direct Katniss Voice Search (bypasses intent disambiguation chooser)
        try {
            Intent intent = new Intent("android.speech.action.RECOGNIZE_SPEECH");
            intent.setPackage("com.google.android.katniss");
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // 3. Voice Command Action
        try {
            Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // 4. Web Search Action
        try {
            Intent intent = new Intent("android.speech.action.WEB_SEARCH");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // 5. System Search Dialog
        try {
            startSearch(null, false, null, true);
            return;
        } catch (Exception ignored) {}

        // 6. Generic Speech Recognizer Fallback
        try {
            Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Google Assistant tidak dapat dibuka", Toast.LENGTH_SHORT).show();
        }
    }

    private void styleGlassDialog(AlertDialog dialog) {
        styleGlassDialog(this, dialog);
    }

    public static void styleGlassDialog(Activity ctx, AlertDialog dialog) {
        if (ctx == null || dialog == null) return;
        try {
            float d = ctx.getResources().getDisplayMetrics().density;
            boolean isDark = ThemeManager.isDarkGlow(ctx);
            boolean isNeu = ThemeManager.isNeumorphism(ctx);
            if (dialog.getWindow() != null) {
                if (isDark) {
                    GradientDrawable dialogBg = new GradientDrawable();
                    dialogBg.setColor(0xF508080C);
                    dialogBg.setCornerRadius(22 * d);
                    dialogBg.setStroke(Math.max(1, Math.round(1.15f * d)), 0x3300F0FF);
                    dialog.getWindow().setBackgroundDrawable(dialogBg);
                } else if (isNeu) {
                    NeumorphicDrawable dialogBg = new NeumorphicDrawable(22 * d, d, NeumorphicDrawable.MODE_EXTRUDED, false);
                    dialog.getWindow().setBackgroundDrawable(dialogBg);
                } else {
                    GradientDrawable dialogBg = new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{0xD9252B38, 0xD9191E2A}
                    );
                    dialogBg.setCornerRadius(22 * d);
                    dialogBg.setStroke(Math.max(1, Math.round(1.5f * d)), 0x66FFFFFF);
                    dialog.getWindow().setBackgroundDrawable(dialogBg);
                }
            }
            Typeface outfit = ctx.getResources().getFont(R.font.outfit);
            TextView titleView = dialog.findViewById(android.R.id.title);
            if (titleView != null) {
                titleView.setTypeface(outfit, Typeface.BOLD);
                titleView.setFontVariationSettings("'wght' 700");
                titleView.setTextSize(20f);
                titleView.setTextColor(isNeu ? 0xFF2B3036 : 0xFFFFFFFF);
                if (isNeu) titleView.setShadowLayer(0, 0, 0, 0);
                else titleView.setShadowLayer(4 * d, 0, 1 * d, 0xDD000000);
            }
            TextView msgView = dialog.findViewById(android.R.id.message);
            if (msgView != null) {
                msgView.setTypeface(outfit, Typeface.BOLD);
                msgView.setFontVariationSettings("'wght' 600");
                msgView.setTextSize(15f);
                msgView.setTextColor(isNeu ? 0xFF3A414A : 0xF0FFFFFF);
                msgView.setLineSpacing(0, 1.35f);
                if (isNeu) msgView.setShadowLayer(0, 0, 0, 0);
                else msgView.setShadowLayer(3 * d, 0, 1 * d, 0xCC000000);
            }
            int[] btnIds = new int[]{
                    DialogInterface.BUTTON_POSITIVE,
                    DialogInterface.BUTTON_NEGATIVE,
                    DialogInterface.BUTTON_NEUTRAL
            };
            for (int btnId : btnIds) {
                Button btn = dialog.getButton(btnId);
                if (btn == null) continue;
                boolean isPrimary = (btnId == DialogInterface.BUTTON_POSITIVE);
                btn.setTypeface(outfit, Typeface.BOLD);
                btn.setFontVariationSettings(isPrimary ? "'wght' 700" : "'wght' 650");
                btn.setTextSize(14.5f);
                btn.setAllCaps(false);
                btn.setPadding((int) (16 * d), (int) (6 * d), (int) (16 * d), (int) (6 * d));
                ViewGroup.LayoutParams rawLp = btn.getLayoutParams();
                if (rawLp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) rawLp;
                    mlp.leftMargin = (int) (6 * d);
                    mlp.rightMargin = (int) (6 * d);
                    btn.setLayoutParams(mlp);
                }

                StateListDrawable btnBg = new StateListDrawable();
                if (isNeu) {
                    NeumorphicDrawable onNeu = new NeumorphicDrawable(10 * d, d, NeumorphicDrawable.MODE_RECESSED, false);
                    NeumorphicDrawable offNeu = new NeumorphicDrawable(10 * d, d, NeumorphicDrawable.MODE_EXTRUDED, false);
                    btnBg.addState(new int[]{android.R.attr.state_focused}, onNeu);
                    btnBg.addState(new int[]{}, offNeu);
                    btn.setTextColor(new ColorStateList(
                            new int[][]{{android.R.attr.state_focused}, {}},
                            new int[]{0xFF1F2429, isPrimary ? 0xFF2B3036 : 0xFF4A5568}
                    ));
                } else if (isDark) {
                    GradientDrawable on = new GradientDrawable();
                    on.setColor(0xFF05070B);
                    on.setCornerRadius(10 * d);
                    on.setStroke(Math.max(1, Math.round(1.2f * d)), 0xFF00F0FF);
                    GradientDrawable off = new GradientDrawable();
                    off.setColor(0xFF10121A);
                    off.setCornerRadius(10 * d);
                    off.setStroke(Math.max(1, Math.round(1.0f * d)), 0x26FFFFFF);
                    btnBg.addState(new int[]{android.R.attr.state_focused}, on);
                    btnBg.addState(new int[]{}, off);
                    btn.setTextColor(new ColorStateList(
                            new int[][]{{android.R.attr.state_focused}, {}},
                            new int[]{0xFF00F0FF, isPrimary ? 0xFFFFFFFF : 0xCCFFFFFF}
                    ));
                } else {
                    GradientDrawable on = new GradientDrawable();
                    on.setColor(0x42FFFFFF);
                    on.setCornerRadius(10 * d);
                    on.setStroke(Math.max(1, Math.round(1.5f * d)), 0xF2FFFFFF);
                    GradientDrawable off = new GradientDrawable();
                    off.setColor(0x1CFFFFFF);
                    off.setCornerRadius(10 * d);
                    off.setStroke(Math.max(1, Math.round(1.0f * d)), 0x33FFFFFF);
                    btnBg.addState(new int[]{android.R.attr.state_focused}, on);
                    btnBg.addState(new int[]{}, off);
                    btn.setTextColor(new ColorStateList(
                            new int[][]{{android.R.attr.state_focused}, {}},
                            new int[]{0xFFFFFFFF, isPrimary ? 0xFFFFFFFF : 0xD9FFFFFF}
                    ));
                }
                btn.setBackground(btnBg);
            }
            ListView listView = dialog.getListView();
            if (listView != null) {
                listView.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        if (child instanceof TextView) {
                            TextView tv = (TextView) child;
                            tv.setTypeface(outfit, Typeface.BOLD);
                            tv.setFontVariationSettings("'wght' 650");
                            tv.setTextSize(15.5f);
                            tv.setTextColor(isNeu ? 0xFF1E242B : 0xFFFFFFFF);
                            if (isNeu) tv.setShadowLayer(0, 0, 0, 0);
                            else tv.setShadowLayer(3 * d, 0, 1 * d, 0xAA000000);
                            tv.setPadding((int) (18 * d), (int) (12 * d), (int) (18 * d), (int) (12 * d));
                        }
                    }

                    @Override
                    public void onChildViewRemoved(View parent, View child) {}
                });
            }
        } catch (Exception ignored) {}
    }

    private void switchWallpaperCategory(String category) {
        getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit().putString("active_category", category).apply();
        String displayLabel = ("Indonesia".equalsIgnoreCase(category) || "Malaysia".equalsIgnoreCase(category)) ? "Pesona Nusantara" : category;
        Toast.makeText(this, "Kategori: " + displayLabel, Toast.LENGTH_SHORT).show();

        if ("Custom".equalsIgnoreCase(category)) {
            placesMode = false;
            File customDir = new File(getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                wallpapers = new ArrayList<>(Arrays.asList(files));
                getSharedPreferences("wallpaper", MODE_PRIVATE).edit().putInt("index", -1).apply();
                showNextWallpaper(true);
                return;
            }
        } else {
            placesMode = false;
        }

        List<File> matching = OnlineWallpapers.listByCategory(this, category);
        final boolean hadCached = !matching.isEmpty();
        if (hadCached) {
            wallpapers = matching;
            showNextWallpaper(true);
        } else {
            Toast.makeText(this, "Mengunduh Wallpaper 4K " + displayLabel + "...", Toast.LENGTH_SHORT).show();
        }
        if (matching.size() < OnlineWallpapers.countForCategory(category)) {
            new Thread(() -> {
                boolean downloaded = OnlineWallpapers.syncCategory(getApplicationContext(), category);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    List<File> fresh = OnlineWallpapers.listByCategory(MainActivity.this, category);
                    if (!fresh.isEmpty()) {
                        wallpapers = fresh;
                        if (!hadCached) {
                            showNextWallpaper(true);
                        }
                    }
                });
            }, "wall-cat-sync").start();
        }
    }

    private void smartBoostTv() {
        new Thread(() -> {
            try {
                File cache = getCacheDir();
                if (cache != null && cache.isDirectory()) {
                    File[] files = cache.listFiles();
                    if (files != null) for (File f : files) f.delete();
                }
            } catch (Exception ignored) {}
            CacheCleaner.maybeRun(getApplicationContext(), 1);
            System.gc();
            handler.post(() -> Toast.makeText(this, "🧹 Memori & cache TV berhasil dioptimalkan!", Toast.LENGTH_SHORT).show());
        }, "cache-boost").start();
    }

    private void showWallpaperUploadQrDialog() {
        if (uploadServer != null) {
            uploadServer.stop();
            uploadServer = null;
        }

        String localIp = WallpaperUploadServer.getLocalIpAddress();
        int port = 8080;
        WallpaperUploadServer.UploadListener listener = createUploadListener();
        uploadServer = new WallpaperUploadServer(this, port, listener);
        boolean started = uploadServer.start();
        if (!started) {
            port = 8888;
            uploadServer = new WallpaperUploadServer(this, port, listener);
            started = uploadServer.start();
        }

        if (!started) {
            Toast.makeText(this, "Gagal: Tidak dapat memulai server unggah lokal.", Toast.LENGTH_LONG).show();
            return;
        }

        String uploadUrl = "http://" + localIp + ":" + port;
        Bitmap qrBmp = QrBitmapUtil.createQrBitmap(uploadUrl, Math.round(260 * density));

        boolean isNeu = ThemeManager.isNeumorphism(this);
        boolean isDark = ThemeManager.isDarkGlow(this);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        int padH = Math.round(36 * density);
        int padV = Math.round(28 * density);
        rootLayout.setPadding(padH, padV, padH, padV);
        if (isNeu) {
            rootLayout.setBackground(new NeumorphicDrawable(22 * density, density, NeumorphicDrawable.MODE_EXTRUDED, false));
        } else if (isDark) {
            GradientDrawable qrBg = new GradientDrawable();
            qrBg.setColor(0xF508080C);
            qrBg.setCornerRadius(22 * density);
            qrBg.setStroke(Math.max(1, Math.round(1.2f * density)), 0x3300F0FF);
            rootLayout.setBackground(qrBg);
        } else {
            rootLayout.setBackgroundColor(0xF0131722); // Elegant dark slate
        }

        // Title
        TextView title = new TextView(this);
        title.setText("📲 Unggah Wallpaper Dari HP");
        title.setTextColor(isNeu ? 0xFF2B3036 : 0xFFFFFFFF);
        title.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        title.setFontVariationSettings("'wght' 700");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        if (isNeu) title.setShadowLayer(0, 0, 0, 0);
        else title.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
        title.setGravity(Gravity.CENTER);
        rootLayout.addView(title);

        // Subtitle instructions
        TextView sub = new TextView(this);
        sub.setText("Hubungkan HP ke Wi-Fi yang sama, lalu pindai kode QR di bawah:");
        sub.setTextColor(isNeu ? 0xFF5A626E : 0xFFE2E8F0);
        sub.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        sub.setFontVariationSettings("'wght' 600");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        if (isNeu) sub.setShadowLayer(0, 0, 0, 0);
        else sub.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Math.round(6 * density), 0, Math.round(18 * density));
        rootLayout.addView(sub);

        // QR Code Container (Crisp White Card with subtle margin)
        FrameLayout qrContainer = new FrameLayout(this);
        GradientDrawable qrCardBg = new GradientDrawable();
        qrCardBg.setColor(0xFFFFFFFF);
        qrCardBg.setCornerRadius(14 * density);
        if (isNeu) {
            qrCardBg.setStroke(Math.max(1, Math.round(1.0f * density)), 0x22000000);
        } else if (isDark) {
            qrCardBg.setStroke(Math.max(1, Math.round(1.2f * density)), 0x8800F0FF);
        }
        qrContainer.setBackground(qrCardBg);
        int qrPad = Math.round(12 * density);
        qrContainer.setPadding(qrPad, qrPad, qrPad, qrPad);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qrLp.gravity = Gravity.CENTER_HORIZONTAL;
        qrContainer.setLayoutParams(qrLp);

        qrImageView = new ImageView(this);
        if (qrBmp != null) {
            qrImageView.setImageBitmap(qrBmp);
        }
        int qrDim = Math.round(220 * density);
        FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(qrDim, qrDim);
        imgLp.gravity = Gravity.CENTER;
        qrImageView.setLayoutParams(imgLp);
        qrContainer.addView(qrImageView);
        rootLayout.addView(qrContainer);

        // Web URL pill
        qrUrlText = new TextView(this);
        qrUrlText.setText("🌐 " + uploadUrl);
        qrUrlText.setTextColor(isNeu ? 0xFF0284C7 : (isDark ? 0xFF00F0FF : 0xFF38BDF8));
        qrUrlText.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        qrUrlText.setFontVariationSettings("'wght' 700");
        qrUrlText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        if (isNeu) qrUrlText.setShadowLayer(0, 0, 0, 0);
        else qrUrlText.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        qrUrlText.setGravity(Gravity.CENTER);
        qrUrlText.setPadding(0, Math.round(14 * density), 0, Math.round(4 * density));
        rootLayout.addView(qrUrlText);

        // Status text
        qrStatusText = new TextView(this);
        qrStatusText.setText("📡 Menunggu pindaian kamera atau foto dari HP...");
        qrStatusText.setTextColor(isNeu ? 0xFF3A414A : 0xFFF1F5F9);
        qrStatusText.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        qrStatusText.setFontVariationSettings("'wght' 600");
        qrStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (isNeu) qrStatusText.setShadowLayer(0, 0, 0, 0);
        else qrStatusText.setShadowLayer(3 * density, 0, 1 * density, 0xAA000000);
        qrStatusText.setGravity(Gravity.CENTER);
        qrStatusText.setPadding(0, 0, 0, Math.round(18 * density));
        rootLayout.addView(qrStatusText);

        // Close button
        Button btnClose = new Button(this);
        btnClose.setText("Tutup / Batal");
        btnClose.setAllCaps(false);
        StateListDrawable closeBg = new StateListDrawable();
        if (isNeu) {
            closeBg.addState(new int[]{android.R.attr.state_focused},
                    new NeumorphicDrawable(12 * density, density, NeumorphicDrawable.MODE_RECESSED, false));
            closeBg.addState(new int[]{},
                    new NeumorphicDrawable(12 * density, density, NeumorphicDrawable.MODE_EXTRUDED, false));
            btnClose.setTextColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{0xFF1F2429, 0xFF2B3036}));
        } else if (isDark) {
            GradientDrawable on = new GradientDrawable();
            on.setColor(0xFF05070B);
            on.setCornerRadius(12 * density);
            on.setStroke(Math.max(1, Math.round(1.2f * density)), 0xFF00F0FF);
            GradientDrawable off = new GradientDrawable();
            off.setColor(0xFF10121A);
            off.setCornerRadius(12 * density);
            off.setStroke(Math.max(1, Math.round(1.0f * density)), 0x33FFFFFF);
            closeBg.addState(new int[]{android.R.attr.state_focused}, on);
            closeBg.addState(new int[]{}, off);
            btnClose.setTextColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{0xFF00F0FF, 0xFFFFFFFF}));
        } else {
            GradientDrawable on = new GradientDrawable();
            on.setColor(0x42FFFFFF);
            on.setCornerRadius(12 * density);
            on.setStroke(Math.max(2, Math.round(1.5f * density)), 0xF2FFFFFF);
            GradientDrawable off = new GradientDrawable();
            off.setColor(0x1EFFFFFF);
            off.setCornerRadius(12 * density);
            off.setStroke(Math.max(1, Math.round(1.0f * density)), 0x40FFFFFF);
            closeBg.addState(new int[]{android.R.attr.state_focused}, on);
            closeBg.addState(new int[]{}, off);
            btnClose.setTextColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{0xFFFFFFFF, 0xE6FFFFFF}));
        }
        btnClose.setBackground(closeBg);
        btnClose.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        btnClose.setFontVariationSettings("'wght' 700");
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnClose.setFocusable(true);
        btnClose.setFocusableInTouchMode(true);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                Math.round(180 * density), Math.round(42 * density));
        btnLp.gravity = Gravity.CENTER_HORIZONTAL;
        btnClose.setLayoutParams(btnLp);
        rootLayout.addView(btnClose);

        // Watermark credit
        TextView credit = new TextView(this);
        credit.setText("✨ Aura TV Home • Edisi Indonesia");
        credit.setTextColor(isNeu ? 0xFF5A626E : 0xFFCBD5E1);
        credit.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        credit.setFontVariationSettings("'wght' 600");
        credit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, Math.round(14 * density), 0, 0);
        rootLayout.addView(credit);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setView(rootLayout);
        AlertDialog createdQrDialog = builder.create();
        qrUploadDialog = createdQrDialog;

        btnClose.setOnClickListener(v -> qrUploadDialog.dismiss());

        qrUploadDialog.setOnDismissListener(dialog -> {
            if (uploadServer != null) {
                uploadServer.stop();
                uploadServer = null;
            }
            qrUploadDialog = null;
        });

        createdQrDialog.show();
        styleGlassDialog(createdQrDialog);
        btnClose.requestFocus();
    }

    private WallpaperUploadServer.UploadListener createUploadListener() {
        return new WallpaperUploadServer.UploadListener() {
            @Override
            public void onUploaded(File imageFile) {
                onCustomWallpaperUploaded(imageFile);
            }

            @Override
            public void onDeleted(File file) {
                onCustomWallpaperDeleted(file);
            }

            @Override
            public void onSelected(File file) {
                onCustomWallpaperSelected(file);
            }

            @Override
            public void onSlideshowMode() {
                onCustomWallpaperSlideshow();
            }

            @Override
            public void onCleared() {
                onCustomWallpaperCleared();
            }
        };
    }

    private void onCustomWallpaperUploaded(File file) {
        runOnUiThread(() -> {
            SoundEffects.playSelect();
            Toast.makeText(this, "✅ Wallpaper berhasil diunggah dari HP!", Toast.LENGTH_LONG).show();

            getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                    .putString("active_category", "Custom")
                    .putBoolean("custom_single_mode", false)
                    .remove("custom_single_file")
                    .apply();

            placesMode = false;
            File customDir = new File(getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                wallpapers = new ArrayList<>(Arrays.asList(files));
            } else {
                wallpapers = Collections.singletonList(file);
            }

            getSharedPreferences("wallpaper", MODE_PRIVATE).edit().putInt("index", -1).apply();
            showNextWallpaper(true);

            if (qrStatusText != null) {
                qrStatusText.setText("🎉 Berhasil Diterima & Ditampilkan di TV!");
                qrStatusText.setTextColor(0xFF4ADE80);
            }
            if (qrUrlText != null) {
                qrUrlText.setText("✅ Wallpaper Aktif: " + file.getName());
                qrUrlText.setTextColor(0xFF4ADE80);
            }
            if (qrImageView != null) {
                try {
                    Bitmap thumb = decodeWallpaper(file);
                    if (thumb != null) {
                        qrImageView.setImageBitmap(thumb);
                        qrImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    }
                } catch (Exception ignored) {}
            }

            handler.postDelayed(() -> {
                if (qrUploadDialog != null && qrUploadDialog.isShowing()) {
                    qrUploadDialog.dismiss();
                }
            }, 3000);
        });
    }

    private void onCustomWallpaperSelected(File file) {
        runOnUiThread(() -> {
            SoundEffects.playSelect();
            getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                    .putString("active_category", "Custom")
                    .putBoolean("custom_single_mode", true)
                    .putString("custom_single_file", file.getName())
                    .apply();

            placesMode = false;
            wallpapers = Collections.singletonList(file);
            getSharedPreferences("wallpaper", MODE_PRIVATE).edit().putInt("index", -1).apply();
            showNextWallpaper(true);
            Toast.makeText(this, "📌 Kunci 1 Wallpaper: " + file.getName(), Toast.LENGTH_SHORT).show();
        });
    }

    private void onCustomWallpaperSlideshow() {
        runOnUiThread(() -> {
            SoundEffects.playSelect();
            getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                    .putString("active_category", "Custom")
                    .putBoolean("custom_single_mode", false)
                    .remove("custom_single_file")
                    .apply();

            placesMode = false;

            File customDir = new File(getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                wallpapers = new ArrayList<>(Arrays.asList(files));
                getSharedPreferences("wallpaper", MODE_PRIVATE).edit().putInt("index", -1).apply();
                showNextWallpaper(true);
                Toast.makeText(this, "🔄 Mode Putar Semua Wallpaper Aktif (" + files.length + " foto)", Toast.LENGTH_SHORT).show();
            } else {
                switchWallpaperCategory("Natural");
            }
        });
    }

    private void onCustomWallpaperDeleted(File file) {
        runOnUiThread(() -> {
            String singleFile = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("custom_single_file", null);
            if (singleFile != null && singleFile.equals(file.getName())) {
                getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                        .putBoolean("custom_single_mode", false)
                        .remove("custom_single_file")
                        .apply();
            }

            File customDir = new File(getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
            String activeCat = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("active_category", "Natural");

            if ("Custom".equalsIgnoreCase(activeCat)) {
                if (files != null && files.length > 0) {
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    wallpapers = new ArrayList<>(Arrays.asList(files));
                    getSharedPreferences("wallpaper", MODE_PRIVATE).edit().putInt("index", -1).apply();
                    showNextWallpaper(true);
                    Toast.makeText(this, "🗑️ Gambar dihapus. Tersisa " + files.length + " foto.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Semua gambar telah dihapus. Beralih ke Alam.", Toast.LENGTH_SHORT).show();
                    switchWallpaperCategory("Natural");
                }
            } else {
                Toast.makeText(this, "🗑️ Gambar dihapus dari penyimpanan.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onCustomWallpaperCleared() {
        runOnUiThread(() -> {
            getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                    .putBoolean("custom_single_mode", false)
                    .remove("custom_single_file")
                    .apply();

            String activeCat = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("active_category", "Natural");
            if ("Custom".equalsIgnoreCase(activeCat)) {
                switchWallpaperCategory("Natural");
            }
            Toast.makeText(this, "🧹 Semua gambar unggahan telah dibersihkan.", Toast.LENGTH_SHORT).show();
        });
    }

    private void showCustomWallpaperManagerDialog() {
        File customDir = new File(getFilesDir(), "custom_wallpapers");
        File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
        if (files == null || files.length == 0) {
            AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog)
                    .setTitle("📁 Kelola Gambar Unggahan")
                    .setMessage("Tidak ada gambar pribadi ditemukan di penyimpanan TV. Anda dapat mengunggah gambar dari HP menggunakan kode QR.")
                    .setPositiveButton("📲 Buka Kode QR", (d, w) -> showWallpaperUploadQrDialog())
                    .setNegativeButton("Kembali", (d, w) -> showWallpaperDialog())
                    .show();
            styleGlassDialog(dialog);
            return;
        }

        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        boolean isSingleMode = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getBoolean("custom_single_mode", false);
        String singleFile = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("custom_single_file", "");

        List<ThemedDialogItem> items = new ArrayList<>();

        items.add(new ThemedDialogItem(
                "📲 Unggah Foto Baru (Kode QR HP)",
                "Pindai kode QR dari HP untuk menambahkan foto baru",
                false,
                null,
                this::showWallpaperUploadQrDialog
        ));

        items.add(new ThemedDialogItem(
                "🔄 Putar Semua Gambar (Mode Slideshow)",
                "Tampilkan semua " + files.length + " foto secara bergantian",
                !isSingleMode,
                !isSingleMode ? "AKTIF" : null,
                this::onCustomWallpaperSlideshow
        ));

        items.add(new ThemedDialogItem(
                "🗑️ Hapus Semua Gambar (Kosongkan)",
                "Bersihkan seluruh foto pribadi dari penyimpanan TV",
                false,
                null,
                () -> {
                    AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog)
                            .setTitle("⚠️ Hapus Semua Gambar?")
                            .setMessage("Apakah Anda yakin ingin menghapus semua " + files.length + " gambar yang diunggah?")
                            .setPositiveButton("Ya, Hapus Semua", (d, w) -> {
                                for (File f : files) f.delete();
                                onCustomWallpaperCleared();
                            })
                            .setNegativeButton("Batal", null)
                            .show();
                    styleGlassDialog(dialog);
                }
        ));

        SimpleDateFormat sdf = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());
        for (File f : files) {
            double kb = f.length() / 1024.0;
            String sizeStr = kb > 1024 ? String.format(Locale.ROOT, "%.1f MB", kb / 1024.0) : String.format(Locale.ROOT, "%.0f KB", kb);
            boolean isCurrent = isSingleMode && f.getName().equals(singleFile);
            items.add(new ThemedDialogItem(
                    (isCurrent ? "📌 " : "🖼️ ") + f.getName(),
                    sizeStr + " • Diunggah " + sdf.format(new Date(f.lastModified())),
                    isCurrent,
                    isCurrent ? "DIKUNCI" : null,
                    () -> showSingleCustomActionDialog(f)
            ));
        }

        showThemedListDialog(
                "📱 Pengelola Gambar TV (" + files.length + " Foto)",
                "Kelola slideshow atau kunci satu foto pilihan di beranda",
                items,
                null,
                null
        );
    }

    private void showSingleCustomActionDialog(File file) {
        List<ThemedDialogItem> items = new ArrayList<>();
        items.add(new ThemedDialogItem(
                "📌 Jadikan Wallpaper Tunggal",
                "Kunci gambar ini saja sebagai latar beranda TV",
                false,
                null,
                () -> onCustomWallpaperSelected(file)
        ));
        items.add(new ThemedDialogItem(
                "🗑️ Hapus Gambar Ini",
                "Hapus " + file.getName() + " dari penyimpanan",
                false,
                null,
                () -> {
                    file.delete();
                    onCustomWallpaperDeleted(file);
                }
        ));
        items.add(new ThemedDialogItem(
                "🧹 Simpan Gambar Ini Saja & Hapus Yang Lain",
                "Pertahankan foto ini dan bersihkan foto unggahan lainnya",
                false,
                null,
                () -> {
                    File customDir = new File(getFilesDir(), "custom_wallpapers");
                    File[] allFiles = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
                    if (allFiles != null) {
                        for (File f : allFiles) {
                            if (!f.getName().equals(file.getName())) {
                                f.delete();
                            }
                        }
                    }
                    onCustomWallpaperSelected(file);
                    Toast.makeText(this, "🧹 Gambar lain dihapus. Gambar ini dipertahankan.", Toast.LENGTH_SHORT).show();
                }
        ));
        items.add(new ThemedDialogItem(
                "⬅️ Kembali ke Pengelola Gambar",
                "Kembali ke daftar foto unggahan",
                false,
                null,
                this::showCustomWallpaperManagerDialog
        ));

        showThemedListDialog(
                "🖼️ " + file.getName(),
                "Pilih tindakan untuk foto unggahan ini",
                items,
                null,
                null
        );
    }

    private void showHazeLocationDialog() {
        showAsiaCountryDialog();
    }

    private void showBraviaLocationDialog() {
        showAsiaCountryDialog();
    }

    private void showAsiaCountryDialog() {
        CitySearchDialog.show(this, this::refreshLocationDependentData);
    }

    private void showStationListDialog(String region) {
        CitySearchDialog.show(this, this::refreshLocationDependentData);
    }

    private void showPrayerZoneDialog() {
        showAsiaCountryDialog();
    }

    /** Shows an Apple TV-style frosted glass Putrajaya prayer timetable popup. */
    private void showPrayerPopup() {
        if (openMenu != null) return;
        SoundEffects.playSelect();
        View anchor = prayerTimesView != null ? prayerTimesView : findViewById(R.id.leftBlock);
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        Runnable close = () -> closeMenu(false);

        // Header: Active Prayer Zone
        addInputRow(rows, close, R.drawable.ic_settings, "Waktu Solat " + PrayerTimes.getShortZoneName(this) + " (Tukar Zon ▶)", TvInputManager.INPUT_STATE_CONNECTED, () -> {
            SoundEffects.playSelect();
            closeMenu(false);
            startActivity(new Intent(MainActivity.this, LocationActivity.class));
        });

        // Date row: Islamic Hijri
        addInputRow(rows, close, R.drawable.ic_input, PrayerTimes.getHijriDate(), -1, () -> {});

        addSeparator(rows);

        // Prayer times list
        java.util.List<PrayerTimes.ScheduleItem> schedule = PrayerTimes.getFullSchedule(this);
        for (PrayerTimes.ScheduleItem item : schedule) {
            addPrayerRow(rows, close, item.name, item.timeFormatted, item.isNext);
        }

        addSeparator(rows);

        // Tetapan Azan shortcut
        addInputRow(rows, close, R.drawable.ic_azan, "Tetapan & Pilihan Azan ▶", -1, () -> {
            SoundEffects.playSelect();
            closeMenu(false);
            showAzanSettingsDialog();
        });

        // Tutup
        addInputRow(rows, close, R.drawable.ic_close, "Tutup", -1, () -> {
            SoundEffects.playSelect();
            closeMenu(true);
        });

        int width = (int) (270 * density);
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        width = Math.max(width, panel.getMeasuredWidth());

        showMenu(panel, rows, anchor, width, false);
    }

    private void addPrayerRow(LinearLayout rows, Runnable close, String name, String time, boolean isNext) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_input);
        TextView title = row.findViewById(R.id.title);
        title.setText(name + (isNext ? " (Seterusnya)" : ""));
        title.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        title.setFontVariationSettings(isNext ? "'wght' 700" : "'wght' 650");
        title.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);

        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        status.setText(time);
        status.setVisibility(View.VISIBLE);
        status.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        status.setFontVariationSettings(isNext ? "'wght' 700" : "'wght' 600");
        status.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);

        if (isNext) {
            dot.setVisibility(View.VISIBLE);
        } else {
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            SoundEffects.playSelect();
            close.run();
        });
        rows.addView(row);
    }

    private void checkInactivity() {
        if (!hasWindowFocus() || isFinishing() || isDestroyed()) return;
        long idleMs = android.os.SystemClock.elapsedRealtime() - lastUserInteraction;
        if (idleMs >= TV_SLEEP_TIMEOUT_MS) {
            turnOffTv();
        } else if (idleMs >= SCREENSAVER_TIMEOUT_MS && !screensaverActive) {
            showNightstandScreensaver();
        } else if (screensaverActive && screensaverView != null) {
            updateNightstandScreensaver();
        }
    }

    private void showNightstandScreensaver() {
        if (screensaverActive || isFinishing() || isDestroyed()) return;
        if (wallpaperFullscreen) {
            setWallpaperFullscreen(false);
        }
        screensaverActive = true;
        if (screensaverView == null) {
            screensaverView = getLayoutInflater().inflate(R.layout.view_nightstand, root, false);
            screensaverView.setOnClickListener(v -> dismissScreensaver());
            root.addView(screensaverView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        updateNightstandScreensaver();

        screensaverView.setVisibility(View.VISIBLE);
        screensaverView.setAlpha(0f);
        screensaverView.animate().alpha(1f).setDuration(500).start();
        android.util.Log.i("TvHome", "Nightstand screensaver activated");
    }

    private void updateNightstandScreensaver() {
        if (screensaverView == null) return;
        TextView hijri = screensaverView.findViewById(R.id.nightstandHijri);
        if (hijri != null) hijri.setVisibility(View.GONE);

        TextView prayer = screensaverView.findViewById(R.id.nightstandPrayer);
        if (prayer != null) prayer.setVisibility(View.GONE);

        // Center locked strictly with no movement
        View content = screensaverView.findViewById(R.id.nightstandContent);
        if (content != null) {
            content.animate().cancel();
            content.setTranslationX(0f);
            content.setTranslationY(0f);
        }

        // iPhone StandBy Wind Down Mode: 10:00 PM (22:00) to 6:00 AM (06:00) -> Red Monochrome
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        boolean isNightMode = (hour >= 22 || hour < 6);

        TextView clock = screensaverView.findViewById(R.id.nightstandClock);
        TextView ampm = screensaverView.findViewById(R.id.nightstandAmPm);
        TextView date = screensaverView.findViewById(R.id.nightstandDate);
        TextView hint = screensaverView.findViewById(R.id.nightstandHint);

        if (isNightMode) {
            int red = 0xFFFF453A;
            if (clock != null) clock.setTextColor(red);
            if (ampm != null) ampm.setTextColor(0xCCFF453A);
            if (date != null) date.setTextColor(0xE6FF453A);
            if (hijri != null) hijri.setTextColor(0xE6FF453A);
            if (prayer != null) {
                prayer.setTextColor(red);
                android.graphics.drawable.GradientDrawable redPill = new android.graphics.drawable.GradientDrawable();
                redPill.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                redPill.setColor(0x26FF453A);
                redPill.setStroke((int) (1 * density), 0x59FF453A);
                redPill.setCornerRadius(21 * density);
                prayer.setBackground(redPill);
            }
            if (hint != null) hint.setTextColor(0x55FF453A);
        } else {
            if (clock != null) clock.setTextColor(0xFFFFFFFF);
            if (ampm != null) ampm.setTextColor(0xB3FFFFFF);
            if (date != null) date.setTextColor(0xE6FFFFFF);
            if (hijri != null) hijri.setTextColor(0xD9E6E0D2);
            if (prayer != null) {
                prayer.setTextColor(0xFFFFFFFF);
                prayer.setBackgroundResource(R.drawable.pill_bg);
            }
            if (hint != null) hint.setTextColor(0x4DFFFFFF);
        }
    }

    private void dismissScreensaver() {
        if (!screensaverActive || screensaverView == null) return;
        screensaverActive = false;
        lastUserInteraction = android.os.SystemClock.elapsedRealtime();
        screensaverView.animate().alpha(0f).setDuration(350).withEndAction(() -> {
            if (!screensaverActive && screensaverView != null) {
                screensaverView.setVisibility(View.GONE);
            }
        }).start();
        android.util.Log.i("TvHome", "Nightstand screensaver dismissed");
    }

    private void turnOffTv() {
        android.util.Log.i("TvHome", "TV idle: 10m screensaver + 2h timeout reached, putting TV to sleep");
        boolean ok = HomeKeyService.sleepScreen();
        if (!ok) {
            try {
                Runtime.getRuntime().exec("input keyevent 223");
            } catch (Exception e) {
                android.util.Log.w("TvHome", "Failed to send sleep keyevent: " + e);
            }
        }
    }

    private void ensureSystemScreensaverAndSleepSettings() {
        new Thread(() -> {
            try {
                android.content.ContentResolver cr = getContentResolver();
                // 10 minutes idle -> screensaver (600,000 ms)
                android.provider.Settings.System.putInt(cr, "screen_off_timeout", 600000);
                // 2 hours after screensaver -> TV off (10m + 2h = 7,800,000 ms)
                android.provider.Settings.Secure.putInt(cr, "sleep_timeout", 7800000);
                android.provider.Settings.Secure.putInt(cr, "screensaver_enabled", 1);
                android.provider.Settings.Secure.putString(cr, "screensaver_components", "com.gathin.tvhome/.ClockDream");

                // Auto-enable HomeKeyService for system actions (restart, sleep, force-stop)
                String enabledServices = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                String myService = getPackageName() + "/" + HomeKeyService.class.getName();
                if (enabledServices == null || !enabledServices.contains(myService)) {
                    String newServices = enabledServices == null || enabledServices.isEmpty() ? myService : enabledServices + ":" + myService;
                    android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices);
                    android.provider.Settings.Secure.putInt(cr, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                }

                // Auto-enable MediaNotificationListener for Now Playing media widget
                String enabledListeners = android.provider.Settings.Secure.getString(cr, "enabled_notification_listeners");
                String myListener = getPackageName() + "/" + MediaNotificationListener.class.getName();
                if (enabledListeners == null || !enabledListeners.contains(myListener)) {
                    String newListeners = enabledListeners == null || enabledListeners.isEmpty() ? myListener : enabledListeners + ":" + myListener;
                    try {
                        android.provider.Settings.Secure.putString(cr, "enabled_notification_listeners", newListeners);
                    } catch (Exception ignored) {}
                }
                try {
                    Runtime.getRuntime().exec(new String[]{"cmd", "notification", "allow_listener", myListener, "0"});
                } catch (Exception ignored) {}
            } catch (Exception ignored) {
            }
        }, "system-timeout-sync").start();
    }


    /**
     * China-market MIUI TV firmware answers no standard Settings intents at all; open Xiaomi's own
     * settings app (com.xiaomi.mitv.settings) there, so the Settings and Wi-Fi pills keep working.
     * On firmware with a standard settings handler the behaviour is unchanged.
     */
    private Intent systemIntent(String standardAction) {
        Intent i = new Intent(standardAction);
        if (i.resolveActivity(getPackageManager()) != null) return i;
        i = new Intent().setComponent(new ComponentName(
                "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
        if (i.resolveActivity(getPackageManager()) != null) return i;
        return new Intent(standardAction);   // nothing found: let open() show the can't-open toast
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    private void updateInputsButton() {
        boolean any = liveTvIntent() != null || !sourcePanelService.isEmpty();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput()) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    private Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9). */
    private void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        try {
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Intent i) {
        SoundEffects.playSelect();
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Switches to a passthrough input (HDMI, AV). The standard way is ACTION_VIEW on its channel URI,
     *  which the Live TV app handles; where nothing takes that, the configured `input_action` is sent
     *  with the same URI instead (XGIMI's HDMI player only answers com.xgimi.action.hdmiPlayer). */
    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(view);
            return;
        } catch (ActivityNotFoundException e) {
            if (inputAction.isEmpty()) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        open(new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"));
    }

    private View appDrawerOverlay;
    private View appDrawerPanel;
    private View addAppOverlay;
    private View addAppPanel;

    @Override
    public void onAppDrawerClicked() {
        showGlassAppDrawer();
    }

    @Override
    public void onAddAppClicked() {
        showAddAppSheet();
    }

    private static final class DrawerAppItem {
        final String label;
        final String pkg;
        final ComponentName component;
        final boolean isLeanback;
        final Drawable banner;
        final Drawable icon;

        DrawerAppItem(String label, String pkg, ComponentName component, boolean isLeanback, Drawable banner, Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.component = component;
            this.isLeanback = isLeanback;
            this.banner = banner;
            this.icon = icon;
        }
    }

    private void showGlassAppDrawer() {
        if (appDrawerOverlay != null || isFinishing() || isDestroyed()) return;

        PackageManager pm = getPackageManager();
        java.util.Map<String, DrawerAppItem> byPkg = new java.util.LinkedHashMap<>();

        Intent lbProbe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List< android.content.pm.ResolveInfo > lbList = pm.queryIntentActivities(lbProbe, 0);
        if (lbList != null) {
            for (android.content.pm.ResolveInfo ri : lbList) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || "com.arvanasoft.aplikasi".equals(pkg)
                        || hiddenApps.contains(pkg)) continue;
                ComponentName comp = new ComponentName(pkg, ri.activityInfo.name);
                String label = Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
                Drawable banner = AppAdapter.loadBanner(this, pm, ri);
                Drawable icon = banner == null ? ri.loadIcon(pm) : null;
                byPkg.put(pkg, new DrawerAppItem(label, pkg, comp, true, banner, icon));
            }
        }

        Intent phoneProbe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List< android.content.pm.ResolveInfo > phoneList = pm.queryIntentActivities(phoneProbe, 0);
        if (phoneList != null) {
            for (android.content.pm.ResolveInfo ri : phoneList) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || "com.arvanasoft.aplikasi".equals(pkg)
                        || hiddenApps.contains(pkg) || byPkg.containsKey(pkg)) continue;
                ComponentName comp = new ComponentName(pkg, ri.activityInfo.name);
                String label = Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
                Drawable banner = AppAdapter.loadBanner(this, pm, ri);
                Drawable icon = banner == null ? ri.loadIcon(pm) : null;
                byPkg.put(pkg, new DrawerAppItem(label, pkg, comp, false, banner, icon));
            }
        }

        List<DrawerAppItem> appList = new ArrayList<>(byPkg.values());
        Collections.sort(appList, (a, b) -> a.label.compareToIgnoreCase(b.label));

        boolean isDark = ThemeManager.isDarkGlow(this);
        boolean isNeu = ThemeManager.isNeumorphism(this);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(isDark ? 0xDD000000 : (isNeu ? 0x99EDEAE4 : 0x78050912));
        overlay.setClickable(true);
        overlay.setFocusable(false);
        overlay.setOnClickListener(v -> closeAppDrawer(true));

        int maxW = Math.min(Math.round(860 * density), root.getWidth() - Math.round(72 * density));
        int maxH = Math.min(Math.round(496 * density), root.getHeight() - Math.round(64 * density));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setClipChildren(true);
        panel.setClipToPadding(true);
        int padH = Math.round(22 * density);
        int padV = Math.round(16 * density);
        panel.setPadding(padH, padV, padH, padV);

        if (isDark) {
            GradientDrawable panelBg = new GradientDrawable();
            panelBg.setColor(0xF20A0A0E); // Deep OLED black
            panelBg.setCornerRadius(26 * density);
            panelBg.setStroke(Math.max(1, Math.round(1.15f * density)), 0x3300F0FF); // Thin cyber cyan outline
            panel.setBackground(panelBg);
        } else if (isNeu) {
            NeumorphicDrawable panelBg = new NeumorphicDrawable(26 * density, density, NeumorphicDrawable.MODE_EXTRUDED, true);
            panel.setBackground(panelBg);
        } else {
            GlassDrawable glassBg = new GlassDrawable(panel, 26 * density, density, null, 0.45f);
            panel.setBackground(glassBg);
            Backdrop.watch(panel);
        }

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(maxW, maxH, Gravity.CENTER);
        overlay.addView(panel, panelLp);

        // Header Row
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText("Semua Aplikasi");
        titleView.setTextSize(18.5f);
        titleView.setTextColor(isNeu ? 0xFF2B3036 : 0xFFFFFFFF);
        titleView.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        titleView.setFontVariationSettings("'wght' 700");
        if (isNeu) titleView.setShadowLayer(0, 0, 0, 0);
        else titleView.setShadowLayer(4 * density, 0, 1 * density, 0xCC000000);
        header.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView subView = new TextView(this);
        subView.setText(appList.size() + " Aplikasi  •  Tahan OK untuk Info / Uninstall");
        subView.setTextSize(12f);
        subView.setTextColor(isDark ? 0xFF00F0FF : (isNeu ? 0xFF5A626E : 0xCCFFFFFF));
        subView.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        subView.setFontVariationSettings("'wght' 600");
        if (isNeu) subView.setShadowLayer(0, 0, 0, 0);
        else subView.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
        header.addView(subView);

        panel.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(isDark ? 0x2E00F0FF : (isNeu ? 0x16000000 : 0x24FFFFFF));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(1 * density)));
        divLp.topMargin = Math.round(10 * density);
        divLp.bottomMargin = Math.round(10 * density);
        panel.addView(divider, divLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);

        LinearLayout gridRows = new LinearLayout(this);
        gridRows.setOrientation(LinearLayout.VERTICAL);
        gridRows.setClipChildren(false);
        gridRows.setClipToPadding(false);
        int gridPad = Math.round(6 * density);
        gridRows.setPadding(gridPad, gridPad, gridPad, gridPad);

        final int COLS = 6;
        final List<View> cells = new ArrayList<>();
        final int total = appList.size();
        int rowCount = (total + COLS - 1) / COLS;

        int cellMargin = Math.round(5 * density);
        int innerW = maxW - (padH * 2) - (gridPad * 2);
        int cardW = Math.max(Math.round(80 * density), (innerW / COLS) - (cellMargin * 2));
        int cardH = Math.round(cardW * 101f / 180f);
        final float cardRadius = 11f * density;

        for (int r = 0; r < rowCount; r++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setWeightSum(COLS);
            rowLayout.setClipChildren(false);
            rowLayout.setClipToPadding(false);

            for (int c = 0; c < COLS; c++) {
                int idx = r * COLS + c;
                if (idx >= total) {
                    View spacer = new View(this);
                    LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, 1, 1f);
                    spLp.setMargins(cellMargin, cellMargin, cellMargin, cellMargin);
                    rowLayout.addView(spacer, spLp);
                    continue;
                }

                final DrawerAppItem item = appList.get(idx);
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setFocusable(true);
                cell.setClickable(true);
                cell.setLongClickable(true);
                cell.setClipChildren(false);
                cell.setClipToPadding(false);

                FrameLayout cardBox = new FrameLayout(this);
                cardBox.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cardRadius);
                    }
                });
                cardBox.setClipToOutline(true);

                if (ThemeManager.isDarkGlow(this)) {
                    GradientDrawable cardBaseBg = new GradientDrawable();
                    cardBaseBg.setColor(0xFF000000);
                    cardBaseBg.setStroke(Math.max(1, Math.round(0.9f * density)), 0x1EFFFFFF);
                    cardBaseBg.setCornerRadius(cardRadius);
                    cardBox.setBackground(cardBaseBg);
                } else if (ThemeManager.isNeumorphism(this)) {
                    NeumorphicDrawable cardBaseBg = new NeumorphicDrawable(cardRadius, density, NeumorphicDrawable.MODE_EXTRUDED, false);
                    cardBox.setBackground(cardBaseBg);
                    cardBox.setPadding(0, 0, 0, 0);
                } else {
                    GradientDrawable cardBaseBg = new GradientDrawable();
                    cardBaseBg.setColor(0x22FFFFFF);
                    cardBaseBg.setStroke(Math.max(1, Math.round(1 * density)), 0x30FFFFFF);
                    cardBaseBg.setCornerRadius(cardRadius);
                    cardBox.setBackground(cardBaseBg);
                }

                if (item.banner != null) {
                    ImageView bannerView = new ImageView(this);
                    bannerView.setImageDrawable(item.banner);
                    bannerView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    cardBox.addView(bannerView, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                } else {
                    ImageView iconView = new ImageView(this);
                    iconView.setImageDrawable(item.icon);
                    iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int iconSize = Math.round(cardH * 0.64f);
                    FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
                    cardBox.addView(iconView, iconLp);
                }

                View focusRim = new View(this);
                if (ThemeManager.isNeumorphism(this)) {
                    final RectF rimBounds = new RectF();
                    focusRim.setBackground(new Drawable() {
                        @Override
                        public void draw(@NonNull Canvas canvas) {
                            rimBounds.set(getBounds());
                            NeumorphicDrawable.drawTileFocusRim(canvas, rimBounds, cardRadius, density, 1f);
                        }
                        @Override public void setAlpha(int alpha) {}
                        @Override public void setColorFilter(ColorFilter colorFilter) {}
                        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
                    });
                } else {
                    GradientDrawable rimDrawable = new GradientDrawable();
                    rimDrawable.setColor(0x00000000);
                    rimDrawable.setCornerRadius(cardRadius);
                    boolean isDarkDrawer = ThemeManager.isDarkGlow(this);
                    int rimColor = isDarkDrawer ? 0xFF00F0FF : 0xFFFFFFFF;
                    // Thin, crisp neon border in Dark Glow (1.25dp)
                    int strokeW = Math.max(1, Math.round((isDarkDrawer ? 1.25f : 1.8f) * density));
                    rimDrawable.setStroke(strokeW, rimColor);
                    focusRim.setBackground(rimDrawable);
                }
                focusRim.setAlpha(0f);
                cardBox.addView(focusRim, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                cell.addView(cardBox, new LinearLayout.LayoutParams(cardW, cardH));

                TextView nameView = new TextView(this);
                nameView.setText(item.label);
                nameView.setTextSize(11.5f);
                nameView.setTextColor(isNeu ? 0xFF3A414A : 0xB3FFFFFF);
                nameView.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
                nameView.setFontVariationSettings("'wght' 650");
                nameView.setSingleLine(true);
                nameView.setEllipsize(TextUtils.TruncateAt.END);
                nameView.setGravity(Gravity.CENTER);
                if (isNeu) nameView.setShadowLayer(0, 0, 0, 0);
                else nameView.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
                LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                        cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
                nameLp.topMargin = Math.round(5 * density);
                cell.addView(nameView, nameLp);

                cell.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) SoundEffects.playNav();
                    focusRim.animate().alpha(hasFocus ? 1f : 0f).setDuration(140).start();
                    nameView.setTextColor(hasFocus ? (isNeu ? 0xFF1F2429 : 0xFFFFFFFF) : (isNeu ? 0xFF3A414A : 0xB3FFFFFF));
                    float s = hasFocus ? 1.08f : 1.0f;
                    cardBox.animate().scaleX(s).scaleY(s).translationZ(hasFocus ? 8 * density : 0f)
                            .setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                });

                cell.setOnClickListener(v -> {
                    SoundEffects.playSelect();
                    closeAppDrawer(false);
                    try {
                        RecentAppsManager.recordAppLaunch(MainActivity.this, item.pkg);
                        Intent launchIntent = new Intent(Intent.ACTION_MAIN)
                                .addCategory(item.isLeanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                                .setComponent(item.component)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        startActivity(launchIntent);
                    } catch (Exception ex) {
                        Intent fallback = pm.getLaunchIntentForPackage(item.pkg);
                        if (fallback != null) {
                            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(fallback);
                        } else {
                            Toast.makeText(MainActivity.this, getString(R.string.cant_open, item.label), Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                cell.setOnLongClickListener(v -> {
                    SoundEffects.playSelect();
                    try {
                        Intent infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + item.pkg));
                        infoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(infoIntent);
                    } catch (Exception ignored) {
                    }
                    return true;
                });

                final int cellIndex = idx;
                cell.setOnKeyListener((v, keyCode, event) -> {
                    boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            if (cellIndex % COLS > 0 && down) {
                                cells.get(cellIndex - 1).requestFocus();
                            }
                            return true;
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            if (cellIndex % COLS < COLS - 1 && cellIndex + 1 < total && down) {
                                cells.get(cellIndex + 1).requestFocus();
                            }
                            return true;
                        case KeyEvent.KEYCODE_DPAD_UP:
                            if (cellIndex >= COLS && down) {
                                cells.get(cellIndex - COLS).requestFocus();
                            }
                            return true;
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            int curRow = cellIndex / COLS;
                            int maxRow = (total - 1) / COLS;
                            if (curRow < maxRow && down) {
                                cells.get(Math.min(cellIndex + COLS, total - 1)).requestFocus();
                            }
                            return true;
                        case KeyEvent.KEYCODE_BACK:
                        case KeyEvent.KEYCODE_ESCAPE:
                            if (down) closeAppDrawer(true);
                            return true;
                        default:
                            return false;
                    }
                });

                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cellLp.setMargins(cellMargin, cellMargin, cellMargin, cellMargin);
                rowLayout.addView(cell, cellLp);
                cells.add(cell);
            }
            gridRows.addView(rowLayout, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        scroll.addView(gridRows, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        appDrawerOverlay = overlay;
        appDrawerPanel = panel;
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        overlay.animate().alpha(1f).setDuration(200).start();
        panel.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .withEndAction(panel::invalidate).start();

        if (!cells.isEmpty()) {
            cells.get(0).requestFocus();
            panel.post(() -> {
                if (appDrawerOverlay == overlay && !cells.isEmpty()) {
                    cells.get(0).requestFocus();
                }
            });
        }
    }

    private void closeAppDrawer(boolean restoreFocus) {
        final View overlay = appDrawerOverlay;
        final View panel = appDrawerPanel;
        if (overlay == null) return;
        appDrawerOverlay = null;
        appDrawerPanel = null;
        if (restoreFocus) {
            SoundEffects.playNav();
            focusTile(lastFocused);
        }
        if (panel != null) {
            panel.animate().scaleX(0.95f).scaleY(0.95f).setDuration(150).start();
        }
        overlay.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            root.removeView(overlay);
        }).start();
    }

    private void showAddAppSheet() {
        if (addAppOverlay != null || isFinishing() || isDestroyed()) return;

        PackageManager pm = getPackageManager();
        java.util.Map<String, DrawerAppItem> byPkg = new java.util.LinkedHashMap<>();

        Intent lbProbe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<android.content.pm.ResolveInfo> lbList = pm.queryIntentActivities(lbProbe, 0);
        if (lbList != null) {
            for (android.content.pm.ResolveInfo ri : lbList) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || "com.arvanasoft.aplikasi".equals(pkg)
                        || hiddenApps.contains(pkg) || adapter.isPackageOnHome(pkg)) continue;
                ComponentName comp = new ComponentName(pkg, ri.activityInfo.name);
                String label = Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
                Drawable banner = AppAdapter.loadBanner(this, pm, ri);
                Drawable icon = banner == null ? ri.loadIcon(pm) : null;
                byPkg.put(pkg, new DrawerAppItem(label, pkg, comp, true, banner, icon));
            }
        }

        Intent phoneProbe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> phoneList = pm.queryIntentActivities(phoneProbe, 0);
        if (phoneList != null) {
            for (android.content.pm.ResolveInfo ri : phoneList) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || "com.arvanasoft.aplikasi".equals(pkg)
                        || hiddenApps.contains(pkg) || byPkg.containsKey(pkg)
                        || adapter.isPackageOnHome(pkg)) continue;
                ComponentName comp = new ComponentName(pkg, ri.activityInfo.name);
                String label = Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
                Drawable banner = AppAdapter.loadBanner(this, pm, ri);
                Drawable icon = banner == null ? ri.loadIcon(pm) : null;
                byPkg.put(pkg, new DrawerAppItem(label, pkg, comp, false, banner, icon));
            }
        }

        final List<DrawerAppItem> appList = new ArrayList<>(byPkg.values());
        Collections.sort(appList, (a, b) -> a.label.compareToIgnoreCase(b.label));

        boolean isDark = ThemeManager.isDarkGlow(this);
        boolean isNeu = ThemeManager.isNeumorphism(this);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(isDark ? 0xCC000000 : (isNeu ? 0x88F0F2F5 : 0x4D050912));
        overlay.setClickable(true);
        overlay.setFocusable(false);
        overlay.setOnClickListener(v -> closeAddAppSheet(true));

        int sheetW = Math.max(Math.round(320 * density),
                Math.min(Math.round(360 * density), Math.round(root.getWidth() * 0.35f)));
        int sheetH = root.getHeight() - Math.round(48 * density);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setClipChildren(true);
        panel.setClipToPadding(true);
        int padH = Math.round(18 * density);
        int padV = Math.round(16 * density);
        panel.setPadding(padH, padV, padH, padV);

        if (isDark) {
            GradientDrawable panelBg = new GradientDrawable();
            panelBg.setColor(0xF20A0A0E); // Deep OLED black
            panelBg.setCornerRadius(24 * density);
            panelBg.setStroke(Math.max(1, Math.round(1.15f * density)), 0x3300F0FF); // Thin cyber cyan outline
            panel.setBackground(panelBg);
        } else if (isNeu) {
            NeumorphicDrawable panelBg = new NeumorphicDrawable(24 * density, density, NeumorphicDrawable.MODE_EXTRUDED, true);
            panel.setBackground(panelBg);
        } else {
            GlassDrawable glassBg = new GlassDrawable(panel, 24 * density, density, null, 0.45f);
            panel.setBackground(glassBg);
            Backdrop.watch(panel);
        }

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                sheetW, sheetH, Gravity.END | Gravity.CENTER_VERTICAL);
        panelLp.rightMargin = Math.round(24 * density);
        overlay.addView(panel, panelLp);

        TextView titleView = new TextView(this);
        titleView.setText("Tambah ke Beranda");
        titleView.setTextSize(17.5f);
        titleView.setTextColor(isNeu ? 0xFF2B3036 : 0xFFFFFFFF);
        titleView.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        titleView.setFontVariationSettings("'wght' 700");
        if (isNeu) titleView.setShadowLayer(0, 0, 0, 0);
        else titleView.setShadowLayer(4 * density, 0, 1 * density, 0xCC000000);
        panel.addView(titleView);

        final TextView subView = new TextView(this);
        subView.setText(appList.isEmpty()
                ? "Semua aplikasi sudah tampil di beranda"
                : (appList.size() + " Aplikasi tersedia  •  Klik OK untuk tambah"));
        subView.setTextSize(11.5f);
        subView.setTextColor(isDark ? 0xFF00F0FF : (isNeu ? 0xFF5A626E : 0xBFFFFFFF));
        subView.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
        subView.setFontVariationSettings("'wght' 550");
        if (isNeu) subView.setShadowLayer(0, 0, 0, 0);
        else subView.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Math.round(2 * density);
        panel.addView(subView, subLp);

        View divider = new View(this);
        divider.setBackgroundColor(isDark ? 0x2E00F0FF : (isNeu ? 0x16000000 : 0x24FFFFFF));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(1 * density)));
        divLp.topMargin = Math.round(10 * density);
        divLp.bottomMargin = Math.round(10 * density);
        panel.addView(divider, divLp);

        final ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);

        final LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setClipChildren(false);
        listContainer.setClipToPadding(false);
        int listPad = Math.round(4 * density);
        listContainer.setPadding(listPad, listPad, listPad, listPad);

        final List<View> rowViews = new ArrayList<>();

        if (appList.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("Tidak ada aplikasi tersembunyi.\nTekan Back untuk kembali.");
            emptyView.setTextSize(13f);
            emptyView.setTextColor(0xB3FFFFFF);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
            emptyView.setPadding(padH, Math.round(64 * density), padH, Math.round(64 * density));
            emptyView.setFocusable(true);
            emptyView.setOnClickListener(v -> closeAddAppSheet(true));
            emptyView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
                        || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                    closeAddAppSheet(true);
                    return true;
                }
                return false;
            });
            listContainer.addView(emptyView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            rowViews.add(emptyView);
        } else {
            final int thumbW = Math.round(64 * density);
            final int thumbH = Math.round(36 * density);
            final float thumbRadius = 7.5f * density;
            final float rowRadius = 13f * density;

            for (int i = 0; i < appList.size(); i++) {
                final DrawerAppItem item = appList.get(i);
                final LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setFocusable(true);
                row.setClickable(true);
                int rPadH = Math.round(10 * density);
                int rPadV = Math.round(8 * density);
                row.setPadding(rPadH, rPadV, rPadH, rPadV);

                final GradientDrawable rowBg = new GradientDrawable();
                rowBg.setCornerRadius(rowRadius);
                if (isDark) {
                    rowBg.setColor(0xFF10121A);
                    rowBg.setStroke(Math.max(1, Math.round(0.9f * density)), 0x1EFFFFFF);
                    row.setBackground(rowBg);
                } else if (isNeu) {
                    row.setBackground(new NeumorphicDrawable(rowRadius, density, NeumorphicDrawable.MODE_EXTRUDED, false));
                } else {
                    rowBg.setColor(0x18FFFFFF);
                    rowBg.setStroke(Math.max(1, Math.round(1.1f * density)), 0x26FFFFFF);
                    row.setBackground(rowBg);
                }

                // Left thumbnail (16:9 TV Banner or square icon inside glass mini-card)
                FrameLayout thumbBox = new FrameLayout(this);
                thumbBox.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), thumbRadius);
                    }
                });
                thumbBox.setClipToOutline(true);
                GradientDrawable thumbBg = new GradientDrawable();
                thumbBg.setCornerRadius(thumbRadius);
                if (isDark) {
                    thumbBg.setColor(0xFF000000);
                    thumbBg.setStroke(Math.max(1, Math.round(0.9f * density)), 0x22FFFFFF);
                    thumbBox.setBackground(thumbBg);
                } else if (isNeu) {
                    thumbBox.setBackground(new NeumorphicDrawable(thumbRadius, density, NeumorphicDrawable.MODE_EXTRUDED, false));
                } else {
                    thumbBg.setColor(0x28FFFFFF);
                    thumbBg.setStroke(Math.max(1, Math.round(1 * density)), 0x33FFFFFF);
                    thumbBox.setBackground(thumbBg);
                }

                if (item.banner != null) {
                    ImageView bImg = new ImageView(this);
                    bImg.setImageDrawable(item.banner);
                    bImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    thumbBox.addView(bImg, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                } else {
                    ImageView iImg = new ImageView(this);
                    iImg.setImageDrawable(item.icon);
                    iImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int iSize = Math.round(thumbH * 0.72f);
                    thumbBox.addView(iImg, new FrameLayout.LayoutParams(iSize, iSize, Gravity.CENTER));
                }
                row.addView(thumbBox, new LinearLayout.LayoutParams(thumbW, thumbH));

                // Middle text column
                LinearLayout textCol = new LinearLayout(this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                textColLp.leftMargin = Math.round(11 * density);
                textColLp.rightMargin = Math.round(8 * density);

                final TextView nameTv = new TextView(this);
                nameTv.setText(item.label);
                nameTv.setTextSize(13.5f);
                nameTv.setTextColor(isNeu ? 0xFF2B3036 : 0xD9FFFFFF);
                nameTv.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
                nameTv.setFontVariationSettings("'wght' 650");
                nameTv.setSingleLine(true);
                nameTv.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(nameTv);

                row.addView(textCol, textColLp);

                // Right '+' Badge
                final TextView plusBadge = new TextView(this);
                plusBadge.setText("+");
                plusBadge.setTextSize(16f);
                plusBadge.setGravity(Gravity.CENTER);
                plusBadge.setTypeface(getResources().getFont(R.font.outfit), Typeface.BOLD);
                final GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setShape(GradientDrawable.OVAL);
                if (isDark) {
                    badgeBg.setColor(0x2B00F0FF);
                    badgeBg.setStroke(Math.max(1, Math.round(1.1f * density)), 0x4D00F0FF);
                    plusBadge.setTextColor(0xFF00F0FF);
                    plusBadge.setBackground(badgeBg);
                } else if (isNeu) {
                    plusBadge.setTextColor(0xFF2B3036);
                    plusBadge.setBackground(new NeumorphicDrawable(14 * density, density, NeumorphicDrawable.MODE_EXTRUDED, false));
                } else {
                    badgeBg.setColor(0x22FFFFFF);
                    badgeBg.setStroke(Math.max(1, Math.round(1.2f * density)), 0x40FFFFFF);
                    plusBadge.setTextColor(0xCCFFFFFF);
                    plusBadge.setBackground(badgeBg);
                }
                int badgeSize = Math.round(28 * density);
                row.addView(plusBadge, new LinearLayout.LayoutParams(badgeSize, badgeSize));

                row.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) SoundEffects.playNav();
                    if (isDark) {
                        rowBg.setColor(hasFocus ? 0xFF000000 : 0xFF10121A);
                        // Thin neon cyan stroke (1.25dp) when focused
                        rowBg.setStroke(Math.max(1, Math.round((hasFocus ? 1.25f : 0.9f) * density)), hasFocus ? 0xFF00F0FF : 0x1EFFFFFF);
                        badgeBg.setColor(hasFocus ? 0xFF00F0FF : 0x2B00F0FF);
                        badgeBg.setStroke(Math.max(1, Math.round(1.2f * density)), hasFocus ? 0xFF00E5FF : 0x4D00F0FF);
                        plusBadge.setTextColor(hasFocus ? 0xFF000000 : 0xFF00F0FF);
                        nameTv.setTextColor(hasFocus ? 0xFFFFFFFF : 0xD9FFFFFF);
                    } else if (isNeu) {
                        row.setBackground(new NeumorphicDrawable(rowRadius, density,
                                hasFocus ? NeumorphicDrawable.MODE_RECESSED : NeumorphicDrawable.MODE_EXTRUDED, false));
                        plusBadge.setBackground(new NeumorphicDrawable(14 * density, density,
                                hasFocus ? NeumorphicDrawable.MODE_RECESSED : NeumorphicDrawable.MODE_EXTRUDED, false));
                        plusBadge.setTextColor(hasFocus ? 0xFF1F2429 : 0xFF2B3036);
                        nameTv.setTextColor(hasFocus ? 0xFF1F2429 : 0xFF2B3036);
                    } else {
                        rowBg.setColor(hasFocus ? 0x3DFFFFFF : 0x18FFFFFF);
                        rowBg.setStroke(Math.max(1, Math.round((hasFocus ? 1.6f : 1.2f) * density)),
                                hasFocus ? 0xF2FFFFFF : 0x26FFFFFF);
                        badgeBg.setColor(hasFocus ? 0x5938BDF8 : 0x22FFFFFF);
                        badgeBg.setStroke(Math.max(1, Math.round(1.4f * density)),
                                hasFocus ? 0xFFFFFFFF : 0x40FFFFFF);
                        plusBadge.setTextColor(hasFocus ? 0xFFFFFFFF : 0xCCFFFFFF);
                        nameTv.setTextColor(hasFocus ? 0xFFFFFFFF : 0xD9FFFFFF);
                    }
                    float s = hasFocus ? 1.02f : 1.0f;
                    row.animate().scaleX(s).scaleY(s).setDuration(140).start();
                });

                row.setOnClickListener(v -> {
                    SoundEffects.playSelect();
                    int idx = rowViews.indexOf(row);
                    adapter.addPackageToHome(item.pkg, item.component.flattenToShortString());
                    reloadTiles();
                    Toast.makeText(MainActivity.this, item.label + " ditambahkan ke beranda", Toast.LENGTH_SHORT).show();

                    rowViews.remove(row);
                    listContainer.removeView(row);

                    if (rowViews.isEmpty()) {
                        closeAddAppSheet(false);
                        // Focus the newly added app (right before the '+' tile)
                        int newPos = Math.max(0, adapter.getItemCount() - 2);
                        focusTile(newPos);
                    } else {
                        subView.setText(rowViews.size() + " Aplikasi tersedia  •  Klik OK untuk tambah");
                        int nextIdx = Math.min(idx, rowViews.size() - 1);
                        rowViews.get(nextIdx).requestFocus();
                    }
                });

                row.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    int idx = rowViews.indexOf(row);
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (idx > 0) rowViews.get(idx - 1).requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (idx + 1 < rowViews.size()) rowViews.get(idx + 1).requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                            || keyCode == KeyEvent.KEYCODE_BACK
                            || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        closeAddAppSheet(true);
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        return true;
                    }
                    return false;
                });

                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = Math.round(7 * density);
                listContainer.addView(row, rowLp);
                rowViews.add(row);
            }
        }

        scroll.addView(listContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        addAppOverlay = overlay;
        addAppPanel = panel;
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay.setAlpha(0f);
        panel.setTranslationX(36 * density);
        overlay.animate().alpha(1f).setDuration(180).start();
        panel.animate().translationX(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .withEndAction(panel::invalidate).start();

        if (!rowViews.isEmpty()) {
            rowViews.get(0).requestFocus();
            panel.post(() -> {
                if (addAppOverlay == overlay && !rowViews.isEmpty()) {
                    rowViews.get(0).requestFocus();
                }
            });
        }
    }

    private void closeAddAppSheet(boolean restoreFocus) {
        final View overlay = addAppOverlay;
        final View panel = addAppPanel;
        if (overlay == null) return;
        addAppOverlay = null;
        addAppPanel = null;
        if (restoreFocus) {
            SoundEffects.playNav();
            focusTile(Math.min(lastFocused, Math.max(0, adapter.getItemCount() - 1)));
        }
        if (panel != null) {
            panel.animate().translationX(36 * density).setDuration(150).start();
        }
        overlay.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            root.removeView(overlay);
        }).start();
    }

    /** The glass menu while one is open (Inputs, or an app's), and what it opened from. */
    private View openMenu;
    private View menuAnchor;
    private final int[] menuAt = new int[2], menuGliderAt = new int[2];

    /**
     * Menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. It is drawn in
     * Home's own window rather than a popup window of its own: a new window takes about a quarter of
     * a second to reach the screen on this TV, so the selection could not glide out of the pill into
     * it. Here Home's own focus shape glides from the pill to the first row, the way it glides into
     * the dock's tray, and the menu's glass paints it over itself.
     */
    private void showInputs() {
        if (openMenu != null) return;
        SoundEffects.playSelect();
        View anchor = btnInputs;
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        Runnable close = () -> closeMenu(false);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                    // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    if (tunerId == null || (hw && !tunerIsHw) || (hw == tunerIsHw && info.getId().compareTo(tunerId) < 0)) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                    }
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, close, hdmi ? R.drawable.ic_hdmi : R.drawable.ic_av, label, state,
                        () -> openInput(id));
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
            addInputRow(rows, close, R.drawable.ic_input, getString(R.string.inputs), -1, this::openSourcePanel);
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null && config.showLiveTv) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, close, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        width = Math.max(width, panel.getMeasuredWidth());

        showMenu(panel, rows, anchor, width, false);
    }

    /**
     * Shows a glass menu in Home's own window, under {@code anchor} or, with {@code above}, over it:
     * Home's focus shape glides from the anchor to the first row, the menu's glass paints it over
     * itself, and the glass and rows fade in around it. Up from the first row (Down from the last when
     * the menu is above), or Back, closes it and the shape glides back to the anchor.
     */
    private void showMenu(FrameLayout panel, LinearLayout rows, View anchor, int width, boolean above) {
        boolean isDark = ThemeManager.isDarkGlow(this);
        boolean isNeu = ThemeManager.isNeumorphism(this);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        if (isDark) {
            final float pRadius = 20 * density;
            final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(Math.max(1f, 1.15f * density));
            final Path clipPath = new Path();
            final RectF rectF = new RectF();
            Drawable darkBg = new Drawable() {
                private int mAlpha = 255;
                @Override
                public void draw(@NonNull Canvas c) {
                    int w = getBounds().width();
                    int h = getBounds().height();
                    if (w <= 0 || h <= 0) return;
                    float a = mAlpha / 255f;
                    rectF.set(0, 0, w, h);
                    fillPaint.setColor(0x08090D | (Math.round(0xF5 * a) << 24));
                    c.drawRoundRect(rectF, pRadius, pRadius, fillPaint);

                    if (glider != null && glider.isShowing()) {
                        int save = c.save();
                        clipPath.reset();
                        clipPath.addRoundRect(rectF, pRadius, pRadius, Path.Direction.CW);
                        c.clipPath(clipPath);
                        glider.drawShape(c, glider.getLeft() - panel.getLeft(), glider.getTop() - panel.getTop(), panel);
                        c.restoreToCount(save);
                    }

                    float inset = strokePaint.getStrokeWidth() * 0.5f;
                    rectF.set(inset, inset, w - inset, h - inset);
                    strokePaint.setColor(0x00F0FF | (Math.round(0x38 * a) << 24));
                    c.drawRoundRect(rectF, Math.max(0f, pRadius - inset), Math.max(0f, pRadius - inset), strokePaint);
                }
                @Override
                public void setAlpha(int alpha) {
                    mAlpha = alpha;
                    invalidateSelf();
                }
                @Override public void setColorFilter(ColorFilter colorFilter) {}
                @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
            };
            panel.setBackground(darkBg);
        } else if (isNeu) {
            NeumorphicDrawable neuBg = new NeumorphicDrawable(
                    panel, 20 * density, density, NeumorphicDrawable.MODE_EXTRUDED, true, glider, true);
            panel.setBackground(neuBg);
        } else {
            GlassDrawable glassBg = new GlassDrawable(panel, 20 * density, density, glider, 0.5f, true);
            panel.setBackground(glassBg);
            Backdrop.watch(panel);
        }
        glider.addCompanion(panel);
        anchor.getLocationInWindow(menuAt);
        root.getLocationInWindow(menuGliderAt);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        int sideMargin = Math.round(56 * density);
        int maxRight = root.getWidth() - sideMargin;
        View settingsBtn = topSettings != null ? topSettings : findViewById(R.id.btnSettings);
        if (settingsBtn != null && settingsBtn.getWidth() > 0) {
            int[] setAt = new int[2];
            settingsBtn.getLocationInWindow(setAt);
            maxRight = setAt[0] - menuGliderAt[0] + settingsBtn.getWidth();
        }
        lp.leftMargin = Math.max(sideMargin, Math.min(menuAt[0] - menuGliderAt[0], maxRight - width));
        int gap = (int) (10 * density);
        if (above) {
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
            lp.topMargin = Math.max(0, menuAt[1] - menuGliderAt[1] - gap - panel.getMeasuredHeight());
        } else {
            lp.topMargin = menuAt[1] - menuGliderAt[1] + anchor.getHeight() + gap;
        }
        root.addView(panel, lp);
        openMenu = panel;
        menuAnchor = anchor;

        // Focus stays strictly inside the menu rows until it closes.
        final List<View> focusableRows = new ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (!row.isFocusable()) continue;
            focusableRows.add(row);
            row.setOnFocusChangeListener((v, has) -> {
                if (has) glideToMenuRow(panel, rows, v);
            });
        }
        final View top = focusableRows.isEmpty() ? null : focusableRows.get(0);
        final View bottom = focusableRows.isEmpty() ? null : focusableRows.get(focusableRows.size() - 1);
        for (int i = 0; i < focusableRows.size(); i++) {
            final int idx = i;
            View row = focusableRows.get(i);
            row.setOnKeyListener((v, keyCode, event) -> {
                boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (idx > 0) {
                            if (down) focusableRows.get(idx - 1).requestFocus();
                            return true;
                        }
                        if (down && !above) closeMenu(true);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (idx < focusableRows.size() - 1) {
                            if (down) focusableRows.get(idx + 1).requestFocus();
                            return true;
                        }
                        if (down && above) closeMenu(true);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        return true;
                    case KeyEvent.KEYCODE_BACK:
                        if (down) closeMenu(true);
                        return true;
                    default:
                        return false;
                }
            });
        }

        // The panel background and the rows fade in around the selection as it arrives.
        final Drawable panelBg = panel.getBackground();
        if (panelBg != null) panelBg.setAlpha(0);
        rows.setAlpha(0f);
        final float slide = (above ? 8 : -8) * density;
        rows.setTranslationY(slide);
        android.animation.ValueAnimator in = android.animation.ValueAnimator.ofFloat(0f, 1f);
        in.setDuration(220);
        in.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        in.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            if (panelBg != null) panelBg.setAlpha(Math.round(255 * f));
            rows.setAlpha(f);
            rows.setTranslationY(slide * (1f - f));
        });
        in.start();
        if (top != null) {
            top.requestFocus();
            panel.post(() -> {
                if (openMenu == panel && top != null) {
                    top.requestFocus();
                }
            });
        }
    }

    /**
     * Glides Home's focus shape onto a row of the Inputs menu. From layout positions, not on-screen
     * ones: the rows slide into place as the menu opens, and the shape must land where the row ends
     * up, not where it was when focus arrived. The first row takes focus before the menu has been
     * laid out, so then it waits for the layout.
     */
    private void glideToMenuRow(View panel, View rows, View row) {
        SoundEffects.playNav();
        if (!panel.isLaidOut() || row.getHeight() == 0) {
            panel.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    panel.removeOnLayoutChangeListener(this);
                    // Still inside the layout pass here, where the panel does not count as laid
                    // out yet: glide once the pass is over.
                    panel.post(() -> {
                        if (row.hasFocus()) glideToMenuRow(panel, rows, row);
                    });
                }
            });
            return;
        }
        float l = panel.getLeft() + rows.getLeft() + row.getLeft() - glider.getLeft();
        float t = panel.getTop() + rows.getTop() + row.getTop() - glider.getTop();
        gliderOnPill = true;
        glider.setStyle(Glider.DARK);
        glider.moveTo(l, t, l + row.getWidth(), t + row.getHeight(), 13 * density);
    }

    /** Closes the open menu; with {@code toAnchor}, focus and the selection go back to what it opened from. */
    private void closeMenu(boolean toAnchor) {
        View panel = openMenu;
        if (panel == null) return;
        SoundEffects.playNav();
        openMenu = null;
        if (toAnchor && menuAnchor != null) menuAnchor.requestFocus();
        menuAnchor = null;
        panel.animate().alpha(0f).setDuration(reducedMotion ? 80 : 150).withEndAction(() -> {
            glider.removeCompanion(panel);
            root.removeView(panel);
        }).start();
    }

    private boolean inMenu(View v) {
        for (View p = v; p != null && openMenu != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == openMenu) return true;
        }
        return false;
    }

    private PopupWindow glassPopup(View panel, int width) {
        boolean isDark = ThemeManager.isDarkGlow(this);
        boolean isNeu = ThemeManager.isNeumorphism(this);
        if (isDark) {
            GradientDrawable darkBg = new GradientDrawable();
            darkBg.setColor(0xF508090D); // Deep OLED black
            darkBg.setCornerRadius(20 * density);
            darkBg.setStroke(Math.max(1, Math.round(1.15f * density)), 0x3800F0FF); // Thin cyber cyan outline
            panel.setBackground(darkBg);
        } else if (isNeu) {
            NeumorphicDrawable neuBg = new NeumorphicDrawable(20 * density, density, NeumorphicDrawable.MODE_EXTRUDED, true);
            panel.setBackground(neuBg);
        } else {
            panel.setBackground(new GlassDrawable(panel, 20 * density, density, null, 0.5f));
            Backdrop.watch(panel);
        }
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (openPopup == popup) openPopup = null;
        });
        openPopup = popup;
        return popup;
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor pill. */
    private void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        boolean isNeu = ThemeManager.isNeumorphism(this);
        line.setBackgroundColor(isNeu ? 0x16000000 : 0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, Runnable close, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        boolean isNeu = ThemeManager.isNeumorphism(this);
        ImageView iconView = row.findViewById(R.id.icon);
        iconView.setImageResource(iconRes);
        TextView titleView = row.findViewById(R.id.title);
        titleView.setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);

        if (isNeu) {
            iconView.setColorFilter(0xFF2B3036);
            titleView.setTextColor(0xFF2B3036);
            titleView.setShadowLayer(0, 0, 0, 0);
            if (status != null) {
                status.setTextColor(0xFF5A626E);
                status.setShadowLayer(0, 0, 0, 0);
            }
        }
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            close.run();
            action.run();
        });
        rows.addView(row);
    }

    // ---- module cards ----------------------------------------------------------------------------

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    private void showCardPrompt() {
        if (!moduleCards || !tilesReady || promptsPaused || cardPrompt != null || !hasWindowFocus() || isFinishing()) return;
        if (cards.isEmpty() || !CardFeature.working()) return;
        CardFeature.run(() -> {
            showCardPromptNow();
            return null;
        }, null);
    }

    private void showCardPromptNow() {
        final Cards.Card card = Cards.firstPending(cards);
        if (card == null) return;

        View panel = getLayoutInflater().inflate(R.layout.dialog_card_prompt, root, false);
        ((TextView) panel.findViewById(R.id.title)).setText(getString(R.string.card_prompt_title, card.appLabel));
        ((TextView) panel.findViewById(R.id.body)).setText(card.state == Cards.State.RESIGNED
                ? getString(R.string.card_prompt_resigned, card.appLabel)
                : card.state == Cards.State.RELABELLED
                ? getString(R.string.card_prompt_relabelled, card.appLabel)
                : getString(R.string.card_prompt_body, card.appLabel));
        TextView names = panel.findViewById(R.id.names);
        if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) names.setVisibility(View.GONE);
        else names.setText(getString(R.string.card_prompt_names, card.label, card.appLabel));
        ((TextView) panel.findViewById(R.id.identity)).setText(
                getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest)));
        TextView keep = panel.findViewById(R.id.btnKeep);
        TextView show = panel.findViewById(R.id.btnShow);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (440 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        final boolean[] decided = {false};
        View.OnClickListener choose = v -> {
            if (decided[0]) return;
            decided[0] = true;
            cardPrompt = null;
            dialog.dismiss();
            decideCard(card, v.getId() == R.id.btnShow);   // guards its own card calls; the tile reload stays outside
            restoreFocus();   // the tiles changed under the prompt; this also chains the next prompt
        };
        keep.setOnClickListener(choose);
        show.setOnClickListener(choose);
        dialog.setOnCancelListener(d -> choose.onClick(keep));
        dialog.setOnDismissListener(d -> {
            if (cardPrompt == dialog) cardPrompt = null;
        });
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == R.id.btnKeep;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnShow;
        };
        keep.setOnKeyListener(keys);
        show.setOnKeyListener(keys);
        cardPrompt = dialog;
        dialog.show();
        keep.requestFocus();
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private void decideCard(Cards.Card card, boolean show) {
        boolean saved = CardFeature.run(() -> Cards.decide(Cards.storeFile(this), card, show), false);
        if (!saved) {
            Toast.makeText(this, R.string.card_not_saved, Toast.LENGTH_SHORT).show();
            promptsPaused = true;
        }
        reloadTiles();
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    private void showModuleCards() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(this, R.string.no_module_cards, Toast.LENGTH_SHORT).show();
            return;
        }
        CardFeature.run(() -> {
            showModuleCardsNow();
            return null;
        }, null);
    }

    private void showModuleCardsNow() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_cards, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = glassPopup(panel, (int) (400 * density));
        List<Cards.Card> listed = new ArrayList<>(cards);
        Collections.sort(listed, Cards::byLabel);
        for (Cards.Card card : listed) {
            View row = getLayoutInflater().inflate(R.layout.item_card_row, rows, false);
            row.setTag(card.pkg);
            ((TextView) row.findViewById(R.id.title)).setText(card.label);
            TextView app = row.findViewById(R.id.app);
            if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) app.setVisibility(View.GONE);
            else app.setText(getString(R.string.card_app_name, card.appLabel));
            ((TextView) row.findViewById(R.id.pkg)).setText(card.pkg);
            ((TextView) row.findViewById(R.id.digest)).setText(Cards.longDigest(card.digest));
            final String pkg = card.pkg;
            row.setOnClickListener(v -> CardFeature.run(() -> {
                // The list stays open; the decision is for the card as it is now, not as it was listed.
                Cards.Card now = cardFor(pkg);
                if (now == null) return null;
                decideCard(now, now.state != Cards.State.SHOWN);
                // Every row, not only this one: a toggle can move another card across the limit of 8.
                updateCardStates(rows);
                return null;
            }, null));
            rows.addView(row);
        }
        updateCardStates(rows);
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (34 * density);
        popup.setHeight(Math.min(panel.getMeasuredHeight(), Math.max(room, (int) (120 * density))));
        dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private Cards.Card cardFor(String pkg) {
        for (Cards.Card c : cards) if (c.pkg.equals(pkg)) return c;
        return null;
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private void updateCardStates(LinearLayout rows) {
        List<Cards.Card> drawn = Cards.shown(cards);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            Cards.Card card = row.getTag() instanceof String ? cardFor((String) row.getTag()) : null;
            ((TextView) row.findViewById(R.id.state)).setText(card != null ? stateText(card, drawn.contains(card)) : "");
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private String stateText(Cards.Card card, boolean drawn) {
        switch (card.state) {
            case SHOWN:
                return getString(drawn ? R.string.card_state_shown : R.string.card_state_over_limit);
            case HIDDEN:
                return getString(R.string.card_state_hidden);
            case RESIGNED:
                return getString(R.string.card_state_new_key);
            case RELABELLED:
                return getString(R.string.card_state_new_label);
            default:
                return getString(R.string.card_state_new);
        }
    }
}
