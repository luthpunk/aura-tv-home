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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Live weather and air pollutant index (API / IPU) for Malaysia.
 * Fetches official ground-station Air Pollutant Index from Jabatan Alam Sekitar (JAS) Malaysia MyEQMS/APIMS,
 * paired with detailed 7-day and hourly weather forecasts from Open-Meteo.
 * Supports Putrajaya and all official monitoring stations across Malaysia.
 */
final class WeatherAirQuality {
    private static final String TAG = "TvHome.Weather";
    private static final String PREFS_NAME = "weather_aqi_cache";
    private static final String KEY_WEATHER_JSON = "weather_json";
    private static final String KEY_AQI_JSON = "aqi_json";
    private static final String KEY_TEMP = "temp";
    private static final String KEY_CODE = "weather_code";
    private static final String KEY_AQI = "aqi";
    private static final String KEY_AQI_STATUS = "aqi_status";
    private static final String KEY_AQI_PARAM = "aqi_param";
    private static final String KEY_AQI_STATION_PLACE = "aqi_place";
    private static final String KEY_AQI_DATETIME = "aqi_datetime";
    private static final String KEY_AQI_IS_JAS = "aqi_is_jas";
    private static final String KEY_LAST_UPDATE = "last_update";
    private static final long REFRESH_INTERVAL_MS = 30L * 60 * 1000; // 30 minutes

    static class HourlyItem {
        final String time;
        final String icon;
        final int temp;
        final int rainChance;
        final int weatherCode;
        final int hour24;

        HourlyItem(String time, String icon, int temp, int rainChance) {
            this(time, icon, temp, rainChance, 2, 15);
        }

        HourlyItem(String time, String icon, int temp, int rainChance, int weatherCode, int hour24) {
            this.time = time;
            this.icon = icon;
            this.temp = temp;
            this.rainChance = rainChance;
            this.weatherCode = weatherCode;
            this.hour24 = hour24;
        }
    }

    static class DailyItem {
        final String day;
        final String icon;
        final int maxTemp;
        final int minTemp;
        final int rainChance;

        DailyItem(String day, String icon, int maxTemp, int minTemp, int rainChance) {
            this.day = day;
            this.icon = icon;
            this.maxTemp = maxTemp;
            this.minTemp = minTemp;
            this.rainChance = rainChance;
        }
    }

    static class FullWeather {
        final String location;
        final String stationId;
        final String stationPlace;
        final boolean isJasSource;
        final int temp;
        final int apparentTemp;
        final int weatherCode;
        final String condition;
        final String icon;
        final int highTemp;
        final int lowTemp;
        final int humidity;
        final int windSpeed;
        final int uvIndex;
        final int rainChanceToday;
        final String sunrise;
        final String sunset;
        final int aqi;
        final String aqiStatus;
        final int aqiColor;
        final String aqiParam;
        final double pm25;
        final double pm10;
        final String updatedTime;
        final List<HourlyItem> hourly;
        final List<DailyItem> daily;

        FullWeather(String location, String stationId, String stationPlace, boolean isJasSource,
                    int temp, int apparentTemp, int weatherCode, String condition, String icon,
                    int highTemp, int lowTemp, int humidity, int windSpeed, int uvIndex,
                    int rainChanceToday, String sunrise, String sunset, int aqi,
                    String aqiStatus, int aqiColor, String aqiParam, double pm25, double pm10,
                    String updatedTime, List<HourlyItem> hourly, List<DailyItem> daily) {
            this.location = location;
            this.stationId = stationId;
            this.stationPlace = stationPlace;
            this.isJasSource = isJasSource;
            this.temp = temp;
            this.apparentTemp = apparentTemp;
            this.weatherCode = weatherCode;
            this.condition = condition;
            this.icon = icon;
            this.highTemp = highTemp;
            this.lowTemp = lowTemp;
            this.humidity = humidity;
            this.windSpeed = windSpeed;
            this.uvIndex = uvIndex;
            this.rainChanceToday = rainChanceToday;
            this.sunrise = sunrise;
            this.sunset = sunset;
            this.aqi = aqi;
            this.aqiStatus = aqiStatus;
            this.aqiColor = aqiColor;
            this.aqiParam = aqiParam;
            this.pm25 = pm25;
            this.pm10 = pm10;
            this.updatedTime = updatedTime;
            this.hourly = hourly;
            this.daily = daily;
        }
    }

    private WeatherAirQuality() {
    }

    /** Forces an immediate background sync regardless of cache freshness. */
    static void sync(Context context, Runnable onUpdated) {
        syncInternal(context, onUpdated, true);
    }

    /** Fetches weather and air quality in background if due (30-min cache). */
    static void syncIfDue(Context context, Runnable onUpdated) {
        syncInternal(context, onUpdated, false);
    }

    private static void syncInternal(Context context, Runnable onUpdated, boolean force) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                long last = prefs.getLong(KEY_LAST_UPDATE, 0);
                long now = System.currentTimeMillis();
                if (!force && now - last < REFRESH_INTERVAL_MS && prefs.contains(KEY_WEATHER_JSON)) {
                    return; // Cache is still fresh
                }

                double lat = AppLocationManager.getLatitude(context);
                double lon = AppLocationManager.getLongitude(context);
                String stationId = AppLocationManager.getStationId(context);
                String defaultPlace = AppLocationManager.getStationPlace(context);

                // 1. Fetch Complete Weather Forecast (Open-Meteo)
                String weatherUrl = String.format(Locale.ROOT,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&hourly=temperature_2m,precipitation_probability,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max&timezone=auto&forecast_days=7",
                        lat, lon);
                JSONObject wJson = fetchJson(weatherUrl);

                int temp = 29;
                int code = 2; // Partly cloudy
                if (wJson != null && wJson.has("current")) {
                    JSONObject curr = wJson.getJSONObject("current");
                    temp = (int) Math.round(curr.optDouble("temperature_2m", 29.0));
                    code = curr.optInt("weather_code", 2);
                }

                // 2. Fetch Official Air Quality from Jabatan Alam Sekitar Malaysia (APIMS)
                String jasUrl = "https://eqms.doe.gov.my/api3/publicportalapims/currentapicurrentlocation?stationid=" + stationId;
                JSONObject jasJson = stationId.startsWith("CA") ? fetchJson(jasUrl) : null;

                boolean isMalay = true;
                int aqi = 50;
                String aqiStatus = "Sedang";
                String aqiParam = "PM2.5";
                String aqiPlace = defaultPlace;
                String aqiDateTime = "";
                boolean isJas = false;

                if (jasJson != null && jasJson.has("data")) {
                    JSONArray arr = jasJson.optJSONArray("data");
                    if (arr != null && arr.length() > 0) {
                        JSONObject st = arr.getJSONObject(0);
                        aqi = st.optInt("API", 50);
                        aqiStatus = st.optString("CLASS", getAqiStatus(aqi, isMalay));
                        aqiParam = st.optString("PARAM_SELECTED", "PM2.5");
                        aqiPlace = st.optString("PLACE", defaultPlace);
                        aqiDateTime = st.optString("DATETIME", "");
                        isJas = true;
                        Log.i(TAG, "Official JAS Malaysia APIMS sync successful for " + stationId + ": IPU=" + aqi + " (" + aqiStatus + ")");
                    }
                }

                // Fallback to Open-Meteo Air Quality if JAS Malaysia endpoint is temporarily down
                JSONObject fallbackAqiJson = null;
                if (!isJas) {
                    Log.w(TAG, "JAS Malaysia unreachable, falling back to Open-Meteo Air Quality");
                    String aqiUrl = String.format(Locale.ROOT,
                            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=pm2_5,pm10,us_aqi",
                            lat, lon);
                    fallbackAqiJson = fetchJson(aqiUrl);
                    if (fallbackAqiJson != null && fallbackAqiJson.has("current")) {
                        JSONObject curr = fallbackAqiJson.getJSONObject("current");
                        aqi = curr.optInt("us_aqi", 50);
                        double pm25 = curr.optDouble("pm2_5", 15.0);
                        if (aqi <= 0 && pm25 > 0) {
                            aqi = (int) Math.round(pm25 * 2.1);
                        }
                        aqiStatus = getAqiStatus(aqi, isMalay);
                    }
                }

                SharedPreferences.Editor edit = prefs.edit();
                if (wJson != null) edit.putString(KEY_WEATHER_JSON, wJson.toString());
                if (fallbackAqiJson != null) edit.putString(KEY_AQI_JSON, fallbackAqiJson.toString());
                edit.putInt(KEY_TEMP, temp)
                    .putInt(KEY_CODE, code)
                    .putInt(KEY_AQI, aqi)
                    .putString(KEY_AQI_STATUS, aqiStatus)
                    .putString(KEY_AQI_PARAM, aqiParam)
                    .putString(KEY_AQI_STATION_PLACE, aqiPlace)
                    .putString(KEY_AQI_DATETIME, aqiDateTime)
                    .putBoolean(KEY_AQI_IS_JAS, isJas)
                    .putLong(KEY_LAST_UPDATE, now)
                    .apply();

                Log.i(TAG, "Full weather & AQI synced successfully (JAS=" + isJas + ", IPU=" + aqi + ")");

                if (onUpdated != null) {
                    new Handler(Looper.getMainLooper()).post(onUpdated);
                }
            } catch (Exception e) {
                Log.w(TAG, "Weather/AQI sync error: " + e.getMessage());
            }
        }, "weather-aqi-sync").start();
    }

    /** Formats a clean Spannable UI string for the Home screen: "⛅ 29° Cerah  •  AQI 50 Baik" */
    static CharSequence getFormatted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int temp = prefs.getInt(KEY_TEMP, 29);
        int code = prefs.getInt(KEY_CODE, 2);
        int aqi = prefs.getInt(KEY_AQI, 50);

        boolean isMalay = true;
        boolean isNeu = ThemeManager.isNeumorphism(context);

        String icon = getWeatherIcon(code);
        String condition = getWeatherCondition(code, isMalay);

        String aqiStatus = prefs.getString(KEY_AQI_STATUS, getAqiStatus(aqi, isMalay));
        aqiStatus = translateAqiStatus(aqiStatus);
        int aqiColor = getAqiColor(aqi, isNeu);

        SpannableStringBuilder sb = new SpannableStringBuilder();

        // Weather: e.g. "⛅ 29° Cerah" — readable slate/silver-grey across all themes
        int weatherColor = isNeu ? ThemeManager.neu(0xFF3A414A) : 0xFFD6DCE5;
        int dotColor = isNeu ? ThemeManager.neu(0xFF68707C) : 0x99CBD5E1;

        int startWeather = sb.length();
        sb.append(icon).append(" ").append(String.valueOf(temp)).append("° ").append(condition);
        sb.setSpan(new ForegroundColorSpan(weatherColor), startWeather, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), startWeather, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Separator: "  •  "
        int startDot = sb.length();
        sb.append("  •  ");
        sb.setSpan(new ForegroundColorSpan(dotColor), startDot, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Air Quality: "AQI 112 Tidak Sehat"
        int startAqi = sb.length();
        String prefix = "AQI ";
        sb.append(prefix).append(String.valueOf(aqi)).append(" ").append(aqiStatus);
        sb.setSpan(new ForegroundColorSpan(aqiColor), startAqi, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), startAqi, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return sb;
    }

    /** Returns full weather model for WeatherActivity dashboard. */
    static FullWeather getFullWeather(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String wStr = prefs.getString(KEY_WEATHER_JSON, null);
        String aStr = prefs.getString(KEY_AQI_JSON, null);

        int temp = prefs.getInt(KEY_TEMP, 29);
        int apparentTemp = temp + 3;
        int code = prefs.getInt(KEY_CODE, 2);
        int highTemp = temp + 3;
        int lowTemp = temp - 4;
        int humidity = 78;
        int windSpeed = 8;
        int uvIndex = 6;
        int rainChanceToday = 60;
        String sunrise = "6:54 AM";
        String sunset = "6:58 PM";

        List<HourlyItem> hourly = new ArrayList<>();
        List<DailyItem> daily = new ArrayList<>();

        if (wStr != null) {
            try {
                JSONObject wJson = new JSONObject(wStr);

                if (wJson.has("current")) {
                    JSONObject curr = wJson.getJSONObject("current");
                    temp = (int) Math.round(curr.optDouble("temperature_2m", temp));
                    apparentTemp = (int) Math.round(curr.optDouble("apparent_temperature", apparentTemp));
                    code = curr.optInt("weather_code", code);
                    humidity = (int) Math.round(curr.optDouble("relative_humidity_2m", humidity));
                    windSpeed = (int) Math.round(curr.optDouble("wind_speed_10m", windSpeed));
                }

                if (wJson.has("daily")) {
                    JSONObject d = wJson.getJSONObject("daily");
                    JSONArray maxTemps = d.optJSONArray("temperature_2m_max");
                    JSONArray minTemps = d.optJSONArray("temperature_2m_min");
                    JSONArray rains = d.optJSONArray("precipitation_probability_max");
                    JSONArray uvs = d.optJSONArray("uv_index_max");
                    JSONArray sunrises = d.optJSONArray("sunrise");
                    JSONArray sunsets = d.optJSONArray("sunset");
                    JSONArray days = d.optJSONArray("time");
                    JSONArray dailyCodes = d.optJSONArray("weather_code");

                    if (maxTemps != null && maxTemps.length() > 0) highTemp = (int) Math.round(maxTemps.optDouble(0, highTemp));
                    if (minTemps != null && minTemps.length() > 0) lowTemp = (int) Math.round(minTemps.optDouble(0, lowTemp));
                    if (rains != null && rains.length() > 0) rainChanceToday = rains.optInt(0, rainChanceToday);
                    if (uvs != null && uvs.length() > 0) uvIndex = (int) Math.round(uvs.optDouble(0, uvIndex));
                    if (sunrises != null && sunrises.length() > 0) sunrise = formatSunTime(sunrises.optString(0, sunrise));
                    if (sunsets != null && sunsets.length() > 0) sunset = formatSunTime(sunsets.optString(0, sunset));

                    // Parse 7 days
                    if (days != null) {
                        int len = Math.min(7, days.length());
                        for (int i = 0; i < len; i++) {
                            String dayStr = days.optString(i, "");
                            String dayLabel = i == 0 ? "Today" : formatDayName(dayStr);
                            int c = dailyCodes != null ? dailyCodes.optInt(i, 2) : 2;
                            int mx = maxTemps != null ? (int) Math.round(maxTemps.optDouble(i, 31.0)) : 31;
                            int mn = minTemps != null ? (int) Math.round(minTemps.optDouble(i, 24.0)) : 24;
                            int r = rains != null ? rains.optInt(i, 50) : 50;
                            daily.add(new DailyItem(dayLabel, getWeatherIcon(c), mx, mn, r));
                        }
                    }
                }

                if (wJson.has("hourly")) {
                    JSONObject h = wJson.getJSONObject("hourly");
                    JSONArray times = h.optJSONArray("time");
                    JSONArray temps = h.optJSONArray("temperature_2m");
                    JSONArray rains = h.optJSONArray("precipitation_probability");
                    JSONArray codes = h.optJSONArray("weather_code");

                    if (times != null && temps != null) {
                        Calendar nowCal = Calendar.getInstance();
                        int currentHour = nowCal.get(Calendar.HOUR_OF_DAY);
                        int count = 0;
                        for (int i = 0; i < times.length() && count < 24; i++) {
                            String tStr = times.optString(i, "");
                            if (tStr.contains("T")) {
                                int hour = Integer.parseInt(tStr.substring(tStr.indexOf('T') + 1, tStr.indexOf(':')));
                                if (count > 0 || hour >= currentHour) {
                                    String label = count == 0 ? "Sekarang" : formatHourLabel(hour);
                                    int t = (int) Math.round(temps.optDouble(i, 28.0));
                                    int c = codes != null ? codes.optInt(i, 2) : 2;
                                    int r = rains != null ? rains.optInt(i, 30) : 30;
                                    hourly.add(new HourlyItem(label, getWeatherIcon(c), t, r, c, hour));
                                    count++;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed parsing weather json: " + e.getMessage());
            }
        }

        // Fill fallback if empty
        if (hourly.isEmpty()) {
            hourly.add(new HourlyItem("Sekarang", "⛅", temp, 40, 2, 14));
            hourly.add(new HourlyItem("15:00", "⛅", temp, 35, 2, 15));
            hourly.add(new HourlyItem("18:00", "☁", temp - 1, 30, 2, 18));
            hourly.add(new HourlyItem("21:00", "☁", temp - 2, 25, 0, 21));
            hourly.add(new HourlyItem("00:00", "☁", temp - 2, 20, 0, 0));
        }

        if (daily.isEmpty()) {
            daily.add(new DailyItem("Today", "⛅", highTemp, lowTemp, 60));
            daily.add(new DailyItem("Sat", "🌧", 31, 24, 75));
            daily.add(new DailyItem("Sun", "⛅", 32, 24, 50));
        }

        // Air Quality
        int aqi = prefs.getInt(KEY_AQI, 50);
        boolean isJas = prefs.getBoolean(KEY_AQI_IS_JAS, true);
        String aqiStatus = prefs.getString(KEY_AQI_STATUS, "Sedang");
        String aqiParam = prefs.getString(KEY_AQI_PARAM, "PM2.5");
        String stationPlace = prefs.getString(KEY_AQI_STATION_PLACE, AppLocationManager.getStationPlace(context));
        String stationId = AppLocationManager.getStationId(context);
        String locationName = AppLocationManager.getLocationName(context).toUpperCase(Locale.ROOT);

        double pm25 = aqi * 0.45;
        double pm10 = aqi * 0.55;

        boolean isMalay = true;
        aqiStatus = translateAqiStatus(aqiStatus);
        int aqiColor = getAqiColor(aqi, ThemeManager.isNeumorphism(context));

        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        String updated = sdf.format(new Date());

        return new FullWeather(locationName, stationId, stationPlace, isJas, temp, apparentTemp,
                code, getWeatherCondition(code, isMalay), getWeatherIcon(code),
                highTemp, lowTemp, humidity, windSpeed, uvIndex, rainChanceToday,
                sunrise, sunset, aqi, aqiStatus, aqiColor, aqiParam, pm25, pm10, updated, hourly, daily);
    }

    private static String translateAqiStatus(String status) {
        if (status == null) return "Sedang";
        String s = status.toLowerCase(Locale.ROOT);
        if (s.contains("good") || s.contains("baik")) return "Baik";
        if (s.contains("moderate") || s.contains("sederhana") || s.contains("sedang")) return "Sedang";
        if (s.contains("very unhealthy") || s.contains("sangat")) return "Sangat Tidak Sehat";
        if (s.contains("unhealthy") || s.contains("tidak sihat") || s.contains("tidak sehat")) return "Tidak Sehat";
        if (s.contains("hazardous") || s.contains("bahaya")) return "Berbahaya";
        return status;
    }

    private static String formatSunTime(String isoTime) {
        try {
            if (isoTime.contains("T")) {
                String[] parts = isoTime.substring(isoTime.indexOf('T') + 1).split(":");
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                String ampm = h < 12 ? "AM" : "PM";
                int hh = h % 12 == 0 ? 12 : h % 12;
                return String.format(Locale.ROOT, "%d:%02d %s", hh, m, ampm);
            }
        } catch (Exception ignored) {
        }
        return "6:54 AM";
    }

    private static String formatDayName(String ymd) {
        try {
            SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            Date d = inFmt.parse(ymd);
            if (d != null) {
                SimpleDateFormat outFmt = new SimpleDateFormat("EEE", Locale.getDefault());
                return outFmt.format(d);
            }
        } catch (Exception ignored) {
        }
        return ymd;
    }

    private static String formatHourLabel(int hour24) {
        return String.format(Locale.ROOT, "%02d:00", hour24);
    }

    private static String getWeatherCondition(int code, boolean isMalay) {
        switch (code) {
            case 0:
                return isMalay ? "Cerah" : "Clear sky";
            case 1:
            case 2:
                return isMalay ? "Cerah Berawan" : "Partly cloudy";
            case 3:
                return isMalay ? "Berawan" : "Overcast";
            case 45:
            case 48:
                return isMalay ? "Berkabut" : "Fog / Haze";
            case 51:
            case 53:
            case 55:
                return isMalay ? "Gerimis" : "Light drizzle";
            case 61:
            case 63:
            case 65:
                return isMalay ? "Hujan" : "Rain";
            case 80:
            case 81:
            case 82:
                return isMalay ? "Hujan Lebat" : "Heavy rain";
            case 95:
            case 96:
            case 99:
                return isMalay ? "Badai Petir" : "Thunderstorm";
            default:
                return isMalay ? "Cerah" : "Fair";
        }
    }

    private static String getWeatherIcon(int code) {
        switch (code) {
            case 0:
                return "☀️";
            case 1:
            case 2:
                return "⛅";
            case 3:
                return "☁️";
            case 45:
            case 48:
                return "🌫️";
            case 51:
            case 53:
            case 55:
                return "🌦️";
            case 61:
            case 63:
            case 65:
                return "🌧️";
            case 80:
            case 81:
            case 82:
                return "⛈️";
            case 95:
            case 96:
            case 99:
                return "⚡";
            default:
                return "⛅";
        }
    }

    /**
     * Draws a crisp monochrome vector weather condition drawable matching the reference design:
     * sun + cloud (partly cloudy day), crescent + cloud (partly cloudy night), crescent moon (clear night),
     * sun with rays (clear day), cloud, rain cloud, or storm cloud.
     */
    static android.graphics.drawable.Drawable createConditionDrawable(
            final int code, final int hour24, final int color, final float widthDp, final float heightDp, final float density) {
        return createConditionDrawable(code, hour24, color, widthDp, heightDp, density, false);
    }

    static android.graphics.drawable.Drawable createConditionDrawable(
            final int code, final int hour24, final int color, final float widthDp, final float heightDp, final float density, final boolean flushLeft) {
        final int wPx = Math.max(1, Math.round(widthDp * density));
        final int hPx = Math.max(1, Math.round(heightDp * density));
        return new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint fillPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final android.graphics.Paint strokePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final android.graphics.Path cloudPath = new android.graphics.Path();

            {
                fillPaint.setStyle(android.graphics.Paint.Style.FILL);
                fillPaint.setColor(color);
                strokePaint.setStyle(android.graphics.Paint.Style.STROKE);
                strokePaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                strokePaint.setColor(color);
            }

            @Override
            public int getIntrinsicWidth() {
                return wPx;
            }

            @Override
            public int getIntrinsicHeight() {
                return hPx;
            }

            @Override
            public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect b = getBounds();
                float w = b.width() > 0 ? b.width() : wPx;
                float h = b.height() > 0 ? b.height() : hPx;
                canvas.save();
                canvas.translate(b.left, b.top);

                boolean isNight = hour24 >= 18 || hour24 < 6;
                strokePaint.setStrokeWidth(Math.max(1.4f * density, h * 0.068f));
                float cLeft = flushLeft ? 0f : w * 0.08f;

                if (code == 0) {
                    if (isNight) {
                        float r = Math.min(w, h) * 0.36f;
                        drawCrescent(canvas, flushLeft ? r : w * 0.52f, h * 0.50f, r);
                    } else {
                        float rOut = Math.min(w, h) * 0.39f;
                        drawSun(canvas, flushLeft ? rOut : w * 0.50f, h * 0.50f, Math.min(w, h) * 0.23f, rOut);
                    }
                } else if (code == 1 || code == 2) {
                    if (isNight) {
                        drawCrescent(canvas, cLeft + w * 0.58f, h * 0.36f, Math.min(w, h) * 0.22f);
                    } else {
                        drawSun(canvas, cLeft + w * 0.56f, h * 0.38f, Math.min(w, h) * 0.16f, Math.min(w, h) * 0.29f);
                    }
                    drawCloud(canvas, cLeft, h * 0.30f, w * 0.80f, h * 0.64f);
                } else if (code >= 51) {
                    drawCloud(canvas, cLeft, h * 0.14f, w * 0.86f, h * 0.56f);
                    // Rain drops below cloud
                    float y1 = h * 0.75f;
                    float y2 = h * 0.90f;
                    canvas.drawLine(cLeft + w * 0.24f, y1, cLeft + w * 0.19f, y2, strokePaint);
                    canvas.drawLine(cLeft + w * 0.43f, y1, cLeft + w * 0.38f, y2, strokePaint);
                    canvas.drawLine(cLeft + w * 0.62f, y1, cLeft + w * 0.57f, y2, strokePaint);
                } else {
                    drawCloud(canvas, cLeft, h * 0.24f, w * 0.86f, h * 0.64f);
                }
                canvas.restore();
            }

            private void drawSun(android.graphics.Canvas c, float cx, float cy, float rInner, float rOuter) {
                c.drawCircle(cx, cy, rInner, fillPaint);
                float rStart = rInner * 1.32f;
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4.0;
                    float cos = (float) Math.cos(ang);
                    float sin = (float) Math.sin(ang);
                    c.drawLine(cx + cos * rStart, cy + sin * rStart, cx + cos * rOuter, cy + sin * rOuter, strokePaint);
                }
            }

            private void drawCrescent(android.graphics.Canvas c, float cx, float cy, float r) {
                android.graphics.Path p = new android.graphics.Path();
                p.addCircle(cx, cy, r, android.graphics.Path.Direction.CW);
                android.graphics.Path cut = new android.graphics.Path();
                cut.addCircle(cx + r * 0.45f, cy - r * 0.28f, r * 0.85f, android.graphics.Path.Direction.CW);
                p.op(cut, android.graphics.Path.Op.DIFFERENCE);
                c.drawPath(p, fillPaint);
            }

            private void drawCloud(android.graphics.Canvas c, float left, float top, float cw, float ch) {
                cloudPath.reset();
                float bottom = top + ch;
                float rLeft = ch * 0.34f;
                float rRight = ch * 0.30f;
                float rTop = ch * 0.44f;

                android.graphics.Path base = new android.graphics.Path();
                base.addRoundRect(new android.graphics.RectF(left, bottom - ch * 0.56f, left + cw, bottom),
                        rLeft, rLeft, android.graphics.Path.Direction.CW);
                base.addCircle(left + cw * 0.42f, top + rTop, rTop, android.graphics.Path.Direction.CW);
                base.addCircle(left + cw * 0.70f, top + ch * 0.56f, rRight, android.graphics.Path.Direction.CW);
                c.drawPath(base, fillPaint);
            }

            @Override
            public void setAlpha(int alpha) {
                fillPaint.setAlpha(alpha);
                strokePaint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter colorFilter) {
                fillPaint.setColorFilter(colorFilter);
                strokePaint.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    private static String getAqiStatus(int aqi, boolean isMalay) {
        if (aqi <= 50) return isMalay ? "Baik" : "Good";
        if (aqi <= 100) return isMalay ? "Sedang" : "Moderate";
        if (aqi <= 200) return isMalay ? "Tidak Sehat" : "Unhealthy";
        if (aqi <= 300) return isMalay ? "Sangat Tidak Sehat" : "Very Unhealthy";
        return isMalay ? "Berbahaya" : "Hazardous";
    }

    public static int getAqiColor(int aqi) {
        return getAqiColor(aqi, false);
    }

    public static int getAqiColor(int aqi, boolean isNeu) {
        if (isNeu) {
            if (aqi <= 50) return ThemeManager.neu(0xFF15803D);  // Deep Emerald Green (Baik)
            if (aqi <= 100) return ThemeManager.neu(0xFF0284C7); // Deep Sky Blue (Sedang)
            if (aqi <= 200) return ThemeManager.neu(0xFFD97706); // Warm Amber-Orange (Tidak Sehat)
            if (aqi <= 300) return ThemeManager.neu(0xFFDC2626); // Crimson Red (Sangat Tidak Sehat)
            return ThemeManager.neu(0xFF7F1D1D);                 // Dark Maroon (Berbahaya)
        }
        if (aqi <= 50) return 0xFF4ADE80;  // Green (Baik)
        if (aqi <= 100) return 0xFF38BDF8; // Sky Blue (Sederhana)
        if (aqi <= 200) return 0xFFFB923C; // Orange (Tidak Sihat)
        if (aqi <= 300) return 0xFFEF4444; // Red (Sangat Tidak Sihat)
        return 0xFF991B1B;                 // Maroon (Berbahaya)
    }

    private static JSONObject fetchJson(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return new JSONObject(sb.toString());
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchJson failed for " + urlStr + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}
