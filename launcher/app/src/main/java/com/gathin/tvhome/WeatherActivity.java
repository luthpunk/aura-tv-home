package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class WeatherActivity extends Activity {

    private WeatherBackgroundView weatherBgView;
    private TextView weatherLocation;
    private View btnChangeLocation;
    private TextView weatherMainTemp;
    private TextView weatherMainCondition;
    private TextView weatherMainSub;

    private LinearLayout hourlyContainer;
    private LinearLayout dailyContainer;

    // Custom Tiles
    private WindCompassView windCompassView;
    private MoonPhaseView moonPhaseView;
    private SunArcView sunArcView;
    private RadarMapView radarMapView;
    private PressureGaugeView pressureGaugeView;

    private TextView sunsetText;
    private TextView uvValText;
    private TextView uvDescText;
    private TextView feelsLikeVal;

    private TextView aqiValue;
    private TextView aqiStatus;
    private AqiSpectrumBarView aqiBar;
    private TextView aqiStation;

    private TextView humidityValText;
    private TextView weatherFooter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);

        weatherBgView = findViewById(R.id.weatherBgView);
        weatherLocation = findViewById(R.id.weatherLocation);
        btnChangeLocation = findViewById(R.id.btnChangeLocation);
        weatherMainTemp = findViewById(R.id.weatherMainTemp);
        weatherMainCondition = findViewById(R.id.weatherMainCondition);
        weatherMainSub = findViewById(R.id.weatherMainSub);

        hourlyContainer = findViewById(R.id.hourlyContainer);
        dailyContainer = findViewById(R.id.dailyContainer);

        windCompassView = findViewById(R.id.windCompassView);
        moonPhaseView = findViewById(R.id.moonPhaseView);
        sunArcView = findViewById(R.id.sunArcView);
        radarMapView = findViewById(R.id.radarMapView);
        pressureGaugeView = findViewById(R.id.pressureGaugeView);

        sunsetText = findViewById(R.id.sunsetText);
        uvValText = findViewById(R.id.uvValText);
        uvDescText = findViewById(R.id.uvDescText);
        feelsLikeVal = findViewById(R.id.feelsLikeVal);

        aqiValue = findViewById(R.id.aqiValue);
        aqiStatus = findViewById(R.id.aqiStatus);
        aqiBar = findViewById(R.id.aqiBar);
        aqiStation = findViewById(R.id.aqiStation);

        humidityValText = findViewById(R.id.humidityValText);
        weatherFooter = findViewById(R.id.weatherFooter);

        if (btnChangeLocation != null) {
            btnChangeLocation.setOnClickListener(v -> showLocationPickerDialog());
            setupFocusAnimation(btnChangeLocation, 1.03f);
        }
        View btnChangeAtmosphere = findViewById(R.id.btnChangeAtmosphere);
        if (btnChangeAtmosphere != null) {
            btnChangeAtmosphere.setOnClickListener(v -> showWeatherAnimationPicker());
            setupFocusAnimation(btnChangeAtmosphere, 1.03f);
        }

        // Setup Card Focus Animators for TV Remote Navigation on Leaf Cards
        setupCardFocus(findViewById(R.id.tileWind));
        setupCardFocus(findViewById(R.id.tileMoon));
        setupCardFocus(findViewById(R.id.tileSunset));
        setupCardFocus(findViewById(R.id.tileUv));
        setupCardFocus(findViewById(R.id.tileFeelsLike));
        setupCardFocus(findViewById(R.id.tileAqi));
        setupCardFocus(findViewById(R.id.tilePrecip));
        setupCardFocus(findViewById(R.id.tileVisibility));
        setupCardFocus(findViewById(R.id.cardRadar));
        setupCardFocus(findViewById(R.id.tileHumidity));
        setupCardFocus(findViewById(R.id.tilePressure));

        populateUi();

        // Background sync to ensure freshest data
        WeatherAirQuality.syncIfDue(this, this::populateUi);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (weatherBgView != null) {
            weatherBgView.startAnimation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (weatherBgView != null) {
            weatherBgView.stopAnimation();
        }
    }

    private void showLocationPickerDialog() {
        CitySearchDialog.show(this, () -> {
            populateUi();
            WeatherAirQuality.sync(this, this::populateUi);
        });
    }

    private void showWeatherAnimationPicker() {
        SharedPreferences p = getSharedPreferences("app_weather_prefs", MODE_PRIVATE);
        String currentMode = p.getString("atmosphere_mode", "AUTO");

        final String[] modeKeys = new String[]{
                "SUNNY", "RAIN", "THUNDERSTORM", "CLOUDY", "NIGHT", "AUTO"
        };
        final String[] titles = new String[]{
                "☀️ Cerah / Sunny",
                "🌧️ Hujan / Rain",
                "⛈️ Badai Petir / Thunderstorm",
                "⛅ Berawan / Cloudy",
                "🌙 Malam / Night",
                "🔄 Otomatis Sesuai Cuaca Saat Ini"
        };
        final String[] subs = new String[]{
                "Pantulan surya, lens flare & cahaya hangat",
                "Tetesan hujan sinematik & percikan air",
                "Kilat menyambar & atmosfer hujan deras",
                "Awan lembut bergerak di langit",
                "Bintang berkelip & sinar bulan",
                "Ikuti kondisi cuaca aktual di kota terpilih"
        };

        List<MainActivity.ThemedDialogItem> items = new ArrayList<>();
        for (int i = 0; i < modeKeys.length; i++) {
            final String key = modeKeys[i];
            final String label = titles[i];
            boolean isActive = key.equalsIgnoreCase(currentMode);
            items.add(new MainActivity.ThemedDialogItem(
                    titles[i],
                    subs[i],
                    isActive,
                    isActive ? "AKTIF" : null,
                    () -> {
                        p.edit().putString("atmosphere_mode", key).apply();
                        TvToast.show(this, "Suasana diatur ke: " + label);
                        populateUi();
                    }
            ));
        }

        MainActivity.showThemedListDialog(
                this,
                "✨ Pilihan Suasana Latar Belakang",
                "Sesuaikan animasi atmosfer cuaca di layar penuh",
                items,
                null,
                null
        );
    }

    private void showAsiaCountryDialog() {
        showLocationPickerDialog();
    }

    private void showStationListDialog(String region) {
        showLocationPickerDialog();
    }

    private void populateUi() {
        WeatherAirQuality.FullWeather w = WeatherAirQuality.getFullWeather(this);

        // 1. Determine Condition and update animated background
        WeatherBackgroundView.ConditionType conditionType = determineCondition(w);
        if (weatherBgView != null) {
            weatherBgView.setCondition(conditionType);
        }

        // 2. Header
        String locDisplay = AppLocationManager.getLocationName(this);
        String neatLoc = "Tuban";
        if (locDisplay != null && !locDisplay.trim().isEmpty()) {
            neatLoc = locDisplay.trim();
            weatherLocation.setText(neatLoc);
        } else {
            weatherLocation.setText("Tuban");
        }

        weatherMainTemp.setText(w.temp + "°");
        
        // Display Condition text matching active atmosphere
        if (conditionType == WeatherBackgroundView.ConditionType.SUNNY) {
            weatherMainCondition.setText("Cerah");
        } else if (conditionType == WeatherBackgroundView.ConditionType.NIGHT) {
            weatherMainCondition.setText("Malam Tenang");
        } else if (conditionType == WeatherBackgroundView.ConditionType.THUNDERSTORM) {
            weatherMainCondition.setText("Badai Petir");
        } else if (conditionType == WeatherBackgroundView.ConditionType.RAIN) {
            weatherMainCondition.setText("Hujan");
        } else {
            weatherMainCondition.setText("Berawan");
        }

        weatherMainSub.setText(String.format(Locale.ROOT, "H:%d°  L:%d°", w.highTemp, w.lowTemp));

        // 3. Hourly Strip (Apple Weather Conditions Card)
        hourlyContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < w.hourly.size(); i++) {
            WeatherAirQuality.HourlyItem item = w.hourly.get(i);
            View v = inflater.inflate(R.layout.item_weather_hourly, hourlyContainer, false);
            TextView timeView = v.findViewById(R.id.hourlyTime);
            ImageView iconView = v.findViewById(R.id.hourlyIcon);
            TextView rainView = v.findViewById(R.id.hourlyRain);
            TextView tempView = v.findViewById(R.id.hourlyTemp);

            timeView.setText(item.time);
            iconView.setImageResource(getIconDrawable(item.icon, item.time));
            tempView.setText(item.temp + "°");

            if (item.rainChance > 0) {
                rainView.setVisibility(View.VISIBLE);
                rainView.setText(item.rainChance + "%");
            } else {
                rainView.setVisibility(View.INVISIBLE); // Keep layout height stable and aligned
            }

            setupFocusAnimation(v, 1.0f);
            hourlyContainer.addView(v);

            // Default focus on first hourly tile
            if (i == 0) {
                v.requestFocus();
            }
        }

        // 4. 10-Day Forecast (Apple Weather Vertical List with Temp Spectrum Bar)
        dailyContainer.removeAllViews();
        int weekMin = 999;
        int weekMax = -999;
        for (WeatherAirQuality.DailyItem d : w.daily) {
            if (d.minTemp < weekMin) weekMin = d.minTemp;
            if (d.maxTemp > weekMax) weekMax = d.maxTemp;
        }
        if (weekMin == 999) {
            weekMin = 20;
            weekMax = 35;
        }

        for (int i = 0; i < w.daily.size(); i++) {
            WeatherAirQuality.DailyItem item = w.daily.get(i);
            View v = inflater.inflate(R.layout.item_weather_daily_row, dailyContainer, false);
            TextView dayView = v.findViewById(R.id.dailyDay);
            ImageView iconView = v.findViewById(R.id.dailyIcon);
            TextView rainView = v.findViewById(R.id.dailyRain);
            TextView minTempView = v.findViewById(R.id.dailyMinTemp);
            TempRangeBarView barView = v.findViewById(R.id.dailyTempBar);
            TextView maxTempView = v.findViewById(R.id.dailyMaxTemp);
            View divider = v.findViewById(R.id.dailyDivider);

            dayView.setText(item.day);
            iconView.setImageResource(getIconDrawable(item.icon, "12:00"));
            if (rainView != null) {
                if (item.rainChance > 0) {
                    rainView.setVisibility(View.VISIBLE);
                    rainView.setText(item.rainChance + "%");
                } else {
                    rainView.setVisibility(View.INVISIBLE);
                }
            }

            minTempView.setText(item.minTemp + "°");
            maxTempView.setText(item.maxTemp + "°");

            boolean isToday = (i == 0 || "Today".equalsIgnoreCase(item.day) || "Hari ini".equalsIgnoreCase(item.day));
            barView.setRange(weekMin, weekMax, item.minTemp, item.maxTemp, isToday ? w.temp : -999);

            if (i == w.daily.size() - 1 && divider != null) {
                divider.setVisibility(View.GONE);
            }

            setupFocusAnimation(v, 1.0f);
            dailyContainer.addView(v);
        }

        // 5. Populate Modular Metric Tiles
        if (windCompassView != null) {
            windCompassView.setWind(w.windSpeed, w.windSpeed + 10, 180f);
        }

        if (sunArcView != null) {
            sunArcView.setTimes(w.sunrise, w.sunset);
        }
        if (sunsetText != null) {
            sunsetText.setText(w.sunset != null ? w.sunset : "6:57 PM");
        }

        if (uvValText != null) {
            uvValText.setText(String.valueOf(w.uvIndex));
        }
        if (uvDescText != null) {
            if (w.uvIndex <= 2) uvDescText.setText("Rendah");
            else if (w.uvIndex <= 5) uvDescText.setText("Sedang");
            else if (w.uvIndex <= 7) uvDescText.setText("Tinggi");
            else uvDescText.setText("Sangat Tinggi");
        }

        if (feelsLikeVal != null) {
            feelsLikeVal.setText(w.apparentTemp + "°");
        }

        // Air Quality
        if (aqiValue != null) aqiValue.setText(String.valueOf(w.aqi));
        if (aqiStatus != null) aqiStatus.setText(w.aqiStatus);
        if (aqiBar != null) aqiBar.setAqi(w.aqi);
        if (aqiStation != null) {
            String activePlace = AppLocationManager.getStationPlace(this);
            if (activePlace != null && !activePlace.isEmpty()) {
                aqiStation.setText(activePlace);
            } else if (w.stationPlace != null && !w.stationPlace.isEmpty()) {
                aqiStation.setText(w.stationPlace);
            } else {
                aqiStation.setText("Alun-Alun Tuban, Jawa Timur");
            }
        }

        // Radar Map
        if (radarMapView != null) {
            radarMapView.setDetails(
                    w.temp,
                    neatLoc,
                    AppLocationManager.getLatitude(this),
                    AppLocationManager.getLongitude(this),
                    AppLocationManager.getActiveProvince(this)
            );
        }

        // Humidity & Pressure
        if (humidityValText != null) {
            humidityValText.setText(w.humidity + "%");
        }
        if (pressureGaugeView != null) {
            pressureGaugeView.setPressure(1014);
        }

        // 6. Footer
        if (weatherFooter != null) {
            weatherFooter.setText(String.format(Locale.ROOT,
                    "Diperbarui %s  •  Open-Meteo Atmospheric & Air Quality Model",
                    w.updatedTime));
        }
    }

    private WeatherBackgroundView.ConditionType determineCondition(WeatherAirQuality.FullWeather w) {
        SharedPreferences p = getSharedPreferences("app_weather_prefs", MODE_PRIVATE);
        String mode = p.getString("atmosphere_mode", "AUTO");
        if ("SUNNY".equalsIgnoreCase(mode)) return WeatherBackgroundView.ConditionType.SUNNY;
        if ("RAIN".equalsIgnoreCase(mode)) return WeatherBackgroundView.ConditionType.RAIN;
        if ("THUNDERSTORM".equalsIgnoreCase(mode)) return WeatherBackgroundView.ConditionType.THUNDERSTORM;
        if ("CLOUDY".equalsIgnoreCase(mode)) return WeatherBackgroundView.ConditionType.CLOUDY;
        if ("NIGHT".equalsIgnoreCase(mode)) return WeatherBackgroundView.ConditionType.NIGHT;

        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        boolean isNight = (hour < 7 || hour >= 19);

        String cond = (w.condition != null) ? w.condition.toLowerCase(Locale.ROOT) : "";
        String icon = (w.icon != null) ? w.icon : "";

        // Thunderstorm codes: 95, 96, 99
        if (cond.contains("thunder") || cond.contains("storm") || cond.contains("ribut") || icon.contains("⛈")) {
            return WeatherBackgroundView.ConditionType.THUNDERSTORM;
        }

        // Real Rain: Heavy rain or showers (codes 61, 63, 65, 80, 81, 82)
        if (cond.contains("heavy") || cond.contains("lebat") || 
            (cond.contains("hujan") && !cond.contains("renyai")) || 
            (cond.contains("rain") && !cond.contains("drizzle") && !cond.contains("light"))) {
            return WeatherBackgroundView.ConditionType.RAIN;
        }

        // Night
        if (isNight) {
            return WeatherBackgroundView.ConditionType.NIGHT;
        }

        // Daytime: In Malaysia, if condition is trace drizzle or sunny or partly cloudy or clear
        // default to Sunny with radiant sky and sun lens flare!
        return WeatherBackgroundView.ConditionType.SUNNY;
    }

    private int getIconDrawable(String icon, String timeText) {
        boolean isNight = false;
        if (timeText != null) {
            String lower = timeText.trim().toLowerCase(Locale.ROOT);
            if ("now".equals(lower)) {
                int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                isNight = (h < 7 || h >= 19);
            } else {
                try {
                    boolean isPm = lower.contains("pm");
                    boolean isAm = lower.contains("am");
                    String digits = lower.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        int hourNum = Integer.parseInt(digits);
                        int hour24;
                        if (isPm) {
                            hour24 = (hourNum == 12) ? 12 : hourNum + 12;
                        } else if (isAm) {
                            hour24 = (hourNum == 12) ? 0 : hourNum;
                        } else {
                            hour24 = hourNum;
                        }
                        isNight = (hour24 < 7 || hour24 >= 19);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (icon == null) return isNight ? R.drawable.wx_moon : R.drawable.wx_partly;
        if (icon.contains("☀")) return isNight ? R.drawable.wx_moon : R.drawable.wx_sun;
        if (icon.contains("⛅")) return isNight ? R.drawable.wx_partly_night : R.drawable.wx_partly;
        if (icon.contains("🌧")) return R.drawable.wx_rain;
        if (icon.contains("⛈")) return R.drawable.wx_storm;
        if (icon.contains("☁")) return R.drawable.wx_cloud;
        if (icon.contains("🌫")) return R.drawable.wx_fog;
        return isNight ? R.drawable.wx_moon : R.drawable.wx_partly;
    }

    private void setupFocusAnimation(View v, float scale) {
        v.setOnFocusChangeListener((view, hasFocus) -> {
            float s = hasFocus ? scale : 1.0f;
            float elev = hasFocus ? 6f : 0f;
            view.animate().scaleX(s).scaleY(s).translationZ(elev).setDuration(140).start();
        });
    }

    private void setupCardFocus(View card) {
        if (card == null) return;
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnFocusChangeListener((view, hasFocus) -> {
            float elev = hasFocus ? 6f : 0f;
            view.animate().scaleX(1.0f).scaleY(1.0f).translationZ(elev).setDuration(120).start();
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
