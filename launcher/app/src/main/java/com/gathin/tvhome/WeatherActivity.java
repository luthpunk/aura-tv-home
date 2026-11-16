package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Fullscreen Apple-style animated weather experience matching aqmangge/glass-tv-home,
 * synchronized with AppLocationManager & CitySearchDialog for Indonesian and global cities.
 */
public class WeatherActivity extends Activity {

    private static final String[] HOURLY_LABELS = {
            "00:00", "03:00", "06:00", "09:00", "12:00", "15:00", "18:00", "21:00"
    };

    private static final class WeatherState {
        final WeatherSkyView.Condition condition;
        final String title, icon;
        final int temp, feelsLike, rainChance, pressure, max, min;
        final int[] hourly;

        WeatherState(WeatherSkyView.Condition condition, String title, String icon,
                     int temp, int feelsLike, int rainChance, int pressure,
                     int max, int min, int[] hourly) {
            this.condition = condition;
            this.title = title;
            this.icon = icon;
            this.temp = temp;
            this.feelsLike = feelsLike;
            this.rainChance = rainChance;
            this.pressure = pressure;
            this.max = max;
            this.min = min;
            this.hourly = hourly;
        }
    }

    private static final class DayForecast {
        final String name, icon, conditionTitle;
        final int high, low, feelsLike, rainChance, pressure;
        final WeatherSkyView.Condition condition;
        final int[] hourlyTemps, hourlyPrecip, hourlyWinds;

        DayForecast(String name, int high, int low, String icon, String conditionTitle,
                    WeatherSkyView.Condition condition, int feelsLike, int rainChance, int pressure,
                    int[] hourlyTemps, int[] hourlyPrecip, int[] hourlyWinds) {
            this.name = name;
            this.high = high;
            this.low = low;
            this.icon = icon;
            this.conditionTitle = conditionTitle;
            this.condition = condition;
            this.feelsLike = feelsLike;
            this.rainChance = rainChance;
            this.pressure = pressure;
            this.hourlyTemps = hourlyTemps;
            this.hourlyPrecip = hourlyPrecip;
            this.hourlyWinds = hourlyWinds;
        }
    }

    private List<DayForecast> days = createDefaultDays();
    private int selectedDayIndex = 0;
    private int activeChip = 0; // 0 = Suhu, 1 = Curah Hujan, 2 = Angin

    private WeatherSkyView sky;
    private HourlyChartView chart;
    private TextView tvIcon, tvLocation, tvTemp, tvCondition, tvMaxMin;
    private TextView tvFeels, tvRain, tvPressure;
    private LinearLayout dayRow;
    private View headerBlock;
    private TextView chipTemp, chipPrecip, chipWind;

    private WeatherState current;
    private int shownTemp = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);

        sky = findViewById(R.id.skyView);
        chart = findViewById(R.id.hourlyChart);
        tvIcon = findViewById(R.id.tvIcon);
        tvLocation = findViewById(R.id.tvLocation);
        tvTemp = findViewById(R.id.tvTemp);
        tvCondition = findViewById(R.id.tvCondition);
        tvMaxMin = findViewById(R.id.tvMaxMin);
        tvFeels = findViewById(R.id.tvFeels);
        tvRain = findViewById(R.id.tvRain);
        tvPressure = findViewById(R.id.tvPressure);
        dayRow = findViewById(R.id.dayRow);
        headerBlock = findViewById(R.id.headerBlock);

        chipTemp = findViewById(R.id.chipTemp);
        chipPrecip = findViewById(R.id.chipPrecip);
        chipWind = findViewById(R.id.chipWind);

        updateLocationLabel();

        if (headerBlock != null) {
            headerBlock.setFocusable(true);
            headerBlock.setFocusableInTouchMode(true);
            headerBlock.setOnFocusChangeListener((v, hasFocus) -> {
                float scale = hasFocus ? 1.04f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(160L)
                        .setInterpolator(new DecelerateInterpolator()).start();
            });
            headerBlock.setOnClickListener(v -> {
                SoundEffects.playSelect();
                CitySearchDialog.show(this, () -> {
                    updateLocationLabel();
                    PrayerTimes.sync(this, null);
                    WeatherAirQuality.sync(this, this::fetchLiveWeather);
                    fetchLiveWeather();
                });
            });
        }

        setupChips();
        buildDayCards();

        current = new WeatherState(
                WeatherSkyView.Condition.SUNNY, "Cerah Berawan", "⛅",
                29, 32, 20, 1010, 32, 25, days.get(0).hourlyTemps);
        loadFromCacheIfPresent();

        sky.setCondition(current.condition, false);
        applyState(current, current.temp - 4);
        playEntrance();

        if (chipTemp != null) {
            chipTemp.post(() -> chipTemp.requestFocus());
        }

        fetchLiveWeather();
    }

    private void updateLocationLabel() {
        String loc = AppLocationManager.getLocationName(this);
        if (loc == null || loc.trim().isEmpty()) loc = "Tuban";
        if (tvLocation != null) {
            tvLocation.setText("📍 " + loc.trim() + " ▾");
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            View focused = getCurrentFocus();
            if (focused != null && focused.getId() != R.id.root) {
                focused.performClick();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void applyState(WeatherState s, int fromTemp) {
        ValueAnimator anim = ValueAnimator.ofInt(fromTemp, s.temp);
        anim.setDuration(1200L);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(it -> {
            shownTemp = (int) it.getAnimatedValue();
            tvTemp.setText(shownTemp + "°C");
        });
        anim.start();

        crossFade(tvCondition, s.title);
        crossFade(tvIcon, s.icon);
        crossFade(tvMaxMin, "Maks: " + s.max + "°C  ·  Min: " + s.min + "°C");
        crossFade(tvFeels, s.feelsLike + "°C");
        crossFade(tvRain, s.rainChance + "%");
        crossFade(tvPressure, s.pressure + "hPa");

        updateChartData();
    }

    private void setupChips() {
        TextView[] chips = new TextView[]{chipTemp, chipPrecip, chipWind};
        for (int i = 0; i < chips.length; i++) {
            final int idx = i;
            TextView c = chips[i];
            if (c == null) continue;
            c.setFocusable(true);
            c.setFocusableInTouchMode(true);
            c.setOnFocusChangeListener((v, hasFocus) -> {
                float scale = hasFocus ? 1.04f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(140L)
                        .setInterpolator(new DecelerateInterpolator()).start();
                updateChipsAppearance();
            });
            c.setOnClickListener(v -> {
                SoundEffects.playSelect();
                activeChip = idx;
                updateChipsAppearance();
                updateChartData();
            });
        }
        updateChipsAppearance();
    }

    private void updateChipsAppearance() {
        TextView[] chips = new TextView[]{chipTemp, chipPrecip, chipWind};
        for (int i = 0; i < chips.length; i++) {
            TextView c = chips[i];
            if (c == null) continue;
            boolean isSelected = (i == activeChip);
            boolean hasFocus = c.hasFocus();
            c.setBackgroundResource(
                    isSelected ? R.drawable.chip_selected
                            : (hasFocus ? R.drawable.chip_focused : R.drawable.chip));
            c.setTextColor((isSelected || hasFocus) ? 0xFFFFFFFF : 0xB3FFFFFF);
        }
    }

    private void updateChartData() {
        DayForecast day = (selectedDayIndex >= 0 && selectedDayIndex < days.size())
                ? days.get(selectedDayIndex) : days.get(0);
        if (activeChip == 0) {
            chart.setData(day.hourlyTemps, "°", HOURLY_LABELS);
        } else if (activeChip == 1) {
            chart.setData(day.hourlyPrecip, "%", HOURLY_LABELS);
        } else {
            chart.setData(day.hourlyWinds, " km/j", HOURLY_LABELS);
        }
        chart.animateIn(120L);
    }

    private void buildDayCards() {
        dayRow.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < days.size(); i++) {
            final int idx = i;
            DayForecast d = days.get(i);
            View card = inflater.inflate(R.layout.item_day, dayRow, false);
            card.setId(View.generateViewId());
            ((TextView) card.findViewById(R.id.tvDayName)).setText(d.name);
            ((TextView) card.findViewById(R.id.tvHigh)).setText("↑ " + d.high + "°C");
            ((TextView) card.findViewById(R.id.tvLow)).setText("↓ " + d.low + "°C");
            ((TextView) card.findViewById(R.id.tvDayIcon)).setText(d.icon);

            updateCardAppearance(card, i == selectedDayIndex, card.hasFocus());

            card.setFocusable(true);
            card.setFocusableInTouchMode(true);
            card.setOnFocusChangeListener((v, hasFocus) -> {
                float scale = hasFocus ? 1.03f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(140L)
                        .setInterpolator(new DecelerateInterpolator()).start();
                updateCardAppearance(card, idx == selectedDayIndex, hasFocus);
            });
            card.setOnClickListener(v -> {
                SoundEffects.playSelect();
                selectDay(idx);
            });

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
            lp.weight = 1f;
            lp.width = 0;
            lp.height = LinearLayout.LayoutParams.MATCH_PARENT;
            if (i > 0) lp.setMarginStart(dp(14f));
            card.setLayoutParams(lp);
            dayRow.addView(card);
        }
        wireDayRowFocus();
    }

    private void wireDayRowFocus() {
        for (int i = 0; i < dayRow.getChildCount(); i++) {
            View child = dayRow.getChildAt(i);
            if (i <= 1) child.setNextFocusUpId(R.id.chipTemp);
            else if (i == 2) child.setNextFocusUpId(R.id.chipPrecip);
            else child.setNextFocusUpId(R.id.chipWind);
            if (i > 0) child.setNextFocusLeftId(dayRow.getChildAt(i - 1).getId());
            if (i < dayRow.getChildCount() - 1) child.setNextFocusRightId(dayRow.getChildAt(i + 1).getId());
        }
    }

    private void selectDay(int index) {
        if (index < 0 || index >= days.size()) return;
        selectedDayIndex = index;
        DayForecast day = days.get(index);

        for (int i = 0; i < dayRow.getChildCount(); i++) {
            View card = dayRow.getChildAt(i);
            updateCardAppearance(card, i == selectedDayIndex, card.hasFocus());
        }

        if (sky.getCurrentCondition() != day.condition) {
            sky.setCondition(day.condition, true, 2000L);
        }

        int targetTemp = (day.high + day.low) / 2;
        ValueAnimator anim = ValueAnimator.ofInt(shownTemp, targetTemp);
        anim.setDuration(1000L);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(it -> {
            shownTemp = (int) it.getAnimatedValue();
            tvTemp.setText(shownTemp + "°C");
        });
        anim.start();

        crossFade(tvCondition, day.conditionTitle);
        crossFade(tvIcon, day.icon);
        crossFade(tvMaxMin, "Maks: " + day.high + "°C  ·  Min: " + day.low + "°C");
        crossFade(tvFeels, day.feelsLike + "°C");
        crossFade(tvRain, day.rainChance + "%");
        crossFade(tvPressure, day.pressure + "hPa");

        updateChartData();
    }

    private void updateCardAppearance(View card, boolean isSelected, boolean hasFocus) {
        int bg = isSelected ? R.drawable.glass_card_selected
                : (hasFocus ? R.drawable.glass_card_focused : R.drawable.glass_card);
        card.setBackgroundResource(bg);
    }

    private void crossFade(final TextView tv, final String newText) {
        if (tv == null || newText == null || newText.contentEquals(tv.getText())) return;
        tv.animate().alpha(0f).translationY(-dpF(6f)).setDuration(220L).withEndAction(() -> {
            tv.setText(newText);
            tv.setTranslationY(dpF(6f));
            tv.animate().alpha(1f).translationY(0f).setDuration(350L).start();
        }).start();
    }

    private void playEntrance() {
        List<View> views = new ArrayList<>();
        views.add(findViewById(R.id.headerBlock));
        views.add(findViewById(R.id.statsBlock));
        views.add(findViewById(R.id.hourlyCard));
        for (int i = 0; i < dayRow.getChildCount(); i++) {
            views.add(dayRow.getChildAt(i));
        }

        for (int i = 0; i < views.size(); i++) {
            View v = views.get(i);
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(dpF(30f));
            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(120L + i * 75L)
                    .setDuration(600L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void loadFromCacheIfPresent() {
        try {
            WeatherAirQuality.FullWeather cached = WeatherAirQuality.getFullWeather(this);
            if (cached == null) return;
            WeatherSkyView.Condition cond = mapCondition(cached.weatherCode);
            int[] hArray = new int[8];
            if (cached.hourly != null && !cached.hourly.isEmpty()) {
                int step = Math.max(1, cached.hourly.size() / 8);
                for (int i = 0; i < 8; i++) {
                    hArray[i] = cached.hourly.get(Math.min(i * step, cached.hourly.size() - 1)).temp;
                }
            } else {
                hArray = days.get(0).hourlyTemps;
            }
            int p = 1010;
            String title = cached.condition != null && !cached.condition.isEmpty()
                    ? cached.condition : conditionTitle(cached.weatherCode);
            current = new WeatherState(
                    cond,
                    title,
                    conditionEmoji(cached.weatherCode),
                    cached.temp,
                    cached.apparentTemp,
                    cached.rainChanceToday,
                    p,
                    cached.highTemp,
                    cached.lowTemp,
                    hArray
            );
        } catch (Exception ignored) {
        }
    }

    private void fetchLiveWeather() {
        final double lat = AppLocationManager.getLatitude(this);
        final double lon = AppLocationManager.getLongitude(this);
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = String.format(Locale.ROOT,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,surface_pressure,wind_speed_10m"
                                + "&hourly=temperature_2m,precipitation_probability,wind_speed_10m"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                                + "&timezone=auto&forecast_days=5",
                        lat, lon);
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                if (conn.getResponseCode() != 200) return;

                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }

                JSONObject json = new JSONObject(sb.toString());
                JSONObject curr = json.optJSONObject("current");
                JSONObject daily = json.optJSONObject("daily");
                JSONObject hourlyObj = json.optJSONObject("hourly");
                if (curr == null || daily == null) return;

                int temp = (int) Math.round(curr.optDouble("temperature_2m", 28.0));
                int feels = (int) Math.round(curr.optDouble("apparent_temperature", 31.0));
                int code = curr.optInt("weather_code", 2);
                int pressure = (int) Math.round(curr.optDouble("surface_pressure", 1010.0));
                double precipMm = curr.optDouble("precipitation", 0.0);

                JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
                JSONArray minArr = daily.optJSONArray("temperature_2m_min");
                JSONArray codeArr = daily.optJSONArray("weather_code");
                JSONArray rainArr = daily.optJSONArray("precipitation_probability_max");

                JSONArray hTemps = hourlyObj != null ? hourlyObj.optJSONArray("temperature_2m") : null;
                JSONArray hRains = hourlyObj != null ? hourlyObj.optJSONArray("precipitation_probability") : null;
                JSONArray hWinds = hourlyObj != null ? hourlyObj.optJSONArray("wind_speed_10m") : null;

                SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", new Locale("id", "ID"));
                Calendar cal = Calendar.getInstance();

                final List<DayForecast> newDays = new ArrayList<>();
                int count = Math.min(5, maxArr != null ? maxArr.length() : 0);

                for (int d = 0; d < count; d++) {
                    String dName;
                    if (d == 0) {
                        dName = "Hari Ini";
                    } else {
                        cal.add(Calendar.DAY_OF_YEAR, 1);
                        dName = capitalizeFirst(dayFmt.format(cal.getTime()));
                    }

                    int dMax = (int) Math.round(maxArr.optDouble(d, 31.0));
                    int dMin = (int) Math.round(minArr != null ? minArr.optDouble(d, 24.0) : 24.0);
                    int dCode = codeArr != null ? codeArr.optInt(d, 2) : 2;
                    int dRain = (int) Math.round(rainArr != null ? rainArr.optDouble(d, 30.0) : 30.0);

                    WeatherSkyView.Condition dCond = mapCondition(dCode);
                    String dTitle = conditionTitle(dCode);
                    String dIcon = conditionEmoji(dCode);

                    int[] hT = new int[8];
                    int[] hR = new int[8];
                    int[] hW = new int[8];
                    for (int i = 0; i < 8; i++) {
                        int idx = d * 24 + i * 3;
                        hT[i] = (int) Math.round(hTemps != null ? hTemps.optDouble(idx, (dMax + dMin) / 2.0) : 27.0);
                        hR[i] = (int) Math.round(hRains != null ? hRains.optDouble(idx, dRain) : dRain);
                        hW[i] = (int) Math.round(hWinds != null ? hWinds.optDouble(idx, 12.0) : 12.0);
                    }

                    newDays.add(new DayForecast(
                            dName, dMax, dMin, dIcon, dTitle, dCond,
                            dMax + 2, dRain, pressure, hT, hR, hW));
                }

                if (newDays.isEmpty()) return;
                DayForecast td = newDays.get(0);
                final WeatherState todayState = new WeatherState(
                        mapCondition(code),
                        conditionTitle(code),
                        conditionEmoji(code),
                        temp,
                        feels,
                        precipMm > 0.1 ? Math.max(85, td.rainChance) : td.rainChance,
                        pressure,
                        td.high,
                        td.low,
                        td.hourlyTemps);

                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    days = newDays;
                    selectedDayIndex = 0;
                    buildDayCards();
                    current = todayState;
                    sky.setCondition(current.condition, true, 2000L);
                    applyState(current, shownTemp);
                });
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "weather-screen-fetch").start();
    }

    private static WeatherSkyView.Condition mapCondition(int weatherCode) {
        if (weatherCode >= 95 || (weatherCode >= 51 && weatherCode <= 67) || (weatherCode >= 80 && weatherCode <= 82)) {
            return WeatherSkyView.Condition.STORM;
        }
        return WeatherSkyView.Condition.SUNNY;
    }

    private static String conditionTitle(int code) {
        if (code >= 95) return "Badai Petir";
        if (code >= 80) return "Hujan Deras";
        if (code >= 61) return "Hujan";
        if (code >= 51) return "Gerimis";
        if (code >= 45) return "Berkabut";
        if (code >= 3) return "Berawan";
        if (code >= 1) return "Cerah Berawan";
        return "Cerah";
    }

    private static String conditionEmoji(int code) {
        if (code >= 95) return "⛈️";
        if (code >= 61 || (code >= 80 && code <= 82)) return "🌧️";
        if (code >= 51) return "🌦️";
        if (code >= 3) return "☁️";
        if (code >= 1) return "🌤️";
        return "☀️";
    }

    private static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<DayForecast> createDefaultDays() {
        List<DayForecast> list = new ArrayList<>();
        String[] names = {"Hari Ini", "Besok", "Lusa", "Hari +3", "Hari +4"};
        for (int i = 0; i < names.length; i++) {
            list.add(new DayForecast(
                    names[i], 31, 24,
                    i == 0 ? "⛅" : "🌤️",
                    "Cerah Berawan",
                    WeatherSkyView.Condition.SUNNY,
                    33, 25, 1010,
                    new int[]{25, 25, 26, 28, 31, 31, 29, 26},
                    new int[]{25, 20, 15, 10, 15, 20, 25, 25},
                    new int[]{10, 11, 12, 14, 16, 15, 12, 10}));
        }
        return list;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private float dpF(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
