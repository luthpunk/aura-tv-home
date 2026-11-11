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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Manages fetching, caching, and parsing real-time football/soccer match fixtures & live scores.
 * Uses dual-engine fetching:
 * 1. ESPN Public Scoreboard & Calendar API (100% unblocked on all Indonesian ISPs, zero API key)
 * 2. LiveScore Stage & Date API with DNS-over-HTTPS / Direct TLS SNI fallback (for Liga 1 Indonesia & full coverage)
 */
public final class FootballScheduleManager {
    private static final String TAG = "FootballSchedule";
    private static final String PREFS_NAME = "football_widget_cache";

    public static final String MODE_NONE = "none";
    public static final String MODE_MINIMAL = "minimal"; // Marquee Running Text Pill
    public static final String MODE_LARGE = "large";     // Full Card View (3 Fixtures)

    public static final String LEAGUE_ALL = "all";
    public static final String LEAGUE_EPL = "eng.1";
    public static final String LEAGUE_UCL = "uefa.champions";
    public static final String LEAGUE_LALIGA = "esp.1";
    public static final String LEAGUE_SERIEA = "ita.1";
    public static final String LEAGUE_BUNDESLIGA = "ger.1";
    public static final String LEAGUE_IDN = "idn.1";
    public static final String LEAGUE_UEFA = "uefa";

    private static final String KEY_MODE = "widget_mode";
    private static final String KEY_LEAGUE_FILTER = "league_filter";
    private static final String KEY_CACHE_JSON = "cached_events_json_v3";
    private static final String KEY_CACHE_LEAGUE = "cached_league_v3";
    private static final String KEY_LAST_UPDATE = "last_update_ts_v3";

    // 15-minute cache interval for non-empty results
    private static final long CACHE_TTL_MS = 15 * 60 * 1000L;
    private static volatile boolean isSyncing = false;
    private static volatile long lastFailedAttemptMs = 0L;

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
                String hs = homeScore.isEmpty() ? "0" : homeScore;
                String as = awayScore.isEmpty() ? "0" : awayScore;
                return (isFinished ? "FT " : "") + hs + " - " + as;
            }
            return timeStr;
        }

        public String getCompactSummary() {
            StringBuilder sb = new StringBuilder();
            if (isLive) {
                sb.append("🔴 LIVE ").append(statusDetail.isEmpty() ? "" : statusDetail + " ");
                sb.append(homeTeam).append(" ").append(homeScore).append("-").append(awayScore).append(" ").append(awayTeam);
            } else if (isFinished) {
                sb.append("[").append(leagueCode).append(" FT] ").append(homeTeam).append(" ")
                  .append(homeScore).append("-").append(awayScore).append(" ").append(awayTeam);
            } else {
                sb.append("[").append(leagueCode).append("] ").append(homeTeam).append(" vs ").append(awayTeam)
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
            next = LEAGUE_EPL;
        } else if (LEAGUE_EPL.equals(cur)) {
            next = LEAGUE_UCL;
        } else if (LEAGUE_UCL.equals(cur)) {
            next = LEAGUE_LALIGA;
        } else if (LEAGUE_LALIGA.equals(cur)) {
            next = LEAGUE_SERIEA;
        } else if (LEAGUE_SERIEA.equals(cur)) {
            next = LEAGUE_BUNDESLIGA;
        } else if (LEAGUE_BUNDESLIGA.equals(cur)) {
            next = LEAGUE_IDN;
        } else if (LEAGUE_IDN.equals(cur)) {
            next = LEAGUE_UEFA;
        } else {
            next = LEAGUE_ALL;
        }
        setLeagueFilter(context, next);
        return next;
    }

    public static String getLeagueDisplayName(String leagueCode) {
        if (LEAGUE_EPL.equals(leagueCode)) return "Premier League";
        if (LEAGUE_UCL.equals(leagueCode)) return "Champions League";
        if (LEAGUE_LALIGA.equals(leagueCode)) return "La Liga";
        if (LEAGUE_SERIEA.equals(leagueCode)) return "Serie A";
        if (LEAGUE_BUNDESLIGA.equals(leagueCode)) return "Bundesliga";
        if (LEAGUE_IDN.equals(leagueCode)) return "Liga 1 Indonesia";
        if (LEAGUE_UEFA.equals(leagueCode)) return "UEFA Nations League";
        return "Semua Jadwal Populer";
    }

    public static void syncForce(Context context, Runnable onUpdated) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_LAST_UPDATE).remove(KEY_CACHE_JSON).apply();
        lastFailedAttemptMs = 0L;
        syncIfDue(context, onUpdated);
    }

    public static void syncIfDue(Context context, Runnable onUpdated) {
        if (context == null) return;
        String mode = getWidgetMode(context);
        if (MODE_NONE.equals(mode)) return;

        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long last = sp.getLong(KEY_LAST_UPDATE, 0);
        String cachedLeague = sp.getString(KEY_CACHE_LEAGUE, "");
        String cachedJson = sp.getString(KEY_CACHE_JSON, "");
        String currentFilter = getLeagueFilter(context);
        long now = System.currentTimeMillis();

        boolean hasValidCache = cachedJson != null && cachedJson.length() > 5 && currentFilter.equals(cachedLeague);
        if (hasValidCache && (now - last < CACHE_TTL_MS)) {
            if (onUpdated != null) {
                new Handler(Looper.getMainLooper()).post(onUpdated);
            }
            return;
        }

        if (isSyncing) return;
        if (!hasValidCache && (now - lastFailedAttemptMs < 8_000L)) {
            return;
        }

        isSyncing = true;
        new Thread(() -> {
            try {
                String filter = getLeagueFilter(context);
                List<MatchItem> matches = fetchMatchesForFilter(filter);

                if (!matches.isEmpty()) {
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
                            .putString(KEY_CACHE_LEAGUE, filter)
                            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                            .apply();
                } else {
                    // Never lock out retries for 15 minutes when offline or empty
                    lastFailedAttemptMs = System.currentTimeMillis();
                }

                if (onUpdated != null) {
                    new Handler(Looper.getMainLooper()).post(onUpdated);
                }
            } catch (Exception e) {
                lastFailedAttemptMs = System.currentTimeMillis();
                Log.w(TAG, "Football sync failed: " + e.getMessage());
            } finally {
                isSyncing = false;
            }
        }, "football-sync").start();
    }

    private static List<MatchItem> fetchMatchesForFilter(String filter) {
        if (LEAGUE_EPL.equals(filter)) {
            return fetchSingleLeague("eng.1", "england/premier-league", "Premier League", "EPL", 12);
        } else if (LEAGUE_UCL.equals(filter)) {
            return fetchSingleLeague("uefa.champions", "champions-league/league-stage", "Champions League", "UCL", 12);
        } else if (LEAGUE_LALIGA.equals(filter)) {
            return fetchSingleLeague("esp.1", "spain/laliga", "La Liga", "LALIGA", 12);
        } else if (LEAGUE_SERIEA.equals(filter)) {
            return fetchSingleLeague("ita.1", "italy/serie-a", "Serie A", "SERIE A", 12);
        } else if (LEAGUE_BUNDESLIGA.equals(filter)) {
            return fetchSingleLeague("ger.1", "germany/bundesliga", "Bundesliga", "BUNDESLIGA", 12);
        } else if (LEAGUE_IDN.equals(filter)) {
            List<MatchItem> idn = fetchStageFromLiveScore("indonesia/super-league", "Liga 1 Indonesia", "LIGA 1", 12);
            if (idn.isEmpty()) {
                idn = fetchLeagueFromEspn("idn.1", "Liga 1 Indonesia", "LIGA 1", 12);
            }
            return idn;
        } else if (LEAGUE_UEFA.equals(filter)) {
            List<MatchItem> uefa = fetchLeagueFromEspn("uefa.nations", "UEFA Nations League", "UEFA", 12);
            if (uefa.isEmpty()) {
                uefa = fetchUefaNationsLeague(12);
            }
            return uefa;
        }

        // LEAGUE_ALL: Gather genuine top-flight matches across major leagues
        List<MatchItem> combined = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        String[][] leagues = {
                {"uefa.nations", null, "UEFA Nations League", "UEFA"},
                {"eng.1", "england/premier-league", "Premier League", "EPL"},
                {"uefa.champions", "champions-league/league-stage", "Champions League", "UCL"},
                {"esp.1", "spain/laliga", "La Liga", "LALIGA"},
                {"ita.1", "italy/serie-a", "Serie A", "SERIE A"},
                {"ger.1", "germany/bundesliga", "Bundesliga", "BUNDESLIGA"}
        };

        for (String[] lg : leagues) {
            List<MatchItem> items = fetchLeagueFromEspn(lg[0], lg[2], lg[3], 4);
            if (items.isEmpty() && lg[1] != null) {
                items = fetchStageFromLiveScore(lg[1], lg[2], lg[3], 4);
            }
            for (MatchItem m : items) {
                String key = (m.homeTeam + "_" + m.awayTeam).toLowerCase(Locale.ROOT);
                if (seenIds.add(key)) combined.add(m);
            }
        }

        // Also include Liga 1 Indonesia upcoming fixtures
        List<MatchItem> idnItems = fetchStageFromLiveScore("indonesia/super-league", "Liga 1 Indonesia", "LIGA 1", 3);
        for (MatchItem m : idnItems) {
            String key = (m.homeTeam + "_" + m.awayTeam).toLowerCase(Locale.ROOT);
            if (seenIds.add(key)) combined.add(m);
        }

        sortMatches(combined);
        if (combined.size() > 15) {
            return new ArrayList<>(combined.subList(0, 15));
        }
        return combined;
    }

    private static List<MatchItem> fetchSingleLeague(String espnSlug, String liveScoreStage, String leagueName, String leagueCode, int limit) {
        List<MatchItem> items = fetchLeagueFromEspn(espnSlug, leagueName, leagueCode, limit);
        if (!items.isEmpty()) return items;
        return fetchStageFromLiveScore(liveScoreStage, leagueName, leagueCode, limit);
    }

    /**
     * Primary Engine: ESPN Public Scoreboard + Calendar API.
     * Unblocked on all Indonesian ISPs (Indihome, Telkomsel, Biznet, XL, FirstMedia).
     */
    private static List<MatchItem> fetchLeagueFromEspn(String slug, String defaultLeagueName, String leagueCode, int limit) {
        List<MatchItem> upcomingAndLive = new ArrayList<>();
        List<MatchItem> recentFinished = new ArrayList<>();
        long now = System.currentTimeMillis();

        try {
            SimpleDateFormat ymdFmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
            ymdFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            String todayUtc = ymdFmt.format(new Date(now - 6 * 3600_000L));

            // 1. Discover exact upcoming matchday dates via ESPN ondays calendar
            List<String> targetDates = new ArrayList<>();
            String ondaysJson = httpGetString("https://sports.core.api.espn.com/v2/sports/soccer/leagues/" + slug + "/calendar/ondays", false);
            if (ondaysJson != null) {
                JSONObject root = new JSONObject(ondaysJson);
                JSONObject evDate = root.optJSONObject("eventDate");
                JSONArray dates = evDate != null ? evDate.optJSONArray("dates") : null;
                if (dates != null) {
                    for (int i = 0; i < dates.length(); i++) {
                        String raw = dates.optString(i, "");
                        if (raw.length() >= 10) {
                            String ymd = raw.substring(0, 10).replace("-", "");
                            if (ymd.compareTo(todayUtc) >= 0) {
                                targetDates.add(ymd);
                                if (targetDates.size() >= 2) break;
                            }
                        }
                    }
                }
            }

            // 2. Fetch scoreboard for discovered upcoming dates (or default scoreboard if none)
            if (targetDates.isEmpty()) {
                String sbJson = httpGetString("https://site.api.espn.com/apis/site/v2/sports/soccer/" + slug + "/scoreboard", false);
                if (sbJson != null) {
                    parseEspnScoreboardJson(sbJson, defaultLeagueName, leagueCode, now, upcomingAndLive, recentFinished);
                }
            } else {
                for (String d : targetDates) {
                    String sbJson = httpGetString("https://site.api.espn.com/apis/site/v2/sports/soccer/" + slug + "/scoreboard?dates=" + d, false);
                    if (sbJson != null) {
                        parseEspnScoreboardJson(sbJson, defaultLeagueName, leagueCode, now, upcomingAndLive, recentFinished);
                    }
                    if (upcomingAndLive.size() >= limit) break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "ESPN fetch failed for " + slug + ": " + e.getMessage());
        }

        List<MatchItem> result = !upcomingAndLive.isEmpty() ? upcomingAndLive : recentFinished;
        sortMatches(result);
        if (result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    private static void parseEspnScoreboardJson(String json, String defaultLeagueName, String leagueCode, long now,
                                                List<MatchItem> upcomingAndLive, List<MatchItem> recentFinished) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray events = root.optJSONArray("events");
            if (events == null) return;

            SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
            isoFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            SimpleDateFormat isoSecFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            isoSecFmt.setTimeZone(TimeZone.getTimeZone("UTC"));

            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.optJSONObject(i);
                if (ev == null) continue;

                String id = ev.optString("id", String.valueOf(i));
                String dateStr = ev.optString("date", "");
                long ts = now;
                try {
                    Date d = dateStr.length() > 17 ? isoSecFmt.parse(dateStr) : isoFmt.parse(dateStr);
                    if (d != null) ts = d.getTime();
                } catch (Exception ignored) {}

                JSONObject statusObj = ev.optJSONObject("status");
                JSONObject typeObj = statusObj != null ? statusObj.optJSONObject("type") : null;
                String state = typeObj != null ? typeObj.optString("state", "pre") : "pre";
                String shortDetail = typeObj != null ? typeObj.optString("shortDetail", "") : "";

                boolean isLive = "in".equalsIgnoreCase(state);
                boolean isFinished = "post".equalsIgnoreCase(state);

                JSONArray comps = ev.optJSONArray("competitions");
                if (comps == null || comps.length() == 0) continue;
                JSONObject comp = comps.optJSONObject(0);
                if (comp == null) continue;
                JSONArray competitors = comp.optJSONArray("competitors");
                if (competitors == null || competitors.length() < 2) continue;

                JSONObject homeComp = competitors.optJSONObject(0);
                JSONObject awayComp = competitors.optJSONObject(1);
                for (int c = 0; c < competitors.length(); c++) {
                    JSONObject item = competitors.optJSONObject(c);
                    if (item == null) continue;
                    if ("home".equalsIgnoreCase(item.optString("homeAway"))) homeComp = item;
                    else if ("away".equalsIgnoreCase(item.optString("homeAway"))) awayComp = item;
                }
                if (homeComp == null || awayComp == null) continue;

                JSONObject hTeam = homeComp.optJSONObject("team");
                JSONObject aTeam = awayComp.optJSONObject("team");
                String rawHome = hTeam != null ? hTeam.optString("displayName", hTeam.optString("name", "Home")) : "Home";
                String rawAway = aTeam != null ? aTeam.optString("displayName", aTeam.optString("name", "Away")) : "Away";

                String homeName = cleanTeamName(rawHome);
                String awayName = cleanTeamName(rawAway);
                String homeShort = hTeam != null ? hTeam.optString("abbreviation", makeShortName(homeName)) : makeShortName(homeName);
                String awayShort = aTeam != null ? aTeam.optString("abbreviation", makeShortName(awayName)) : makeShortName(awayName);

                String homeScore = homeComp.optString("score", "0");
                String awayScore = awayComp.optString("score", "0");
                String timeStr = formatTimestampWib(ts);

                MatchItem match = new MatchItem(
                        id, defaultLeagueName, leagueCode,
                        homeName, awayName, homeShort, awayShort,
                        homeScore, awayScore,
                        isLive ? shortDetail : "", isLive, isFinished,
                        timeStr, ts
                );

                if (isFinished) {
                    recentFinished.add(match);
                } else {
                    upcomingAndLive.add(match);
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Secondary Engine: LiveScore Stage API with automatic DoH / Direct TLS SNI fallback
     * if the local ISP router blocks DNS resolution for livescore.com.
     */
    private static List<MatchItem> fetchStageFromLiveScore(String stagePath, String defaultLeagueName, String leagueCode, int limit) {
        List<MatchItem> list = new ArrayList<>();
        try {
            String urlStr = "https://prod-public-api.livescore.com/v1/api/app/stage/soccer/" + stagePath + "/7";
            String body = httpGetString(urlStr, true);
            if (body != null) {
                long now = System.currentTimeMillis();
                JSONObject root = new JSONObject(body);
                JSONArray stages = root.optJSONArray("Stages");
                if (stages != null) {
                    for (int s = 0; s < stages.length(); s++) {
                        JSONObject stage = stages.optJSONObject(s);
                        if (stage == null) continue;
                        String snm = stage.optString("Snm", defaultLeagueName);
                        JSONArray events = stage.optJSONArray("Events");
                        if (events == null) continue;

                        for (int e = 0; e < events.length(); e++) {
                            JSONObject ev = events.optJSONObject(e);
                            if (ev == null) continue;
                            MatchItem item = parseEventObject(ev, snm, leagueCode, now);
                            if (item != null) {
                                list.add(item);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error fetching stage " + stagePath + ": " + e.getMessage());
        }

        sortMatches(list);
        if (list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    private static List<MatchItem> fetchUefaNationsLeague(int limit) {
        List<MatchItem> results = new ArrayList<>();
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        SimpleDateFormat ymdFmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        ymdFmt.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));

        for (int i = 0; i < 6; i++) {
            Calendar dayCal = (Calendar) cal.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, i);
            String dateStr = ymdFmt.format(dayCal.getTime());
            results.addAll(fetchDayStrictTopTier(dateStr, LEAGUE_UEFA));
            if (results.size() >= limit) break;
        }
        return results;
    }

    private static List<MatchItem> fetchDayStrictTopTier(String dateStr, String filter) {
        List<MatchItem> list = new ArrayList<>();
        try {
            String urlStr = "https://prod-public-api.livescore.com/v1/api/app/date/soccer/" + dateStr + "/7";
            String body = httpGetString(urlStr, true);
            if (body != null) {
                long now = System.currentTimeMillis();
                JSONObject root = new JSONObject(body);
                JSONArray stages = root.optJSONArray("Stages");
                if (stages != null) {
                    for (int s = 0; s < stages.length(); s++) {
                        JSONObject stage = stages.optJSONObject(s);
                        if (stage == null) continue;

                        String cnm = stage.optString("Cnm", "");
                        String snm = stage.optString("Snm", "");
                        String ccd = stage.optString("Ccd", "").toLowerCase(Locale.ROOT);
                        String scd = stage.optString("Scd", "").toLowerCase(Locale.ROOT);

                        if (!strictStageMatches(filter, ccd, scd)) {
                            continue;
                        }

                        String leagueCode = getShortLeagueCode(ccd, scd, snm);
                        JSONArray events = stage.optJSONArray("Events");
                        if (events == null) continue;

                        for (int e = 0; e < events.length(); e++) {
                            JSONObject ev = events.optJSONObject(e);
                            if (ev == null) continue;
                            MatchItem item = parseEventObject(ev, cnm + " - " + snm, leagueCode, now);
                            if (item != null) {
                                list.add(item);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error fetching LiveScore for " + dateStr + ": " + e.getMessage());
        }
        return list;
    }

    /**
     * Performs an HTTPS GET request. If useDohFallback is true and standard DNS fails/blocks,
     * resolves the host via Google DNS-over-HTTPS (dns.google) and performs a direct TLS SNI request.
     */
    private static String httpGetString(String urlStr, boolean useDohFallback) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)");
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                return sb.toString();
            }
        } catch (Exception e) {
            if (useDohFallback) {
                return httpGetViaDirectTls(urlStr);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
        return useDohFallback ? httpGetViaDirectTls(urlStr) : null;
    }

    private static String httpGetViaDirectTls(String urlStr) {
        Socket rawSocket = null;
        SSLSocket sslSocket = null;
        try {
            URL url = new URL(urlStr);
            String host = url.getHost();
            String path = url.getFile();
            String ip = "34.54.215.216"; // Default LiveScore Anycast CDN IP

            // Query Google DoH for fresh A record
            HttpURLConnection dohConn = null;
            try {
                URL dohUrl = new URL("https://dns.google/resolve?name=" + host + "&type=A");
                dohConn = (HttpURLConnection) dohUrl.openConnection();
                dohConn.setConnectTimeout(2500);
                dohConn.setReadTimeout(2500);
                if (dohConn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(dohConn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    JSONObject dnsJson = new JSONObject(sb.toString());
                    JSONArray answers = dnsJson.optJSONArray("Answer");
                    if (answers != null) {
                        for (int i = 0; i < answers.length(); i++) {
                            JSONObject a = answers.optJSONObject(i);
                            if (a != null && a.optInt("type") == 1) {
                                String candidate = a.optString("data", "");
                                if (!candidate.isEmpty()) {
                                    ip = candidate;
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (dohConn != null) dohConn.disconnect();
            }

            rawSocket = new Socket();
            rawSocket.connect(new InetSocketAddress(ip, 443), 4000);
            rawSocket.setSoTimeout(4500);

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            sslSocket = (SSLSocket) factory.createSocket(rawSocket, host, 443, true);
            sslSocket.startHandshake();
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, sslSocket.getSession())) {
                return null;
            }

            String req = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "User-Agent: Mozilla/5.0 (Android TV)\r\n"
                    + "Accept: application/json\r\n"
                    + "Accept-Encoding: identity\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream out = sslSocket.getOutputStream();
            out.write(req.getBytes(StandardCharsets.UTF_8));
            out.flush();

            BufferedReader br = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = br.readLine();
            if (statusLine == null || !statusLine.contains("200")) return null;

            boolean chunked = false;
            String headerLine;
            while ((headerLine = br.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase(Locale.ROOT).contains("transfer-encoding: chunked")) {
                    chunked = true;
                }
            }

            StringBuilder body = new StringBuilder();
            if (chunked) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("{") || trimmed.endsWith("}")) {
                        body.append(line);
                    } else {
                        try {
                            int len = Integer.parseInt(trimmed, 16);
                            if (len == 0) break;
                        } catch (NumberFormatException nfe) {
                            body.append(line);
                        }
                    }
                }
            } else {
                String line;
                while ((line = br.readLine()) != null) {
                    body.append(line);
                }
            }
            String res = body.toString();
            int firstBrace = res.indexOf('{');
            int lastBrace = res.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return res.substring(firstBrace, lastBrace + 1);
            }
        } catch (Exception ignored) {
        } finally {
            try { if (sslSocket != null) sslSocket.close(); } catch (Exception ignored) {}
            try { if (rawSocket != null) rawSocket.close(); } catch (Exception ignored) {}
        }
        return null;
    }

    private static void sortMatches(List<MatchItem> list) {
        Collections.sort(list, (a, b) -> {
            if (a.isLive != b.isLive) return a.isLive ? -1 : 1;
            if (a.isFinished != b.isFinished) return a.isFinished ? 1 : -1;
            long dayA = a.timestamp / (24 * 3600_000L);
            long dayB = b.timestamp / (24 * 3600_000L);
            if (dayA != dayB) return Long.compare(dayA, dayB);
            boolean bigA = isBigMatch(a.homeTeam, a.awayTeam);
            boolean bigB = isBigMatch(b.homeTeam, b.awayTeam);
            if (bigA != bigB) return bigA ? -1 : 1;
            return Long.compare(a.timestamp, b.timestamp);
        });
    }

    private static MatchItem parseEventObject(JSONObject ev, String leagueName, String leagueCode, long now) {
        String eps = ev.optString("Eps", "");
        boolean isLive = eps.contains("'") || "HT".equalsIgnoreCase(eps) || "ET".equalsIgnoreCase(eps)
                || "PEN".equalsIgnoreCase(eps) || "In Progress".equalsIgnoreCase(eps);
        boolean isUpcoming = "NS".equalsIgnoreCase(eps);
        if (!isLive && !isUpcoming) return null;

        long esd = ev.optLong("Esd", 0);
        long matchTimestamp = parseEsdToTimestamp(esd);
        if (isUpcoming && matchTimestamp < now - (3 * 3600_000L)) {
            return null;
        }

        JSONArray t1Arr = ev.optJSONArray("T1");
        JSONArray t2Arr = ev.optJSONArray("T2");
        if (t1Arr == null || t1Arr.length() == 0 || t2Arr == null || t2Arr.length() == 0) return null;

        JSONObject t1Obj = t1Arr.optJSONObject(0);
        JSONObject t2Obj = t2Arr.optJSONObject(0);
        if (t1Obj == null || t2Obj == null) return null;

        String rawHome = t1Obj.optString("Nm", "Home");
        String rawAway = t2Obj.optString("Nm", "Away");
        String homeName = cleanTeamName(rawHome);
        String awayName = cleanTeamName(rawAway);
        String homeShort = t1Obj.optString("Abr", "");
        String awayShort = t2Obj.optString("Abr", "");
        if (homeShort.isEmpty()) homeShort = makeShortName(homeName);
        if (awayShort.isEmpty()) awayShort = makeShortName(awayName);

        String homeScore = ev.optString("Tr1", "");
        String awayScore = ev.optString("Tr2", "");
        String statusDetail = isLive ? eps : "";
        String timeStr = formatFriendlyDate(matchTimestamp, esd);
        String eid = ev.optString("Eid", rawHome + "_" + esd);

        return new MatchItem(
                eid, leagueName, leagueCode,
                homeName, awayName, homeShort, awayShort,
                homeScore, awayScore,
                statusDetail, isLive, false,
                timeStr, matchTimestamp
        );
    }

    /**
     * Strict whitelist of senior top-flight competitions only (excludes U17/U19/U21, Women's, and lower divisions).
     */
    private static boolean strictStageMatches(String filter, String ccd, String scd) {
        if (scd.contains("u17") || scd.contains("u18") || scd.contains("u19") || scd.contains("u20")
                || scd.contains("u21") || scd.contains("women") || ccd.contains("women")
                || ccd.contains("u-17") || ccd.contains("u19") || ccd.contains("youth")) {
            return false;
        }
        if (LEAGUE_UEFA.equals(filter)) {
            return ccd.equals("uefa-nations-league");
        }
        if (LEAGUE_EPL.equals(filter)) {
            return ccd.equals("england") && scd.equals("premier-league");
        }
        if (LEAGUE_UCL.equals(filter)) {
            return ccd.equals("champions-league");
        }
        if (LEAGUE_LALIGA.equals(filter)) {
            return ccd.equals("spain") && scd.equals("laliga");
        }
        if (LEAGUE_SERIEA.equals(filter)) {
            return ccd.equals("italy") && scd.equals("serie-a");
        }
        if (LEAGUE_BUNDESLIGA.equals(filter)) {
            return ccd.equals("germany") && scd.equals("bundesliga");
        }
        if (LEAGUE_IDN.equals(filter)) {
            return ccd.equals("indonesia") && (scd.equals("super-league") || scd.equals("liga-1"));
        }

        // LEAGUE_ALL strict top-tier check
        return (ccd.equals("england") && scd.equals("premier-league"))
                || (ccd.equals("spain") && scd.equals("laliga"))
                || (ccd.equals("italy") && scd.equals("serie-a"))
                || (ccd.equals("germany") && scd.equals("bundesliga"))
                || (ccd.equals("france") && scd.equals("ligue-1"))
                || ccd.equals("champions-league")
                || ccd.equals("europa-league")
                || (ccd.equals("uefa-nations-league") && (scd.startsWith("league-a") || scd.startsWith("league-b") || scd.contains("final")))
                || (ccd.equals("indonesia") && (scd.equals("super-league") || scd.equals("liga-1")));
    }

    private static boolean isBigMatch(String home, String away) {
        return isPopularClub(home) || isPopularClub(away);
    }

    private static boolean isPopularClub(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        String[] giants = {
                "arsenal", "man city", "manchester city", "liverpool", "man united", "manchester united",
                "chelsea", "tottenham", "real madrid", "barcelona", "atletico", "ac milan", "milan",
                "inter", "juventus", "napoli", "roma", "bayern", "dortmund", "leverkusen", "psg",
                "paris", "persib", "persija", "persebaya", "arema", "borneo", "bali united", "indonesia",
                "spain", "england", "france", "germany", "italy", "portugal", "netherlands", "brazil", "argentina"
        };
        for (String g : giants) {
            if (n.contains(g)) return true;
        }
        return false;
    }

    public static String cleanTeamName(String name) {
        if (name == null || name.isEmpty()) return "TBD";
        String n = name.trim();
        if (n.equalsIgnoreCase("Manchester United")) return "Man United";
        if (n.equalsIgnoreCase("Manchester City")) return "Man City";
        if (n.equalsIgnoreCase("Tottenham Hotspur")) return "Tottenham";
        if (n.equalsIgnoreCase("Newcastle United")) return "Newcastle";
        if (n.equalsIgnoreCase("Nottingham Forest")) return "Nottm Forest";
        if (n.equalsIgnoreCase("Brighton & Hove Albion") || n.equalsIgnoreCase("Brighton and Hove Albion")) return "Brighton";
        if (n.equalsIgnoreCase("Wolverhampton Wanderers")) return "Wolves";
        if (n.equalsIgnoreCase("West Ham United")) return "West Ham";
        if (n.equalsIgnoreCase("AFC Bournemouth")) return "Bournemouth";
        if (n.equalsIgnoreCase("Borussia M'gladbach") || n.equalsIgnoreCase("Borussia Moenchengladbach")) return "M'gladbach";
        if (n.equalsIgnoreCase("Borussia Dortmund")) return "Dortmund";
        if (n.equalsIgnoreCase("Bayer Leverkusen") || n.equalsIgnoreCase("Bayer 04 Leverkusen")) return "Leverkusen";
        if (n.equalsIgnoreCase("Paris Saint-Germain")) return "PSG";
        if (n.equalsIgnoreCase("Atletico Madrid") || n.equalsIgnoreCase("Atlético Madrid")) return "Atletico";
        if (n.equalsIgnoreCase("Deportivo Alaves") || n.equalsIgnoreCase("Deportivo Alavés")) return "Alaves";
        if (n.equalsIgnoreCase("Deportivo A Coruna")) return "Dep. La Coruna";
        if (n.equalsIgnoreCase("Athletic Club")) return "Athletic Bilbao";
        if (n.equalsIgnoreCase("Internazionale") || n.equalsIgnoreCase("Inter")) return "Inter Milan";
        if (n.equalsIgnoreCase("Hellas Verona")) return "Verona";
        if (n.equalsIgnoreCase("Como 1907")) return "Como";
        if (n.equalsIgnoreCase("Borneo FC Samarinda")) return "Borneo FC";
        if (n.equalsIgnoreCase("Persib Bandung")) return "Persib";
        if (n.equalsIgnoreCase("Persija Jakarta")) return "Persija";
        if (n.equalsIgnoreCase("Persebaya Surabaya")) return "Persebaya";
        if (n.equalsIgnoreCase("Persita Tangerang")) return "Persita";
        if (n.equalsIgnoreCase("Bosnia and Herzegovina")) return "Bosnia";
        if (n.equalsIgnoreCase("North Macedonia")) return "Macedonia";
        if (n.equalsIgnoreCase("1. FC Union Berlin")) return "Union Berlin";
        if (n.equalsIgnoreCase("FC Cologne") || n.equalsIgnoreCase("1. FC Köln")) return "FC Koln";
        if (n.equalsIgnoreCase("TSG Hoffenheim")) return "Hoffenheim";
        if (n.equalsIgnoreCase("SC Freiburg")) return "Freiburg";
        if (n.equalsIgnoreCase("Fenerbahce") || n.equalsIgnoreCase("Fenerbahçe")) return "Fenerbahce";
        if (n.equalsIgnoreCase("Feyenoord Rotterdam")) return "Feyenoord";
        return n;
    }

    private static String getShortLeagueCode(String ccd, String scd, String snm) {
        String lower = (ccd + " " + scd + " " + snm).toLowerCase(Locale.ROOT);
        if (lower.contains("nations")) return "UEFA";
        if (lower.contains("premier")) return "EPL";
        if (lower.contains("champions")) return "UCL";
        if (lower.contains("europa")) return "UEL";
        if (lower.contains("laliga") || lower.contains("la liga")) return "LALIGA";
        if (lower.contains("serie a") || lower.contains("serie-a")) return "SERIE A";
        if (lower.contains("bundesliga")) return "BUNDESLIGA";
        if (lower.contains("indonesia") || lower.contains("liga 1") || lower.contains("super-league")) return "LIGA 1";
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
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }

    private static String formatTimestampWib(long timestamp) {
        SimpleDateFormat hmFmt = new SimpleDateFormat("HH:mm", Locale.US);
        hmFmt.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
        String timeOnly = hmFmt.format(new Date(timestamp));
        return formatFriendlyDateWithTime(timestamp, timeOnly);
    }

    private static String formatFriendlyDate(long timestamp, long esd) {
        String s = String.valueOf(esd);
        String timeOnly = (s.length() >= 12) ? s.substring(8, 10) + ":" + s.substring(10, 12) : "TBD";
        return formatFriendlyDateWithTime(timestamp, timeOnly);
    }

    private static String formatFriendlyDateWithTime(long timestamp, String timeOnly) {
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
        if (diffDays >= 0 && diffDays < 6) {
            int dow = calMatch.get(Calendar.DAY_OF_WEEK);
            String dayName;
            switch (dow) {
                case Calendar.SUNDAY: dayName = "Min"; break;
                case Calendar.MONDAY: dayName = "Sen"; break;
                case Calendar.TUESDAY: dayName = "Sel"; break;
                case Calendar.WEDNESDAY: dayName = "Rab"; break;
                case Calendar.THURSDAY: dayName = "Kam"; break;
                case Calendar.FRIDAY: dayName = "Jum"; break;
                case Calendar.SATURDAY: dayName = "Sab"; break;
                default: dayName = ""; break;
            }
            return dayName + " " + timeOnly;
        }

        SimpleDateFormat dateFmt = new SimpleDateFormat("d MMM", new Locale("id", "ID"));
        dateFmt.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
        return dateFmt.format(new Date(timestamp)) + " " + timeOnly;
    }

    public static List<MatchItem> getCachedMatches(Context context) {
        List<MatchItem> list = new ArrayList<>();
        if (context == null) return list;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_CACHE_JSON, null);
        if (json == null || json.length() <= 5) return list;

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
            String league = getLeagueDisplayName(getLeagueFilter(context));
            return "⚽ " + league + ": Memuat jadwal pertandingan terbaru...";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(matches.size(), 12); i++) {
            if (i > 0) sb.append("   •   ");
            sb.append(matches.get(i).getCompactSummary());
        }
        return sb.toString();
    }
}
