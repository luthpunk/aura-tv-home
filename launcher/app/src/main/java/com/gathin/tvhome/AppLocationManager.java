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
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppLocationManager {
    private static final String TAG = "TvHome.Location";
    public static final String PREFS_NAME = "app_location_prefs";

    public static final String KEY_REGION = "region_name";
    public static final String KEY_STATION_ID = "station_id";
    public static final String KEY_LOCATION_NAME = "location_name";
    public static final String KEY_STATION_PLACE = "station_place";
    public static final String KEY_LATITUDE = "latitude";
    public static final String KEY_LONGITUDE = "longitude";
    private static final String KEY_MIGRATED_TUBAN_DEFAULT = "migrated_tuban_default_v1";

    // Defaults to Tuban, Jawa Timur, Indonesia
    public static final String DEFAULT_REGION = "Indonesia";
    public static final String DEFAULT_STATION_ID = "ID_TUBAN";
    public static final String DEFAULT_LOCATION_NAME = "Tuban";
    public static final String DEFAULT_STATION_PLACE = "Alun-Alun Tuban, Jawa Timur";
    public static final double DEFAULT_LATITUDE = -6.8976;
    public static final double DEFAULT_LONGITUDE = 112.0649;

    public interface OnlineSearchCallback {
        void onResults(List<Station> stations);
    }

    public static class Station {
        public final String id;
        public final String name;
        public final String state;
        public final String country;
        public final String place;
        public final double lat;
        public final double lon;

        public Station(String id, String name, String state, String country, String place, double lat, double lon) {
            this.id = id;
            this.name = name;
            this.state = state != null ? state : "";
            this.country = country != null ? country : "Indonesia";
            this.place = place != null ? place : "";
            this.lat = lat;
            this.lon = lon;
        }

        public String getDisplayName() {
            if (state.isEmpty() || state.equalsIgnoreCase(country)) {
                return name + " (" + country + ")";
            }
            return name + " (" + state + ")";
        }

        public String getShortMapLabel() {
            String s = name;
            if (s.startsWith("Kab. ")) {
                s = s.substring(5);
            }
            int paren = s.indexOf(" (");
            if (paren > 0) {
                s = s.substring(0, paren);
            }
            return s.trim();
        }
    }

    private static final Map<String, List<Station>> REGION_STATIONS = new LinkedHashMap<>();
    private static final List<Station> ALL_STATIONS = new ArrayList<>();

    static {
        // --- 🇮🇩 Indonesia (Seluruh Kabupaten & Kota se-Indonesia) ---
        List<Station> idStations = new ArrayList<>();
        idStations.add(new Station("ID_TUBAN", "Tuban", "Jawa Timur", "Indonesia", "Alun-Alun Tuban, Jawa Timur", -6.8976, 112.0649));
        idStations.add(new Station("ID_LMG", "Lamongan", "Jawa Timur", "Indonesia", "Alun-Alun Lamongan, Jawa Timur", -7.1193, 112.4165));
        idStations.add(new Station("ID_BBT", "Babat", "Jawa Timur", "Indonesia", "Kec. Babat, Kab. Lamongan, Jawa Timur", -7.1044, 112.1672));
        idStations.add(new Station("ID_BJN", "Bojonegoro", "Jawa Timur", "Indonesia", "Pusat Kota Bojonegoro, Jawa Timur", -7.1502, 111.8817));
        idStations.add(new Station("ID_GRS", "Gresik", "Jawa Timur", "Indonesia", "Alun-Alun Gresik, Jawa Timur", -7.1566, 112.6555));
        idStations.add(new Station("ID_SBY", "Surabaya", "Jawa Timur", "Indonesia", "Pusat Kota Surabaya, Jawa Timur", -7.2575, 112.7521));
        idStations.add(new Station("ID_SDA", "Sidoarjo", "Jawa Timur", "Indonesia", "Alun-Alun Sidoarjo, Jawa Timur", -7.4478, 112.7183));
        idStations.add(new Station("ID_MJK", "Mojokerto", "Jawa Timur", "Indonesia", "Kota Mojokerto, Jawa Timur", -7.4704, 112.4401));
        idStations.add(new Station("ID_MJK_KAB", "Kab. Mojokerto (Mojosari)", "Jawa Timur", "Indonesia", "Mojosari, Kab. Mojokerto, Jawa Timur", -7.5194, 112.5567));
        idStations.add(new Station("ID_JBG", "Jombang", "Jawa Timur", "Indonesia", "Alun-Alun Jombang, Jawa Timur", -7.546, 112.2331));
        idStations.add(new Station("ID_NGK", "Nganjuk", "Jawa Timur", "Indonesia", "Alun-Alun Nganjuk, Jawa Timur", -7.6042, 111.9035));
        idStations.add(new Station("ID_MDN", "Madiun", "Jawa Timur", "Indonesia", "Kota Madiun, Jawa Timur", -7.6298, 111.5239));
        idStations.add(new Station("ID_MDN_KAB", "Kab. Madiun (Caruban)", "Jawa Timur", "Indonesia", "Caruban, Kab. Madiun, Jawa Timur", -7.5472, 111.6539));
        idStations.add(new Station("ID_NGW", "Ngawi", "Jawa Timur", "Indonesia", "Alun-Alun Ngawi, Jawa Timur", -7.4038, 111.4464));
        idStations.add(new Station("ID_MGT", "Magetan", "Jawa Timur", "Indonesia", "Alun-Alun Magetan, Jawa Timur", -7.6542, 111.3281));
        idStations.add(new Station("ID_PNG", "Ponorogo", "Jawa Timur", "Indonesia", "Alun-Alun Ponorogo, Jawa Timur", -7.8667, 111.4667));
        idStations.add(new Station("ID_PCT", "Pacitan", "Jawa Timur", "Indonesia", "Pusat Kota Pacitan, Jawa Timur", -8.1945, 111.1025));
        idStations.add(new Station("ID_KDR", "Kediri", "Jawa Timur", "Indonesia", "Kota Kediri, Jawa Timur", -7.8166, 112.0114));
        idStations.add(new Station("ID_KDR_KAB", "Kab. Kediri (Pare / Ngasem)", "Jawa Timur", "Indonesia", "Pare / Ngasem, Kab. Kediri, Jawa Timur", -7.7667, 112.1969));
        idStations.add(new Station("ID_BLT", "Blitar", "Jawa Timur", "Indonesia", "Kota Blitar, Jawa Timur", -8.0955, 112.1609));
        idStations.add(new Station("ID_BLT_KAB", "Kab. Blitar (Kanigoro)", "Jawa Timur", "Indonesia", "Kanigoro, Kab. Blitar, Jawa Timur", -8.1303, 112.2189));
        idStations.add(new Station("ID_TLG", "Tulungagung", "Jawa Timur", "Indonesia", "Alun-Alun Tulungagung, Jawa Timur", -8.0657, 111.9026));
        idStations.add(new Station("ID_TRG", "Trenggalek", "Jawa Timur", "Indonesia", "Alun-Alun Trenggalek, Jawa Timur", -8.0504, 111.7081));
        idStations.add(new Station("ID_MLG", "Malang", "Jawa Timur", "Indonesia", "Alun-Alun Kota Malang, Jawa Timur", -7.9826, 112.6308));
        idStations.add(new Station("ID_MLG_KAB", "Kab. Malang (Kepanjen)", "Jawa Timur", "Indonesia", "Kepanjen, Kab. Malang, Jawa Timur", -8.1307, 112.5731));
        idStations.add(new Station("ID_BATU", "Batu", "Jawa Timur", "Indonesia", "Alun-Alun Kota Wisata Batu, Jawa Timur", -7.8671, 112.5239));
        idStations.add(new Station("ID_PSR", "Pasuruan", "Jawa Timur", "Indonesia", "Kota Pasuruan, Jawa Timur", -7.6453, 112.9075));
        idStations.add(new Station("ID_PSR_KAB", "Kab. Pasuruan (Bangil)", "Jawa Timur", "Indonesia", "Bangil, Kab. Pasuruan, Jawa Timur", -7.5969, 112.8181));
        idStations.add(new Station("ID_PBL", "Probolinggo", "Jawa Timur", "Indonesia", "Kota Probolinggo, Jawa Timur", -7.7543, 113.2159));
        idStations.add(new Station("ID_PBL_KAB", "Kab. Probolinggo (Kraksaan)", "Jawa Timur", "Indonesia", "Kraksaan, Kab. Probolinggo, Jawa Timur", -7.76, 113.415));
        idStations.add(new Station("ID_LMJ", "Lumajang", "Jawa Timur", "Indonesia", "Alun-Alun Lumajang, Jawa Timur", -8.1335, 113.2248));
        idStations.add(new Station("ID_JBR", "Jember", "Jawa Timur", "Indonesia", "Alun-Alun Jember, Jawa Timur", -8.1724, 113.6995));
        idStations.add(new Station("ID_BDW", "Bondowoso", "Jawa Timur", "Indonesia", "Alun-Alun Bondowoso, Jawa Timur", -7.9135, 113.8214));
        idStations.add(new Station("ID_STB", "Situbondo", "Jawa Timur", "Indonesia", "Alun-Alun Situbondo, Jawa Timur", -7.7062, 114.0098));
        idStations.add(new Station("ID_BWI", "Banyuwangi", "Jawa Timur", "Indonesia", "Pusat Kota Banyuwangi, Jawa Timur", -8.2192, 114.3691));
        idStations.add(new Station("ID_BKL", "Bangkalan", "Jawa Timur", "Indonesia", "Alun-Alun Bangkalan, Madura, Jawa Timur", -7.0455, 112.7453));
        idStations.add(new Station("ID_SMP", "Sampang", "Jawa Timur", "Indonesia", "Pusat Kota Sampang, Madura, Jawa Timur", -7.1872, 113.2394));
        idStations.add(new Station("ID_PMK", "Pamekasan", "Jawa Timur", "Indonesia", "Alun-Alun Pamekasan, Madura, Jawa Timur", -7.1568, 113.4746));
        idStations.add(new Station("ID_SMN", "Sumenep", "Jawa Timur", "Indonesia", "Pusat Kota Sumenep, Madura, Jawa Timur", -7.0167, 113.8667));
        idStations.add(new Station("ID_JKT", "Jakarta", "DKI Jakarta", "Indonesia", "Monas / Gambir, DKI Jakarta", -6.1754, 106.8272));
        idStations.add(new Station("ID_JKP", "Jakarta Pusat", "DKI Jakarta", "Indonesia", "Menteng / Tanah Abang, DKI Jakarta", -6.1805, 106.8284));
        idStations.add(new Station("ID_JKS", "Jakarta Selatan", "DKI Jakarta", "Indonesia", "Kebayoran Baru / Kemang, DKI Jakarta", -6.2615, 106.8106));
        idStations.add(new Station("ID_JKB", "Jakarta Barat", "DKI Jakarta", "Indonesia", "Kembangan / Grogol, DKI Jakarta", -6.1674, 106.7637));
        idStations.add(new Station("ID_JKT_TIM", "Jakarta Timur", "DKI Jakarta", "Indonesia", "Cakung / Jatinegara, DKI Jakarta", -6.225, 106.9004));
        idStations.add(new Station("ID_JKU", "Jakarta Utara", "DKI Jakarta", "Indonesia", "Kelapa Gading / Tanjung Priok, DKI Jakarta", -6.1384, 106.8639));
        idStations.add(new Station("ID_KEP_SERIBU", "Kepulauan Seribu", "DKI Jakarta", "Indonesia", "Pulau Pramuka, DKI Jakarta", -5.7447, 106.6136));
        idStations.add(new Station("ID_TNG", "Tangerang", "Banten", "Indonesia", "Pusat Kota Tangerang, Banten", -6.1783, 106.6319));
        idStations.add(new Station("ID_TANGSEL", "Tangerang Selatan", "Banten", "Indonesia", "BSD / Serpong, Tangerang Selatan, Banten", -6.2886, 106.7179));
        idStations.add(new Station("ID_TNG_KAB", "Kab. Tangerang (Tigaraksa)", "Banten", "Indonesia", "Tigaraksa / Cikupa, Kab. Tangerang, Banten", -6.2442, 106.4733));
        idStations.add(new Station("ID_SRG", "Serang", "Banten", "Indonesia", "Alun-Alun Kota Serang, Banten", -6.1149, 106.1502));
        idStations.add(new Station("ID_SRG_KAB", "Kab. Serang (Ciruas)", "Banten", "Indonesia", "Ciruas, Kab. Serang, Banten", -6.1264, 106.2386));
        idStations.add(new Station("ID_CLG", "Cilegon", "Banten", "Indonesia", "Pusat Kota Cilegon, Banten", -6.0025, 106.0111));
        idStations.add(new Station("ID_PDG_BTN", "Pandeglang", "Banten", "Indonesia", "Alun-Alun Pandeglang, Banten", -6.3086, 106.1067));
        idStations.add(new Station("ID_LBK", "Lebak (Rangkasbitung)", "Banten", "Indonesia", "Rangkasbitung, Kab. Lebak, Banten", -6.3578, 106.2519));
        idStations.add(new Station("ID_BDG", "Bandung", "Jawa Barat", "Indonesia", "Gedung Sate / Alun-Alun Kota Bandung, Jawa Barat", -6.9175, 107.6191));
        idStations.add(new Station("ID_BDG_KAB", "Kab. Bandung (Soreang)", "Jawa Barat", "Indonesia", "Soreang, Kab. Bandung, Jawa Barat", -7.0253, 107.5197));
        idStations.add(new Station("ID_KBB", "Bandung Barat (Ngamprah / Lembang)", "Jawa Barat", "Indonesia", "Ngamprah / Lembang, Bandung Barat, Jawa Barat", -6.8169, 107.6185));
        idStations.add(new Station("ID_CMH", "Cimahi", "Jawa Barat", "Indonesia", "Pusat Kota Cimahi, Jawa Barat", -6.8841, 107.5413));
        idStations.add(new Station("ID_SMD", "Sumedang", "Jawa Barat", "Indonesia", "Alun-Alun Sumedang, Jawa Barat", -6.8586, 107.9206));
        idStations.add(new Station("ID_GRT", "Garut", "Jawa Barat", "Indonesia", "Tarogong / Pusat Kota Garut, Jawa Barat", -7.2279, 107.9087));
        idStations.add(new Station("ID_TSM", "Tasikmalaya", "Jawa Barat", "Indonesia", "Kota Tasikmalaya, Jawa Barat", -7.3274, 108.2207));
        idStations.add(new Station("ID_TSM_KAB", "Kab. Tasikmalaya (Singaparna)", "Jawa Barat", "Indonesia", "Singaparna, Kab. Tasikmalaya, Jawa Barat", -7.3514, 108.1114));
        idStations.add(new Station("ID_CMS", "Ciamis", "Jawa Barat", "Indonesia", "Alun-Alun Ciamis, Jawa Barat", -7.3258, 108.3536));
        idStations.add(new Station("ID_BJR_JBR", "Banjar", "Jawa Barat", "Indonesia", "Kota Banjar, Jawa Barat", -7.37, 108.5342));
        idStations.add(new Station("ID_PND", "Pangandaran", "Jawa Barat", "Indonesia", "Pantai Pangandaran / Parigi, Jawa Barat", -7.6841, 108.6531));
        idStations.add(new Station("ID_BGR", "Bogor", "Jawa Barat", "Indonesia", "Kebun Raya / Kota Bogor, Jawa Barat", -6.5971, 106.806));
        idStations.add(new Station("ID_BGR_KAB", "Kab. Bogor (Cibinong)", "Jawa Barat", "Indonesia", "Cibinong, Kab. Bogor, Jawa Barat", -6.4816, 106.8542));
        idStations.add(new Station("ID_DPK", "Depok", "Jawa Barat", "Indonesia", "Margonda, Kota Depok, Jawa Barat", -6.4025, 106.7942));
        idStations.add(new Station("ID_BKS", "Bekasi", "Jawa Barat", "Indonesia", "Pusat Kota Bekasi, Jawa Barat", -6.2383, 106.9756));
        idStations.add(new Station("ID_BKS_KAB", "Kab. Bekasi (Cikarang)", "Jawa Barat", "Indonesia", "Cikarang, Kab. Bekasi, Jawa Barat", -6.2845, 107.1705));
        idStations.add(new Station("ID_KRW", "Karawang", "Jawa Barat", "Indonesia", "Pusat Kota Karawang, Jawa Barat", -6.3227, 107.3376));
        idStations.add(new Station("ID_PWK", "Purwakarta", "Jawa Barat", "Indonesia", "Situ Buleud / Purwakarta, Jawa Barat", -6.5569, 107.4433));
        idStations.add(new Station("ID_SBG", "Subang", "Jawa Barat", "Indonesia", "Alun-Alun Subang, Jawa Barat", -6.5714, 107.7586));
        idStations.add(new Station("ID_CJR", "Cianjur", "Jawa Barat", "Indonesia", "Alun-Alun Cianjur, Jawa Barat", -6.8168, 107.1425));
        idStations.add(new Station("ID_SKB", "Sukabumi", "Jawa Barat", "Indonesia", "Kota Sukabumi, Jawa Barat", -6.9277, 106.93));
        idStations.add(new Station("ID_SKB_KAB", "Kab. Sukabumi (Palabuhanratu)", "Jawa Barat", "Indonesia", "Palabuhanratu, Kab. Sukabumi, Jawa Barat", -6.9875, 106.5514));
        idStations.add(new Station("ID_CRB", "Cirebon", "Jawa Barat", "Indonesia", "Kota Cirebon, Jawa Barat", -6.732, 108.5523));
        idStations.add(new Station("ID_CRB_KAB", "Kab. Cirebon (Sumber)", "Jawa Barat", "Indonesia", "Sumber, Kab. Cirebon, Jawa Barat", -6.7625, 108.4831));
        idStations.add(new Station("ID_IDM", "Indramayu", "Jawa Barat", "Indonesia", "Alun-Alun Indramayu, Jawa Barat", -6.3276, 108.3249));
        idStations.add(new Station("ID_MJL", "Majalengka", "Jawa Barat", "Indonesia", "Alun-Alun Majalengka, Jawa Barat", -6.8364, 108.2278));
        idStations.add(new Station("ID_KNG", "Kuningan", "Jawa Barat", "Indonesia", "Alun-Alun Kuningan, Jawa Barat", -6.9761, 108.4831));
        idStations.add(new Station("ID_SMG", "Semarang", "Jawa Tengah", "Indonesia", "Simpang Lima, Kota Semarang, Jawa Tengah", -6.9932, 110.4203));
        idStations.add(new Station("ID_SMG_KAB", "Kab. Semarang (Ungaran)", "Jawa Tengah", "Indonesia", "Ungaran, Kab. Semarang, Jawa Tengah", -7.1397, 110.405));
        idStations.add(new Station("ID_SLT", "Salatiga", "Jawa Tengah", "Indonesia", "Pusat Kota Salatiga, Jawa Tengah", -7.3305, 110.5084));
        idStations.add(new Station("ID_KDL", "Kendal", "Jawa Tengah", "Indonesia", "Alun-Alun Kendal, Jawa Tengah", -6.9194, 110.2036));
        idStations.add(new Station("ID_DMK", "Demak", "Jawa Tengah", "Indonesia", "Alun-Alun Demak, Jawa Tengah", -6.8906, 110.6397));
        idStations.add(new Station("ID_GRB", "Grobogan (Purwodadi)", "Jawa Tengah", "Indonesia", "Purwodadi, Kab. Grobogan, Jawa Tengah", -7.0869, 110.9158));
        idStations.add(new Station("ID_KDS", "Kudus", "Jawa Tengah", "Indonesia", "Simpang Tujuh Kudus, Jawa Tengah", -6.8048, 110.8405));
        idStations.add(new Station("ID_JPR", "Jepara", "Jawa Tengah", "Indonesia", "Alun-Alun Jepara, Jawa Tengah", -6.5889, 110.6683));
        idStations.add(new Station("ID_PTI", "Pati", "Jawa Tengah", "Indonesia", "Alun-Alun Pati, Jawa Tengah", -6.7558, 111.0381));
        idStations.add(new Station("ID_RMB", "Rembang", "Jawa Tengah", "Indonesia", "Alun-Alun Rembang, Jawa Tengah", -6.7086, 111.3417));
        idStations.add(new Station("ID_BLR", "Blora", "Jawa Tengah", "Indonesia", "Alun-Alun Blora, Jawa Tengah", -6.9697, 111.4136));
        idStations.add(new Station("ID_CEPU", "Cepu", "Jawa Tengah", "Indonesia", "Kec. Cepu, Kab. Blora, Jawa Tengah", -7.1492, 111.5908));
        idStations.add(new Station("ID_SLO", "Surakarta (Solo)", "Jawa Tengah", "Indonesia", "Balai Kota Surakarta (Solo), Jawa Tengah", -7.5755, 110.8243));
        idStations.add(new Station("ID_SKH", "Sukoharjo", "Jawa Tengah", "Indonesia", "Alun-Alun Sukoharjo, Jawa Tengah", -7.6833, 110.8333));
        idStations.add(new Station("ID_KLT", "Klaten", "Jawa Tengah", "Indonesia", "Alun-Alun Klaten, Jawa Tengah", -7.7058, 110.6064));
        idStations.add(new Station("ID_BYL", "Boyolali", "Jawa Tengah", "Indonesia", "Pusat Kota Boyolali, Jawa Tengah", -7.5322, 110.5956));
        idStations.add(new Station("ID_SRG_JTG", "Sragen", "Jawa Tengah", "Indonesia", "Alun-Alun Sragen, Jawa Tengah", -7.4264, 111.0222));
        idStations.add(new Station("ID_KRA", "Karanganyar", "Jawa Tengah", "Indonesia", "Alun-Alun Karanganyar, Jawa Tengah", -7.5961, 110.9508));
        idStations.add(new Station("ID_WNG", "Wonogiri", "Jawa Tengah", "Indonesia", "Alun-Alun Wonogiri, Jawa Tengah", -7.8139, 110.9239));
        idStations.add(new Station("ID_MGL", "Magelang", "Jawa Tengah", "Indonesia", "Alun-Alun Kota Magelang, Jawa Tengah", -7.4797, 110.2177));
        idStations.add(new Station("ID_MGL_KAB", "Kab. Magelang (Mungkid / Borobudur)", "Jawa Tengah", "Indonesia", "Mungkid, Kab. Magelang, Jawa Tengah", -7.6079, 110.2038));
        idStations.add(new Station("ID_TMG", "Temanggung", "Jawa Tengah", "Indonesia", "Alun-Alun Temanggung, Jawa Tengah", -7.3167, 110.175));
        idStations.add(new Station("ID_WSB", "Wonosobo", "Jawa Tengah", "Indonesia", "Alun-Alun Wonosobo, Jawa Tengah", -7.3614, 109.9003));
        idStations.add(new Station("ID_PWR", "Purworejo", "Jawa Tengah", "Indonesia", "Alun-Alun Purworejo, Jawa Tengah", -7.7142, 110.0086));
        idStations.add(new Station("ID_KBM", "Kebumen", "Jawa Tengah", "Indonesia", "Alun-Alun Kebumen, Jawa Tengah", -7.6686, 109.6519));
        idStations.add(new Station("ID_BNA", "Banjarnegara", "Jawa Tengah", "Indonesia", "Alun-Alun Banjarnegara, Jawa Tengah", -7.3986, 109.6961));
        idStations.add(new Station("ID_PBG", "Purbalingga", "Jawa Tengah", "Indonesia", "Alun-Alun Purbalingga, Jawa Tengah", -7.3881, 109.3639));
        idStations.add(new Station("ID_BMS", "Banyumas (Purwokerto)", "Jawa Tengah", "Indonesia", "Alun-Alun Purwokerto, Kab. Banyumas, Jawa Tengah", -7.4244, 109.2302));
        idStations.add(new Station("ID_PWK_JTG", "Purwokerto", "Jawa Tengah", "Indonesia", "Pusat Kota Purwokerto, Jawa Tengah", -7.4244, 109.2302));
        idStations.add(new Station("ID_CLC", "Cilacap", "Jawa Tengah", "Indonesia", "Alun-Alun Cilacap, Jawa Tengah", -7.7276, 109.0069));
        idStations.add(new Station("ID_PKL", "Pekalongan", "Jawa Tengah", "Indonesia", "Kota Pekalongan, Jawa Tengah", -6.8886, 109.6753));
        idStations.add(new Station("ID_PKL_KAB", "Kab. Pekalongan (Kajen)", "Jawa Tengah", "Indonesia", "Kajen, Kab. Pekalongan, Jawa Tengah", -7.0275, 109.5828));
        idStations.add(new Station("ID_BTG", "Batang", "Jawa Tengah", "Indonesia", "Alun-Alun Batang, Jawa Tengah", -6.9081, 109.7303));
        idStations.add(new Station("ID_PML", "Pemalang", "Jawa Tengah", "Indonesia", "Alun-Alun Pemalang, Jawa Tengah", -6.8906, 109.3808));
        idStations.add(new Station("ID_TGL", "Tegal", "Jawa Tengah", "Indonesia", "Alun-Alun Kota Tegal, Jawa Tengah", -6.8694, 109.1402));
        idStations.add(new Station("ID_TGL_KAB", "Kab. Tegal (Slawi)", "Jawa Tengah", "Indonesia", "Slawi, Kab. Tegal, Jawa Tengah", -6.9839, 109.1369));
        idStations.add(new Station("ID_BBS", "Brebes", "Jawa Tengah", "Indonesia", "Alun-Alun Brebes, Jawa Tengah", -6.8706, 109.0422));
        idStations.add(new Station("ID_YGY", "Yogyakarta", "D.I. Yogyakarta", "Indonesia", "Malioboro / Tugu, Kota Yogyakarta", -7.7956, 110.3695));
        idStations.add(new Station("ID_SLM", "Sleman", "D.I. Yogyakarta", "Indonesia", "Depok / Beran, Kab. Sleman, D.I. Yogyakarta", -7.7161, 110.3556));
        idStations.add(new Station("ID_BTL", "Bantul", "D.I. Yogyakarta", "Indonesia", "Paseban, Kab. Bantul, D.I. Yogyakarta", -7.8878, 110.3289));
        idStations.add(new Station("ID_GNK", "Gunungkidul (Wonosari)", "D.I. Yogyakarta", "Indonesia", "Wonosari, Kab. Gunungkidul, D.I. Yogyakarta", -7.9669, 110.6025));
        idStations.add(new Station("ID_KLP", "Kulon Progo (Wates)", "D.I. Yogyakarta", "Indonesia", "Wates, Kab. Kulon Progo, D.I. Yogyakarta", -7.8575, 110.1583));
        idStations.add(new Station("ID_DPS", "Denpasar", "Bali", "Indonesia", "Renon / Puputan, Kota Denpasar, Bali", -8.6705, 115.2126));
        idStations.add(new Station("ID_BDG_BALI", "Badung (Mangupura / Kuta)", "Bali", "Indonesia", "Kuta / Nusa Dua, Kab. Badung, Bali", -8.6003, 115.1764));
        idStations.add(new Station("ID_KUTA", "Kuta", "Bali", "Indonesia", "Pantai Kuta, Kab. Badung, Bali", -8.7233, 115.1723));
        idStations.add(new Station("ID_UBUD", "Ubud", "Bali", "Indonesia", "Ubud, Kab. Gianyar, Bali", -8.5069, 115.2625));
        idStations.add(new Station("ID_GNY", "Gianyar", "Bali", "Indonesia", "Pusat Kota Gianyar, Bali", -8.5444, 115.3253));
        idStations.add(new Station("ID_TBN", "Tabanan", "Bali", "Indonesia", "Pusat Kota Tabanan, Bali", -8.5411, 115.1253));
        idStations.add(new Station("ID_KLK", "Klungkung (Semarapura)", "Bali", "Indonesia", "Semarapura, Kab. Klungkung, Bali", -8.5342, 115.4028));
        idStations.add(new Station("ID_BGL", "Bangli", "Bali", "Indonesia", "Pusat Kota Bangli, Bali", -8.4542, 115.3547));
        idStations.add(new Station("ID_KRA_BALI", "Karangasem (Amlapura)", "Bali", "Indonesia", "Amlapura, Kab. Karangasem, Bali", -8.4481, 115.6128));
        idStations.add(new Station("ID_BLL", "Buleleng (Singaraja)", "Bali", "Indonesia", "Singaraja, Kab. Buleleng, Bali", -8.112, 115.0882));
        idStations.add(new Station("ID_JMB_BALI", "Jembrana (Negara)", "Bali", "Indonesia", "Negara, Kab. Jembrana, Bali", -8.3569, 114.6231));
        idStations.add(new Station("ID_BNA_ACEH", "Banda Aceh", "Aceh", "Indonesia", "Baiturrahman, Kota Banda Aceh, Aceh", 5.5483, 95.3238));
        idStations.add(new Station("ID_SBG_ACEH", "Sabang", "Aceh", "Indonesia", "Pulau Weh, Kota Sabang, Aceh", 5.8933, 95.3214));
        idStations.add(new Station("ID_LSM", "Lhokseumawe", "Aceh", "Indonesia", "Kota Lhokseumawe, Aceh", 5.1801, 97.1507));
        idStations.add(new Station("ID_LGS", "Langsa", "Aceh", "Indonesia", "Kota Langsa, Aceh", 4.4683, 97.9683));
        idStations.add(new Station("ID_SBS", "Subulussalam", "Aceh", "Indonesia", "Kota Subulussalam, Aceh", 2.6414, 98.005));
        idStations.add(new Station("ID_ABES", "Aceh Besar (Jantho)", "Aceh", "Indonesia", "Kota Jantho, Kab. Aceh Besar, Aceh", 5.2831, 95.6147));
        idStations.add(new Station("ID_PID", "Pidie (Sigli)", "Aceh", "Indonesia", "Sigli, Kab. Pidie, Aceh", 5.3847, 95.9608));
        idStations.add(new Station("ID_PJA", "Pidie Jaya (Meureudu)", "Aceh", "Indonesia", "Meureudu, Kab. Pidie Jaya, Aceh", 5.2422, 96.2419));
        idStations.add(new Station("ID_BIR", "Bireuen", "Aceh", "Indonesia", "Pusat Kota Bireuen, Aceh", 5.2014, 96.7019));
        idStations.add(new Station("ID_AUT", "Aceh Utara (Lhoksukon)", "Aceh", "Indonesia", "Lhoksukon, Kab. Aceh Utara, Aceh", 5.0519, 97.3181));
        idStations.add(new Station("ID_ATIM", "Aceh Timur (Idi Rayeuk)", "Aceh", "Indonesia", "Idi Rayeuk, Kab. Aceh Timur, Aceh", 4.9506, 97.7689));
        idStations.add(new Station("ID_ATAM", "Aceh Tamiang (Karang Baru)", "Aceh", "Indonesia", "Karang Baru, Kab. Aceh Tamiang, Aceh", 4.2831, 98.0592));
        idStations.add(new Station("ID_ATENG", "Aceh Tengah (Takengon)", "Aceh", "Indonesia", "Takengon, Kab. Aceh Tengah, Aceh", 4.6306, 96.8442));
        idStations.add(new Station("ID_BMER", "Bener Meriah (Simpang Tiga Redelong)", "Aceh", "Indonesia", "Redelong, Kab. Bener Meriah, Aceh", 4.7258, 96.8617));
        idStations.add(new Station("ID_AGAR", "Aceh Tenggara (Kutacane)", "Aceh", "Indonesia", "Kutacane, Kab. Aceh Tenggara, Aceh", 3.4839, 97.8108));
        idStations.add(new Station("ID_GLUES", "Gayo Lues (Blangkejeren)", "Aceh", "Indonesia", "Blangkejeren, Kab. Gayo Lues, Aceh", 3.9822, 97.3428));
        idStations.add(new Station("ID_ABAR", "Aceh Barat (Meulaboh)", "Aceh", "Indonesia", "Meulaboh, Kab. Aceh Barat, Aceh", 4.145, 96.1264));
        idStations.add(new Station("ID_NRAYA", "Nagan Raya (Suka Makmue)", "Aceh", "Indonesia", "Suka Makmue, Kab. Nagan Raya, Aceh", 4.1678, 96.3239));
        idStations.add(new Station("ID_AJAYA", "Aceh Jaya (Calang)", "Aceh", "Indonesia", "Calang, Kab. Aceh Jaya, Aceh", 4.6347, 95.5819));
        idStations.add(new Station("ID_ABDYA", "Aceh Barat Daya (Blangpidie)", "Aceh", "Indonesia", "Blangpidie, Kab. Aceh Barat Daya, Aceh", 3.7433, 96.8339));
        idStations.add(new Station("ID_ASEL", "Aceh Selatan (Tapaktuan)", "Aceh", "Indonesia", "Tapaktuan, Kab. Aceh Selatan, Aceh", 3.2597, 97.1819));
        idStations.add(new Station("ID_ASING", "Aceh Singkil", "Aceh", "Indonesia", "Singkil, Kab. Aceh Singkil, Aceh", 2.2819, 97.7942));
        idStations.add(new Station("ID_SML", "Simeulue (Sinabang)", "Aceh", "Indonesia", "Sinabang, Kab. Simeulue, Aceh", 2.48, 96.3775));
        idStations.add(new Station("ID_MDN_SUMUT", "Medan", "Sumatera Utara", "Indonesia", "Lapangan Merdeka, Kota Medan, Sumatera Utara", 3.5952, 98.6722));
        idStations.add(new Station("ID_BNJ", "Binjai", "Sumatera Utara", "Indonesia", "Kota Binjai, Sumatera Utara", 3.6001, 98.4854));
        idStations.add(new Station("ID_TBT", "Tebing Tinggi", "Sumatera Utara", "Indonesia", "Kota Tebing Tinggi, Sumatera Utara", 3.3285, 99.1625));
        idStations.add(new Station("ID_PMS", "Pematangsiantar", "Sumatera Utara", "Indonesia", "Kota Pematangsiantar, Sumatera Utara", 2.9595, 99.0682));
        idStations.add(new Station("ID_TJB", "Tanjungbalai", "Sumatera Utara", "Indonesia", "Kota Tanjungbalai, Sumatera Utara", 2.9667, 99.8));
        idStations.add(new Station("ID_SBG_SUMUT", "Sibolga", "Sumatera Utara", "Indonesia", "Kota Sibolga, Sumatera Utara", 1.7427, 98.7792));
        idStations.add(new Station("ID_PSP", "Padangsidimpuan", "Sumatera Utara", "Indonesia", "Kota Padangsidimpuan, Sumatera Utara", 1.3789, 99.2719));
        idStations.add(new Station("ID_GST", "Gunungsitoli", "Sumatera Utara", "Indonesia", "Pulau Nias, Kota Gunungsitoli, Sumatera Utara", 1.2856, 97.6158));
        idStations.add(new Station("ID_DSD", "Deli Serdang (Lubuk Pakam)", "Sumatera Utara", "Indonesia", "Lubuk Pakam, Kab. Deli Serdang, Sumatera Utara", 3.5558, 98.8753));
        idStations.add(new Station("ID_SBD", "Serdang Bedagai (Sei Rampah)", "Sumatera Utara", "Indonesia", "Sei Rampah, Kab. Serdang Bedagai, Sumatera Utara", 3.4856, 99.1389));
        idStations.add(new Station("ID_LKT", "Langkat (Stabat)", "Sumatera Utara", "Indonesia", "Stabat, Kab. Langkat, Sumatera Utara", 3.7517, 98.4556));
        idStations.add(new Station("ID_KARO", "Karo (Kabanjahe / Berastagi)", "Sumatera Utara", "Indonesia", "Kabanjahe / Berastagi, Kab. Karo, Sumatera Utara", 3.1008, 98.4908));
        idStations.add(new Station("ID_SML_SUMUT", "Simalungun (Raya / Parapat)", "Sumatera Utara", "Indonesia", "Raya / Parapat, Kab. Simalungun, Sumatera Utara", 2.9667, 98.85));
        idStations.add(new Station("ID_ASH", "Asahan (Kisaran)", "Sumatera Utara", "Indonesia", "Kisaran, Kab. Asahan, Sumatera Utara", 2.9842, 99.6186));
        idStations.add(new Station("ID_BBARA", "Batu Bara (Lima Puluh)", "Sumatera Utara", "Indonesia", "Lima Puluh, Kab. Batu Bara, Sumatera Utara", 3.1678, 99.4239));
        idStations.add(new Station("ID_LBH", "Labuhanbatu (Rantauprapat)", "Sumatera Utara", "Indonesia", "Rantauprapat, Kab. Labuhanbatu, Sumatera Utara", 2.1, 99.8333));
        idStations.add(new Station("ID_LBHU", "Labuhanbatu Utara (Aek Kanopan)", "Sumatera Utara", "Indonesia", "Aek Kanopan, Kab. Labuhanbatu Utara, Sumatera Utara", 2.5642, 99.6525));
        idStations.add(new Station("ID_LBHS", "Labuhanbatu Selatan (Kotapinang)", "Sumatera Utara", "Indonesia", "Kotapinang, Kab. Labuhanbatu Selatan, Sumatera Utara", 1.8944, 100.0911));
        idStations.add(new Station("ID_TOBA", "Toba (Balige)", "Sumatera Utara", "Indonesia", "Balige, Danau Toba, Sumatera Utara", 2.3333, 99.0667));
        idStations.add(new Station("ID_SMS", "Samosir (Pangururan)", "Sumatera Utara", "Indonesia", "Pangururan, Pulau Samosir, Sumatera Utara", 2.6075, 98.6947));
        idStations.add(new Station("ID_HUMB", "Humbang Hasundutan (Dolok Sanggul)", "Sumatera Utara", "Indonesia", "Dolok Sanggul, Sumatera Utara", 2.2569, 98.7483));
        idStations.add(new Station("ID_TAPUT", "Tapanuli Utara (Tarutung)", "Sumatera Utara", "Indonesia", "Tarutung, Kab. Tapanuli Utara, Sumatera Utara", 2.0167, 98.9667));
        idStations.add(new Station("ID_TAPTENG", "Tapanuli Tengah (Pandan)", "Sumatera Utara", "Indonesia", "Pandan, Kab. Tapanuli Tengah, Sumatera Utara", 1.6908, 98.8269));
        idStations.add(new Station("ID_TAPSEL", "Tapanuli Selatan (Sipirok)", "Sumatera Utara", "Indonesia", "Sipirok, Kab. Tapanuli Selatan, Sumatera Utara", 1.6039, 99.2719));
        idStations.add(new Station("ID_MADINA", "Mandailing Natal (Panyabungan)", "Sumatera Utara", "Indonesia", "Panyabungan, Kab. Mandailing Natal, Sumatera Utara", 0.855, 99.5619));
        idStations.add(new Station("ID_PALAS", "Padang Lawas (Sibuhuan)", "Sumatera Utara", "Indonesia", "Sibuhuan, Kab. Padang Lawas, Sumatera Utara", 1.0539, 99.7497));
        idStations.add(new Station("ID_PALUTA", "Padang Lawas Utara (Gunung Tua)", "Sumatera Utara", "Indonesia", "Gunung Tua, Kab. Padang Lawas Utara, Sumatera Utara", 1.5472, 99.6275));
        idStations.add(new Station("ID_DAIRI", "Dairi (Sidikalang)", "Sumatera Utara", "Indonesia", "Sidikalang, Kab. Dairi, Sumatera Utara", 2.75, 98.3167));
        idStations.add(new Station("ID_PAKPAK", "Pakpak Bharat (Salak)", "Sumatera Utara", "Indonesia", "Salak, Kab. Pakpak Bharat, Sumatera Utara", 2.5597, 98.3278));
        idStations.add(new Station("ID_NIAS", "Nias (Gido)", "Sumatera Utara", "Indonesia", "Gido, Kab. Nias, Sumatera Utara", 1.1219, 97.7219));
        idStations.add(new Station("ID_NIAS_SEL", "Nias Selatan (Teluk Dalam)", "Sumatera Utara", "Indonesia", "Teluk Dalam, Kab. Nias Selatan, Sumatera Utara", 0.5653, 97.8214));
        idStations.add(new Station("ID_NIAS_UT", "Nias Utara (Lotu)", "Sumatera Utara", "Indonesia", "Lotu, Kab. Nias Utara, Sumatera Utara", 1.3831, 97.405));
        idStations.add(new Station("ID_NIAS_BAR", "Nias Barat (Lahomi)", "Sumatera Utara", "Indonesia", "Lahomi, Kab. Nias Barat, Sumatera Utara", 1.0597, 97.4792));
        idStations.add(new Station("ID_PDG", "Padang", "Sumatera Barat", "Indonesia", "Pantai Padang, Kota Padang, Sumatera Barat", -0.9471, 100.4172));
        idStations.add(new Station("ID_BKT", "Bukittinggi", "Sumatera Barat", "Indonesia", "Jam Gadang, Kota Bukittinggi, Sumatera Barat", -0.3056, 100.3692));
        idStations.add(new Station("ID_PYK", "Payakumbuh", "Sumatera Barat", "Indonesia", "Kota Payakumbuh, Sumatera Barat", -0.2223, 100.6327));
        idStations.add(new Station("ID_PDP", "Padang Panjang", "Sumatera Barat", "Indonesia", "Kota Padang Panjang, Sumatera Barat", -0.4628, 100.4081));
        idStations.add(new Station("ID_PRM", "Pariaman", "Sumatera Barat", "Indonesia", "Pantai Gandoriah, Kota Pariaman, Sumatera Barat", -0.6264, 100.1205));
        idStations.add(new Station("ID_SLK", "Solok", "Sumatera Barat", "Indonesia", "Kota Solok, Sumatera Barat", -0.7931, 100.6561));
        idStations.add(new Station("ID_SLK_KAB", "Kab. Solok (Arosuka)", "Sumatera Barat", "Indonesia", "Arosuka, Kab. Solok, Sumatera Barat", -0.9428, 100.5989));
        idStations.add(new Station("ID_SWL", "Sawahlunto", "Sumatera Barat", "Indonesia", "Kota Sawahlunto, Sumatera Barat", -0.6819, 100.7789));
        idStations.add(new Station("ID_AGAM", "Agam (Lubuk Basung)", "Sumatera Barat", "Indonesia", "Lubuk Basung, Kab. Agam, Sumatera Barat", -0.3133, 100.0322));
        idStations.add(new Station("ID_TDTR", "Tanah Datar (Batusangkar)", "Sumatera Barat", "Indonesia", "Batusangkar, Kab. Tanah Datar, Sumatera Barat", -0.4567, 100.5919));
        idStations.add(new Station("ID_LPK", "Lima Puluh Kota (Sarilamak)", "Sumatera Barat", "Indonesia", "Sarilamak, Kab. Lima Puluh Kota, Sumatera Barat", -0.1583, 100.6639));
        idStations.add(new Station("ID_PDPR", "Padang Pariaman (Parit Malintang)", "Sumatera Barat", "Indonesia", "Parit Malintang, Kab. Padang Pariaman, Sumatera Barat", -0.625, 100.2583));
        idStations.add(new Station("ID_PSSL", "Pesisir Selatan (Painan)", "Sumatera Barat", "Indonesia", "Painan, Kab. Pesisir Selatan, Sumatera Barat", -1.35, 100.5667));
        idStations.add(new Station("ID_SLKS", "Solok Selatan (Padang Aro)", "Sumatera Barat", "Indonesia", "Padang Aro, Kab. Solok Selatan, Sumatera Barat", -1.5644, 101.2647));
        idStations.add(new Station("ID_SJJ", "Sijunjung (Muaro Sijunjung)", "Sumatera Barat", "Indonesia", "Muaro Sijunjung, Sumatera Barat", -0.6928, 100.9539));
        idStations.add(new Station("ID_DHR", "Dharmasraya (Pulau Punjung)", "Sumatera Barat", "Indonesia", "Pulau Punjung, Kab. Dharmasraya, Sumatera Barat", -0.9667, 101.4833));
        idStations.add(new Station("ID_PSM", "Pasaman (Lubuk Sikaping)", "Sumatera Barat", "Indonesia", "Lubuk Sikaping, Kab. Pasaman, Sumatera Barat", 0.1306, 100.1672));
        idStations.add(new Station("ID_PSMB", "Pasaman Barat (Simpang Empat)", "Sumatera Barat", "Indonesia", "Simpang Empat, Kab. Pasaman Barat, Sumatera Barat", 0.0881, 99.8219));
        idStations.add(new Station("ID_MTW", "Kepulauan Mentawai (Tuapejat)", "Sumatera Barat", "Indonesia", "Tuapejat, Kep. Mentawai, Sumatera Barat", -2.0297, 99.5886));
        idStations.add(new Station("ID_PKU", "Pekanbaru", "Riau", "Indonesia", "Pusat Kota Pekanbaru, Riau", 0.5071, 101.4478));
        idStations.add(new Station("ID_DMI", "Dumai", "Riau", "Indonesia", "Kota Dumai, Riau", 1.6675, 101.4478));
        idStations.add(new Station("ID_KPR", "Kampar (Bangkinang)", "Riau", "Indonesia", "Bangkinang, Kab. Kampar, Riau", 0.3364, 101.0253));
        idStations.add(new Station("ID_BKS_RIAU", "Bengkalis", "Riau", "Indonesia", "Pusat Kota Bengkalis, Riau", 1.4819, 102.1319));
        idStations.add(new Station("ID_SIAK", "Siak (Siak Sri Indrapura)", "Riau", "Indonesia", "Siak Sri Indrapura, Kab. Siak, Riau", 0.7933, 102.0497));
        idStations.add(new Station("ID_PLW", "Pelalawan (Pangkalan Kerinci)", "Riau", "Indonesia", "Pangkalan Kerinci, Kab. Pelalawan, Riau", 0.4025, 101.8556));
        idStations.add(new Station("ID_INHU", "Indragiri Hulu (Rengat)", "Riau", "Indonesia", "Rengat, Kab. Indragiri Hulu, Riau", -0.375, 102.5447));
        idStations.add(new Station("ID_INHIL", "Indragiri Hilir (Tembilahan)", "Riau", "Indonesia", "Tembilahan, Kab. Indragiri Hilir, Riau", -0.3186, 103.1558));
        idStations.add(new Station("ID_KUANSING", "Kuantan Singingi (Teluk Kuantan)", "Riau", "Indonesia", "Teluk Kuantan, Kab. Kuantan Singingi, Riau", -0.5256, 101.5644));
        idStations.add(new Station("ID_ROHUL", "Rokan Hulu (Pasir Pengaraian)", "Riau", "Indonesia", "Pasir Pengaraian, Kab. Rokan Hulu, Riau", 0.8719, 100.3019));
        idStations.add(new Station("ID_ROHIL", "Rokan Hilir (Bagansiapiapi)", "Riau", "Indonesia", "Bagansiapiapi, Kab. Rokan Hilir, Riau", 2.1592, 100.8069));
        idStations.add(new Station("ID_MRNT", "Kepulauan Meranti (Selatpanjang)", "Riau", "Indonesia", "Selatpanjang, Kep. Meranti, Riau", 1.0119, 102.7097));
        idStations.add(new Station("ID_BTM", "Batam", "Kepulauan Riau", "Indonesia", "Batam Centre / Nagoya, Kepulauan Riau", 1.1301, 104.0529));
        idStations.add(new Station("ID_TPI", "Tanjungpinang", "Kepulauan Riau", "Indonesia", "Pulau Bintan, Kota Tanjungpinang, Kepulauan Riau", 0.9186, 104.4467));
        idStations.add(new Station("ID_BNT_KEPRI", "Bintan (Bandar Seri Bentan)", "Kepulauan Riau", "Indonesia", "Bandar Seri Bentan, Kab. Bintan, Kepulauan Riau", 1.0828, 104.4819));
        idStations.add(new Station("ID_KRM", "Karimun (Tanjung Balai Karimun)", "Kepulauan Riau", "Indonesia", "Tanjung Balai Karimun, Kepulauan Riau", 0.9922, 103.4331));
        idStations.add(new Station("ID_NTN", "Natuna (Ranai)", "Kepulauan Riau", "Indonesia", "Ranai, Kab. Natuna, Kepulauan Riau", 3.9408, 108.3847));
        idStations.add(new Station("ID_LNG", "Lingga (Daik)", "Kepulauan Riau", "Indonesia", "Daik, Kab. Lingga, Kepulauan Riau", -0.2147, 104.6231));
        idStations.add(new Station("ID_ANM", "Kepulauan Anambas (Tarempa)", "Kepulauan Riau", "Indonesia", "Tarempa, Kep. Anambas, Kepulauan Riau", 3.2144, 106.2189));
        idStations.add(new Station("ID_JMB", "Jambi", "Jambi", "Indonesia", "Gentala Arasy, Kota Jambi, Jambi", -1.6101, 103.6131));
        idStations.add(new Station("ID_SPN", "Sungai Penuh", "Jambi", "Indonesia", "Kota Sungai Penuh, Jambi", -2.0628, 101.3917));
        idStations.add(new Station("ID_MJB", "Muaro Jambi (Sengeti)", "Jambi", "Indonesia", "Sengeti, Kab. Muaro Jambi, Jambi", -1.4667, 103.5167));
        idStations.add(new Station("ID_BTH", "Batanghari (Muara Bulian)", "Jambi", "Indonesia", "Muara Bulian, Kab. Batanghari, Jambi", -1.7206, 103.2686));
        idStations.add(new Station("ID_TJB_BAR", "Tanjung Jabung Barat (Kuala Tungkal)", "Jambi", "Indonesia", "Kuala Tungkal, Jambi", -0.8167, 103.4667));
        idStations.add(new Station("ID_TJB_TIM", "Tanjung Jabung Timur (Muara Sabak)", "Jambi", "Indonesia", "Muara Sabak, Jambi", -1.1319, 103.8542));
        idStations.add(new Station("ID_BGO", "Bungo (Muara Bungo)", "Jambi", "Indonesia", "Muara Bungo, Kab. Bungo, Jambi", -1.5036, 102.1153));
        idStations.add(new Station("ID_TEBO", "Tebo (Muara Tebo)", "Jambi", "Indonesia", "Muara Tebo, Kab. Tebo, Jambi", -1.4547, 102.4358));
        idStations.add(new Station("ID_MRG", "Merangin (Bangko)", "Jambi", "Indonesia", "Bangko, Kab. Merangin, Jambi", -2.0758, 102.2731));
        idStations.add(new Station("ID_SRL", "Sarolangun", "Jambi", "Indonesia", "Pusat Kota Sarolangun, Jambi", -2.3, 102.7167));
        idStations.add(new Station("ID_KRC", "Kerinci (Siulak)", "Jambi", "Indonesia", "Siulak / Kayu Aro, Kab. Kerinci, Jambi", -1.9667, 101.35));
        idStations.add(new Station("ID_PLM", "Palembang", "Sumatera Selatan", "Indonesia", "Jembatan Ampera, Kota Palembang, Sumatera Selatan", -2.9761, 104.7754));
        idStations.add(new Station("ID_PBM", "Prabumulih", "Sumatera Selatan", "Indonesia", "Kota Prabumulih, Sumatera Selatan", -3.4328, 104.2356));
        idStations.add(new Station("ID_LLG", "Lubuklinggau", "Sumatera Selatan", "Indonesia", "Kota Lubuklinggau, Sumatera Selatan", -3.2967, 102.8617));
        idStations.add(new Station("ID_PGA", "Pagar Alam", "Sumatera Selatan", "Indonesia", "Gunung Dempo, Kota Pagar Alam, Sumatera Selatan", -4.0208, 103.2519));
        idStations.add(new Station("ID_BYS", "Banyuasin (Pangkalan Balai)", "Sumatera Selatan", "Indonesia", "Pangkalan Balai, Kab. Banyuasin, Sumatera Selatan", -2.8833, 104.3833));
        idStations.add(new Station("ID_MUBA", "Musi Banyuasin (Sekayu)", "Sumatera Selatan", "Indonesia", "Sekayu, Kab. Musi Banyuasin, Sumatera Selatan", -2.8886, 103.8358));
        idStations.add(new Station("ID_OI", "Ogan Ilir (Indralaya)", "Sumatera Selatan", "Indonesia", "Indralaya, Kab. Ogan Ilir, Sumatera Selatan", -3.2389, 104.6597));
        idStations.add(new Station("ID_OKI", "Ogan Komering Ilir (Kayuagung)", "Sumatera Selatan", "Indonesia", "Kayuagung, Kab. OKI, Sumatera Selatan", -3.3917, 104.8306));
        idStations.add(new Station("ID_OKU", "Ogan Komering Ulu (Baturaja)", "Sumatera Selatan", "Indonesia", "Baturaja, Kab. OKU, Sumatera Selatan", -4.1289, 104.1686));
        idStations.add(new Station("ID_OKUT", "OKU Timur (Martapura)", "Sumatera Selatan", "Indonesia", "Martapura, Kab. OKU Timur, Sumatera Selatan", -4.3197, 104.3519));
        idStations.add(new Station("ID_OKUS", "OKU Selatan (Muaradua)", "Sumatera Selatan", "Indonesia", "Muaradua, Kab. OKU Selatan, Sumatera Selatan", -4.5572, 104.0628));
        idStations.add(new Station("ID_MRE", "Muara Enim", "Sumatera Selatan", "Indonesia", "Pusat Kota Muara Enim, Sumatera Selatan", -3.6531, 103.7747));
        idStations.add(new Station("ID_LHT", "Lahat", "Sumatera Selatan", "Indonesia", "Pusat Kota Lahat, Sumatera Selatan", -3.7931, 103.5419));
        idStations.add(new Station("ID_ELW", "Empat Lawang (Tebing Tinggi)", "Sumatera Selatan", "Indonesia", "Tebing Tinggi, Kab. Empat Lawang, Sumatera Selatan", -3.5919, 103.0842));
        idStations.add(new Station("ID_MURA", "Musi Rawas (Muara Beliti)", "Sumatera Selatan", "Indonesia", "Muara Beliti, Kab. Musi Rawas, Sumatera Selatan", -3.2536, 103.0189));
        idStations.add(new Station("ID_MURATARA", "Musi Rawas Utara (Rupit)", "Sumatera Selatan", "Indonesia", "Rupit, Kab. Musi Rawas Utara, Sumatera Selatan", -2.8153, 102.8719));
        idStations.add(new Station("ID_PALI", "PALI (Talang Ubi)", "Sumatera Selatan", "Indonesia", "Talang Ubi, Kab. PALI, Sumatera Selatan", -3.2981, 103.8542));
        idStations.add(new Station("ID_PGK", "Pangkalpinang", "Kep. Bangka Belitung", "Indonesia", "Alun-Alun Taman Merdeka, Pangkalpinang", -2.1316, 106.1169));
        idStations.add(new Station("ID_BGK", "Bangka (Sungailiat)", "Kep. Bangka Belitung", "Indonesia", "Sungailiat, Kab. Bangka, Bangka Belitung", -1.8561, 106.1192));
        idStations.add(new Station("ID_BGK_BAR", "Bangka Barat (Mentok)", "Kep. Bangka Belitung", "Indonesia", "Mentok, Kab. Bangka Barat, Bangka Belitung", -2.0653, 105.1631));
        idStations.add(new Station("ID_BGK_TGH", "Bangka Tengah (Koba)", "Kep. Bangka Belitung", "Indonesia", "Koba, Kab. Bangka Tengah, Bangka Belitung", -2.4939, 106.4119));
        idStations.add(new Station("ID_BGK_SEL", "Bangka Selatan (Toboali)", "Kep. Bangka Belitung", "Indonesia", "Toboali, Kab. Bangka Selatan, Bangka Belitung", -3.0097, 106.4539));
        idStations.add(new Station("ID_BLT_BABEL", "Belitung (Tanjungpandan)", "Kep. Bangka Belitung", "Indonesia", "Tanjungpandan, Pulau Belitung", -2.7419, 107.6347));
        idStations.add(new Station("ID_BLT_TIM", "Belitung Timur (Manggar)", "Kep. Bangka Belitung", "Indonesia", "Manggar, Kab. Belitung Timur", -2.8769, 108.2739));
        idStations.add(new Station("ID_BGL_KOTA", "Bengkulu", "Bengkulu", "Indonesia", "Pantai Panjang, Kota Bengkulu", -3.7928, 102.2608));
        idStations.add(new Station("ID_RLB", "Rejang Lebong (Curup)", "Bengkulu", "Indonesia", "Curup, Kab. Rejang Lebong, Bengkulu", -3.4667, 102.5333));
        idStations.add(new Station("ID_KPH", "Kepahiang", "Bengkulu", "Indonesia", "Pusat Kota Kepahiang, Bengkulu", -3.605, 102.5642));
        idStations.add(new Station("ID_LBG", "Lebong (Tubei)", "Bengkulu", "Indonesia", "Tubei, Kab. Lebong, Bengkulu", -3.1289, 102.2019));
        idStations.add(new Station("ID_BGLU", "Bengkulu Utara (Arga Makmur)", "Bengkulu", "Indonesia", "Arga Makmur, Kab. Bengkulu Utara", -3.4333, 102.1833));
        idStations.add(new Station("ID_BGLT", "Bengkulu Tengah (Karang Tinggi)", "Bengkulu", "Indonesia", "Karang Tinggi, Kab. Bengkulu Tengah", -3.7042, 102.4167));
        idStations.add(new Station("ID_BGLS", "Bengkulu Selatan (Manna)", "Bengkulu", "Indonesia", "Manna, Kab. Bengkulu Selatan, Bengkulu", -4.455, 102.9108));
        idStations.add(new Station("ID_SLM_BGL", "Seluma (Tais)", "Bengkulu", "Indonesia", "Tais, Kab. Seluma, Bengkulu", -4.07, 102.5597));
        idStations.add(new Station("ID_KAUR", "Kaur (Bintuhan)", "Bengkulu", "Indonesia", "Bintuhan, Kab. Kaur, Bengkulu", -4.795, 103.3442));
        idStations.add(new Station("ID_MKM", "Mukomuko", "Bengkulu", "Indonesia", "Pusat Kota Mukomuko, Bengkulu", -2.5736, 101.1136));
        idStations.add(new Station("ID_BDL", "Bandar Lampung", "Lampung", "Indonesia", "Tanjung Karang, Kota Bandar Lampung, Lampung", -5.3971, 105.2668));
        idStations.add(new Station("ID_MTR_LPG", "Metro", "Lampung", "Indonesia", "Pusat Kota Metro, Lampung", -5.1131, 105.3067));
        idStations.add(new Station("ID_LPGS", "Lampung Selatan (Kalianda)", "Lampung", "Indonesia", "Kalianda, Kab. Lampung Selatan, Lampung", -5.7364, 105.5922));
        idStations.add(new Station("ID_LPGT", "Lampung Tengah (Gunung Sugih)", "Lampung", "Indonesia", "Gunung Sugih, Kab. Lampung Tengah, Lampung", -4.9767, 105.2147));
        idStations.add(new Station("ID_LPGU", "Lampung Utara (Kotabumi)", "Lampung", "Indonesia", "Kotabumi, Kab. Lampung Utara, Lampung", -4.825, 104.8817));
        idStations.add(new Station("ID_LPGB", "Lampung Barat (Liwa)", "Lampung", "Indonesia", "Liwa, Kab. Lampung Barat, Lampung", -5.0258, 104.0736));
        idStations.add(new Station("ID_LPGTIM", "Lampung Timur (Sukadana)", "Lampung", "Indonesia", "Sukadana, Kab. Lampung Timur, Lampung", -5.0583, 105.5417));
        idStations.add(new Station("ID_TGM", "Tanggamus (Kota Agung)", "Lampung", "Indonesia", "Kota Agung, Kab. Tanggamus, Lampung", -5.4939, 104.6231));
        idStations.add(new Station("ID_PSW", "Pesawaran (Gedong Tataan)", "Lampung", "Indonesia", "Gedong Tataan, Kab. Pesawaran, Lampung", -5.3889, 105.075));
        idStations.add(new Station("ID_PRW", "Pringsewu", "Lampung", "Indonesia", "Pusat Kota Pringsewu, Lampung", -5.3581, 104.9744));
        idStations.add(new Station("ID_WKN", "Way Kanan (Blambangan Umpu)", "Lampung", "Indonesia", "Blambangan Umpu, Kab. Way Kanan, Lampung", -4.5042, 104.5278));
        idStations.add(new Station("ID_TLB", "Tulang Bawang (Menggala)", "Lampung", "Indonesia", "Menggala, Kab. Tulang Bawang, Lampung", -4.475, 105.2417));
        idStations.add(new Station("ID_TBB", "Tulang Bawang Barat (Panaragan)", "Lampung", "Indonesia", "Panaragan, Kab. Tulang Bawang Barat, Lampung", -4.5361, 105.0528));
        idStations.add(new Station("ID_MSJ", "Mesuji", "Lampung", "Indonesia", "Wiralaga Mulya, Kab. Mesuji, Lampung", -4.0439, 105.4019));
        idStations.add(new Station("ID_PSB", "Pesisir Barat (Krui)", "Lampung", "Indonesia", "Krui, Kab. Pesisir Barat, Lampung", -5.1917, 103.9319));
        idStations.add(new Station("ID_MTR", "Mataram", "Nusa Tenggara Barat", "Indonesia", "Pulau Lombok, Kota Mataram, NTB", -8.5833, 116.1167));
        idStations.add(new Station("ID_BIMA", "Bima", "Nusa Tenggara Barat", "Indonesia", "Kota Bima, Pulau Sumbawa, NTB", -8.46, 118.7267));
        idStations.add(new Station("ID_BIMA_KAB", "Kab. Bima (Woha)", "Nusa Tenggara Barat", "Indonesia", "Woha, Kab. Bima, NTB", -8.5833, 118.6833));
        idStations.add(new Station("ID_LOBAR", "Lombok Barat (Gerung / Senggigi)", "Nusa Tenggara Barat", "Indonesia", "Gerung / Senggigi, Lombok Barat, NTB", -8.6811, 116.1239));
        idStations.add(new Station("ID_LOTENG", "Lombok Tengah (Praya / Mandalika)", "Nusa Tenggara Barat", "Indonesia", "Praya / Mandalika, Lombok Tengah, NTB", -8.7056, 116.2711));
        idStations.add(new Station("ID_LOTIM", "Lombok Timur (Selong)", "Nusa Tenggara Barat", "Indonesia", "Selong, Kab. Lombok Timur, NTB", -8.6472, 116.5333));
        idStations.add(new Station("ID_LOUT", "Lombok Utara (Tanjung / Gili)", "Nusa Tenggara Barat", "Indonesia", "Tanjung, Kab. Lombok Utara, NTB", -8.3547, 116.1558));
        idStations.add(new Station("ID_SBW", "Sumbawa (Sumbawa Besar)", "Nusa Tenggara Barat", "Indonesia", "Sumbawa Besar, Kab. Sumbawa, NTB", -8.4931, 117.4203));
        idStations.add(new Station("ID_SBWB", "Sumbawa Barat (Taliwang)", "Nusa Tenggara Barat", "Indonesia", "Taliwang, Kab. Sumbawa Barat, NTB", -8.7478, 116.8525));
        idStations.add(new Station("ID_DMP", "Dompu", "Nusa Tenggara Barat", "Indonesia", "Pusat Kota Dompu, NTB", -8.5356, 118.4633));
        idStations.add(new Station("ID_KPG", "Kupang", "Nusa Tenggara Timur", "Indonesia", "Kota Kupang, Pulau Timor, NTT", -10.1772, 123.607));
        idStations.add(new Station("ID_KPG_KAB", "Kab. Kupang (Oelamasi)", "Nusa Tenggara Timur", "Indonesia", "Oelamasi, Kab. Kupang, NTT", -10.0833, 123.8667));
        idStations.add(new Station("ID_LBJ", "Labuan Bajo (Manggarai Barat)", "Nusa Tenggara Timur", "Indonesia", "Labuan Bajo, Kab. Manggarai Barat, NTT", -8.4964, 119.8877));
        idStations.add(new Station("ID_MGR", "Manggarai (Ruteng)", "Nusa Tenggara Timur", "Indonesia", "Ruteng, Kab. Manggarai, NTT", -8.6136, 120.4644));
        idStations.add(new Station("ID_MGRT", "Manggarai Timur (Borong)", "Nusa Tenggara Timur", "Indonesia", "Borong, Kab. Manggarai Timur, NTT", -8.8147, 120.6083));
        idStations.add(new Station("ID_NGD", "Ngada (Bajawa)", "Nusa Tenggara Timur", "Indonesia", "Bajawa, Kab. Ngada, NTT", -8.7856, 120.9719));
        idStations.add(new Station("ID_NGK_NTT", "Nagekeo (Mbay)", "Nusa Tenggara Timur", "Indonesia", "Mbay, Kab. Nagekeo, NTT", -8.5667, 121.2667));
        idStations.add(new Station("ID_ENDE", "Ende", "Nusa Tenggara Timur", "Indonesia", "Pusat Kota Ende / Kelimutu, NTT", -8.8433, 121.6622));
        idStations.add(new Station("ID_SIKKA", "Sikka (Maumere)", "Nusa Tenggara Timur", "Indonesia", "Maumere, Kab. Sikka, NTT", -8.6189, 122.2122));
        idStations.add(new Station("ID_FLOTIM", "Flores Timur (Larantuka)", "Nusa Tenggara Timur", "Indonesia", "Larantuka, Kab. Flores Timur, NTT", -8.3422, 122.9814));
        idStations.add(new Station("ID_LMB", "Lembata (Lewoleba)", "Nusa Tenggara Timur", "Indonesia", "Lewoleba, Kab. Lembata, NTT", -8.3739, 123.4158));
        idStations.add(new Station("ID_ALOR", "Alor (Kalabahi)", "Nusa Tenggara Timur", "Indonesia", "Kalabahi, Kab. Alor, NTT", -8.2167, 124.5167));
        idStations.add(new Station("ID_BELU", "Belu (Atambua)", "Nusa Tenggara Timur", "Indonesia", "Atambua, Kab. Belu, NTT", -9.1061, 124.8925));
        idStations.add(new Station("ID_MLK_NTT", "Malaka (Betun)", "Nusa Tenggara Timur", "Indonesia", "Betun, Kab. Malaka, NTT", -9.5667, 124.9));
        idStations.add(new Station("ID_TTU", "Timor Tengah Utara (Kefamenanu)", "Nusa Tenggara Timur", "Indonesia", "Kefamenanu, Kab. TTU, NTT", -9.4467, 124.4781));
        idStations.add(new Station("ID_TTS", "Timor Tengah Selatan (Soe)", "Nusa Tenggara Timur", "Indonesia", "Soe, Kab. TTS, NTT", -9.8606, 124.2839));
        idStations.add(new Station("ID_ROTE", "Rote Ndao (Baa)", "Nusa Tenggara Timur", "Indonesia", "Baa, Pulau Rote, NTT", -10.73, 123.04));
        idStations.add(new Station("ID_SABU", "Sabu Raijua (Seba)", "Nusa Tenggara Timur", "Indonesia", "Seba, Kab. Sabu Raijua, NTT", -10.495, 121.8417));
        idStations.add(new Station("ID_SMBT", "Sumba Timur (Waingapu)", "Nusa Tenggara Timur", "Indonesia", "Waingapu, Kab. Sumba Timur, NTT", -9.6567, 120.2642));
        idStations.add(new Station("ID_SMBB", "Sumba Barat (Waikabubak)", "Nusa Tenggara Timur", "Indonesia", "Waikabubak, Kab. Sumba Barat, NTT", -9.6306, 119.4125));
        idStations.add(new Station("ID_SMBTG", "Sumba Tengah (Waibakul)", "Nusa Tenggara Timur", "Indonesia", "Waibakul, Kab. Sumba Tengah, NTT", -9.6194, 119.6083));
        idStations.add(new Station("ID_SMBBD", "Sumba Barat Daya (Tambolaka)", "Nusa Tenggara Timur", "Indonesia", "Tambolaka, Kab. Sumba Barat Daya, NTT", -9.4319, 119.2319));
        idStations.add(new Station("ID_PNK", "Pontianak", "Kalimantan Barat", "Indonesia", "Tugu Khatulistiwa, Kota Pontianak, Kalimantan Barat", -0.0263, 109.3425));
        idStations.add(new Station("ID_SKW", "Singkawang", "Kalimantan Barat", "Indonesia", "Kota Singkawang, Kalimantan Barat", 0.9073, 108.9872));
        idStations.add(new Station("ID_KBR", "Kubu Raya (Sungai Raya)", "Kalimantan Barat", "Indonesia", "Sungai Raya, Kab. Kubu Raya, Kalimantan Barat", -0.1039, 109.3958));
        idStations.add(new Station("ID_MPW", "Mempawah", "Kalimantan Barat", "Indonesia", "Pusat Kota Mempawah, Kalimantan Barat", 0.3647, 108.9597));
        idStations.add(new Station("ID_SBS_KALBAR", "Sambas", "Kalimantan Barat", "Indonesia", "Pusat Kota Sambas, Kalimantan Barat", 1.3625, 109.3075));
        idStations.add(new Station("ID_BGK_KALBAR", "Bengkayang", "Kalimantan Barat", "Indonesia", "Pusat Kota Bengkayang, Kalimantan Barat", 0.8214, 109.4808));
        idStations.add(new Station("ID_LDK", "Landak (Ngabang)", "Kalimantan Barat", "Indonesia", "Ngabang, Kab. Landak, Kalimantan Barat", 0.3842, 109.9575));
        idStations.add(new Station("ID_SGU", "Sanggau", "Kalimantan Barat", "Indonesia", "Pusat Kota Sanggau, Kalimantan Barat", 0.1222, 110.5889));
        idStations.add(new Station("ID_SKD", "Sekadau", "Kalimantan Barat", "Indonesia", "Pusat Kota Sekadau, Kalimantan Barat", 0.0142, 110.9008));
        idStations.add(new Station("ID_STG", "Sintang", "Kalimantan Barat", "Indonesia", "Pusat Kota Sintang, Kalimantan Barat", 0.0681, 111.4981));
        idStations.add(new Station("ID_MLW", "Melawi (Nanga Pinoh)", "Kalimantan Barat", "Indonesia", "Nanga Pinoh, Kab. Melawi, Kalimantan Barat", -0.3347, 111.7419));
        idStations.add(new Station("ID_KPH_KALBAR", "Kapuas Hulu (Putussibau)", "Kalimantan Barat", "Indonesia", "Putussibau, Kab. Kapuas Hulu, Kalimantan Barat", 0.8617, 112.9253));
        idStations.add(new Station("ID_KTP", "Ketapang", "Kalimantan Barat", "Indonesia", "Pusat Kota Ketapang, Kalimantan Barat", -1.8386, 109.9722));
        idStations.add(new Station("ID_KYU", "Kayong Utara (Sukadana)", "Kalimantan Barat", "Indonesia", "Sukadana, Kab. Kayong Utara, Kalimantan Barat", -1.2397, 109.9519));
        idStations.add(new Station("ID_PLK", "Palangka Raya", "Kalimantan Tengah", "Indonesia", "Bundaran Besar, Kota Palangka Raya, Kalimantan Tengah", -2.2136, 113.9108));
        idStations.add(new Station("ID_KOBAR", "Kotawaringin Barat (Pangkalan Bun)", "Kalimantan Tengah", "Indonesia", "Pangkalan Bun, Kalimantan Tengah", -2.6833, 111.6167));
        idStations.add(new Station("ID_KOTIM", "Kotawaringin Timur (Sampit)", "Kalimantan Tengah", "Indonesia", "Sampit, Kab. Kotawaringin Timur, Kalimantan Tengah", -2.5325, 112.9597));
        idStations.add(new Station("ID_KPS", "Kapuas (Kuala Kapuas)", "Kalimantan Tengah", "Indonesia", "Kuala Kapuas, Kab. Kapuas, Kalimantan Tengah", -3.0092, 114.3875));
        idStations.add(new Station("ID_PLP", "Pulang Pisau", "Kalimantan Tengah", "Indonesia", "Pusat Kota Pulang Pisau, Kalimantan Tengah", -2.7536, 114.2639));
        idStations.add(new Station("ID_KTG", "Katingan (Kasongan)", "Kalimantan Tengah", "Indonesia", "Kasongan, Kab. Katingan, Kalimantan Tengah", -1.895, 113.3922));
        idStations.add(new Station("ID_SRY", "Seruyan (Kuala Pembuang)", "Kalimantan Tengah", "Indonesia", "Kuala Pembuang, Kab. Seruyan, Kalimantan Tengah", -3.3886, 112.5447));
        idStations.add(new Station("ID_SKM", "Sukamara", "Kalimantan Tengah", "Indonesia", "Pusat Kota Sukamara, Kalimantan Tengah", -2.6258, 111.2369));
        idStations.add(new Station("ID_LMD", "Lamandau (Nanga Bulik)", "Kalimantan Tengah", "Indonesia", "Nanga Bulik, Kab. Lamandau, Kalimantan Tengah", -2.1764, 111.4589));
        idStations.add(new Station("ID_GMS", "Gunung Mas (Kuala Kurun)", "Kalimantan Tengah", "Indonesia", "Kuala Kurun, Kab. Gunung Mas, Kalimantan Tengah", -1.1028, 113.8647));
        idStations.add(new Station("ID_BARSEL", "Barito Selatan (Buntok)", "Kalimantan Tengah", "Indonesia", "Buntok, Kab. Barito Selatan, Kalimantan Tengah", -1.7197, 114.8386));
        idStations.add(new Station("ID_BARTIM", "Barito Timur (Tamiang Layang)", "Kalimantan Tengah", "Indonesia", "Tamiang Layang, Kalimantan Tengah", -2.1167, 115.1667));
        idStations.add(new Station("ID_BARUT", "Barito Utara (Muara Teweh)", "Kalimantan Tengah", "Indonesia", "Muara Teweh, Kab. Barito Utara, Kalimantan Tengah", -0.9539, 114.8975));
        idStations.add(new Station("ID_MRA", "Murung Raya (Puruk Cahu)", "Kalimantan Tengah", "Indonesia", "Puruk Cahu, Kab. Murung Raya, Kalimantan Tengah", -0.6258, 114.5681));
        idStations.add(new Station("ID_BJM", "Banjarmasin", "Kalimantan Selatan", "Indonesia", "Siring Sungai Martapura, Kota Banjarmasin", -3.3194, 114.5908));
        idStations.add(new Station("ID_BJB", "Banjarbaru", "Kalimantan Selatan", "Indonesia", "Lapangan Murjani, Kota Banjarbaru, Kalimantan Selatan", -3.442, 114.8318));
        idStations.add(new Station("ID_BJR_KALSEL", "Banjar (Martapura)", "Kalimantan Selatan", "Indonesia", "Martapura, Kab. Banjar, Kalimantan Selatan", -3.4108, 114.8453));
        idStations.add(new Station("ID_BATOLA", "Barito Kuala (Marabahan)", "Kalimantan Selatan", "Indonesia", "Marabahan, Kab. Barito Kuala, Kalimantan Selatan", -2.9819, 114.7647));
        idStations.add(new Station("ID_TPN", "Tapin (Rantau)", "Kalimantan Selatan", "Indonesia", "Rantau, Kab. Tapin, Kalimantan Selatan", -2.9378, 115.1469));
        idStations.add(new Station("ID_HSS", "Hulu Sungai Selatan (Kandangan)", "Kalimantan Selatan", "Indonesia", "Kandangan, Kab. HSS, Kalimantan Selatan", -2.7753, 115.2669));
        idStations.add(new Station("ID_HST", "Hulu Sungai Tengah (Barabai)", "Kalimantan Selatan", "Indonesia", "Barabai, Kab. HST, Kalimantan Selatan", -2.5847, 115.3819));
        idStations.add(new Station("ID_HSU", "Hulu Sungai Utara (Amuntai)", "Kalimantan Selatan", "Indonesia", "Amuntai, Kab. HSU, Kalimantan Selatan", -2.4178, 115.2494));
        idStations.add(new Station("ID_BLG", "Balangan (Paringin)", "Kalimantan Selatan", "Indonesia", "Paringin, Kab. Balangan, Kalimantan Selatan", -2.3333, 115.4667));
        idStations.add(new Station("ID_TBL", "Tabalong (Tanjung)", "Kalimantan Selatan", "Indonesia", "Tanjung, Kab. Tabalong, Kalimantan Selatan", -2.1647, 115.3875));
        idStations.add(new Station("ID_TLA", "Tanah Laut (Pelaihari)", "Kalimantan Selatan", "Indonesia", "Pelaihari, Kab. Tanah Laut, Kalimantan Selatan", -3.7997, 114.7642));
        idStations.add(new Station("ID_TBM", "Tanah Bumbu (Batulicin)", "Kalimantan Selatan", "Indonesia", "Batulicin, Kab. Tanah Bumbu, Kalimantan Selatan", -3.4542, 116.0028));
        idStations.add(new Station("ID_KTB", "Kotabaru", "Kalimantan Selatan", "Indonesia", "Pulau Laut, Kab. Kotabaru, Kalimantan Selatan", -3.2389, 116.2267));
        idStations.add(new Station("ID_SMD_KALTIM", "Samarinda", "Kalimantan Timur", "Indonesia", "Tepian Mahakam, Kota Samarinda, Kalimantan Timur", -0.5022, 117.1536));
        idStations.add(new Station("ID_BPN", "Balikpapan", "Kalimantan Timur", "Indonesia", "Pantai Kemala, Kota Balikpapan, Kalimantan Timur", -1.2379, 116.8529));
        idStations.add(new Station("ID_IKN", "Ibu Kota Nusantara (IKN)", "Kalimantan Timur", "Indonesia", "Sepaku, Ibu Kota Nusantara, Kalimantan Timur", -0.973, 116.7088));
        idStations.add(new Station("ID_BTG_KALTIM", "Bontang", "Kalimantan Timur", "Indonesia", "Kota Bontang, Kalimantan Timur", 0.1336, 117.4856));
        idStations.add(new Station("ID_KUKAR", "Kutai Kartanegara (Tenggarong)", "Kalimantan Timur", "Indonesia", "Tenggarong, Kab. Kutai Kartanegara, Kalimantan Timur", -0.4333, 116.9833));
        idStations.add(new Station("ID_KUTIM", "Kutai Timur (Sangatta)", "Kalimantan Timur", "Indonesia", "Sangatta, Kab. Kutai Timur, Kalimantan Timur", 0.5036, 117.5356));
        idStations.add(new Station("ID_KUBAR", "Kutai Barat (Sendawar)", "Kalimantan Timur", "Indonesia", "Sendawar, Kab. Kutai Barat, Kalimantan Timur", -0.2333, 115.7));
        idStations.add(new Station("ID_MHU", "Mahakam Ulu (Ujoh Bilang)", "Kalimantan Timur", "Indonesia", "Ujoh Bilang, Kab. Mahakam Ulu, Kalimantan Timur", 0.5167, 115.25));
        idStations.add(new Station("ID_BRU", "Berau (Tanjung Redeb)", "Kalimantan Timur", "Indonesia", "Tanjung Redeb / Derawan, Kab. Berau, Kalimantan Timur", 2.1506, 117.4958));
        idStations.add(new Station("ID_PSR_KALTIM", "Paser (Tana Paser)", "Kalimantan Timur", "Indonesia", "Tana Paser, Kab. Paser, Kalimantan Timur", -1.9056, 116.1969));
        idStations.add(new Station("ID_PPU", "Penajam Paser Utara", "Kalimantan Timur", "Indonesia", "Penajam, Kab. Penajam Paser Utara, Kalimantan Timur", -1.3, 116.7333));
        idStations.add(new Station("ID_TRK", "Tarakan", "Kalimantan Utara", "Indonesia", "Pulau Tarakan, Kota Tarakan, Kalimantan Utara", 3.3, 117.6333));
        idStations.add(new Station("ID_TJS", "Bulungan (Tanjung Selor)", "Kalimantan Utara", "Indonesia", "Tanjung Selor, Kab. Bulungan, Kalimantan Utara", 2.8375, 117.3653));
        idStations.add(new Station("ID_NNK", "Nunukan", "Kalimantan Utara", "Indonesia", "Pulau Nunukan, Kab. Nunukan, Kalimantan Utara", 4.1356, 117.6647));
        idStations.add(new Station("ID_MLN", "Malinau", "Kalimantan Utara", "Indonesia", "Pusat Kota Malinau, Kalimantan Utara", 3.5847, 116.6389));
        idStations.add(new Station("ID_TTD", "Tana Tidung (Tideng Pale)", "Kalimantan Utara", "Indonesia", "Tideng Pale, Kab. Tana Tidung, Kalimantan Utara", 3.6019, 117.0958));
        idStations.add(new Station("ID_MKS", "Makassar", "Sulawesi Selatan", "Indonesia", "Pantai Losari, Kota Makassar, Sulawesi Selatan", -5.1477, 119.4327));
        idStations.add(new Station("ID_PRE", "Parepare", "Sulawesi Selatan", "Indonesia", "Kota Parepare, Sulawesi Selatan", -4.0135, 119.6255));
        idStations.add(new Station("ID_PLP_SULSEL", "Palopo", "Sulawesi Selatan", "Indonesia", "Kota Palopo, Sulawesi Selatan", -2.9926, 120.1969));
        idStations.add(new Station("ID_GWA", "Gowa (Sungguminasa / Malino)", "Sulawesi Selatan", "Indonesia", "Sungguminasa, Kab. Gowa, Sulawesi Selatan", -5.2039, 119.4519));
        idStations.add(new Station("ID_MRS", "Maros", "Sulawesi Selatan", "Indonesia", "Turikale / Bantimurung, Kab. Maros, Sulawesi Selatan", -5.0056, 119.5731));
        idStations.add(new Station("ID_PKJ", "Pangkep (Pangkajene)", "Sulawesi Selatan", "Indonesia", "Pangkajene, Kab. Pangkep, Sulawesi Selatan", -4.8322, 119.5469));
        idStations.add(new Station("ID_BRR", "Barru", "Sulawesi Selatan", "Indonesia", "Pusat Kota Barru, Sulawesi Selatan", -4.4081, 119.6208));
        idStations.add(new Station("ID_BONE", "Bone (Watampone)", "Sulawesi Selatan", "Indonesia", "Watampone, Kab. Bone, Sulawesi Selatan", -4.5386, 120.3278));
        idStations.add(new Station("ID_SPG", "Soppeng (Watansoppeng)", "Sulawesi Selatan", "Indonesia", "Watansoppeng, Kab. Soppeng, Sulawesi Selatan", -4.3519, 119.8869));
        idStations.add(new Station("ID_WAJO", "Wajo (Sengkang)", "Sulawesi Selatan", "Indonesia", "Sengkang, Kab. Wajo, Sulawesi Selatan", -4.1281, 120.0319));
        idStations.add(new Station("ID_SDR", "Sidenreng Rappang (Sidrap)", "Sulawesi Selatan", "Indonesia", "Pangkajene Sidenreng, Kab. Sidrap, Sulawesi Selatan", -3.9247, 119.7969));
        idStations.add(new Station("ID_PNR", "Pinrang", "Sulawesi Selatan", "Indonesia", "Pusat Kota Pinrang, Sulawesi Selatan", -3.7889, 119.6522));
        idStations.add(new Station("ID_ERK", "Enrekang", "Sulawesi Selatan", "Indonesia", "Pusat Kota Enrekang, Sulawesi Selatan", -3.5647, 119.7742));
        idStations.add(new Station("ID_TATOR", "Tana Toraja (Makale)", "Sulawesi Selatan", "Indonesia", "Makale, Kab. Tana Toraja, Sulawesi Selatan", -3.1039, 119.8519));
        idStations.add(new Station("ID_TORUT", "Toraja Utara (Rantepao)", "Sulawesi Selatan", "Indonesia", "Rantepao, Kab. Toraja Utara, Sulawesi Selatan", -2.97, 119.8981));
        idStations.add(new Station("ID_LUWU", "Luwu (Belopa)", "Sulawesi Selatan", "Indonesia", "Belopa, Kab. Luwu, Sulawesi Selatan", -3.3944, 120.3667));
        idStations.add(new Station("ID_LUTRA", "Luwu Utara (Masamba)", "Sulawesi Selatan", "Indonesia", "Masamba, Kab. Luwu Utara, Sulawesi Selatan", -2.5539, 120.3242));
        idStations.add(new Station("ID_LUTIM", "Luwu Timur (Malili / Sorowako)", "Sulawesi Selatan", "Indonesia", "Malili / Sorowako, Kab. Luwu Timur, Sulawesi Selatan", -2.6333, 121.0969));
        idStations.add(new Station("ID_TKL", "Takalar (Pattallassang)", "Sulawesi Selatan", "Indonesia", "Pattallassang, Kab. Takalar, Sulawesi Selatan", -5.4194, 119.4494));
        idStations.add(new Station("ID_JNP", "Jeneponto (Bontosunggu)", "Sulawesi Selatan", "Indonesia", "Bontosunggu, Kab. Jeneponto, Sulawesi Selatan", -5.6789, 119.7347));
        idStations.add(new Station("ID_BTG_SULSEL", "Bantaeng", "Sulawesi Selatan", "Indonesia", "Pantai Seruni, Kab. Bantaeng, Sulawesi Selatan", -5.5469, 119.9469));
        idStations.add(new Station("ID_BLK", "Bulukumba (Tanjung Bira)", "Sulawesi Selatan", "Indonesia", "Bulukumba / Tanjung Bira, Sulawesi Selatan", -5.5567, 120.1939));
        idStations.add(new Station("ID_SNJ", "Sinjai", "Sulawesi Selatan", "Indonesia", "Pusat Kota Sinjai, Sulawesi Selatan", -5.1242, 120.2536));
        idStations.add(new Station("ID_SLY", "Kepulauan Selayar (Benteng)", "Sulawesi Selatan", "Indonesia", "Benteng, Kep. Selayar, Sulawesi Selatan", -6.1186, 120.4589));
        idStations.add(new Station("ID_MND", "Manado", "Sulawesi Utara", "Indonesia", "Boulevard / Bunaken, Kota Manado, Sulawesi Utara", 1.4748, 124.8421));
        idStations.add(new Station("ID_BTG_SULUT", "Bitung", "Sulawesi Utara", "Indonesia", "Kota Bitung, Sulawesi Utara", 1.4451, 125.1824));
        idStations.add(new Station("ID_TMH", "Tomohon", "Sulawesi Utara", "Indonesia", "Kota Bunga Tomohon, Sulawesi Utara", 1.3236, 124.8392));
        idStations.add(new Station("ID_KTG_SULUT", "Kotamobagu", "Sulawesi Utara", "Indonesia", "Kota Kotamobagu, Sulawesi Utara", 0.7267, 124.3119));
        idStations.add(new Station("ID_MNH", "Minahasa (Tondano)", "Sulawesi Utara", "Indonesia", "Tondano, Kab. Minahasa, Sulawesi Utara", 1.3047, 124.9122));
        idStations.add(new Station("ID_MINUT", "Minahasa Utara (Airmadidi)", "Sulawesi Utara", "Indonesia", "Airmadidi, Kab. Minahasa Utara, Sulawesi Utara", 1.4258, 124.9819));
        idStations.add(new Station("ID_MINSEL", "Minahasa Selatan (Amurang)", "Sulawesi Utara", "Indonesia", "Amurang, Kab. Minahasa Selatan, Sulawesi Utara", 1.1869, 124.5681));
        idStations.add(new Station("ID_MITRA", "Minahasa Tenggara (Ratahan)", "Sulawesi Utara", "Indonesia", "Ratahan, Kab. Minahasa Tenggara, Sulawesi Utara", 1.0589, 124.7958));
        idStations.add(new Station("ID_BOLMONG", "Bolaang Mongondow (Lolak)", "Sulawesi Utara", "Indonesia", "Lolak, Kab. Bolaang Mongondow, Sulawesi Utara", 0.8767, 124.0142));
        idStations.add(new Station("ID_BOLMUT", "Bolaang Mongondow Utara (Boroko)", "Sulawesi Utara", "Indonesia", "Boroko, Bolaang Mongondow Utara, Sulawesi Utara", 0.9058, 123.3681));
        idStations.add(new Station("ID_BOLTIM", "Bolaang Mongondow Timur (Tutuyan)", "Sulawesi Utara", "Indonesia", "Tutuyan, Bolaang Mongondow Timur, Sulawesi Utara", 0.7681, 124.6219));
        idStations.add(new Station("ID_BOLSEL", "Bolaang Mongondow Selatan (Bolaang Uki)", "Sulawesi Utara", "Indonesia", "Bolaang Uki, Bolaang Mongondow Selatan", 0.3719, 123.9781));
        idStations.add(new Station("ID_SGH", "Kepulauan Sangihe (Tahuna)", "Sulawesi Utara", "Indonesia", "Tahuna, Kep. Sangihe, Sulawesi Utara", 3.6108, 125.4922));
        idStations.add(new Station("ID_TLD", "Kepulauan Talaud (Melonguane)", "Sulawesi Utara", "Indonesia", "Melonguane, Kep. Talaud, Sulawesi Utara", 4.0022, 126.6775));
        idStations.add(new Station("ID_SITARO", "Kepulauan Sitaro (Ondong Siau)", "Sulawesi Utara", "Indonesia", "Ondong Siau, Kep. Sitaro, Sulawesi Utara", 2.7481, 125.3719));
        idStations.add(new Station("ID_GTO", "Gorontalo", "Gorontalo", "Indonesia", "Pusat Kota Gorontalo, Gorontalo", 0.5435, 123.0568));
        idStations.add(new Station("ID_GTO_KAB", "Kab. Gorontalo (Limboto)", "Gorontalo", "Indonesia", "Limboto, Kab. Gorontalo, Gorontalo", 0.6269, 122.9792));
        idStations.add(new Station("ID_BNBL", "Bone Bolango (Suwawa)", "Gorontalo", "Indonesia", "Suwawa, Kab. Bone Bolango, Gorontalo", 0.5569, 123.1469));
        idStations.add(new Station("ID_BLM", "Boalemo (Tilamuta)", "Gorontalo", "Indonesia", "Tilamuta, Kab. Boalemo, Gorontalo", 0.5208, 122.3397));
        idStations.add(new Station("ID_PHW", "Pohuwato (Marisa)", "Gorontalo", "Indonesia", "Marisa, Kab. Pohuwato, Gorontalo", 0.4567, 121.9439));
        idStations.add(new Station("ID_GORUT", "Gorontalo Utara (Kwandang)", "Gorontalo", "Indonesia", "Kwandang, Kab. Gorontalo Utara, Gorontalo", 0.8358, 122.9069));
        idStations.add(new Station("ID_PALU", "Palu", "Sulawesi Tengah", "Indonesia", "Teluk Palu, Kota Palu, Sulawesi Tengah", -0.8917, 119.8707));
        idStations.add(new Station("ID_DGL", "Donggala", "Sulawesi Tengah", "Indonesia", "Banawa, Kab. Donggala, Sulawesi Tengah", -0.6736, 119.7419));
        idStations.add(new Station("ID_SIGI", "Sigi (Sigi Biromaru)", "Sulawesi Tengah", "Indonesia", "Sigi Biromaru, Kab. Sigi, Sulawesi Tengah", -0.975, 119.9083));
        idStations.add(new Station("ID_PRG", "Parigi Moutong (Parigi)", "Sulawesi Tengah", "Indonesia", "Parigi, Kab. Parigi Moutong, Sulawesi Tengah", -0.8147, 120.1758));
        idStations.add(new Station("ID_POSO", "Poso", "Sulawesi Tengah", "Indonesia", "Pusat Kota Poso, Sulawesi Tengah", -1.3958, 120.7525));
        idStations.add(new Station("ID_TOJO", "Tojo Una-Una (Ampana)", "Sulawesi Tengah", "Indonesia", "Ampana, Kab. Tojo Una-Una, Sulawesi Tengah", -0.8681, 121.5819));
        idStations.add(new Station("ID_MRW", "Morowali (Bungku)", "Sulawesi Tengah", "Indonesia", "Bungku, Kab. Morowali, Sulawesi Tengah", -2.5422, 121.9681));
        idStations.add(new Station("ID_MRWU", "Morowali Utara (Kolonodale)", "Sulawesi Tengah", "Indonesia", "Kolonodale, Kab. Morowali Utara, Sulawesi Tengah", -1.9869, 121.3389));
        idStations.add(new Station("ID_BGI", "Banggai (Luwuk)", "Sulawesi Tengah", "Indonesia", "Luwuk, Kab. Banggai, Sulawesi Tengah", -0.9517, 122.7875));
        idStations.add(new Station("ID_BGIK", "Banggai Kepulauan (Salakan)", "Sulawesi Tengah", "Indonesia", "Salakan, Kab. Banggai Kepulauan, Sulawesi Tengah", -1.3119, 123.2939));
        idStations.add(new Station("ID_BGIL", "Banggai Laut", "Sulawesi Tengah", "Indonesia", "Banggai, Kab. Banggai Laut, Sulawesi Tengah", -1.5919, 123.5019));
        idStations.add(new Station("ID_TLI", "Tolitoli", "Sulawesi Tengah", "Indonesia", "Pusat Kota Tolitoli, Sulawesi Tengah", 1.0378, 120.8192));
        idStations.add(new Station("ID_BUOL", "Buol", "Sulawesi Tengah", "Indonesia", "Pusat Kota Buol, Sulawesi Tengah", 1.1569, 121.4369));
        idStations.add(new Station("ID_MMJ", "Mamuju", "Sulawesi Barat", "Indonesia", "Pantai Manakarra, Kota Mamuju, Sulawesi Barat", -2.6772, 118.8872));
        idStations.add(new Station("ID_MMJT", "Mamuju Tengah (Tobadak)", "Sulawesi Barat", "Indonesia", "Tobadak, Kab. Mamuju Tengah, Sulawesi Barat", -2.0667, 119.2833));
        idStations.add(new Station("ID_MJN", "Majene", "Sulawesi Barat", "Indonesia", "Pusat Kota Majene, Sulawesi Barat", -3.5403, 118.9708));
        idStations.add(new Station("ID_PLM_SULBAR", "Polewali Mandar (Polewali)", "Sulawesi Barat", "Indonesia", "Polewali, Kab. Polewali Mandar, Sulawesi Barat", -3.4325, 119.3436));
        idStations.add(new Station("ID_MMS", "Mamasa", "Sulawesi Barat", "Indonesia", "Pusat Kota Mamasa, Sulawesi Barat", -2.9389, 119.3769));
        idStations.add(new Station("ID_PSKY", "Pasangkayu", "Sulawesi Barat", "Indonesia", "Pusat Kota Pasangkayu, Sulawesi Barat", -1.1681, 119.3653));
        idStations.add(new Station("ID_KDI", "Kendari", "Sulawesi Tenggara", "Indonesia", "Teluk Kendari, Kota Kendari, Sulawesi Tenggara", -3.9985, 122.513));
        idStations.add(new Station("ID_BAU", "Baubau", "Sulawesi Tenggara", "Indonesia", "Benteng Keraton Buton, Kota Baubau, Sulawesi Tenggara", -5.4633, 122.6022));
        idStations.add(new Station("ID_KNW", "Konawe (Unaaha)", "Sulawesi Tenggara", "Indonesia", "Unaaha, Kab. Konawe, Sulawesi Tenggara", -3.8472, 122.0469));
        idStations.add(new Station("ID_KONSEL", "Konawe Selatan (Andoolo)", "Sulawesi Tenggara", "Indonesia", "Andoolo, Kab. Konawe Selatan, Sulawesi Tenggara", -4.3269, 122.2819));
        idStations.add(new Station("ID_KONUT", "Konawe Utara (Wanggudu)", "Sulawesi Tenggara", "Indonesia", "Wanggudu, Kab. Konawe Utara, Sulawesi Tenggara", -3.5358, 122.1269));
        idStations.add(new Station("ID_KONKEP", "Konawe Kepulauan (Langara)", "Sulawesi Tenggara", "Indonesia", "Langara, Pulau Wawonii, Sulawesi Tenggara", -4.0419, 123.0058));
        idStations.add(new Station("ID_KLK_SULTRA", "Kolaka", "Sulawesi Tenggara", "Indonesia", "Pusat Kota Kolaka, Sulawesi Tenggara", -4.0519, 121.5958));
        idStations.add(new Station("ID_KOLUT", "Kolaka Utara (Lasusua)", "Sulawesi Tenggara", "Indonesia", "Lasusua, Kab. Kolaka Utara, Sulawesi Tenggara", -3.5139, 120.8842));
        idStations.add(new Station("ID_KOLTIM", "Kolaka Timur (Tirawuta)", "Sulawesi Tenggara", "Indonesia", "Tirawuta, Kab. Kolaka Timur, Sulawesi Tenggara", -3.9969, 121.8681));
        idStations.add(new Station("ID_BMB", "Bombana (Rumbia)", "Sulawesi Tenggara", "Indonesia", "Rumbia, Kab. Bombana, Sulawesi Tenggara", -4.7739, 121.9969));
        idStations.add(new Station("ID_MUNA", "Muna (Raha)", "Sulawesi Tenggara", "Indonesia", "Raha, Kab. Muna, Sulawesi Tenggara", -4.8389, 122.7247));
        idStations.add(new Station("ID_MUNBAR", "Muna Barat (Laworo)", "Sulawesi Tenggara", "Indonesia", "Laworo, Kab. Muna Barat, Sulawesi Tenggara", -4.8197, 122.4639));
        idStations.add(new Station("ID_BTN", "Buton (Pasarwajo)", "Sulawesi Tenggara", "Indonesia", "Pasarwajo, Kab. Buton, Sulawesi Tenggara", -5.5028, 122.8458));
        idStations.add(new Station("ID_BUTUR", "Buton Utara (Buranga)", "Sulawesi Tenggara", "Indonesia", "Buranga, Kab. Buton Utara, Sulawesi Tenggara", -4.7697, 123.1681));
        idStations.add(new Station("ID_BUTSEL", "Buton Selatan (Batauga)", "Sulawesi Tenggara", "Indonesia", "Batauga, Kab. Buton Selatan, Sulawesi Tenggara", -5.6039, 122.6169));
        idStations.add(new Station("ID_BUTENG", "Buton Tengah (Labungkari)", "Sulawesi Tenggara", "Indonesia", "Labungkari, Kab. Buton Tengah, Sulawesi Tenggara", -5.3181, 122.5481));
        idStations.add(new Station("ID_WKT", "Wakatobi (Wangi-Wangi)", "Sulawesi Tenggara", "Indonesia", "Wangi-Wangi, Kab. Wakatobi, Sulawesi Tenggara", -5.3167, 123.5833));
        idStations.add(new Station("ID_AMB", "Ambon", "Maluku", "Indonesia", "Lapangan Merdeka, Kota Ambon, Maluku", -3.6954, 128.1814));
        idStations.add(new Station("ID_TUAL", "Tual", "Maluku", "Indonesia", "Kepulauan Kei, Kota Tual, Maluku", -5.6306, 132.7483));
        idStations.add(new Station("ID_MSH", "Maluku Tengah (Masohi / Banda Neira)", "Maluku", "Indonesia", "Masohi, Kab. Maluku Tengah, Maluku", -3.3036, 128.9553));
        idStations.add(new Station("ID_SBB", "Seram Bagian Barat (Piru)", "Maluku", "Indonesia", "Piru, Kab. Seram Bagian Barat, Maluku", -3.0681, 128.1969));
        idStations.add(new Station("ID_SBT", "Seram Bagian Timur (Bula)", "Maluku", "Indonesia", "Bula, Kab. Seram Bagian Timur, Maluku", -3.1058, 130.4969));
        idStations.add(new Station("ID_MLKT", "Maluku Tenggara (Langgur)", "Maluku", "Indonesia", "Langgur, Kab. Maluku Tenggara, Maluku", -5.6619, 132.7319));
        idStations.add(new Station("ID_BURU", "Buru (Namlea)", "Maluku", "Indonesia", "Namlea, Pulau Buru, Maluku", -3.2739, 127.0958));
        idStations.add(new Station("ID_BURSEL", "Buru Selatan (Namrole)", "Maluku", "Indonesia", "Namrole, Kab. Buru Selatan, Maluku", -3.8519, 126.7369));
        idStations.add(new Station("ID_ARU", "Kepulauan Aru (Dobo)", "Maluku", "Indonesia", "Dobo, Kep. Aru, Maluku", -5.7639, 134.2158));
        idStations.add(new Station("ID_TNM", "Kepulauan Tanimbar (Saumlaki)", "Maluku", "Indonesia", "Saumlaki, Kep. Tanimbar, Maluku", -7.9775, 131.2969));
        idStations.add(new Station("ID_MBD", "Maluku Barat Daya (Tiakur)", "Maluku", "Indonesia", "Tiakur, Pulau Moa, Maluku", -8.1758, 127.8197));
        idStations.add(new Station("ID_TTE", "Ternate", "Maluku Utara", "Indonesia", "Gunung Gamalama, Kota Ternate, Maluku Utara", 0.7893, 127.3774));
        idStations.add(new Station("ID_TDR", "Tidore Kepulauan (Sofifi)", "Maluku Utara", "Indonesia", "Sofifi / Tidore, Maluku Utara", 0.6789, 127.4022));
        idStations.add(new Station("ID_HALBAR", "Halmahera Barat (Jailolo)", "Maluku Utara", "Indonesia", "Jailolo, Kab. Halmahera Barat, Maluku Utara", 1.0739, 127.4681));
        idStations.add(new Station("ID_HALUT", "Halmahera Utara (Tobelo)", "Maluku Utara", "Indonesia", "Tobelo, Kab. Halmahera Utara, Maluku Utara", 1.7289, 128.0081));
        idStations.add(new Station("ID_HALTENG", "Halmahera Tengah (Weda)", "Maluku Utara", "Indonesia", "Weda, Kab. Halmahera Tengah, Maluku Utara", 0.3358, 127.8739));
        idStations.add(new Station("ID_HALTIM", "Halmahera Timur (Maba)", "Maluku Utara", "Indonesia", "Maba, Kab. Halmahera Timur, Maluku Utara", 0.7139, 128.2969));
        idStations.add(new Station("ID_HALSEL", "Halmahera Selatan (Labuha)", "Maluku Utara", "Indonesia", "Labuha, Pulau Bacan, Maluku Utara", -0.6319, 127.4858));
        idStations.add(new Station("ID_SULA", "Kepulauan Sula (Sanana)", "Maluku Utara", "Indonesia", "Sanana, Kep. Sula, Maluku Utara", -2.0581, 125.9781));
        idStations.add(new Station("ID_MRT", "Pulau Morotai (Daruba)", "Maluku Utara", "Indonesia", "Daruba, Pulau Morotai, Maluku Utara", 2.0519, 128.2908));
        idStations.add(new Station("ID_TLB_MALUT", "Pulau Taliabu (Bobong)", "Maluku Utara", "Indonesia", "Bobong, Pulau Taliabu, Maluku Utara", -1.9681, 124.3858));
        idStations.add(new Station("ID_JYP", "Jayapura", "Papua", "Indonesia", "Teluk Youtefa, Kota Jayapura, Papua", -2.5337, 140.7181));
        idStations.add(new Station("ID_JYP_KAB", "Kab. Jayapura (Sentani)", "Papua", "Indonesia", "Sentani, Kab. Jayapura, Papua", -2.5694, 140.5061));
        idStations.add(new Station("ID_BIAK", "Biak Numfor (Biak)", "Papua", "Indonesia", "Kota Biak, Papua", -1.1767, 136.082));
        idStations.add(new Station("ID_SRUI", "Kepulauan Yapen (Serui)", "Papua", "Indonesia", "Serui, Kep. Yapen, Papua", -1.8833, 136.2333));
        idStations.add(new Station("ID_SRM", "Sarmi", "Papua", "Indonesia", "Pusat Kota Sarmi, Papua", -1.8681, 138.7519));
        idStations.add(new Station("ID_KRM_PAPUA", "Keerom (Waris / Arso)", "Papua", "Indonesia", "Arso, Kab. Keerom, Papua", -2.9219, 140.7781));
        idStations.add(new Station("ID_WRP", "Waropen (Botawa)", "Papua", "Indonesia", "Botawa, Kab. Waropen, Papua", -2.2858, 136.6819));
        idStations.add(new Station("ID_SPR", "Supiori (Sorendiweri)", "Papua", "Indonesia", "Sorendiweri, Kab. Supiori, Papua", -0.7381, 135.6019));
        idStations.add(new Station("ID_SRG_PBD", "Sorong", "Papua Barat Daya", "Indonesia", "Kota Sorong, Papua Barat Daya", -0.8762, 131.2558));
        idStations.add(new Station("ID_SRG_KAB_2", "Kab. Sorong (Aimas)", "Papua Barat Daya", "Indonesia", "Aimas, Kab. Sorong, Papua Barat Daya", -0.9581, 131.3269));
        idStations.add(new Station("ID_SRG_SEL", "Sorong Selatan (Teminabuan)", "Papua Barat Daya", "Indonesia", "Teminabuan, Papua Barat Daya", -1.4458, 132.0169));
        idStations.add(new Station("ID_RJA", "Raja Ampat (Waisai)", "Papua Barat Daya", "Indonesia", "Waisai, Kep. Raja Ampat, Papua Barat Daya", -0.4239, 130.8219));
        idStations.add(new Station("ID_MKW", "Manokwari", "Papua Barat", "Indonesia", "Teluk Doreri, Manokwari, Papua Barat", -0.8615, 134.062));
        idStations.add(new Station("ID_FKF", "Fakfak", "Papua Barat", "Indonesia", "Pusat Kota Fakfak, Papua Barat", -2.9264, 132.2981));
        idStations.add(new Station("ID_KMN", "Kaimana", "Papua Barat", "Indonesia", "Senja Kaimana, Papua Barat", -3.6619, 133.7647));
        idStations.add(new Station("ID_BTI", "Teluk Bintuni", "Papua Barat", "Indonesia", "Bintuni, Kab. Teluk Bintuni, Papua Barat", -2.1139, 133.5269));
        idStations.add(new Station("ID_WND", "Teluk Wondama (Rasiei)", "Papua Barat", "Indonesia", "Rasiei, Kab. Teluk Wondama, Papua Barat", -2.8158, 134.5081));
        idStations.add(new Station("ID_TIM", "Mimika (Timika)", "Papua Tengah", "Indonesia", "Timika, Kab. Mimika, Papua Tengah", -4.5467, 136.8872));
        idStations.add(new Station("ID_NBR", "Nabire", "Papua Tengah", "Indonesia", "Pusat Kota Nabire, Papua Tengah", -3.3647, 135.5019));
        idStations.add(new Station("ID_PNI", "Paniai (Enarotali)", "Papua Tengah", "Indonesia", "Enarotali, Kab. Paniai, Papua Tengah", -3.9319, 136.3769));
        idStations.add(new Station("ID_PCJ", "Puncak Jaya (Mulia)", "Papua Tengah", "Indonesia", "Mulia, Kab. Puncak Jaya, Papua Tengah", -3.7269, 137.9681));
        idStations.add(new Station("ID_WMN", "Jayawijaya (Wamena)", "Papua Pegunungan", "Indonesia", "Lembah Baliem, Wamena, Papua Pegunungan", -4.0958, 138.9522));
        idStations.add(new Station("ID_YHK", "Yahukimo (Dekai)", "Papua Pegunungan", "Indonesia", "Dekai, Kab. Yahukimo, Papua Pegunungan", -4.8619, 139.4839));
        idStations.add(new Station("ID_PGB", "Pegunungan Bintang (Oksibil)", "Papua Pegunungan", "Indonesia", "Oksibil, Papua Pegunungan", -4.9069, 140.6281));
        idStations.add(new Station("ID_MRK", "Merauke", "Papua Selatan", "Indonesia", "Titik Timur Nusantara, Merauke, Papua Selatan", -8.4932, 140.4018));
        idStations.add(new Station("ID_BVD", "Boven Digoel (Tanah Merah)", "Papua Selatan", "Indonesia", "Tanah Merah, Kab. Boven Digoel, Papua Selatan", -6.0969, 140.3019));
        idStations.add(new Station("ID_ASM", "Asmat (Agats)", "Papua Selatan", "Indonesia", "Agats, Kab. Asmat, Papua Selatan", -5.5408, 138.1347));
        idStations.add(new Station("ID_MPP", "Mappi (Kepi)", "Papua Selatan", "Indonesia", "Kepi, Kab. Mappi, Papua Selatan", -6.5269, 139.3219));
        REGION_STATIONS.put("Indonesia", idStations);
        ALL_STATIONS.addAll(idStations);
    }

    public static List<String> getAvailableRegions() {
        return new ArrayList<>(REGION_STATIONS.keySet());
    }

    public static List<Station> getStationsForRegion(String region) {
        if (region == null || !REGION_STATIONS.containsKey(region)) {
            return REGION_STATIONS.get("Indonesia");
        }
        return Collections.unmodifiableList(REGION_STATIONS.get(region));
    }

    public static List<Station> getPresetStations() {
        return Collections.unmodifiableList(ALL_STATIONS);
    }

    /**
     * Searches all Indonesian cities/regencies matching the user's typed query.
     * Returns empty list when query is empty so the UI only reveals results once typed.
     */
    public static List<Station> searchIndonesianCities(String query) {
        if (query == null) return Collections.emptyList();
        String raw = query.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) return Collections.emptyList();

        String q = raw;
        if (q.startsWith("kota ")) q = q.substring(5).trim();
        else if (q.startsWith("kabupaten ")) q = q.substring(10).trim();
        else if (q.startsWith("kab. ")) q = q.substring(5).trim();
        else if (q.startsWith("kab ")) q = q.substring(4).trim();
        if (q.isEmpty()) q = raw;

        List<Station> idList = REGION_STATIONS.get("Indonesia");
        if (idList == null) return Collections.emptyList();

        List<Station> tier1 = new ArrayList<>(); // City name starts with query
        List<Station> tier2 = new ArrayList<>(); // Word or alias inside city name starts with query
        List<Station> tier3 = new ArrayList<>(); // City name contains query
        List<Station> tier4 = new ArrayList<>(); // Province or place contains query

        for (Station s : idList) {
            String nameLower = s.name.toLowerCase(Locale.ROOT);
            String cleanName = nameLower.startsWith("kab. ") ? nameLower.substring(5).trim() : nameLower;
            String stateLower = s.state.toLowerCase(Locale.ROOT);
            String placeLower = s.place.toLowerCase(Locale.ROOT);

            if (cleanName.startsWith(q) || nameLower.startsWith(q) || nameLower.startsWith(raw)) {
                tier1.add(s);
            } else if ( cleanName.contains(" " + q) || cleanName.contains("(" + q) || cleanName.contains("/ " + q) ) {
                tier2.add(s);
            } else if (nameLower.contains(q)) {
                tier3.add(s);
            } else if (stateLower.contains(q) || placeLower.contains(q)) {
                tier4.add(s);
            }
        }

        List<Station> combined = new ArrayList<>(tier1.size() + tier2.size() + tier3.size() + tier4.size());
        combined.addAll(tier1);
        combined.addAll(tier2);
        combined.addAll(tier3);
        combined.addAll(tier4);

        if (combined.size() > 40) {
            return new ArrayList<>(combined.subList(0, 40));
        }
        return combined;
    }

    /**
     * Queries Open-Meteo Geocoding API in the background for additional Indonesian towns/kecamatan.
     */
    public static void searchOnlineIndonesianCities(final String query, final OnlineSearchCallback callback) {
        if (query == null || query.trim().length() < 2 || callback == null) return;
        final String q = query.trim();
        new Thread(() -> {
            List<Station> extra = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                String urlStr = "https://geocoding-api.open-meteo.com/v1/search?name="
                        + URLEncoder.encode(q, "UTF-8")
                        + "&count=15&language=id&format=json";
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray results = root.optJSONArray("results");
                    if (results != null) {
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject item = results.getJSONObject(i);
                            String cc = item.optString("country_code", "");
                            if (!"ID".equalsIgnoreCase(cc)) continue;
                            String name = item.optString("name", "");
                            if (name.isEmpty()) continue;
                            String admin1 = item.optString("admin1", "Indonesia");
                            String admin2 = item.optString("admin2", "");
                            double lat = item.optDouble("latitude", DEFAULT_LATITUDE);
                            double lon = item.optDouble("longitude", DEFAULT_LONGITUDE);
                            int geoId = item.optInt("id", i);
                            String place = (!admin2.isEmpty() && !admin2.equalsIgnoreCase(name))
                                    ? (admin2 + ", " + admin1)
                                    : (name + ", " + admin1);
                            extra.add(new Station("ID_GEO_" + geoId, name, admin1, "Indonesia", place, lat, lon));
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            if (!extra.isEmpty()) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onResults(extra));
            }
        }, "city-geo-search").start();
    }

    /**
     * Returns nearby Indonesian cities around (lat, lon) sorted by distance, excluding the center city itself.
     */
    public static List<Station> getNearbyCities(double lat, double lon, int maxCount) {
        List<Station> idList = REGION_STATIONS.get("Indonesia");
        if (idList == null || idList.isEmpty()) return Collections.emptyList();

        List<Station> candidates = new ArrayList<>();
        for (Station s : idList) {
            double dLat = s.lat - lat;
            double dLon = s.lon - lon;
            double distSq = dLat * dLat + dLon * dLon;
            // Exclude exact same coordinate (< ~2km) and far-away islands (> ~350km)
            if (distSq > 0.0005 && distSq < 10.0) {
                candidates.add(s);
            }
        }
        Collections.sort(candidates, new Comparator<Station>() {
            @Override
            public int compare(Station a, Station b) {
                double da = (a.lat - lat) * (a.lat - lat) + (a.lon - lon) * (a.lon - lon);
                double db = (b.lat - lat) * (b.lat - lat) + (b.lon - lon) * (b.lon - lon);
                return Double.compare(da, db);
            }
        });
        if (candidates.size() > maxCount) {
            return new ArrayList<>(candidates.subList(0, maxCount));
        }
        return candidates;
    }

    public static String getActiveProvince(Context context) {
        String id = getStationId(context);
        String name = getLocationName(context);
        for (Station s : ALL_STATIONS) {
            if (s.id.equalsIgnoreCase(id) || s.name.equalsIgnoreCase(name)) {
                if (s.state != null && !s.state.isEmpty()) return s.state;
            }
        }
        String place = getStationPlace(context);
        if (place != null && place.contains(",")) {
            return place.substring(place.lastIndexOf(',') + 1).trim();
        }
        return "Jawa Timur";
    }

    private static void ensureDefaultTubanMigrated(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_MIGRATED_TUBAN_DEFAULT, false)) {
            prefs.edit()
                    .putString(KEY_REGION, DEFAULT_REGION)
                    .putString(KEY_STATION_ID, DEFAULT_STATION_ID)
                    .putString(KEY_LOCATION_NAME, DEFAULT_LOCATION_NAME)
                    .putString(KEY_STATION_PLACE, DEFAULT_STATION_PLACE)
                    .putString(KEY_LATITUDE, String.valueOf(DEFAULT_LATITUDE))
                    .putString(KEY_LONGITUDE, String.valueOf(DEFAULT_LONGITUDE))
                    .putBoolean(KEY_MIGRATED_TUBAN_DEFAULT, true)
                    .apply();

            SharedPreferences wPrefs = context.getSharedPreferences("weather_aqi_cache", Context.MODE_PRIVATE);
            wPrefs.edit()
                    .putLong("last_update", 0L)
                    .putString("aqi_place", DEFAULT_STATION_PLACE)
                    .apply();
        }
    }

    public static String getActiveRegion(Context context) {
        ensureDefaultTubanMigrated(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_REGION, DEFAULT_REGION);
    }

    public static void setActiveRegion(Context context, String region) {
        if (region == null) return;
        ensureDefaultTubanMigrated(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_REGION, region).apply();
    }

    public static String getStationId(Context context) {
        ensureDefaultTubanMigrated(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_STATION_ID, DEFAULT_STATION_ID);
    }

    public static String getLocationName(Context context) {
        ensureDefaultTubanMigrated(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LOCATION_NAME, DEFAULT_LOCATION_NAME);
    }

    public static String getStationPlace(Context context) {
        ensureDefaultTubanMigrated(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_STATION_PLACE, DEFAULT_STATION_PLACE);
    }

    public static double getLatitude(Context context) {
        ensureDefaultTubanMigrated(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            return Double.parseDouble(prefs.getString(KEY_LATITUDE, String.valueOf(DEFAULT_LATITUDE)));
        } catch (Exception ignored) {
            return DEFAULT_LATITUDE;
        }
    }

    public static double getLongitude(Context context) {
        ensureDefaultTubanMigrated(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            return Double.parseDouble(prefs.getString(KEY_LONGITUDE, String.valueOf(DEFAULT_LONGITUDE)));
        } catch (Exception ignored) {
            return DEFAULT_LONGITUDE;
        }
    }

    public static void setStation(Context context, Station s) {
        if (s == null) return;
        setCustomLocation(context, s.id, s.name, s.country, s.place, s.lat, s.lon);
    }

    public static void setCustomLocation(Context context, String stationId, String locationName, String place, double lat, double lon) {
        setCustomLocation(context, stationId, locationName, DEFAULT_REGION, place, lat, lon);
    }

    public static void setCustomLocation(Context context, String stationId, String locationName, String country, String place, double lat, double lon) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_STATION_ID, stationId)
                .putString(KEY_LOCATION_NAME, locationName)
                .putString(KEY_REGION, country != null ? country : DEFAULT_REGION)
                .putString(KEY_STATION_PLACE, place)
                .putString(KEY_LATITUDE, String.valueOf(lat))
                .putString(KEY_LONGITUDE, String.valueOf(lon))
                .apply();

        SharedPreferences wPrefs = context.getSharedPreferences("weather_aqi_cache", Context.MODE_PRIVATE);
        wPrefs.edit()
                .putLong("last_update", 0L)
                .putString("aqi_place", place)
                .apply();

        Log.i(TAG, "Location set to: " + locationName + " (" + country + ") [" + lat + ", " + lon + "]");

        // Trigger immediate sync for Weather & Prayer Times
        WeatherAirQuality.sync(context, null);
        PrayerTimes.sync(context, null);
    }

    public static Station findStationByQuery(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        List<Station> matches = searchIndonesianCities(query);
        if (!matches.isEmpty()) return matches.get(0);
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (Station s : ALL_STATIONS) {
            if (s.id.equalsIgnoreCase(q) || s.name.toLowerCase(Locale.ROOT).contains(q) || s.state.toLowerCase(Locale.ROOT).contains(q) || s.country.toLowerCase(Locale.ROOT).contains(q)) {
                return s;
            }
        }
        return null;
    }

    public static Station findNearestStation(double lat, double lon) {
        Station best = null;
        double minDistance = Double.MAX_VALUE;
        for (Station s : ALL_STATIONS) {
            double dLat = s.lat - lat;
            double dLon = s.lon - lon;
            double dist = dLat * dLat + dLon * dLon;
            if (dist < minDistance) {
                minDistance = dist;
                best = s;
            }
        }
        return best != null ? best : ALL_STATIONS.get(0);
    }
}
