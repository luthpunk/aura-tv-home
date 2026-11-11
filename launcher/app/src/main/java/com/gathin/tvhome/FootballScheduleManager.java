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
 * Manages fetching, caching, and parsing real-time football/soccer match fixtures & live scores.
 * Data source: LiveScore Global CDN API (Fast, reliable, real-time, zero-key, updated 24/7).
 * Strictly focuses on SCHEDULED upcoming fixtures and LIVE matches.
 */
public final class FootballScheduleManager {
    private static final String TAG = "FootballSchedule";
    private static final String PREFS_NAME = "football_widget_cache";

    public static final String MODE_NONE = "none";
    public static final String MODE_MINIMAL = "minimal"; // Marquee Running Text Pill
    public static final String MODE_LARGE = "large";     // Full Card View (3 Fixtures)

    public static final String LEAGUE_ALL = "all";
    public static final String LEAGUE_UEFA = "uefa";
    public static final String LEAGUE_EPL = "eng.1";
    public static final String LEAGUE_UCL = "uefa.champions";
    public static final String LEAGUE_LALIGA = "esp.1";
    public static final String LEAGUE_SERIEA = "ita.1";
    public static final String LEAGUE_IDN = "idn.1";

    private static final String KEY_MODE = "widget_mode";
    private static final String KEY_LEAGUE_FILTER = "league_filter";
    private static final String KEY_CACHE_JSON = "cached_events_json";
    private static final String KEY_LAST_UPDATE = "last_update_ts";

    // 15-minute cache interval to keep schedules fresh while keeping TV cool
    private static final long CACHE_TTL_MS = 15 * 60 * 1000L;

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
        if (LEAGUE_ALL.equals(cur)) {
            next = LEAGUE_UEFA;
        } else if (LEAGUE_UEFA.equals(cur)) {
            next = LEAGUE_EPL;
        } else if (LEAGUE_EPL.equals(cur)) {
            next = LEAGUE_UCL;
        } else if (LEAGUE_UCL.equals(cur)) {
            next = LEAGUE_LALIGA;
        } else if (LEAGUE_LALIGA.equals(cur)) {
            next = LEAGUE_SERIEA;
        } else if (LEAGUE_SERIEA.equals(cur)) {
            next = LEAGUE_IDN;
        } else {
            next = LEAGUE_ALL;
        }
        setLeagueFilter(context, next);
        return next;
    }

    public static String getLeagueDisplayName(String leagueCode) {
        if (LEAGUE_UEFA.equals(leagueCode)) return "UEFA Nations League";
        if (LEAGUE_EPL.equals(leagueCode)) return "Premier League";
        if (LEAGUE_UCL.equals(leagueCode)) return "Champions League";
        if (LEAGUE_LALIGA.equals(leagueCode)) return "La Liga";
        if (LEAGUE_SERIEA.equals(leagueCode)) return "Serie A";
        if (LEAGUE_IDN.equals(leagueCode)) return "Liga 1 Indonesia";
        return "Semua Jadwal Populer";
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
                List<MatchItem> matches = fetchMatchesFromLiveScore(filter);

                if (!matches.isEmpty()) {
                    // Sort: LIVE matches first, then upcoming by timestamp
                    Collections.sort(matches, (a, b) -> {
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

    private static List<MatchItem> fetchMatchesFromLiveScore(String filter) {
        List<MatchItem> results = new ArrayList<>();
        int maxDays = LEAGUE_ALL.equals(filter) ? 3 : 14;

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        SimpleDateFormat ymdFmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        ymdFmt.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));

        for (int i = 0; i < maxDays; i++) {
            Calendar dayCal = (Calendar) cal.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, i);
            String dateStr = ymdFmt.format(dayCal.getTime());

            List<MatchItem> dayMatches = fetchDayFromLiveScore(dateStr, filter);
            results.addAll(dayMatches);

            // Once we have at least 5 upcoming fixtures for a specific league, or 10 for ALL, stop scanning
            if (LEAGUE_ALL.equals(filter) && results.size() >= 10) break;
            if (!LEAGUE_ALL.equals(filter) && results.size() >= 5) break;
        }

        return results;
    }

    private static List<MatchItem> fetchDayFromLiveScore(String dateStr, String filter) {
        List<MatchItem> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            // LiveScore app API endpoint: timezone parameter 7 corresponds to UTC+7 (WIB)
            String urlStr = "https://prod-public-api.livescore.com/v1/api/app/date/soccer/" + dateStr + "/7";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)");

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONArray stages = root.optJSONArray("Stages");
                if (stages != null) {
                    for (int s = 0; s < stages.length(); s++) {
                        JSONObject stage = stages.optJSONObject(s);
                        if (stage == null) continue;

                        String cnm = stage.optString("Cnm", "");
                        String snm = stage.optString("Snm", "");
                        String ccd = stage.optString("Ccd", "").toLowerCase(Locale.ROOT);
                        String scd = stage.optString("Scd", "").toLowerCase(Locale.ROOT);

                        if (!leagueMatchesFilter(filter, ccd, scd, cnm, snm)) {
                            continue;
                        }

                        String leagueCode = getShortLeagueCode(ccd, scd, snm);

                        JSONArray events = stage.optJSONArray("Events");
                        if (events == null) continue;

                        for (int e = 0; e < events.length(); e++) {
                            JSONObject ev = events.optJSONObject(e);
                            if (ev == null) continue;

                            String eps = ev.optString("Eps", "");
                            boolean isLive = eps.contains("'") || "HT".equalsIgnoreCase(eps) || "ET".equalsIgnoreCase(eps)
                                    || "In Progress".equalsIgnoreCase(eps);
                            boolean isUpcoming = "NS".equalsIgnoreCase(eps);

                            // Only take live matches or upcoming fixtures (skip finished matches)
                            if (!isLive && !isUpcoming) continue;

                            String eid = ev.optString("Eid", String.valueOf(e));
                            JSONArray t1Arr = ev.optJSONArray("T1");
                            JSONArray t2Arr = ev.optJSONArray("T2");
                            if (t1Arr == null || t1Arr.length() == 0 || t2Arr == null || t2Arr.length() == 0) continue;

                            JSONObject t1Obj = t1Arr.optJSONObject(0);
                            JSONObject t2Obj = t2Arr.optJSONObject(0);
                            if (t1Obj == null || t2Obj == null) continue;

                            String homeName = t1Obj.optString("Nm", "Home");
                            String awayName = t2Obj.optString("Nm", "Away");
                            String homeShort = t1Obj.optString("Abr", "");
                            String awayShort = t2Obj.optString("Abr", "");
                            if (homeShort.isEmpty()) homeShort = makeShortName(homeName);
                            if (awayShort.isEmpty()) awayShort = makeShortName(awayName);

                            String homeScore = ev.optString("Tr1", "");
                            String awayScore = ev.optString("Tr2", "");
                            String statusDetail = isLive ? eps : "";

                            long esd = ev.optLong("Esd", 0);
                            long matchTimestamp = parseEsdToTimestamp(esd);
                            String timeStr = formatFriendlyDate(matchTimestamp, esd);

                            list.add(new MatchItem(
                                    eid, snm, leagueCode,
                                    homeName, awayName, homeShort, awayShort,
                                    homeScore, awayScore,
                                    statusDetail, isLive, false,
                                    timeStr, matchTimestamp
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error fetching LiveScore for " + dateStr + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }

    private static boolean leagueMatchesFilter(String filter, String ccd, String scd, String cnm, String snm) {
        String lowerAll = (cnm + " " + snm + " " + ccd + " " + scd).toLowerCase(Locale.ROOT);

        if (LEAGUE_EPL.equals(filter)) {
            return ccd.equals("england") && (scd.equals("premier-league") || snm.equalsIgnoreCase("Premier League"));
        } else if (LEAGUE_UEFA.equals(filter)) {
            return ccd.contains("uefa-nations") || lowerAll.contains("nations league");
        } else if (LEAGUE_UCL.equals(filter)) {
            return ccd.contains("champions-league") || lowerAll.contains("champions league");
        } else if (LEAGUE_LALIGA.equals(filter)) {
            return ccd.equals("spain") && (scd.equals("laliga") || snm.equalsIgnoreCase("LaLiga"));
        } else if (LEAGUE_SERIEA.equals(filter)) {
            return ccd.equals("italy") && (scd.equals("serie-a") || snm.equalsIgnoreCase("Serie A"));
        } else if (LEAGUE_IDN.equals(filter)) {
            return ccd.equals("indonesia") || lowerAll.contains("liga 1") || lowerAll.contains("super league");
        }

        // LEAGUE_ALL: Prioritize top international and European competitions
        // UEFA Nations League, EPL, UCL, LaLiga, Serie A, World Cup Qualifiers, Friendlies, Indonesian Liga 1
        boolean isTopTier = (ccd.equals("england") && scd.equals("premier-league"))
                || (ccd.equals("spain") && scd.equals("laliga"))
                || (ccd.equals("italy") && scd.equals("serie-a"))
                || ccd.contains("champions-league")
                || ccd.contains("uefa-nations")
                || ccd.contains("international-friendlies")
                || ccd.contains("world-cup")
                || ccd.contains("euro-")
                || ccd.equals("indonesia")
                || lowerAll.contains("premier league")
                || lowerAll.contains("la liga")
                || lowerAll.contains("serie a")
                || lowerAll.contains("nations league");

        return isTopTier;
    }

    private static String getShortLeagueCode(String ccd, String scd, String snm) {
        String lower = (ccd + " " + scd + " " + snm).toLowerCase(Locale.ROOT);
        if (lower.contains("nations")) return "UEFA";
        if (lower.contains("premier")) return "EPL";
        if (lower.contains("champions")) return "UCL";
        if (lower.contains("laliga") || lower.contains("la liga")) return "LALIGA";
        if (lower.contains("serie a")) return "SERIE A";
        if (lower.contains("indonesia") || lower.contains("liga 1")) return "LIGA 1";
        if (lower.contains("friendlies")) return "UJI COBA";
        return "BOLA";
    }

    private static String makeShortName(String name) {
        if (name == null || name.isEmpty()) return "TBD";
        if (name.length() <= 4) return name.toUpperCase(Locale.ROOT);
        String[] parts = name.split(" ");
        if (parts.length >= 2 && parts[0].length() <= 3) {
            return (parts[0] + " " + parts[1].substring(0, Math.min(parts[1].length(), 3))).toUpperCase(Locale.ROOT);
        }
        return name.substring(0, Math.min(name.length(), 4)).toUpperCase(Locale.ROOT);
    }

    private static long parseEsdToTimestamp(long esd) {
        if (esd < 20200000000000L) return System.currentTimeMillis();
        try {
            String s = String.valueOf(esd);
            int year = Integer.parseInt(s.substring(0, 4));
            int month = Integer.parseInt(s.substring(4, 6)) - 1;
            int day = Integer.parseInt(s.substring(6, 8));
            int hour = Integer.parseInt(s.substring(8, 10));
            int min = Integer.parseInt(s.substring(10, 12));

            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
            cal.set(year, month, day, hour, min, 0);
            return cal.getTimeInMillis();
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }

    private static String formatFriendlyDate(long timestamp, long esd) {
        String s = String.valueOf(esd);
        String timeOnly = (s.length() >= 12) ? s.substring(8, 10) + ":" + s.substring(10, 12) : "TBD";

        Calendar calMatch = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        calMatch.setTimeInMillis(timestamp);

        Calendar calNow = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        Calendar calTomorrow = (Calendar) calNow.clone();
        calTomorrow.add(Calendar.DAY_OF_YEAR, 1);

        if (calMatch.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
            calMatch.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)) {
            return "Hari ini " + timeOnly;
        } else if (calMatch.get(Calendar.YEAR) == calTomorrow.get(Calendar.YEAR) &&
                   calMatch.get(Calendar.DAY_OF_YEAR) == calTomorrow.get(Calendar.DAY_OF_YEAR)) {
            return "Besok " + timeOnly;
        }

        long diffDays = (calMatch.getTimeInMillis() - calNow.getTimeInMillis()) / (24 * 3600 * 1000L);
        if (diffDays < 6) {
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

        // Show Date (e.g. "21 Nov 19:30")
        SimpleDateFormat dateFmt = new SimpleDateFormat("d MMM", new Locale("id", "ID"));
        dateFmt.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
        return dateFmt.format(new Date(timestamp)) + " " + timeOnly;
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
            return "Jadwal Pertandingan Bola: Menghubungkan jadwal terbaru...";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(matches.size(), 12); i++) {
            if (i > 0) sb.append("   •   ");
            sb.append(matches.get(i).getCompactSummary());
        }
        return sb.toString();
    }
}
