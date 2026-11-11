package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Manages fetching, caching, and parsing football/soccer match fixtures & live scores.
 * Data source: ESPN Global Scoreboard CDN API (Lightweight, keyless, reliable).
 * Strictly focuses on SCHEDULED upcoming fixtures and LIVE matches.
 */
public final class FootballScheduleManager {
    private static final String TAG = "FootballSchedule";
    private static final String PREFS_NAME = "football_widget_cache";

    public static final String MODE_NONE = "none";
    public static final String MODE_MINIMAL = "minimal"; // Marquee Running Text Pill
    public static final String MODE_LARGE = "large";     // Full Card View (3 Fixtures)

    public static final String LEAGUE_ALL = "all";
    public static final String LEAGUE_IDN = "idn.1";
    public static final String LEAGUE_EPL = "eng.1";
    public static final String LEAGUE_UCL = "uefa.champions";
    public static final String LEAGUE_LALIGA = "esp.1";
    public static final String LEAGUE_SERIEA = "ita.1";

    private static final String KEY_MODE = "widget_mode";
    private static final String KEY_LEAGUE_FILTER = "league_filter";
    private static final String KEY_CACHE_JSON = "cached_events_json";
    private static final String KEY_LAST_UPDATE = "last_update_ts";

    // 20-minute cache interval to prevent excessive requests & keep TV cool
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
            if (isLive) {
                return (homeScore.isEmpty() ? "0" : homeScore) + " - " + (awayScore.isEmpty() ? "0" : awayScore);
            }
            return timeStr;
        }

        public String getCompactSummary() {
            StringBuilder sb = new StringBuilder();
            if (isLive) {
                sb.append("🔴 LIVE ").append(statusDetail.isEmpty() ? "" : statusDetail + " ");
                sb.append(homeShort).append(" ").append(homeScore).append("-").append(awayScore).append(" ").append(awayShort);
            } else {
                sb.append(leagueCode).append(": ").append(homeShort).append(" vs ").append(awayShort)
                  .append(" (").append(timeStr).append(")");
            }
            return sb.toString();
        }
    }

    private FootballScheduleManager() {}

    public static String getWidgetMode(Context context) {
        if (context == null) return MODE_NONE;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_MODE, MODE_NONE);
    }

    public static void setWidgetMode(Context context, String mode) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_MODE, mode).apply();
    }

    public static String getLeagueFilter(Context context) {
        if (context == null) return LEAGUE_ALL;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_LEAGUE_FILTER, LEAGUE_ALL);
    }

    public static void setLeagueFilter(Context context, String league) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_LEAGUE_FILTER, league).apply();
    }

    public static String cycleNextLeague(Context context) {
        String cur = getLeagueFilter(context);
        String next;
        if (LEAGUE_IDN.equals(cur)) {
            next = LEAGUE_EPL;
        } else if (LEAGUE_EPL.equals(cur)) {
            next = LEAGUE_UCL;
        } else if (LEAGUE_UCL.equals(cur)) {
            next = LEAGUE_LALIGA;
        } else if (LEAGUE_LALIGA.equals(cur)) {
            next = LEAGUE_SERIEA;
        } else if (LEAGUE_SERIEA.equals(cur)) {
            next = LEAGUE_ALL;
        } else {
            next = LEAGUE_IDN;
        }
        setLeagueFilter(context, next);
        return next;
    }

    public static String getLeagueDisplayName(String leagueCode) {
        if (LEAGUE_IDN.equals(leagueCode)) return "Liga 1 Indonesia";
        if (LEAGUE_EPL.equals(leagueCode)) return "Premier League";
        if (LEAGUE_UCL.equals(leagueCode)) return "Champions League";
        if (LEAGUE_LALIGA.equals(leagueCode)) return "La Liga";
        if (LEAGUE_SERIEA.equals(leagueCode)) return "Serie A";
        return "Semua Liga Populer";
    }

    public static void syncForce(Context context, Runnable onUpdated) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_LAST_UPDATE).remove(KEY_CACHE_JSON).apply();
        syncIfDue(context, onUpdated);
    }

    public static void syncIfDue(Context context, Runnable onUpdated) {
        if (context == null) return;
        String mode = getWidgetMode(context);
        if (MODE_NONE.equals(mode)) return;

        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long last = sp.getLong(KEY_LAST_UPDATE, 0);
        long now = System.currentTimeMillis();

        if (now - last < CACHE_TTL_MS && sp.contains(KEY_CACHE_JSON)) {
            if (onUpdated != null) {
                new Handler(Looper.getMainLooper()).post(onUpdated);
            }
            return;
        }

        new Thread(() -> {
            try {
                String filter = getLeagueFilter(context);
                List<MatchItem> matches = new ArrayList<>();

                if (LEAGUE_ALL.equals(filter)) {
                    matches.addAll(fetchUpcomingFixtures(LEAGUE_IDN, "Liga 1", "Liga 1"));
                    matches.addAll(fetchUpcomingFixtures(LEAGUE_EPL, "Premier League", "EPL"));
                    matches.addAll(fetchUpcomingFixtures(LEAGUE_UCL, "Champions League", "UCL"));
                } else {
                    String name = getLeagueDisplayName(filter);
                    matches.addAll(fetchUpcomingFixtures(filter, name, getShortCode(filter)));
                }

                if (!matches.isEmpty()) {
                    // Sort: LIVE first, then Upcoming chronologically
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
                    new Handler(Looper.getMainLooper()).post(onUpdated);
                }
            } catch (Exception e) {
                Log.w(TAG, "Football sync failed: " + e.getMessage());
            }
        }, "football-sync").start();
    }

    private static String getShortCode(String league) {
        if (LEAGUE_EPL.equals(league)) return "EPL";
        if (LEAGUE_UCL.equals(league)) return "UCL";
        if (LEAGUE_IDN.equals(league)) return "Liga 1";
        if (LEAGUE_LALIGA.equals(league)) return "La Liga";
        if (LEAGUE_SERIEA.equals(league)) return "Serie A";
        return "BOLA";
    }

    private static List<MatchItem> fetchUpcomingFixtures(String league, String leagueName, String leagueCode) {
        List<MatchItem> list = fetchLeagueEndpoint(league, leagueName, leagueCode, null);
        
        // Filter strictly for LIVE or SCHEDULED upcoming matches (discard finished/FT)
        List<MatchItem> upcoming = new ArrayList<>();
        for (MatchItem m : list) {
            if (m.isLive || !m.isFinished) {
                upcoming.add(m);
            }
        }

        // If no scheduled match today, lookup league calendar for the next match day
        if (upcoming.isEmpty()) {
            String nextDate = findNextCalendarDate(league);
            if (nextDate != null && !nextDate.isEmpty()) {
                List<MatchItem> nextList = fetchLeagueEndpoint(league, leagueName, leagueCode, nextDate);
                for (MatchItem m : nextList) {
                    if (m.isLive || !m.isFinished) {
                        upcoming.add(m);
                    }
                }
            }
        }

        // If league is in off-season (e.g. Liga 1 off-season), show latest fixtures as fallback
        if (upcoming.isEmpty() && !list.isEmpty()) {
            upcoming.addAll(list);
        }

        return upcoming;
    }

    private static String findNextCalendarDate(String league) {
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://site.api.espn.com/apis/site/v2/sports/soccer/" + league + "/scoreboard";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)");

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONArray leagues = root.optJSONArray("leagues");
                if (leagues != null && leagues.length() > 0) {
                    JSONObject lg = leagues.optJSONObject(0);
                    JSONArray cal = lg != null ? lg.optJSONArray("calendar") : null;
                    if (cal != null && cal.length() > 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                        String todayStr = sdf.format(new Date());

                        String bestDate = null;
                        for (int i = 0; i < cal.length(); i++) {
                            Object item = cal.get(i);
                            String dStr = "";
                            if (item instanceof String) {
                                dStr = (String) item;
                            } else if (item instanceof JSONObject) {
                                dStr = ((JSONObject) item).optString("date", ((JSONObject) item).optString("startDate", ""));
                            }
                            if (dStr.length() >= 10) {
                                String ymd = dStr.substring(0, 10);
                                if (ymd.compareTo(todayStr) >= 0) {
                                    bestDate = ymd.replace("-", "");
                                    break;
                                }
                            }
                        }
                        return bestDate;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error finding calendar for " + league + ": " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static List<MatchItem> fetchLeagueEndpoint(String league, String leagueName, String leagueCode, String dateParam) {
        List<MatchItem> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://site.api.espn.com/apis/site/v2/sports/soccer/" + league + "/scoreboard";
            if (dateParam != null && !dateParam.isEmpty()) {
                urlStr += "?dates=" + dateParam;
            }
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
                                    timeStr = formatFriendlyDate(d);
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

    private static String formatFriendlyDate(Date matchDate) {
        Calendar calMatch = Calendar.getInstance();
        calMatch.setTime(matchDate);

        Calendar calNow = Calendar.getInstance();
        Calendar calTomorrow = Calendar.getInstance();
        calTomorrow.add(Calendar.DAY_OF_YEAR, 1);

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeOnly = timeFmt.format(matchDate);

        if (calMatch.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
            calMatch.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)) {
            return "Hari ini " + timeOnly;
        } else if (calMatch.get(Calendar.YEAR) == calTomorrow.get(Calendar.YEAR) &&
                   calMatch.get(Calendar.DAY_OF_YEAR) == calTomorrow.get(Calendar.DAY_OF_YEAR)) {
            return "Besok " + timeOnly;
        }

        // Show day name in Indonesian
        int dow = calMatch.get(Calendar.DAY_OF_WEEK);
        String dayName;
        switch (dow) {
            case Calendar.SUNDAY: dayName = "Minggu"; break;
            case Calendar.MONDAY: dayName = "Senin"; break;
            case Calendar.TUESDAY: dayName = "Selasa"; break;
            case Calendar.WEDNESDAY: dayName = "Rabu"; break;
            case Calendar.THURSDAY: dayName = "Kamis"; break;
            case Calendar.FRIDAY: dayName = "Jumat"; break;
            case Calendar.SATURDAY: dayName = "Sabtu"; break;
            default: dayName = ""; break;
        }

        return dayName + " " + timeOnly;
    }

    public static List<MatchItem> getCachedMatches(Context context) {
        List<MatchItem> list = new ArrayList<>();
        if (context == null) return list;
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
            return "Jadwal Pertandingan Bola: Menghubungkan jadwal...";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(matches.size(), 10); i++) {
            if (i > 0) sb.append("   •   ");
            sb.append(matches.get(i).getCompactSummary());
        }
        return sb.toString();
    }
}
