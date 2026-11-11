package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order),
 * then the module cards the owner chose to show (by label), the rest is alphabetical. Once anyone has
 * moved a tile (hold OK, then Move), the order they made is kept instead, and apps installed later
 * join at the end.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);

        /** OK was held on a tile. Return false to open the app's info page, as before. */
        boolean onTileHeld(int position, View card);

        /** Opens the built-in 6x6 Glass App Drawer. */
        void onAppDrawerClicked();

        /** Opens the right-side Glass Add-App sheet. */
        void onAddAppClicked();
    }

    static final ComponentName APP_DRAWER_COMPONENT = new ComponentName("com.gathin.tvhome", "com.gathin.tvhome.AppDrawer");
    static final ComponentName ADD_APP_COMPONENT = new ComponentName("com.gathin.tvhome", "com.gathin.tvhome.AddApp");

    static final class Entry {
        static final int APP = 0, HOME_SETTINGS = 1, CARD = 2, APP_DRAWER = 3, ADD_APP = 4;

        final int kind;
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        /** Text under the tile when it is focused in the grid: the label, plus the caption for a card. */
        final String name;
        /** Ambient glow colour; 0 until sampled from the banner, or the card's accent from the start. */
        int ambient;
        final boolean hasNativeBanner;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this(APP, label, component, banner, icon, null, 0, true);
        }

        Entry(String label, ComponentName component, Drawable banner, Drawable icon, boolean hasNativeBanner) {
            this(APP, label, component, banner, icon, null, 0, hasNativeBanner);
        }

        Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent) {
            this(kind, label, component, banner, icon, caption, accent, true);
        }

        Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent, boolean hasNativeBanner) {
            this.kind = kind;
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.name = caption == null ? label : label + " \u00B7 " + caption;
            this.ambient = accent;
            this.hasNativeBanner = hasNativeBanner;
        }
    }

    static final float FOCUS_SCALE = 1.07f;
    /** Shared by every tile's zoom, so a focus move makes no new objects. */
    private static final DecelerateInterpolator ZOOM_REDUCED = new DecelerateInterpolator();
    private static final OvershootInterpolator ZOOM_IN = new OvershootInterpolator(1.6f);
    private static final OvershootInterpolator ZOOM_OUT = new OvershootInterpolator(0f);
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
     * highlight, capped at the design size so 4 columns on a 1080p TV look exactly as before.
     */
    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 18 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return itemHeightPx(tileHeightPx, density, 5);
    }

    static int itemHeightPx(int tileHeightPx, float density, int columns) {
        int labelAndMargin = Math.round(29 * density);
        int vPad = (columns >= 7) ? Math.round(4 * density) : (columns >= 6 ? Math.round(6 * density) : Math.round(8 * density));
        return tileHeightPx + labelAndMargin + (vPad * 2);
    }

    private final Context ctx;
    private final TileListener listener;
    private int dockSize;
    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final boolean showHomeSettings;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): no overshoot, shorter fades. */
    private final boolean reducedMotion;
    private final List<Entry> items = new ArrayList<>();
    /** The order someone arranged the tiles in, by key (see {@link #keyAt}); empty until they do. */
    private final List<String> arranged = new ArrayList<>();
    private final Set<String> removedApps = new HashSet<>();
    private final Set<String> addedApps = new HashSet<>();
    private static final String ORDER_PREFS = "tiles", ORDER_KEY = "order",
            REMOVED_KEY = "removed_apps", ADDED_KEY = "added_apps";
    /** Rebind payload: only whether the tile's name shows, which depends on being in the dock. */
    private static final Object DOCK_CHANGED = new Object();

    AppAdapter(Context ctx, TileListener listener, int dockSize, Config cfg) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.favourites = cfg.dockApps;
        this.hidden = new HashSet<>(cfg.hiddenApps);
        this.showHomeSettings = cfg.showHomeSettingsShortcut;
        this.reducedMotion = cfg.reducedMotion;
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE);
        Set<String> savedRemoved = prefs.getStringSet(REMOVED_KEY, null);
        if (savedRemoved != null) removedApps.addAll(savedRemoved);
        Set<String> savedAdded = prefs.getStringSet(ADDED_KEY, null);
        if (savedAdded != null) addedApps.addAll(savedAdded);
        String saved = prefs.getString(ORDER_KEY, "");
        if (!saved.isEmpty()) {
            String drawerKey = APP_DRAWER_COMPONENT.flattenToShortString();
            String addKey = ADD_APP_COMPONENT.flattenToShortString();
            for (String s : saved.split("\n")) {
                if (s.equals(addKey)) continue;
                if (s.startsWith("com.arvanasoft.aplikasi/")) {
                    if (!arranged.contains(drawerKey)) arranged.add(drawerKey);
                } else {
                    arranged.add(s);
                }
            }
        }
    }

    /**
     * Rebuilds the tiles. A shown module card stands in for every tile of its package (same component,
     * so focus follows it); hidden_apps wins over a card, and a card's package in dock_apps takes that slot.
     */
    /** What the tiles were last built from; the same list next time means there is nothing to redo. */
    private String builtFrom;
    /**
     * The app tiles of the last build, by component and install path: a rebuild (one app updated,
     * installed or removed) loads only what changed, and the rest keep their artwork and sampled glow.
     */
    private Map<String, Entry> builtApps = new HashMap<>();

    /**
     * Rebuilds the tiles, unless the launchable apps, their installed versions and the cards are
     * exactly what the tiles were last built from. Loading every banner, icon and label is most of
     * what coming back to Home costs, it runs before Home can draw, and almost every return finds
     * nothing changed.
     *
     * @return whether the tiles were rebuilt
     */
    boolean reload(List<Cards.Card> cards) {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(probe, 0));
        // A phone-only app named in dock_apps (e.g. the phone Play Store some boxes ship) has no leanback
        // entry; take its ordinary launcher activity instead. Apps not named stay hidden.
        Set<String> listed = new HashSet<>();
        for (ResolveInfo ri : found) if (ri.activityInfo != null) listed.add(ri.activityInfo.packageName);
        Set<String> extraPackages = new HashSet<>(favourites);
        extraPackages.addAll(addedApps);
        for (String pkg : extraPackages) {
            if (listed.contains(pkg)) continue;
            Intent phone = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
            List<ResolveInfo> more = pm.queryIntentActivities(phone, 0);
            if (!more.isEmpty() && more.get(0).activityInfo != null) {
                found.add(more.get(0));
                listed.add(pkg);
            }
        }
        // The install path moves when an app is updated, so it stands in for the version: an update
        // that brought a new banner still gets a rebuild.
        List<String> parts = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            parts.add(ri.activityInfo.packageName + '/' + ri.activityInfo.name + '@'
                    + ri.activityInfo.applicationInfo.sourceDir);
        }
        for (Cards.Card c : cards) {
            parts.add('#' + c.pkg + '/' + c.component.flattenToShortString() + '|' + c.label + '|'
                    + c.caption + '|' + c.accent);
        }
        for (String rPkg : removedApps) parts.add("-" + rPkg);
        for (String aPkg : addedApps) parts.add("+" + aPkg);
        Collections.sort(parts);
        String signature = android.text.TextUtils.join(";", parts);
        if (signature.equals(builtFrom) && !items.isEmpty()) return false;
        builtFrom = signature;
        Map<String, Cards.Card> cardByPkg = new HashMap<>();
        for (Cards.Card c : cards) cardByPkg.put(c.pkg, c);
        List<Entry> fresh = new ArrayList<>();
        Map<String, Entry> apps = new HashMap<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg) || removedApps.contains(pkg)) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Cards.Card card = cardByPkg.get(pkg);
            if (card != null && !card.component.equals(component)) continue;   // the card stands in for this tile
            String key = component.flattenToShortString() + '@' + ri.activityInfo.applicationInfo.sourceDir;
            Entry built = card == null ? builtApps.get(key) : null;
            if (built != null) {
                if (!built.hasNativeBanner && !addedApps.contains(pkg)) continue;
                fresh.add(built);
                apps.put(key, built);
                continue;
            }
            Drawable nativeBanner = loadNativeBanner(ctx, pm, ri);
            boolean hasNative = nativeBanner != null;
            // Default home rule: only show apps that have a TV banner, unless manually added via '+'
            if (!hasNative && !addedApps.contains(pkg)) continue;
            Drawable banner = getOrGenerateBanner(ctx, pm, ri);
            // The icon only shows on a tile without a banner.
            Drawable icon = banner == null ? ri.loadIcon(pm) : null;
            if (card != null) {
                fresh.add(new Entry(Entry.CARD, card.label, component, banner, icon, card.caption, card.accent, hasNative));
            } else {
                // Same cleaner as the card prompt: an app label with a bidi override or line separator must not
                // redraw the tile name as something else (#117).
                Entry entry = new Entry(Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg), component, banner, icon, hasNative);
                fresh.add(entry);
                apps.put(key, entry);
            }
        }
        builtApps = apps;
        fresh.add(new Entry(Entry.APP_DRAWER, "Aplikasi", APP_DRAWER_COMPONENT,
                createGlassAppDrawerBanner(ctx), null, null, 0xFF38BDF8));
        Map<String, Integer> place = new HashMap<>();
        for (int i = 0; i < arranged.size(); i++) place.put(arranged.get(i), i);
        Collections.sort(fresh, (a, b) -> {
            // The order someone arranged comes first; apps it has not seen yet go after it.
            Integer pa = place.get(a.component.flattenToShortString()), pb = place.get(b.component.flattenToShortString());
            if (pa != null || pb != null) {
                if (pa == null) return 1;
                if (pb == null) return -1;
                return Integer.compare(pa, pb);
            }
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            // Cards come right after the configured dock apps, before the alphabetical grid.
            boolean ca = a.kind == Entry.CARD, cb = b.kind == Entry.CARD;
            if (ca != cb) return ca ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        // Optional "Default home" tile (config.xml), only when this TV actually has a home-screen picker.
        if (showHomeSettings
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(new Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings), null, 0));
        }
        // Always keep the "+" (Tambah Aplikasi) Glass card at the very end of the home screen
        fresh.add(new Entry(Entry.ADD_APP, "Tambah Aplikasi", ADD_APP_COMPONENT,
                createGlassAddAppBanner(ctx), null, null, 0xFF38BDF8));
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
        return true;
    }

    private static final Map<String, Drawable> sSyntheticBannerCache = new ConcurrentHashMap<>();

    static Drawable loadNativeBanner(Context ctx, PackageManager pm, ResolveInfo ri) {
        if (ri == null || ri.activityInfo == null) return null;
        int id = ri.activityInfo.banner;
        if (id == 0) id = ri.activityInfo.applicationInfo.banner;
        if (id != 0) {
            try {
                Resources resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                // Keep at least xhdpi detail rather than downsampling artwork before the focus zoom.
                int density = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.getResources().getDisplayMetrics().densityDpi);
                return resources.getDrawableForDensity(id, density, null);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            }
        }
        Drawable d = ri.activityInfo.loadBanner(pm);
        if (d == null) d = ri.activityInfo.applicationInfo.loadBanner(pm);
        return d;
    }

    private static Drawable glassAddAppBanner;
    private static Drawable glassAppDrawerBanner;

    public static void clearBannerCache() {
        sSyntheticBannerCache.clear();
        glassAddAppBanner = null;
        glassAppDrawerBanner = null;
    }

    public void invalidateTheme() {
        builtFrom = null;
        builtApps.clear();
        clearBannerCache();
        notifyDataSetChanged();
    }

    public static Drawable getOrGenerateBanner(Context ctx, PackageManager pm, ResolveInfo ri) {
        Drawable nativeBanner = loadNativeBanner(ctx, pm, ri);
        // If app has native TV banner (like YouTube, Netflix, Disney+, Spotify), KEEP IT!
        if (nativeBanner != null) return nativeBanner;

        // If app does NOT have a native TV banner (e.g. Termux, Chrome, phone apps):
        String pkg = (ri != null && ri.activityInfo != null) ? ri.activityInfo.packageName : null;
        Drawable icon = ri != null ? ri.loadIcon(pm) : null;

        if (ThemeManager.isDarkGlow(ctx)) {
            // Dark Glow: Deep OLED black for sideloaded non-TV apps
            return generateSolidBanner(ctx, icon, pkg, 0xFF0A0A0E);
        } else if (ThemeManager.isNeumorphism(ctx)) {
            // Neumorphism Light: Warm porcelain clay for sideloaded non-TV apps (matches reference)
            return generateSolidBanner(ctx, icon, pkg, ThemeManager.neu(0xFFEDEAE4));
        } else {
            // Frosted Glass: Dominant solid color
            return generateSolidBanner(ctx, icon, pkg);
        }
    }

    public static Drawable loadBanner(Context ctx, PackageManager pm, ResolveInfo ri) {
        return getOrGenerateBanner(ctx, pm, ri);
    }

    /**
     * Generates a 16:9 solid dominant color banner with the original icon centered.
     * Option A: Solid dominant background color extracted dynamically from the icon (no gradients).
     */
    public static Drawable generateSolidBanner(Context ctx, Drawable icon, String pkgKey) {
        return generateSolidBanner(ctx, icon, pkgKey, -1);
    }

    public static Drawable generateSolidBanner(Context ctx, Drawable icon, String pkgKey, int overrideBgColor) {
        if (icon == null) return null;
        String theme = ThemeManager.getActiveTheme(ctx);
        String fullKey = pkgKey != null ? (theme + "_" + pkgKey) : null;
        if (fullKey != null) {
            Drawable cached = sSyntheticBannerCache.get(fullKey);
            if (cached != null) return cached;
        }

        try {
            int bannerW = 320;
            int bannerH = 180;
            Bitmap bannerBmp = Bitmap.createBitmap(bannerW, bannerH, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bannerBmp);

            int dominantColor = overrideBgColor != -1 ? overrideBgColor : extractDominantColor(icon);

            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(dominantColor);
            canvas.drawRect(0, 0, bannerW, bannerH, bgPaint);

            // Center the original icon at ~52% of banner height
            int iconSize = 94;
            int left = (bannerW - iconSize) / 2;
            int top = (bannerH - iconSize) / 2;

            Drawable iconCopy = icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
            iconCopy.setBounds(left, top, left + iconSize, top + iconSize);
            iconCopy.draw(canvas);

            BitmapDrawable result = new BitmapDrawable(ctx.getResources(), bannerBmp);
            if (fullKey != null) {
                sSyntheticBannerCache.put(fullKey, result);
            }
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Extracts the dominant solid color from an app icon.
     */
    public static int extractDominantColor(Drawable icon) {
        if (icon == null) return 0xFF18181B;
        try {
            int sampleSize = 36;
            Bitmap sample = Bitmap.createBitmap(sampleSize, sampleSize, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(sample);
            Drawable copy = icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
            copy.setBounds(0, 0, sampleSize, sampleSize);
            copy.draw(c);

            Map<Integer, Integer> freq = new HashMap<>();
            Map<Integer, long[]> rgbSum = new HashMap<>();
            int bestBucket = -1;
            int maxCount = 0;

            float[] hsv = new float[3];
            for (int y = 0; y < sampleSize; y++) {
                for (int x = 0; x < sampleSize; x++) {
                    int pixel = sample.getPixel(x, y);
                    int a = Color.alpha(pixel);
                    if (a < 100) continue; // Skip transparent or translucent edges

                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int b = Color.blue(pixel);

                    // 4-bit per channel quantization to cluster subtle gradients into the same bucket
                    int bucket = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                    int count = (freq.containsKey(bucket) ? freq.get(bucket) : 0) + 1;
                    freq.put(bucket, count);

                    long[] sum = rgbSum.get(bucket);
                    if (sum == null) {
                        sum = new long[3];
                        rgbSum.put(bucket, sum);
                    }
                    sum[0] += r;
                    sum[1] += g;
                    sum[2] += b;

                    Color.colorToHSV(pixel, hsv);
                    boolean isWhite = (hsv[1] < 0.12f && hsv[2] > 0.88f);
                    if (!isWhite && count > maxCount) {
                        maxCount = count;
                        bestBucket = bucket;
                    }
                }
            }
            sample.recycle();

            // If icon is completely white/monochrome glyph on transparent, fallback to elegant dark slate
            if (bestBucket == -1 || !rgbSum.containsKey(bestBucket)) {
                return 0xFF1E293B;
            }

            long[] sum = rgbSum.get(bestBucket);
            int total = freq.get(bestBucket);
            int avgR = (int) (sum[0] / total);
            int avgG = (int) (sum[1] / total);
            int avgB = (int) (sum[2] / total);

            Color.colorToHSV(Color.rgb(avgR, avgG, avgB), hsv);

            // Minimum luminance for TV OLED: ensures dark icons (like Termux) stay visible as sleek charcoal slate
            if (hsv[2] < 0.12f) {
                hsv[2] = 0.14f;
            }

            return Color.HSVToColor(hsv);
        } catch (Throwable t) {
            return 0xFF18181B;
        }
    }

    public void setDockSize(int dockSize) {
        this.dockSize = dockSize;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, dockSize, density), h = tileHeightPx(w);

        int vPad = (dockSize >= 7) ? Math.round(4 * density) : (dockSize >= 6 ? Math.round(6 * density) : Math.round(8 * density));
        v.setPadding(v.getPaddingLeft(), vPad, v.getPaddingRight(), vPad);

        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round(7 * density);
        name.setLayoutParams(nameLp);
        return new Holder(v, listener, reducedMotion);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.name);
        h.name.setVisibility(position < dockSize ? View.INVISIBLE : View.VISIBLE);   // the dock has no labels
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        h.name.setAlpha(focused ? 1f : 0f);
        h.syncFocusRim(focused, true);
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        float density = ctx.getResources().getDisplayMetrics().density;
        int wPx = h.card.getLayoutParams() != null && h.card.getLayoutParams().width > 0
                ? h.card.getLayoutParams().width : Math.round(DESIGN_W_DP * density);
        float radius = cornerRadius(wPx, density);

        boolean isDark = ThemeManager.isDarkGlow(ctx);
        boolean isNeu = ThemeManager.isNeumorphism(ctx);

        if (isNeu) {
            h.name.setTextColor(ThemeManager.neu(0xFF2B3036));
            h.name.setShadowLayer(0, 0, 0, 0);
        } else {
            h.name.setTextColor(0xF2FFFFFF);
            h.name.setShadowLayer(4f, 0, 1f, 0x99000000);
        }

        if (isDark) {
            // Dark Glow: pitch black OLED card background
            android.graphics.drawable.GradientDrawable darkBg = new android.graphics.drawable.GradientDrawable();
            darkBg.setColor(0xFF000000);
            darkBg.setCornerRadius(radius);
            darkBg.setStroke(Math.max(1, Math.round(1.0f * density)), 0x26FFFFFF);
            h.card.setBackground(darkBg);
            h.card.setPadding(0, 0, 0, 0);
        } else if (isNeu) {
            // Neumorphism Light: Warm extruded porcelain clay card (full-bleed, no padding)
            h.card.setBackground(new NeumorphicDrawable(radius, density, NeumorphicDrawable.MODE_EXTRUDED, false));
            h.card.setPadding(0, 0, 0, 0);
        } else if (e.kind == Entry.ADD_APP) {
            android.graphics.drawable.GradientDrawable glassTileBg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    new int[]{0x26FFFFFF, 0x12FFFFFF});
            glassTileBg.setCornerRadius(radius);
            glassTileBg.setStroke(Math.max(1, Math.round(1.2f * density)), 0x40FFFFFF);
            h.card.setBackground(glassTileBg);
            h.card.setPadding(0, 0, 0, 0);
        } else {
            h.card.setBackgroundResource(R.drawable.tile_bg);
            h.card.setPadding(0, 0, 0, 0);
        }
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            if (isNeu) {
                h.label.setTextColor(ThemeManager.neu(0xFF1E242B));
                h.label.setShadowLayer(0, 0, 0, 0);
            } else {
                h.label.setTextColor(0xFFFFFFFF);
                h.label.setShadowLayer(4f, 0, 1f, 0x99000000);
            }
            h.label.setVisibility(View.VISIBLE);
        }
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION && listener.onTileHeld(pos, v)) return true;
            openAppInfo(e);
            return true;
        });
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (payloads.size() == 1 && payloads.get(0) == DOCK_CHANGED) {
            h.name.setVisibility(position < dockSize ? View.INVISIBLE : View.VISIBLE);
            return;
        }
        onBindViewHolder(h, position);
    }

    /** Android's page for the app at {@code position}: storage, permissions, uninstall. */
    void openAppInfo(int position) {
        if (position >= 0 && position < items.size()) openAppInfo(items.get(position));
    }

    private void openAppInfo(Entry e) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    /** Whether the tile at {@code position} can be moved: every app can, the Default home and Add App tiles cannot. */
    boolean canMove(int position) {
        if (position < 0 || position >= items.size()) return false;
        int kind = items.get(position).kind;
        return kind != Entry.HOME_SETTINGS && kind != Entry.ADD_APP;
    }

    /** The last position a moved tile can take: before the Default home / Add App tiles, which stay last. */
    int lastMovable() {
        int last = items.size() - 1;
        while (last >= 0 && (items.get(last).kind == Entry.HOME_SETTINGS || items.get(last).kind == Entry.ADD_APP)) {
            last--;
        }
        return last;
    }

    /**
     * Moves the tile at {@code from} to {@code to}, the tiles between shifting to make room, and keeps
     * the new order. Returns false when it cannot go there.
     */
    boolean move(int from, int to) {
        if (from == to || !canMove(from) || to < 0 || to > lastMovable()) return false;
        Entry e = items.remove(from);
        items.add(to, e);
        notifyItemMoved(from, to);
        // A tile crossing into or out of the dock shows or hides its name.
        int lo = Math.min(from, to), hi = Math.max(from, to);
        if (lo < dockSize && hi >= dockSize) notifyItemRangeChanged(lo, hi - lo + 1, DOCK_CHANGED);
        arranged.clear();
        for (Entry x : items) {
            if (x.kind != Entry.HOME_SETTINGS && x.kind != Entry.ADD_APP) {
                arranged.add(x.component.flattenToShortString());
            }
        }
        ctx.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE).edit()
                .putString(ORDER_KEY, android.text.TextUtils.join("\n", arranged)).apply();
        return true;
    }

    boolean isAddApp(int position) {
        return position >= 0 && position < items.size() && items.get(position).kind == Entry.ADD_APP;
    }

    boolean canRemove(int position) {
        if (position < 0 || position >= items.size()) return false;
        int kind = items.get(position).kind;
        return kind == Entry.APP || kind == Entry.CARD;
    }

    boolean isPackageOnHome(String pkg) {
        for (Entry e : items) {
            if ((e.kind == Entry.APP || e.kind == Entry.CARD) && e.pkg.equals(pkg)) return true;
        }
        return false;
    }

    String removeAppAt(int position) {
        if (!canRemove(position)) return null;
        Entry e = items.get(position);
        String pkg = e.pkg;
        String compKey = e.component.flattenToShortString();
        removedApps.add(pkg);
        addedApps.remove(pkg);
        arranged.remove(compKey);
        ctx.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(REMOVED_KEY, new HashSet<>(removedApps))
                .putStringSet(ADDED_KEY, new HashSet<>(addedApps))
                .putString(ORDER_KEY, android.text.TextUtils.join("\n", arranged))
                .apply();
        builtFrom = null;
        return e.label;
    }

    void addPackageToHome(String pkg, String compKey) {
        if (pkg == null) return;
        removedApps.remove(pkg);
        addedApps.add(pkg);
        if (!arranged.isEmpty() && compKey != null && !arranged.contains(compKey)) {
            arranged.add(compKey);
        }
        ctx.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(REMOVED_KEY, new HashSet<>(removedApps))
                .putStringSet(ADDED_KEY, new HashSet<>(addedApps))
                .putString(ORDER_KEY, android.text.TextUtils.join("\n", arranged))
                .apply();
        builtFrom = null;
    }

    private Drawable createGlassAddAppBanner(Context context) {
        if (glassAddAppBanner != null) return glassAddAppBanner;
        int w = 360, h = 202;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        boolean isDark = ThemeManager.isDarkGlow(context);
        boolean isNeu = ThemeManager.isNeumorphism(context);

        float box = 76f;
        float l = (w - box) / 2f;
        float t = (h - box) / 2f;
        float radius = 22f;
        RectF rect = new RectF(l, t, l + box, t + box);
        RectF shadowRect = new RectF(l + 1.5f, t + 3.5f, l + box + 1.5f, t + box + 3.5f);

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float arm = 17f;

        if (isDark) {
            // Dark Glow: Deep OLED black canvas with Neon Cyan squircle and glowing '+'
            c.drawColor(0xFF0A0A0E);

            // Subtle electric cyan glow bloom behind squircle
            Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint.setShader(new RadialGradient(cx, cy, box * 0.85f,
                    new int[]{0x3300F0FF, 0x0A00E5FF, 0x00000000},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, glowPaint);

            // Pitch black squircle
            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setColor(0xFF000000);
            c.drawRoundRect(rect, radius, radius, fillPaint);

            // Sleek thin Electric Cyan Neon Rim
            Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setStrokeWidth(1.3f);
            rimPaint.setColor(0xFF00F0FF);
            rimPaint.setShadowLayer(3.5f, 0, 0, 0xFF00E5FF);
            c.drawRoundRect(rect, radius, radius, rimPaint);

            // Electric Cyan '+' Sign
            Paint plusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            plusPaint.setStyle(Paint.Style.STROKE);
            plusPaint.setStrokeWidth(5.2f);
            plusPaint.setStrokeCap(Paint.Cap.ROUND);
            plusPaint.setColor(0xFF00F0FF);
            plusPaint.setShadowLayer(5f, 0, 0, 0xFF00F0FF);
            c.drawLine(cx - arm, cy, cx + arm, cy, plusPaint);
            c.drawLine(cx, cy - arm, cx, cy + arm, plusPaint);
        } else if (isNeu) {
            // Neumorphism Light: Warm porcelain canvas with 3D extruded Soft UI squircle & deep charcoal '+'
            c.drawColor(ThemeManager.neu(0xFFEDEAE4));

            NeumorphicDrawable.drawExtruded(c, rect, radius, 1.5f, 1.0f, true);

            // Deep charcoal '+' Sign
            Paint plusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            plusPaint.setStyle(Paint.Style.STROKE);
            plusPaint.setStrokeWidth(5.5f);
            plusPaint.setStrokeCap(Paint.Cap.ROUND);
            plusPaint.setColor(ThemeManager.neu(0xFF363B42));
            c.drawLine(cx - arm, cy, cx + arm, cy, plusPaint);
            c.drawLine(cx, cy - arm, cx, cy + arm, plusPaint);
        } else {
            // Default: Frosted Glass
            Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint.setShader(new RadialGradient(w * 0.5f, h * 0.5f, h * 0.58f,
                    new int[]{0x28FFFFFF, 0x0E38BDF8, 0x00000000},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, glowPaint);

            Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setColor(0x40000000);
            c.drawRoundRect(shadowRect, radius, radius, shadowPaint);

            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setShader(new LinearGradient(l, t, l + box, t + box,
                    new int[]{0x55FFFFFF, 0x30E2F1FF, 0x2294A3B8},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, radius, radius, fillPaint);

            Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setStrokeWidth(2.0f);
            rimPaint.setShader(new LinearGradient(l, t, l + box, t + box,
                    new int[]{0xFFFFFFFF, 0x40FFFFFF, 0xD9FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, radius, radius, rimPaint);

            Paint plusShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            plusShadow.setStyle(Paint.Style.STROKE);
            plusShadow.setStrokeWidth(5.8f);
            plusShadow.setStrokeCap(Paint.Cap.ROUND);
            plusShadow.setColor(0x4D000000);
            c.drawLine(cx - arm, cy + 2f, cx + arm, cy + 2f, plusShadow);
            c.drawLine(cx, cy - arm + 2f, cx, cy + arm + 2f, plusShadow);

            Paint plusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            plusPaint.setStyle(Paint.Style.STROKE);
            plusPaint.setStrokeWidth(5.8f);
            plusPaint.setStrokeCap(Paint.Cap.ROUND);
            plusPaint.setShader(new LinearGradient(cx - arm, cy - arm, cx + arm, cy + arm,
                    new int[]{0xFFFFFFFF, 0xFFE6F6FF},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            c.drawLine(cx - arm, cy, cx + arm, cy, plusPaint);
            c.drawLine(cx, cy - arm, cx, cy + arm, plusPaint);
        }

        glassAddAppBanner = new BitmapDrawable(context.getResources(), bmp);
        return glassAddAppBanner;
    }

    private Drawable createGlassAppDrawerBanner(Context context) {
        if (glassAppDrawerBanner != null) return glassAppDrawerBanner;
        int w = 360, h = 202;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        boolean isDark = ThemeManager.isDarkGlow(context);
        boolean isNeu = ThemeManager.isNeumorphism(context);

        float cell = 26f;
        float gap = 9f;
        float total = cell * 3f + gap * 2f;
        float startX = (w - total) / 2f;
        float startY = (h - total) / 2f;
        float radius = 7.5f;

        RectF rect = new RectF();
        RectF shadowRect = new RectF();

        if (isDark) {
            // Dark Glow: Deep OLED black canvas with Neon Cyan 3x3 cyber matrix
            c.drawColor(0xFF0A0A0E);

            Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint.setShader(new RadialGradient(w * 0.5f, h * 0.5f, h * 0.65f,
                    new int[]{0x2E00F0FF, 0x0800E5FF, 0x00000000},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, glowPaint);

            Paint cellFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            cellFill.setColor(0xFF000000);

            Paint cellRim = new Paint(Paint.ANTI_ALIAS_FLAG);
            cellRim.setStyle(Paint.Style.STROKE);
            cellRim.setStrokeWidth(1.25f);
            cellRim.setColor(0xFF00F0FF);
            cellRim.setShadowLayer(3f, 0, 0, 0xFF00E5FF);

            Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotPaint.setColor(0xFF00F0FF);
            dotPaint.setShadowLayer(3f, 0, 0, 0xFF00E5FF);

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    float l = startX + col * (cell + gap);
                    float t = startY + row * (cell + gap);
                    rect.set(l, t, l + cell, t + cell);
                    c.drawRoundRect(rect, radius, radius, cellFill);
                    c.drawRoundRect(rect, radius, radius, cellRim);
                    c.drawCircle(rect.centerX(), rect.centerY(), 3.0f, dotPaint);
                }
            }
        } else if (isNeu) {
            // Neumorphism Light: Warm porcelain canvas with 3x3 extruded dark charcoal squircles (matches 1793633365104.jpg)
            c.drawColor(ThemeManager.neu(0xFFEDEAE4));

            Paint cellLightShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            cellLightShadow.setColor(ThemeManager.neu(0xCCFFFFFF));

            Paint cellDarkShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            cellDarkShadow.setColor(ThemeManager.isNeuDarkCached() ? 0xA0000000 : 0x361A1610);

            Paint cellFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            cellFill.setColor(ThemeManager.neu(0xFF383E47));

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    float l = startX + col * (cell + gap);
                    float t = startY + row * (cell + gap);
                    // Top-left white highlight shadow
                    shadowRect.set(l - 1.5f, t - 1.5f, l + cell - 0.5f, t + cell - 0.5f);
                    c.drawRoundRect(shadowRect, radius, radius, cellLightShadow);
                    // Bottom-right soft dark drop shadow
                    shadowRect.set(l + 1.8f, t + 2.2f, l + cell + 1.8f, t + cell + 2.2f);
                    c.drawRoundRect(shadowRect, radius, radius, cellDarkShadow);

                    rect.set(l, t, l + cell, t + cell);
                    c.drawRoundRect(rect, radius, radius, cellFill);
                }
            }
        } else {
            // Default: Frosted Glass
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{0xFF1E293B, 0xFF111827, 0xFF0B111E},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, bgPaint);

            Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint.setShader(new RadialGradient(w * 0.5f, h * 0.5f, h * 0.68f,
                    new int[]{0x4056CCF2, 0x1A38BDF8, 0x00000000},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, glowPaint);

            Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setColor(0x45000000);

            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setStrokeWidth(1.6f);

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    float l = startX + col * (cell + gap);
                    float t = startY + row * (cell + gap);
                    rect.set(l, t, l + cell, t + cell);

                    shadowRect.set(l + 1f, t + 2.5f, l + cell + 1f, t + cell + 2.5f);
                    c.drawRoundRect(shadowRect, radius, radius, shadowPaint);

                    fillPaint.setShader(new LinearGradient(l, t, l + cell, t + cell,
                            new int[]{0xD9FFFFFF, 0x8CE2F1FF, 0x5994A3B8},
                            new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                    c.drawRoundRect(rect, radius, radius, fillPaint);

                    rimPaint.setShader(new LinearGradient(l, t, l + cell, t + cell,
                            new int[]{0xFFFFFFFF, 0x33FFFFFF, 0xB3FFFFFF},
                            new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                    c.drawRoundRect(rect, radius, radius, rimPaint);
                }
            }
        }

        glassAppDrawerBanner = new BitmapDrawable(context.getResources(), bmp);
        return glassAppDrawerBanner;
    }

    boolean isAppDrawer(int position) {
        return position >= 0 && position < items.size() && items.get(position).kind == Entry.APP_DRAWER;
    }

    /** Opens the tile's activity by explicit component, with no extras: a card can name nothing else. */
    private void launch(Entry e) {
        SoundEffects.playSelect();
        if (e.kind == Entry.APP_DRAWER) {
            if (listener != null) listener.onAppDrawerClicked();
            return;
        }
        if (e.kind == Entry.ADD_APP) {
            if (listener != null) listener.onAddAppClicked();
            return;
        }
        if (e.kind == Entry.HOME_SETTINGS) {
            ctx.startActivity(new Intent(ctx, HomeSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            RecentAppsManager.recordAppLaunch(ctx, e.pkg);
            ctx.startActivity(i);
        } catch (Exception ex) {
            try {
                Intent fallback = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(e.component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                ctx.startActivity(fallback);
            } catch (Exception ex2) {
                TvToast.show(ctx, ctx.getString(R.string.cant_open, e.label));
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).component.flattenToShortString().hashCode();
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component.flattenToShortString() : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry; a card's accent when it set one. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    /** Corner radius for a tile of this width: 16dp at the design width, proportionally less when smaller. */
    static float cornerRadius(int tileWidthPx, float density) {
        return 16f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final View focusRim;
        final TextView name;
        final boolean reducedMotion;
        private float focusLevel = 0f;
        private android.animation.ValueAnimator rimAnimator;

        void syncFocusRim(boolean focused, boolean immediate) {
            if (rimAnimator != null) {
                rimAnimator.cancel();
                rimAnimator = null;
            }
            focusRim.setAlpha(1f);
            float target = focused ? 1f : 0f;
            if (immediate || Math.abs(focusLevel - target) < 0.01f) {
                focusLevel = target;
                focusRim.invalidate();
                return;
            }
            rimAnimator = android.animation.ValueAnimator.ofFloat(focusLevel, target);
            rimAnimator.setDuration(reducedMotion ? (focused ? 100 : 80) : (focused ? 170 : 130));
            rimAnimator.addUpdateListener(a -> {
                focusLevel = (float) a.getAnimatedValue();
                focusRim.invalidate();
                if (card.getParent() != null && card.getParent().getParent() instanceof View) {
                    ((View) card.getParent().getParent()).invalidate();
                }
            });
            rimAnimator.start();
        }

        Holder(View v, TileListener listener, boolean reducedMotion) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            focusRim = v.findViewById(R.id.focus_rim);
            name = v.findViewById(R.id.name);
            this.reducedMotion = reducedMotion;
            focusRim.setAlpha(1f);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // 16dp at the design width, scaled down with the tile so small tiles keep the same shape.
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                }
            });
            card.setClipToOutline(true);

            final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            rimPaint.setStyle(Paint.Style.STROKE);
            final float strokePx = 1.5f * density;
            rimPaint.setStrokeWidth(strokePx);
            final LinearGradient rimShader = new LinearGradient(0f, 0f, 1f, 1f,
                    new int[]{0xF2FFFFFF, 0xD91C2029, 0xD91C2029, 0xF2FFFFFF},
                    new float[]{0f, 0.42f, 0.58f, 1f},
                    Shader.TileMode.CLAMP);
            rimPaint.setShader(rimShader);
            final android.graphics.Matrix rimMatrix = new android.graphics.Matrix();

            final Paint innerBevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            innerBevelPaint.setStyle(Paint.Style.STROKE);
            final float bevelPx = 1.0f * density;
            innerBevelPaint.setStrokeWidth(bevelPx);
            innerBevelPaint.setColor(0x59000000);

            // Dark Glow: Sleek, thin, crisp Neon Cyan border (never thick!)
            final Paint darkUnfocusedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            darkUnfocusedPaint.setStyle(Paint.Style.STROKE);
            final float darkUnfocusedPx = 1.0f * density;
            darkUnfocusedPaint.setStrokeWidth(darkUnfocusedPx);

            final Paint neonBloomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            neonBloomPaint.setStyle(Paint.Style.STROKE);
            final float neonBloomPx = 2.4f * density;
            neonBloomPaint.setStrokeWidth(neonBloomPx);

            final Paint neonCrispPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            neonCrispPaint.setStyle(Paint.Style.STROKE);
            final float neonCrispPx = 1.25f * density;
            neonCrispPaint.setStrokeWidth(neonCrispPx);

            final RectF rimRect = new RectF();
            final RectF fullRect = new RectF();
            focusRim.setBackground(new Drawable() {
                @Override
                public void draw(@NonNull Canvas canvas) {
                    Rect b = getBounds();
                    if (b.width() <= 0 || b.height() <= 0) return;
                    float rBase = cornerRadius(b.width(), density);
                    fullRect.set(b);

                    Context c = card.getContext();
                    if (ThemeManager.isDarkGlow(c)) {
                        // Unfocused subtle dark rim over banner
                        if (focusLevel < 1f) {
                            float uInset = darkUnfocusedPx * 0.5f;
                            rimRect.set(b.left + uInset, b.top + uInset, b.right - uInset, b.bottom - uInset);
                            int uAlpha = Math.round(0x28 * (1f - focusLevel));
                            darkUnfocusedPaint.setColor((uAlpha << 24) | 0x00FFFFFF);
                            float rU = Math.max(0f, rBase - uInset);
                            canvas.drawRoundRect(rimRect, rU, rU, darkUnfocusedPaint);
                        }
                        // Focused sleek, thin Neon Cyan rim + soft subtle bloom (uniform on all home screen tiles!)
                        if (focusLevel > 0f) {
                            float bInset = neonBloomPx * 0.5f;
                            rimRect.set(b.left + bInset, b.top + bInset, b.right - bInset, b.bottom - bInset);
                            int bAlpha = Math.round(0x3D * focusLevel);
                            neonBloomPaint.setColor((bAlpha << 24) | 0x0000F0FF);
                            float rB = Math.max(0f, rBase - bInset);
                            canvas.drawRoundRect(rimRect, rB, rB, neonBloomPaint);

                            float cInset = neonCrispPx * 0.5f;
                            rimRect.set(b.left + cInset, b.top + cInset, b.right - cInset, b.bottom - cInset);
                            int cAlpha = Math.round(0xFF * focusLevel);
                            neonCrispPaint.setColor((cAlpha << 24) | 0x0000F0FF);
                            float rC = Math.max(0f, rBase - cInset);
                            canvas.drawRoundRect(rimRect, rC, rC, neonCrispPaint);
                        }
                        return;
                    }

                    if (ThemeManager.isNeumorphism(c)) {
                        // Unfocused 3D Soft UI convex bevel rim over banner
                        if (focusLevel < 1f) {
                            NeumorphicDrawable.drawTileUnfocusedRim(canvas, fullRect, rBase, density, 1f - focusLevel);
                        }
                        // Focused tactile 3D Soft UI highlight bevel rim (replaces Frosted Glass rim)
                        if (focusLevel > 0f) {
                            NeumorphicDrawable.drawTileFocusRim(canvas, fullRect, rBase, density, focusLevel);
                        }
                        return;
                    }

                    // Default: Frosted Glass
                    if (focusLevel <= 0f) return;
                    innerBevelPaint.setAlpha(Math.round(0x59 * focusLevel));
                    rimPaint.setAlpha(Math.round(255 * focusLevel));

                    // Subtle inner dark glass bevel for crisp contrast on white banners (e.g. YouTube)
                    float bevelInset = strokePx + bevelPx * 0.5f;
                    rimRect.set(b.left + bevelInset, b.top + bevelInset, b.right - bevelInset, b.bottom - bevelInset);
                    float rBevel = Math.max(0f, rBase - bevelInset);
                    canvas.drawRoundRect(rimRect, rBevel, rBevel, innerBevelPaint);

                    // 1.5dp Liquid Glass diagonal gradient rim (matches top bar pills)
                    float inset = strokePx * 0.5f;
                    rimRect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
                    rimMatrix.setScale(rimRect.width(), rimRect.height());
                    rimMatrix.postTranslate(rimRect.left, rimRect.top);
                    rimShader.setLocalMatrix(rimMatrix);
                    float r = Math.max(0f, rBase - inset);
                    canvas.drawRoundRect(rimRect, r, r, rimPaint);
                }

                @Override
                public void setAlpha(int alpha) {
                }

                @Override
                public void setColorFilter(android.graphics.ColorFilter colorFilter) {
                }

                @Override
                public int getOpacity() {
                    return android.graphics.PixelFormat.TRANSLUCENT;
                }
            });

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                view.animate().cancel();
                name.animate().cancel();
                syncFocusRim(focused, false);
                if (reducedMotion) {
                    // No overshoot: the extra frames redraw the banner and the dock band on a weak GPU.
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 120 : 80)
                            .setInterpolator(ZOOM_REDUCED)
                            .start();
                    name.animate().alpha(focused ? 1f : 0f).setDuration(100).start();
                } else {
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 260 : 180)
                            .setInterpolator(focused ? ZOOM_IN : ZOOM_OUT)
                            .start();
                    name.animate().alpha(focused ? 1f : 0f)
                            .translationY(focused ? density * 4f : -density * 6f)
                            .setDuration(220).start();
                }
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
