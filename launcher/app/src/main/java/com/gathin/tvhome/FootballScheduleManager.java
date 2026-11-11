package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Manages fetching, caching, and parsing football/soccer match fixtures & live scores.
 * Data source: ESPN Global Scoreboard CDN API (Lightweight, keyless, reliable).
 */
public final class FootballScheduleManager {
    private static final String TAG = "FootballSchedule";
    private static final String PREFS_NAME = "football_widget_cache";

    public static final String MODE_NONE = "none";
    public static final String MODE_MINIMAL = "minimal"; // Marquee Running Text Pill
    public static final String MODE_LARGE = "large";     // Full Card View

    public static final String LEAGUE_ALL = "all";
    public static final String LEAGUE_EPL = "eng.1";
    public static final String LEAGUE_UCL = "uefa.champions";
    public static final String LEAGUE_IDN = "idn.1";
    public static final String LEAGUE_LALIGA = "esp.1";
    public static final String LEAGUE_SERIEA = "ita.1";

    private static final String KEY_MODE = "widget_mode";
    private static final String KEY_LEAGUE_FILTER = "league_filter";
    private static final String KEY_CACHE_JSON = "cached_events_json";
    private static final String KEY_LAST_UPDATE = "last_update_ts";

    // 20-minute cache interval to prevent excessive requests & keep TV battery/CPU cool
    private static final long CACHE_TTL_MS = 20 * 60 * 1000L;

    public static class MatchItem {
        public final String id;
        public final String leagueName;
        public final String leagueCode;
        public final String homeTeam;
        public final String awayTeam;
        public final String homeShort;
        public final String awayShort;
        public final String homeScore;
        public final String awayScore;
        public final String statusDetail;
        public final boolean isLive;
        public final boolean isFinished;
        public final String timeStr;
        public final long timestamp;

        public MatchItem(String id, String leagueName, String leagueCode,
                         String homeTeam, String awayTeam,
                         String homeShort, String awayShort,
                         String homeScore, String awayScore,
                         String statusDetail, boolean isLive, boolean isFinished,
                         String timeStr, long timestamp) {
            this.id = id;
            this.leagueName = leagueName;
            this.leagueCode = leagueCode;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.homeShort = homeShort;
            this.awayShort = awayShort;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.statusDetail = statusDetail;
            this.isLive = isLive;
            this.isFinished = isFinished;
            this.timeStr = timeStr;
            this.timestamp = timestamp;
        }

        public String getScoreOrVs() {
            if (isLive || isFinished) {
                return homeScore + " - " + awayScore;
            }
            return timeStr;
        }

        public String getCompactSummary() {
            StringBuilder sb = new StringBuilder();
            if (isLive) {
                sb.append("🔴 LIVE ").append(statusDetail).append(" ");
            } else if (isFinished) {
                sb.append("[").append(leagueCode).append(" FT] ");
            } else {
                sb.append("[").append(leagueCode).append(" ").append(timeStr).append("] ");
            }

            sb.append(homeShort).append(" ");
            if (isLive || isFinished) {
                sb.append(homeScore).append("-").append(awayScore);
            } else {
                sb.append("vs");
            }
            sb.append(" ").append(awayShort);
            return sb.toString();
        }
    }

    private FootballScheduleManager() {}

    public static String getWidgetMode(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_MODE, MODE_NONE);
    }

    public static void setWidgetMode(Context context, String mode) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_MODE, mode).apply();
    }

    public static String getLeagueFilter(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_LEAGUE_FILTER, LEAGUE_ALL);
    }

    public static void setLeagueFilter(Context context, String league) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_LEAGUE_FILTER, league).apply();
    }

    public static String getLeagueDisplayName(String leagueCode) {
        if (LEAGUE_EPL.equals(leagueCode)) return "Premier League";
        if (LEAGUE_UCL.equals(leagueCode)) return "Champions League";
        if (LEAGUE_IDN.equals(leagueCode)) return "Liga 1 Indonesia";
        if (LEAGUE_LALIGA.equals(leagueCode)) return "La Liga";
        if (LEAGUE_SERIEA.equals(leagueCode)) return "Serie A";
        return "Semua Liga Populer";
    }

    /**
     * Checks if cached match data is expired and synchronizes in background thread if needed.
     */
    public static void syncIfDue(Context context, Runnable onUpdated) {
        String mode = getWidgetMode(context);
        if (MODE_NONE.equals(mode)) return;

        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long last = sp.getLong(KEY_LAST_UPDATE, 0);
        long now = System.currentTimeMillis();

        if (now - last < CACHE_TTL_MS && sp.contains(KEY_CACHE_JSON)) {
            // Cache is still fresh
            if (onUpdated != null) onUpdated.run();
            return;
        }

        new Thread(() -> {
            try {
                String filter = getLeagueFilter(context);
                List<MatchItem> matches = new ArrayList<>();

                if (LEAGUE_ALL.equals(filter)) {
                    // Smart priority: fetch EPL, UCL, and Liga 1
                    matches.addAll(fetchLeagueScoreboard(LEAGUE_EPL, "Premier League", "EPL"));
                    matches.addAll(fetchLeagueScoreboard(LEAGUE_UCL, "Champions League", "UCL"));
                    matches.addAll(fetchLeagueScoreboard(LEAGUE_IDN, "Liga 1", "L1"));
                } else {
                    String name = getLeagueDisplayName(filter);
                    matches.addAll(fetchLeagueScoreboard(filter, name, getShortCode(filter)));
                }

                if (!matches.isEmpty()) {
                    // Sort: LIVE first, then Scheduled by date, then Finished
                    matches.sort((a, b) -> {
                        if (a.isLive != b.isLive) return a.isLive ? -1 : 1;
                        if (a.isFinished != b.isFinished) return a.isFinished ? 1 : -1;
                        return Long.compare(a.timestamp, b.timestamp);
                    });

                    JSONArray arr = new JSONArray();
                    for (MatchItem m : matches) {
                        JSONObject obj = new JSONObject();
                        obj.put("id", m.id);
                        obj.put("leagueName", m.leagueName);
                        obj.put("leagueCode", m.leagueCode);
                        obj.put("homeTeam", m.homeTeam);
                        obj.put("awayTeam", m.awayTeam);
                        obj.put("homeShort", m.homeShort);
                        obj.put("awayShort", m.awayShort);
                        obj.put("homeScore", m.homeScore);
                        obj.put("awayScore", m.awayScore);
                        obj.put("statusDetail", m.statusDetail);
                        obj.put("isLive", m.isLive);
                        obj.put("isFinished", m.isFinished);
                        obj.put("timeStr", m.timeStr);
                        obj.put("timestamp", m.timestamp);
                        arr.put(obj);
                    }

                    sp.edit()
                            .putString(KEY_CACHE_JSON, arr.toString())
                            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                            .apply();
                }

                if (onUpdated != null) {
                    onUpdated.run();
                }
            } catch (Exception e) {
                Log.w(TAG, "Football sync failed: " + e.getMessage());
            }
        }, "football-sync").start();
    }

    private static String getShortCode(String league) {
        if (LEAGUE_EPL.equals(league)) return "EPL";
        if (LEAGUE_UCL.equals(league)) return "UCL";
        if (LEAGUE_IDN.equals(league)) return "L1";
        if (LEAGUE_LALIGA.equals(league)) return "ESP";
        if (LEAGUE_SERIEA.equals(league)) return "ITA";
        return "BOLA";
    }

    private static List<MatchItem> fetchLeagueScoreboard(String league, String leagueName, String leagueCode) {
        List<MatchItem> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://site.api.espn.com/apis/site/v2/sports/soccer/" + league + "/scoreboard";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)");

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONArray events = root.optJSONArray("events");
                if (events != null) {
                    SimpleDateFormat isoParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
                    isoParser.setTimeZone(TimeZone.getTimeZone("UTC"));
                    SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    SimpleDateFormat dayTimeFormatter = new SimpleDateFormat("EEE HH:mm", Locale.getDefault());

                    for (int i = 0; i < events.length(); i++) {
                        JSONObject ev = events.optJSONObject(i);
                        if (ev == null) continue;

                        String id = ev.optString("id", String.valueOf(i));
                        String dateIso = ev.optString("date", "");
                        long ts = 0;
                        String timeStr = "TBD";
                        try {
                            if (!dateIso.isEmpty()) {
                                Date d = isoParser.parse(dateIso);
                                if (d != null) {
                                    ts = d.getTime();
                                    long diffDays = (ts - System.currentTimeMillis()) / (24 * 3600 * 1000L);
                                    if (Math.abs(diffDays) < 1) {
                                        timeStr = timeFormatter.format(d);
                                    } else {
                                        timeStr = dayTimeFormatter.format(d);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}

                        JSONObject statusObj = ev.optJSONObject("status");
                        String statusDetail = "";
                        boolean isLive = false;
                        boolean isFinished = false;
                        if (statusObj != null) {
                            JSONObject typeObj = statusObj.optJSONObject("type");
                            if (typeObj != null) {
                                String state = typeObj.optString("state", "");
                                statusDetail = typeObj.optString("detail", "");
                                if ("in".equalsIgnoreCase(state)) {
                                    isLive = true;
                                } else if ("post".equalsIgnoreCase(state)) {
                                    isFinished = true;
                                }
                            }
                        }

                        JSONArray comps = ev.optJSONArray("competitions");
                        if (comps != null && comps.length() > 0) {
                            JSONObject comp = comps.optJSONObject(0);
                            JSONArray competitors = comp != null ? comp.optJSONArray("competitors") : null;
                            if (competitors != null && competitors.length() >= 2) {
                                String homeName = "Home", awayName = "Away";
                                String homeShort = "HOM", awayShort = "AWY";
                                String homeScore = "", awayScore = "";

                                for (int c = 0; c < competitors.length(); c++) {
                                    JSONObject competitor = competitors.optJSONObject(c);
                                    if (competitor == null) continue;
                                    String homeAway = competitor.optString("homeAway", "");
                                    JSONObject team = competitor.optJSONObject("team");
                                    String name = team != null ? team.optString("displayName", team.optString("name", "")) : "";
                                    String abbrev = team != null ? team.optString("abbreviation", "") : "";
                                    if (abbrev.isEmpty() && name.length() >= 3) {
                                        abbrev = name.substring(0, 3).toUpperCase(Locale.ROOT);
                                    }
                                    String score = competitor.optString("score", "");

                                    if ("home".equalsIgnoreCase(homeAway)) {
                                        homeName = name;
                                        homeShort = abbrev;
                                        homeScore = score;
                                    } else {
                                        awayName = name;
                                        awayShort = abbrev;
                                        awayScore = score;
                                    }
                                }

                                list.add(new MatchItem(
                                        id, leagueName, leagueCode,
                                        homeName, awayName, homeShort, awayShort,
                                        homeScore, awayScore,
                                        statusDetail, isLive, isFinished,
                                        timeStr, ts
                                ));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error fetching " + league + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }

    public static List<MatchItem> getCachedMatches(Context context) {
        List<MatchItem> list = new ArrayList<>();
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_CACHE_JSON, null);
        if (json == null || json.isEmpty()) return list;

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new MatchItem(
                        o.optString("id"),
                        o.optString("leagueName"),
                        o.optString("leagueCode"),
                        o.optString("homeTeam"),
                        o.optString("awayTeam"),
                        o.optString("homeShort"),
                        o.optString("awayShort"),
                        o.optString("homeScore"),
                        o.optString("awayScore"),
                        o.optString("statusDetail"),
                        o.optBoolean("isLive"),
                        o.optBoolean("isFinished"),
                        o.optString("timeStr"),
                        o.optLong("timestamp")
                ));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static String getMarqueeTicker(Context context) {
        List<MatchItem> matches = getCachedMatches(context);
        if (matches.isEmpty()) {
            return "⚽ Jadwal Pertandingan Bola: Menghubungkan jadwal...";
        }

        StringBuilder sb = new StringBuilder("⚽ ");
        for (int i = 0; i < Math.min(matches.size(), 8); i++) {
            if (i > 0) sb.append("   •   ");
            sb.append(matches.get(i).getCompactSummary());
        }
        return sb.toString();
    }
}
