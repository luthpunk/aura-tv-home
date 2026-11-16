package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Prayer times manager synchronized with the active Weather location (AppLocationManager).
 * Fetches official Kemenag RI timetable via MyQuran API for Indonesian cities, falls back to
 * AlAdhan coordinate calendar (method=20) globally, and includes an offline astronomical fallback.
 */
final class PrayerTimes {
    private static final String TAG = "TvHome.Prayer";
    private static final String PREFS_NAME = "prayer_times_cache";
    private static final String KEY_JSON = "takwim_wly01";
    private static final String KEY_DAYS_JSON = "cached_days_json";
    private static final String KEY_MONTH = "takwim_month";
    private static final String KEY_CACHED_LOC = "cached_loc_key";

    static final String[] NAMES = {"Subuh", "Syuruq", "Dzuhur", "Ashar", "Maghrib", "Isya"};
    private static final String[] PRAYER_NAMES = NAMES;
    private static final String[] PRAYER_KEYS = {"fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha"};

    static final class Day {
        final int[] minutes;
        final String hijri;

        Day(int[] minutes, String hijri) {
            this.minutes = minutes;
            this.hijri = hijri;
        }
    }

    static final class WidgetState {
        final PrayerItem[] items;
        final int nextIndex;
        final int activeIndex;
        final String nextName;
        final String nextTime;
        final int remainingMinutes;
        final String locationName;

        WidgetState(PrayerItem[] items, int nextIndex, int activeIndex, String nextName, String nextTime,
                    int remainingMinutes, String locationName) {
            this.items = items;
            this.nextIndex = nextIndex;
            this.activeIndex = activeIndex;
            this.nextName = nextName;
            this.nextTime = nextTime;
            this.remainingMinutes = remainingMinutes;
            this.locationName = locationName;
        }
    }

    static Day day(Context ctx, String zone, Calendar date) {
        long[] times = getTimesForDate(ctx, date);
        if (times == null || times.length < 6) return null;
        int[] minutes = new int[6];
        for (int i = 0; i < 6; i++) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(times[i]);
            minutes[i] = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        }
        return new Day(minutes, getHijriDate());
    }

    static boolean due(Context ctx, String zone) {
        return day(ctx, zone, Calendar.getInstance()) == null;
    }

    static boolean refresh(Context ctx, String zone) {
        sync(ctx, null);
        return true;
    }

    private PrayerTimes() {
    }

    private static String buildLocationKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.contains("manual_prayer_zone")) {
            return "ZONE:" + prefs.getString("manual_prayer_zone", "WLY01");
        }
        double lat = AppLocationManager.getLatitude(context);
        double lon = AppLocationManager.getLongitude(context);
        String locName = AppLocationManager.getLocationName(context);
        return String.format(Locale.ROOT, "%.3f,%.3f|%s", lat, lon, locName);
    }

    /** Forces an immediate background sync for the active weather location. */
    static void sync(Context context, Runnable onUpdated) {
        syncInternal(context, onUpdated, true);
    }

    /** Refresh timetable in background if needed (when month or location changes). */
    static void syncIfDue(Context context, Runnable onUpdated) {
        syncInternal(context, onUpdated, false);
    }

    private static void syncInternal(Context context, Runnable onUpdated, boolean force) {
        final Context appCtx = context.getApplicationContext();
        new Thread(() -> {
            try {
                SharedPreferences prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                Calendar now = Calendar.getInstance();
                int year = now.get(Calendar.YEAR);
                int month = now.get(Calendar.MONTH) + 1;
                int currentMonth = year * 100 + month;
                String locKey = buildLocationKey(appCtx);
                String cachedLoc = prefs.getString(KEY_CACHED_LOC, "");

                if (!force && locKey.equals(cachedLoc)
                        && prefs.getInt(KEY_MONTH, 0) == currentMonth
                        && (prefs.contains(KEY_DAYS_JSON) || prefs.contains(KEY_JSON))) {
                    return;
                }

                if (locKey.startsWith("ZONE:")) {
                    String zone = locKey.substring(5);
                    String raw = httpGet("https://api.waktusolat.app/v2/solat/" + zone);
                    if (raw != null && !raw.isEmpty()) {
                        prefs.edit()
                                .putString(KEY_JSON, raw)
                                .remove(KEY_DAYS_JSON)
                                .putInt(KEY_MONTH, currentMonth)
                                .putString(KEY_CACHED_LOC, locKey)
                                .putString("cached_zone", zone)
                                .apply();
                        if (onUpdated != null) new Handler(Looper.getMainLooper()).post(onUpdated);
                        Log.i(TAG, "Synced JAKIM prayer times for zone " + zone);
                    }
                    return;
                }

                String locName = AppLocationManager.getLocationName(appCtx);
                String place = AppLocationManager.getStationPlace(appCtx);
                double lat = AppLocationManager.getLatitude(appCtx);
                double lon = AppLocationManager.getLongitude(appCtx);

                JSONObject daysMap = fetchMyQuranMonth(locName, place, year, month);
                String sourceUsed = "MyQuran/Kemenag";
                if (daysMap == null || daysMap.length() == 0) {
                    daysMap = fetchAlAdhanMonth(lat, lon, year, month);
                    sourceUsed = "AlAdhan";
                }

                if (daysMap != null && daysMap.length() > 0) {
                    prefs.edit()
                            .putString(KEY_DAYS_JSON, daysMap.toString())
                            .putInt(KEY_MONTH, currentMonth)
                            .putString(KEY_CACHED_LOC, locKey)
                            .apply();
                    if (onUpdated != null) {
                        new Handler(Looper.getMainLooper()).post(onUpdated);
                    }
                    Log.i(TAG, "Synced prayer times via " + sourceUsed + " for " + locName);
                }
            } catch (Exception e) {
                Log.w(TAG, "Prayer sync offline/failed: " + e.getMessage());
            }
        }, "prayer-sync").start();
    }

    private static JSONObject fetchMyQuranMonth(String locName, String place, int year, int month) {
        try {
            Set<String> candidates = new LinkedHashSet<>();
            addCityCandidates(candidates, locName);
            if (place != null && !place.isEmpty()) {
                for (String part : place.split(",")) {
                    String trimmed = part.trim();
                    if (trimmed.startsWith("Kab.") || trimmed.startsWith("Kota")) {
                        addCityCandidates(candidates, trimmed);
                    }
                }
            }

            String cityId = null;
            for (String q : candidates) {
                if (q.length() < 3) continue;
                String encoded = URLEncoder.encode(q, "UTF-8").replace("+", "%20");
                String searchBody = httpGet("https://api.myquran.com/v2/sholat/kota/cari/" + encoded);
                if (searchBody == null) continue;
                JSONObject searchJson = new JSONObject(searchBody);
                if (!searchJson.optBoolean("status", false)) continue;
                JSONArray data = searchJson.optJSONArray("data");
                if (data == null || data.length() == 0) continue;

                String qUpper = q.toUpperCase(Locale.ROOT);
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.optJSONObject(i);
                    if (item == null) continue;
                    String lokasi = item.optString("lokasi", "").toUpperCase(Locale.ROOT);
                    if (lokasi.equals("KOTA " + qUpper) || lokasi.equals("KAB. " + qUpper) || lokasi.equals(qUpper)) {
                        cityId = item.optString("id", null);
                        break;
                    }
                }
                if (cityId == null) {
                    JSONObject first = data.optJSONObject(0);
                    if (first != null) cityId = first.optString("id", null);
                }
                if (cityId != null && !cityId.isEmpty()) break;
            }

            if (cityId == null || cityId.isEmpty()) return null;

            String monthUrl = String.format(Locale.ROOT,
                    "https://api.myquran.com/v2/sholat/jadwal/%s/%d/%02d", cityId, year, month);
            String monthBody = httpGet(monthUrl);
            if (monthBody == null) return null;

            JSONObject root = new JSONObject(monthBody);
            if (!root.optBoolean("status", false)) return null;
            JSONObject dataObj = root.optJSONObject("data");
            if (dataObj == null) return null;
            JSONArray jadwal = dataObj.optJSONArray("jadwal");
            if (jadwal == null || jadwal.length() == 0) return null;

            JSONObject result = new JSONObject();
            for (int i = 0; i < jadwal.length(); i++) {
                JSONObject row = jadwal.optJSONObject(i);
                if (row == null) continue;
                String dateStr = row.optString("date", "");
                int dayNum = extractDayFromIso(dateStr, i + 1);
                JSONArray times = new JSONArray();
                times.put(cleanHhMm(row.optString("subuh", "")));
                times.put(cleanHhMm(row.optString("terbit", "")));
                times.put(cleanHhMm(row.optString("dzuhur", "")));
                times.put(cleanHhMm(row.optString("ashar", "")));
                times.put(cleanHhMm(row.optString("maghrib", "")));
                times.put(cleanHhMm(row.optString("isya", "")));
                result.put(String.valueOf(dayNum), times);
            }
            return result;
        } catch (Exception e) {
            Log.w(TAG, "MyQuran fetch failed: " + e.getMessage());
            return null;
        }
    }

    private static JSONObject fetchAlAdhanMonth(double lat, double lon, int year, int month) {
        try {
            String url = String.format(Locale.ROOT,
                    "https://api.aladhan.com/v1/calendar/%d/%d?latitude=%.4f&longitude=%.4f&method=20",
                    year, month, lat, lon);
            String body = httpGet(url);
            if (body == null) return null;
            JSONObject root = new JSONObject(body);
            JSONArray data = root.optJSONArray("data");
            if (data == null || data.length() == 0) return null;

            JSONObject result = new JSONObject();
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                JSONObject timings = item.optJSONObject("timings");
                if (timings == null) continue;
                int dayNum = i + 1;
                JSONObject dateObj = item.optJSONObject("date");
                if (dateObj != null && dateObj.optJSONObject("gregorian") != null) {
                    String dStr = dateObj.optJSONObject("gregorian").optString("day", "");
                    try {
                        if (!dStr.isEmpty()) dayNum = Integer.parseInt(dStr);
                    } catch (NumberFormatException ignored) {
                    }
                }
                JSONArray times = new JSONArray();
                times.put(cleanHhMm(timings.optString("Fajr", "")));
                times.put(cleanHhMm(timings.optString("Sunrise", "")));
                times.put(cleanHhMm(timings.optString("Dhuhr", "")));
                times.put(cleanHhMm(timings.optString("Asr", "")));
                times.put(cleanHhMm(timings.optString("Maghrib", "")));
                times.put(cleanHhMm(timings.optString("Isha", "")));
                result.put(String.valueOf(dayNum), times);
            }
            return result;
        } catch (Exception e) {
            Log.w(TAG, "AlAdhan fetch failed: " + e.getMessage());
            return null;
        }
    }

    private static void addCityCandidates(Set<String> out, String raw) {
        if (raw == null) return;
        String s = raw.trim();
        int paren = s.indexOf('(');
        if (paren > 0) s = s.substring(0, paren).trim();
        int comma = s.indexOf(',');
        if (comma > 0) s = s.substring(0, comma).trim();
        s = s.replaceFirst("(?i)^(Kabupaten|Kab\\.?|Kota|Kecamatan|Kec\\.?)\\s+", "").trim();
        if (!s.isEmpty()) out.add(s);
    }

    private static int extractDayFromIso(String dateStr, int fallback) {
        if (dateStr != null && dateStr.length() >= 10) {
            try {
                return Integer.parseInt(dateStr.substring(8, 10));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static String cleanHhMm(String raw) {
        if (raw == null) return "00:00";
        String s = raw.trim();
        int space = s.indexOf(' ');
        if (space > 0) s = s.substring(0, space);
        return s.length() >= 5 ? s.substring(0, 5) : s;
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return sb.toString();
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    public static class PrayerZone {
        public final String code;
        public final String state;
        public final String name;
        public final double lat;
        public final double lon;

        public PrayerZone(String code, String state, String name, double lat, double lon) {
            this.code = code;
            this.state = state;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }

        public String getDisplayName() {
            return name + " (" + state + " • " + code + ")";
        }
    }

    private static final List<PrayerZone> PRESET_ZONES = new ArrayList<>();

    static {
        PRESET_ZONES.add(new PrayerZone("WLY01", "W.P.", "Putrajaya & Kuala Lumpur", 2.9158, 101.6909));
        PRESET_ZONES.add(new PrayerZone("WLY02", "W.P.", "Labuan", 5.3347, 115.2289));
        PRESET_ZONES.add(new PrayerZone("SGR01", "Selangor", "Shah Alam, Petaling, Gombak, Sepang", 3.0683, 101.5058));
        PRESET_ZONES.add(new PrayerZone("SGR02", "Selangor", "Kuala Selangor, Sabak Bernam", 3.3500, 101.2500));
        PRESET_ZONES.add(new PrayerZone("SGR03", "Selangor", "Klang, Kuala Langat, Banting", 3.0108, 101.4081));
        PRESET_ZONES.add(new PrayerZone("MLK01", "Melaka", "Bandaraya Melaka, Alor Gajah, Jasin", 2.1909, 102.2571));
        PRESET_ZONES.add(new PrayerZone("NGS02", "Negeri Sembilan", "Seremban, Port Dickson, Nilai", 2.6953, 101.9056));
        PRESET_ZONES.add(new PrayerZone("NGS01", "Negeri Sembilan", "Tampin, Jempol", 2.4700, 102.2300));
        PRESET_ZONES.add(new PrayerZone("JHR02", "Johor", "Johor Bahru, Pasir Gudang, Kulai", 1.4947, 103.7419));
        PRESET_ZONES.add(new PrayerZone("JHR03", "Johor", "Batu Pahat, Muar, Tangkak, Segamat", 1.9191, 102.8658));
        PRESET_ZONES.add(new PrayerZone("JHR04", "Johor", "Kluang, Mersing", 2.0306, 103.3214));
        PRESET_ZONES.add(new PrayerZone("PNG01", "Pulau Pinang", "Seluruh Negeri Pulau Pinang", 5.3942, 100.3983));
        PRESET_ZONES.add(new PrayerZone("PRK02", "Perak", "Ipoh, Batu Gajah, Kampar", 4.6397, 101.1083));
        PRESET_ZONES.add(new PrayerZone("PRK03", "Perak", "Taiping, Bagan Serai, Parit Buntar", 4.8569, 100.7417));
        PRESET_ZONES.add(new PrayerZone("PRK04", "Perak", "Lumut, Manjung, Pangkor, Sitiawan", 4.1953, 100.6728));
        PRESET_ZONES.add(new PrayerZone("PRK01", "Perak", "Tapah, Slim River, Tanjung Malim", 3.6800, 101.5200));
        PRESET_ZONES.add(new PrayerZone("KDH01", "Kedah", "Kota Setar, Pokok Sena, Kubang Pasu", 6.1371, 100.3469));
        PRESET_ZONES.add(new PrayerZone("KDH02", "Kedah", "Kuala Muda, Yan, Pendang, Sungai Petani", 5.6297, 100.4677));
        PRESET_ZONES.add(new PrayerZone("KDH07", "Kedah", "Pulau Langkawi", 6.3316, 99.8585));
        PRESET_ZONES.add(new PrayerZone("PLS01", "Perlis", "Kangar, Padang Besar, Arau", 6.4299, 100.2109));
        PRESET_ZONES.add(new PrayerZone("PHG02", "Pahang", "Kuantan, Pekan, Muadzam Shah", 3.8192, 103.2966));
        PRESET_ZONES.add(new PrayerZone("PHG03", "Pahang", "Jerantut, Temerloh, Maran, Bera", 3.4478, 102.4178));
        PRESET_ZONES.add(new PrayerZone("PHG04", "Pahang", "Bentong, Raub, Lipis", 3.5200, 101.9000));
        PRESET_ZONES.add(new PrayerZone("PHG06", "Pahang", "Cameron Highlands", 4.4700, 101.3800));
        PRESET_ZONES.add(new PrayerZone("TRG01", "Terengganu", "Kuala Terengganu, Marang, Kuala Nerus", 5.3117, 103.1325));
        PRESET_ZONES.add(new PrayerZone("TRG04", "Terengganu", "Kemaman, Dungun", 4.2622, 103.4258));
        PRESET_ZONES.add(new PrayerZone("KTN01", "Kelantan", "Kota Bharu, Bachok, Pasir Puteh, Tumpat", 6.1256, 102.2386));
        PRESET_ZONES.add(new PrayerZone("KTN02", "Kelantan", "Machang, Pasir Mas, Tanah Merah", 5.7600, 102.2100));
        PRESET_ZONES.add(new PrayerZone("SBH07", "Sabah", "Kota Kinabalu, Penampang, Tuaran, Papar", 6.0971, 116.1665));
        PRESET_ZONES.add(new PrayerZone("SBH03", "Sabah", "Tawau, Balung, Merotai, Kalabakan", 4.2564, 117.8931));
        PRESET_ZONES.add(new PrayerZone("SBH05", "Sabah", "Kudat, Kota Marudu, Pitas, Sandakan", 5.8456, 118.0678));
        PRESET_ZONES.add(new PrayerZone("SWK08", "Sarawak", "Kuching, Bau, Lundu, Samarahan, Serian", 1.5622, 110.3889));
        PRESET_ZONES.add(new PrayerZone("SWK04", "Sarawak", "Bintulu, Tatau, Sebauh", 3.1772, 113.0412));
        PRESET_ZONES.add(new PrayerZone("SWK02", "Sarawak", "Miri, Niah, Bekenu, Sibuti", 4.4947, 114.0434));
        PRESET_ZONES.add(new PrayerZone("SWK05", "Sarawak", "Sibu, Mukah, Kanowit, Song", 2.2981, 111.8344));
    }

    public static List<PrayerZone> getPresetZones() {
        return Collections.unmodifiableList(PRESET_ZONES);
    }

    public static String getActiveZone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.contains("manual_prayer_zone")) {
            return prefs.getString("manual_prayer_zone", "WLY01");
        }
        return AppLocationManager.getStationId(context);
    }

    public static String getActiveZoneName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.contains("manual_prayer_zone_name")) {
            return prefs.getString("manual_prayer_zone_name", AppLocationManager.getLocationName(context));
        }
        return AppLocationManager.getLocationName(context);
    }

    public static String getShortZoneName(Context context) {
        String name = getActiveZoneName(context);
        if (name == null || name.trim().isEmpty()) return "Tuban";
        int cut = name.indexOf(',');
        if (cut > 0) name = name.substring(0, cut);
        cut = name.indexOf('&');
        if (cut > 0) name = name.substring(0, cut);
        cut = name.indexOf('(');
        if (cut > 0) name = name.substring(0, cut);
        name = name.trim();
        return name.isEmpty() ? "Tuban" : name;
    }

    public static void clearManualZone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove("manual_prayer_zone")
                .remove("manual_prayer_zone_name")
                .remove("manual_lat")
                .remove("manual_lon")
                .remove(KEY_CACHED_LOC)
                .apply();
    }

    public static void setZone(Context context, String zoneCode) {
        if (zoneCode == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("manual_prayer_zone", zoneCode)
                .putString("manual_prayer_zone_name", zoneCode)
                .apply();
        sync(context, null);
    }

    public static void setPrayerZone(Context context, PrayerZone zone) {
        if (zone == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("manual_prayer_zone", zone.code)
                .putString("manual_prayer_zone_name", zone.name)
                .putString("manual_lat", String.valueOf(zone.lat))
                .putString("manual_lon", String.valueOf(zone.lon))
                .apply();
        sync(context, null);
    }

    static class PrayerItem {
        final String name;
        final String timeStr;
        final boolean isNext;
        final boolean isActive;
        final boolean isPast;

        PrayerItem(String name, String timeStr, boolean isNext, boolean isActive, boolean isPast) {
            this.name = name;
            this.timeStr = timeStr;
            this.isNext = isNext;
            this.isActive = isActive;
            this.isPast = isPast;
        }
    }

    /** Returns full widget state including all 6 daily slots, currently active slot, and next countdown. */
    static WidgetState getWidgetState(Context context) {
        Calendar now = Calendar.getInstance();
        long[] times = getTimesForDate(context, now);
        long nowMs = now.getTimeInMillis();

        int nextIdx = -1;
        for (int i = 0; i < times.length; i++) {
            if (i == 1) continue; // Syuruq is sunrise marker, not a fardhu prayer
            if (times[i] > nowMs) {
                nextIdx = i;
                break;
            }
        }

        // Determine currently active prayer slot (the latest time slot that has already entered today;
        // if before Subuh [00:00..Subuh], Isya [5] from the night is still active).
        int activeIdx = 5;
        for (int i = times.length - 1; i >= 0; i--) {
            if (times[i] <= nowMs) {
                activeIdx = i;
                break;
            }
        }

        long nextTimeMs;
        if (nextIdx == -1) {
            nextIdx = 0; // After Isya -> tomorrow's Subuh
            Calendar tomorrow = (Calendar) now.clone();
            tomorrow.add(Calendar.DAY_OF_MONTH, 1);
            long[] tomTimes = getTimesForDate(context, tomorrow);
            nextTimeMs = (tomTimes != null && tomTimes.length > 0) ? tomTimes[0] : (times[0] + 86400000L);
        } else {
            nextTimeMs = times[nextIdx];
        }

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        PrayerItem[] items = new PrayerItem[6];
        for (int i = 0; i < 6; i++) {
            items[i] = new PrayerItem(
                    PRAYER_NAMES[i],
                    timeFmt.format(new Date(times[i])),
                    i == nextIdx,
                    i == activeIdx,
                    times[i] < nowMs
            );
        }

        long diffMs = Math.max(0L, nextTimeMs - nowMs);
        int remMins = (int) Math.max(1L, (diffMs + 30000L) / 60000L);
        return new WidgetState(
                items,
                nextIdx,
                activeIdx,
                PRAYER_NAMES[nextIdx],
                timeFmt.format(new Date(nextTimeMs)),
                remMins,
                getShortZoneName(context)
        );
    }

    /** Returns all 6 prayers for today with formatted times and status. */
    static PrayerItem[] getPrayerItems(Context context) {
        return getWidgetState(context).items;
    }

    /** Returns formatted clean UI CharSequence with highlighted next prayer, bold and legible. */
    static CharSequence getFormatted(Context context) {
        WidgetState ws = getWidgetState(context);
        SpannableStringBuilder sb = new SpannableStringBuilder();

        int l1Start = sb.length();
        String zoneHeader = ws.locationName.toUpperCase(Locale.ROOT);
        sb.append(zoneHeader).append("  •  BERIKUTNYA: ")
                .append(ws.nextName.toUpperCase(Locale.ROOT)).append(" ").append(ws.nextTime);
        int l1End = sb.length();
        sb.setSpan(new ForegroundColorSpan(0xFFFFFFFF), l1Start, l1End, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), l1Start, l1End, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sb.append("\n");
        for (int i = 0; i < ws.items.length; i++) {
            PrayerItem item = ws.items[i];
            int start = sb.length();
            sb.append(item.name.toUpperCase(Locale.ROOT)).append(" ").append(item.timeStr);
            sb.setSpan(new ForegroundColorSpan(item.isActive ? 0xFFFFA726 : 0xE6FFFFFF),
                    start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (i < ws.items.length - 1) {
                int dotStart = sb.length();
                sb.append("  •  ");
                sb.setSpan(new ForegroundColorSpan(0x80FFFFFF), dotStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    private static long[] getTimesForDate(Context context, Calendar cal) {
        long[] cached = getCachedTimesForDate(context, cal);
        if (cached != null && cached.length == 6) return cached;
        return calculateFallback(context, cal);
    }

    private static long[] getCachedTimesForDate(Context context, Calendar cal) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String locKey = buildLocationKey(context);
            if (!locKey.equals(prefs.getString(KEY_CACHED_LOC, ""))) {
                return null;
            }
            int wantMonth = cal.get(Calendar.YEAR) * 100 + (cal.get(Calendar.MONTH) + 1);
            if (prefs.getInt(KEY_MONTH, 0) != wantMonth) {
                return null;
            }

            int day = cal.get(Calendar.DAY_OF_MONTH);
            String daysJsonStr = prefs.getString(KEY_DAYS_JSON, null);
            if (daysJsonStr != null) {
                JSONObject daysObj = new JSONObject(daysJsonStr);
                JSONArray arr = daysObj.optJSONArray(String.valueOf(day));
                if (arr != null && arr.length() >= 6) {
                    long[] res = new long[6];
                    for (int k = 0; k < 6; k++) {
                        res[k] = parseHhMmToMillis(cal, arr.getString(k));
                    }
                    return res;
                }
            }

            String takimStr = prefs.getString(KEY_JSON, null);
            if (takimStr != null && locKey.startsWith("ZONE:")) {
                JSONObject obj = new JSONObject(takimStr);
                JSONArray prayers = obj.getJSONArray("prayers");
                for (int i = 0; i < prayers.length(); i++) {
                    JSONObject p = prayers.getJSONObject(i);
                    if (p.optInt("day") == day) {
                        long[] res = new long[6];
                        for (int k = 0; k < 6; k++) {
                            res[k] = p.getLong(PRAYER_KEYS[k]) * 1000L;
                        }
                        return res;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static long parseHhMmToMillis(Calendar cal, String hhmm) {
        String[] parts = hhmm.split(":");
        int h = Integer.parseInt(parts[0].trim());
        int m = Integer.parseInt(parts[1].trim());
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, m);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Astronomical calculation fallback based on active weather location & device timezone. */
    private static long[] calculateFallback(Context context, Calendar cal) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        double latitude = prefs.contains("manual_lat")
                ? Double.parseDouble(prefs.getString("manual_lat", String.valueOf(AppLocationManager.getLatitude(context))))
                : AppLocationManager.getLatitude(context);
        double longitude = prefs.contains("manual_lon")
                ? Double.parseDouble(prefs.getString("manual_lon", String.valueOf(AppLocationManager.getLongitude(context))))
                : AppLocationManager.getLongitude(context);
        double timezone = TimeZone.getDefault().getOffset(cal.getTimeInMillis()) / 3600000.0;

        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);

        double d = 367.0 * year - Math.floor((7.0 * (year + Math.floor((month + 9.0) / 12.0))) / 4.0)
                + Math.floor((275.0 * month) / 9.0) + day - 730531.5;

        double L = (280.461 + 0.9856474 * d) % 360.0;
        if (L < 0) L += 360.0;
        double M = (357.528 + 0.9856003 * d) % 360.0;
        if (M < 0) M += 360.0;

        double lambda = L + 1.915 * Math.sin(Math.toRadians(M)) + 0.020 * Math.sin(Math.toRadians(2 * M));
        double epsilon = 23.439 - 0.0000004 * d;
        double alpha = Math.toDegrees(Math.atan2(Math.cos(Math.toRadians(epsilon)) * Math.sin(Math.toRadians(lambda)), Math.cos(Math.toRadians(lambda)))) / 15.0;
        double delta = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(epsilon)) * Math.sin(Math.toRadians(lambda))));

        double noon = 12.0 + timezone - (longitude / 15.0) - (L / 15.0 - alpha);
        if (noon < 0) noon += 24.0;
        if (noon >= 24) noon -= 24.0;

        double fajrHour = noon - sunAngleHour(-20.0, latitude, delta);
        double sunriseHour = noon - sunAngleHour(-0.833, latitude, delta);
        double asrHour = noon + asrHour(1, latitude, delta);
        double sunsetHour = noon + sunAngleHour(-0.833, latitude, delta);
        double ishaHour = noon + sunAngleHour(-18.0, latitude, delta);

        // Kemenag standard +2 min ihtiyat rounding
        double[] hours = {fajrHour + 0.034, sunriseHour, noon + 0.04, asrHour + 0.034, sunsetHour + 0.04, ishaHour + 0.034};
        long[] res = new long[6];
        Calendar base = (Calendar) cal.clone();
        base.set(Calendar.SECOND, 0);
        base.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < 6; i++) {
            double hVal = hours[i];
            while (hVal < 0) hVal += 24.0;
            while (hVal >= 24.0) hVal -= 24.0;
            int totalMins = (int) Math.round(hVal * 60.0);
            int h = (totalMins / 60) % 24;
            int m = totalMins % 60;
            base.set(Calendar.HOUR_OF_DAY, h);
            base.set(Calendar.MINUTE, m);
            res[i] = base.getTimeInMillis();
        }
        return res;
    }

    private static double sunAngleHour(double angle, double lat, double delta) {
        double cosH = (Math.sin(Math.toRadians(angle)) - Math.sin(Math.toRadians(lat)) * Math.sin(Math.toRadians(delta)))
                / (Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(delta)));
        cosH = Math.max(-1.0, Math.min(1.0, cosH));
        return Math.toDegrees(Math.acos(cosH)) / 15.0;
    }

    private static double asrHour(int shadowFactor, double lat, double delta) {
        double phi = Math.abs(lat - delta);
        double cotA = shadowFactor + Math.tan(Math.toRadians(phi));
        double angle = Math.toDegrees(Math.atan(1.0 / cotA));
        return sunAngleHour(angle, lat, delta);
    }

    static class ScheduleItem {
        final String name;
        final String timeFormatted;
        final boolean isNext;
        final boolean isPast;

        ScheduleItem(String name, String timeFormatted, boolean isNext, boolean isPast) {
            this.name = name;
            this.timeFormatted = timeFormatted;
            this.isNext = isNext;
            this.isPast = isPast;
        }
    }

    /** Returns full day prayer list for popup display. */
    static java.util.List<ScheduleItem> getFullSchedule(Context context) {
        WidgetState ws = getWidgetState(context);
        java.util.List<ScheduleItem> list = new java.util.ArrayList<>();
        for (PrayerItem item : ws.items) {
            list.add(new ScheduleItem(item.name, item.timeStr, item.isNext, item.isPast));
        }
        return list;
    }

    /** Returns short summary of next prayer (e.g. for screensaver). */
    static String getNextPrayerSummary(Context context) {
        WidgetState ws = getWidgetState(context);
        return ws.nextName + " • " + ws.nextTime + " (" + ws.locationName + ")";
    }

    /** Returns Islamic Hijrah date (e.g. "11 Jamadilawal 1448H"). */
    static String getHijriDate() {
        try {
            java.time.chrono.HijrahDate h = java.time.chrono.HijrahDate.now();
            int day = h.get(java.time.temporal.ChronoField.DAY_OF_MONTH);
            int month = h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR);
            int year = h.get(java.time.temporal.ChronoField.YEAR);
            String[] months = {
                "Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir",
                "Jumadil Awal", "Jumadil Akhir", "Rajab", "Sya'ban",
                "Ramadhan", "Syawal", "Dzulqa'dah", "Dzulhijjah"
            };
            String monthName = (month >= 1 && month <= 12) ? months[month - 1] : "Hijrah";
            return day + " " + monthName + " " + year + "H";
        } catch (Exception e) {
            return "";
        }
    }
}
