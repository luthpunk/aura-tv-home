package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dynamic 4K dark desktop wallpaper manager.
 * Fetches high-resolution (3840x2160) atmospheric and moody desktop wallpapers
 * directly from an online feed or a curated Unsplash 4K catalog.
 * Caches wallpapers locally for instant loading and offline resilience.
 */
final class OnlineWallpapers {
    private static final String TAG = "TvHome.Online";
    static final String DIR = "online-wallpapers";
    private static final String PREFS_NAME = "online_wallpapers";
    private static final String PREF_LAST_SYNC = "last_sync_time";
    private static final long REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000; // 6 hours
    private static final int MAX_CACHE_FILES = 120;
    private static final int MAX_IMAGE_BYTES = 25 * 1024 * 1024; // 25 MB limit per 4K/8K image

    static class WallpaperItem {
        final String url;
        final String title;
        final String description;
        final String category;

        WallpaperItem(String url, String title, String description, String category) {
            this.url = url;
            this.title = title;
            this.description = description;
            this.category = category;
        }
    }

    // Built-in curated catalog of dark, moody, atmospheric 4K desktop wallpapers (Unsplash & Pexels)
    private static final WallpaperItem[] CURATED_CATALOG = new WallpaperItem[]{
            // Indonesia: Pesona Alam & Landmark Nusantara (Koleksi 4K Ultra HD)
            new WallpaperItem("https://images.unsplash.com/photo-1588668214407-6ea9a6d8c272?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Kaldera Gunung Bromo", "Kabut pagi menyelimuti kawah Gunung Bromo dan lautan pasir Taman Nasional Bromo Tengger Semeru", "Indonesia_JawaTimur"),
            new WallpaperItem("https://images.unsplash.com/photo-1518548419970-58e3b4079ab2?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Kepulauan Raja Ampat", "Gugusan pulau karst hijau di tengah perairan biru zamrud Papua Barat Daya yang memukau", "Indonesia_Papua"),
            new WallpaperItem("https://images.unsplash.com/photo-1596402184320-417e7178b2cd?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Keagungan Candi Borobudur", "Siluet stupa bersejarah Candi Borobudur berlatar kabut senja dan perbukitan Menoreh", "Indonesia_JawaTengah"),
            new WallpaperItem("https://images.unsplash.com/photo-1537996194471-e657df975ab4?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Pura Ulun Danu Beratan", "Keindahan arsitektur pura di tepi Danau Beratan Bedugul yang tenang diselimuti kabut tipis", "Indonesia_Bali"),
            new WallpaperItem("https://images.unsplash.com/photo-1577717903315-1691ae25ab3f?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Puncak Pulau Padar", "Panorama tiga teluk eksotis Taman Nasional Komodo di bawah langit senja keemasan", "Indonesia_NTT"),
            new WallpaperItem("https://images.unsplash.com/photo-1555899434-94d1368aa7af?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Gemerlap Malam Jakarta", "Garis langit gedung pencakar langit ibu kota dengan kilauan cahaya lampu kota di malam hari", "Indonesia_Jakarta"),
            new WallpaperItem("https://images.unsplash.com/photo-1539367628448-4bc5c9d171c8?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Tebing Pantai Kelingking", "Tebing karang ikonik Nusa Penida yang menjulang di atas deburan ombak biru Samudra Hindia", "Indonesia_Bali"),
            new WallpaperItem("https://images.unsplash.com/photo-1544644181-1484b3fdfc62?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Terasering Sawah Ubud", "Hamparan sawah berundak hijau zamrud di lembah tropis Bali pada pagi yang teduh", "Indonesia_Bali"),
            new WallpaperItem("https://images.unsplash.com/photo-1516690561799-46d8f74f9abf?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Danau Kawah Ijen", "Danau kawah asam terbesar di dunia dengan warna hijau toska di puncak pegunungan Banyuwangi", "Indonesia_JawaTimur"),
            new WallpaperItem("https://images.unsplash.com/photo-1501179691627-eeaa65ea017c?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Hutan Hujan Tropis Kalimantan", "Kanopi rimba tropis Nusantara yang diselimuti kabut tipis seusai hujan sore hari", "Indonesia_Kalimantan"),
            new WallpaperItem("https://images.unsplash.com/photo-1559628233-100c798642d4?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Senja Pura Tanah Lot", "Pura karang di pesisir laut Bali yang berpadu dengan siluet matahari terbenam", "Indonesia_Bali"),
            new WallpaperItem("https://images.unsplash.com/photo-1570789210967-2cac24afeb00?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Kaldera Danau Toba", "Ketenangan air danau vulkanik raksasa Sumatera Utara yang dikelilingi perbukitan hijau", "Indonesia_SumateraUtara"),
            new WallpaperItem("https://images.unsplash.com/photo-1565967511849-76a60a516170?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Puncak Gunung Rinjani", "Pemandangan megah Danau Segara Anak dari punggung bukit vulkanik Gunung Rinjani", "Indonesia_NTB"),
            new WallpaperItem("https://images.unsplash.com/photo-1584810359583-96fc3448beaa?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Candi Prambanan Senja", "Kemegahan arsitektur candi Hindu tertinggi di Indonesia di bawah langit senja", "Indonesia_Yogyakarta"),
            new WallpaperItem("https://images.unsplash.com/photo-1505993597083-3bd19fb75e57?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Kabut Pegunungan Nusantara", "Barisan pegunungan tropis Indonesia yang berlapis kabut biru di waktu fajar", "Indonesia_JawaBarat"),
            new WallpaperItem("https://images.unsplash.com/photo-1513415277900-a62401e19be4?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Air Terjun Tumpak Sewu", "Tirai air terjun megah di kaki Gunung Semeru yang dikelilingi tebing rimbun", "Indonesia_JawaTimur"),

            // Nature & Landscapes (Alam Semula Jadi - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Puncak Pegunungan Malam", "Siluet puncak gunung bersalju di bawah taburan bintang malam yang tenang", "Landscape"),
            new WallpaperItem("https://images.pexels.com/photos/1366919/pexels-photo-1366919.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Kabut Hutan Pinus", "Kabut tipis menyelimuti lembah hutan pinus pegunungan yang rimbun", "Landscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1531366936337-7c912a4589a7?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Cahaya Aurora Borealis", "Pendaran cahaya hijau aurora menari di langit malam kutub utara", "Nature"),
            new WallpaperItem("https://images.pexels.com/photos/1624496/pexels-photo-1624496.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Langit Berbintang di Atas Hutan", "Gugusan bintang menerangi siluet pepohonan pinus di malam yang hening", "Nature"),
            new WallpaperItem("https://images.unsplash.com/photo-1448375240586-882707db888b?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Rimba Hutan Berkabut", "Kanopi pepohonan hijau tua yang teduh dalam balutan kabut pagi", "Forest"),
            new WallpaperItem("https://images.pexels.com/photos/1287145/pexels-photo-1287145.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Senja di Danau Pegunungan", "Langit senja yang tenang memantulkan bayangan gunung di permukaan danau", "Nature"),
            new WallpaperItem("https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Lembah Danau Berkabut", "Air danau yang hening memantulkan punggung bukit berkabut", "Landscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Bukit Pasir Malam", "Bayangan lembut di atas hamparan gurun pasir saat malam tiba", "Desert"),
            new WallpaperItem("https://images.pexels.com/photos/3408744/pexels-photo-3408744.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Puncak Salju Biru", "Tebing gunung bersalju di bawah atmosfer langit biru tua", "Mountains"),
            new WallpaperItem("https://images.unsplash.com/photo-1508739773434-c26b3d09e071?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Bentang Alam Vulkanik", "Hamparan tanah vulkanik gelap dengan garis cakrawala minimalis", "Nature"),

            // Cityscape & Architecture (Pencakar Langit & Kota - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Sudut Malam Cyberpunk", "Pantulan lampu neon atmosferik di lorong kota saat malam hari", "Cityscape"),
            new WallpaperItem("https://images.pexels.com/photos/374870/pexels-photo-374870.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Cakrawala Kota Senja", "Gedung pencakar langit berkilau di tepi teluk saat matahari terbenam", "Cityscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Jalanan Kota Saat Hujan", "Kilauan lampu kota yang memantul di atas aspal basah malam hari", "Cityscape"),
            new WallpaperItem("https://images.pexels.com/photos/3052361/pexels-photo-3052361.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Arsitektur Kaca Modern", "Pola geometris fasad gedung pencakar langit kaca yang menjulang tinggi", "Architecture"),
            new WallpaperItem("https://images.pexels.com/photos/169647/pexels-photo-169647.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Gemerlap Metropolis Malam", "Lampu jalanan dan gedung tinggi yang berpendar di tengah malam", "Cityscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1494548162494-384bba4ab999?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Jembatan Gantung Berkabut", "Menara jembatan yang muncul di balik kabut senja yang dramatis", "Architecture"),

            // Space & Astronomy (Luar Angkasa & Kosmos - Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Orbit Bumi Malam Hari", "Pemandangan planet Bumi yang berpendar dari orbit luar angkasa", "Space"),
            new WallpaperItem("https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Galaksi Bima Sakti", "Langit malam pekat yang dipenuhi gugusan bintang Bima Sakti", "Astronomy"),
            new WallpaperItem("https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Nebula Antariksa", "Awan gas antarbintang dan taburan bintang di kedalaman kosmos", "Cosmos"),
            new WallpaperItem("https://images.unsplash.com/photo-1419242902214-272b3f66ee7a?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Hamparan Bintang Kosmik", "Ruang angkasa gelap yang dihiasi galaksi-galaksi jauh", "Space"),

            // Ocean & Water (Lautan & Pantai - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Kedalaman Samudra Biru", "Berkas cahaya matahari menembus ke dalam lautan biru yang tenang", "Underwater"),
            new WallpaperItem("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Ombak Laut Malam", "Gulungan ombak biru tua yang menyapu pesisir pantai yang teduh", "Oceans"),
            new WallpaperItem("https://images.pexels.com/photos/1001682/pexels-photo-1001682.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Gulungan Ombak Samudra", "Alunan ombak laut dalam dengan gradasi warna biru toska gelap", "Oceans"),
            new WallpaperItem("https://images.pexels.com/photos/1032650/pexels-photo-1032650.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Perairan Tropis Tenang", "Pemandangan udara gradasi air laut tropis dan terumbu karang", "Oceans"),
            new WallpaperItem("https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Palung Laut Dalam", "Pendaran cahaya biru lembut di kedalaman samudra", "Underwater"),
            new WallpaperItem("https://images.pexels.com/photos/1295138/pexels-photo-1295138.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Tebing Karang Pesisir", "Tebing batu gelap yang berpadu dengan deburan ombak laut", "Coast"),

            // Minimalist & Dark Abstract OLED (Koleksi Pilihan Pengguna 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1439792675105-701e6a4ab6f0?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Siluet Gunung Malam", "Siluet puncak gunung di bawah atmosfer langit malam yang tenang", "Minimalist"),
            new WallpaperItem("https://images.unsplash.com/photo-1672009190560-12e7bade8d09?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Kurva Abstrak Ungu", "Latar belakang kurva abstrak ungu gelap bernuansa modern minimalis", "Minimalist"),
            new WallpaperItem("https://images.unsplash.com/photo-1663970206579-c157cba7edda?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Abstrak Ungu & Biru Tua", "Gradasi dinamis warna ungu dan biru gelap atmosferik", "Minimalist"),
            new WallpaperItem("https://images.unsplash.com/photo-1567095751004-aa51a2690368?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Gelombang Abu-abu & Biru", "Pola gelombang 3D lembut dalam nuansa abu-abu dan biru malam", "Minimalist"),
            new WallpaperItem("https://images.unsplash.com/photo-1709625862266-014ef072fd93?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Lengkung Monokrom OLED", "Objek melengkung monokrom hitam putih dengan kontras tinggi", "Minimalist"),
            new WallpaperItem("https://images.unsplash.com/photo-1460355976672-71c3f0a4bdac?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Malam Pegunungan Bersalju", "Langit malam berbintang di atas punggung bukit pegunungan es", "Minimalist"),
            new WallpaperItem("https://images.unsplash.com/photo-1686164439898-9790e48910ab?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Gelombang Neon di Atas Hitam", "Alunan pita cahaya neon halus di atas latar hitam pekat", "Minimalist"),
            new WallpaperItem("https://images.unsplash.com/photo-1683908280933-09d19b039198?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Aliran Gelombang Biru Tua", "Latar belakang biru tua elegan dengan alur garis bergelombang", "Minimalist"),
            new WallpaperItem("https://images.unsplash.com/photo-1637825891035-1930cbd564dd?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Pita Cahaya Ungu & Merah Muda", "Gelombang abstrak berpendar dalam kegelapan ruang minimalis", "Minimalist"),
            new WallpaperItem("https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Gelombang Sutra Obsidian", "Alunan gelombang cairan 3D hitam pekat dengan bias cahaya halus untuk layar OLED", "Minimalist"),

            // Anime HD (Ilustrasi Sinematik & Pemandangan Anime 4K)
            new WallpaperItem("https://w.wallhaven.cc/full/3l/wallhaven-3ldkoy.jpg",
                    "Langit Senja Anime", "Ilustrasi sinematik awan keemasan dan siluet kota saat matahari terbenam", "Anime"),
            new WallpaperItem("https://w.wallhaven.cc/full/vq/wallhaven-vqwxd8.jpg",
                    "Stasiun Kereta Senja", "Pemandangan perlintasan kereta api anime di bawah langit jingga dan ungu", "Anime"),
            new WallpaperItem("https://w.wallhaven.cc/full/wy/wallhaven-wy6vqx.jpg",
                    "Malam Berbintang Anime", "Taburan bintang dan komet menghiasi langit malam biru di atas bukit", "Anime"),
            new WallpaperItem("https://w.wallhaven.cc/full/gw/wallhaven-gw5ypd.jpg",
                    "Lembah Hijau Sinematik", "Ilustrasi padang rumput dan awan putih megah bergaya anime", "Anime"),
            new WallpaperItem("https://w.wallhaven.cc/full/yq/wallhaven-yq5mlk.jpg",
                    "Cahaya Kota Malam Anime", "Panorama lampu kota dari balkon di malam yang tenang dan syahdu", "Anime"),
            new WallpaperItem("https://w.wallhaven.cc/full/2y/wallhaven-2y89my.jpg",
                    "Pelabuhan Awan Biru", "Pemandangan laut biru dan awan musim panas dalam goresan anime detail tinggi", "Anime"),
            new WallpaperItem("https://w.wallhaven.cc/full/og/wallhaven-ogjwvl.jpg",
                    "Senja di Tepi Sungai", "Pantulan langit senja merah muda pada aliran sungai kota Jepang", "Anime"),
            new WallpaperItem("https://w.wallhaven.cc/full/gw/wallhaven-gwdvxq.jpg",
                    "Kuil Hutan Magis", "Gerbang torii di tengah hutan berkabut dengan berkas cahaya pagi", "Anime"),
            new WallpaperItem("https://w.wallhaven.cc/full/l8/wallhaven-l83k62.jpg",
                    "Festival Lentera Malam", "Pendaran cahaya lentera hangat di jalanan malam bergaya anime", "Anime"),
            new WallpaperItem("https://w.wallhaven.cc/full/85/wallhaven-8562ko.jpg",
                    "Horizon Galaksi Anime", "Langit malam fantasi dengan bentangan galaksi memantul di permukaan air", "Anime"),
            new WallpaperItem("https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Siluet Neon Tokyo Anime", "Nuansa malam ilustratif kota Jepang dengan kontras cahaya neon", "Anime"),
            new WallpaperItem("https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?auto=format&fit=crop&w=3840&h=2160&q=85",
                    "Estetika Malam Anime", "Ilustrasi suasana malam Jepang bergaya anime modern", "Anime")
    };

    private OnlineWallpapers() {
    }

    static File dir(Context ctx) {
        File base = Config.dir(ctx);
        return base != null ? new File(base, DIR) : null;
    }

    /** Returns all valid downloaded 4K wallpapers in sorted order. */
    static List<File> list(Context ctx) {
        List<File> out = new ArrayList<>();
        File d = dir(ctx);
        File[] files = d != null ? d.listFiles() : null;
        if (files == null) return out;
        for (File f : files) {
            if (f.isFile() && f.length() > 0) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                    out.add(f);
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    public static boolean matchesCategory(String itemCat, String selectedCat) {
        if (selectedCat == null || selectedCat.isEmpty() || "All".equalsIgnoreCase(selectedCat)) return true;
        if (itemCat == null) return false;
        String ic = itemCat.toLowerCase(Locale.ROOT);
        String sc = selectedCat.toLowerCase(Locale.ROOT);
        if (sc.startsWith("indonesia_") || sc.startsWith("malaysia_")) {
            String state = sc.substring(sc.indexOf('_') + 1);
            return ic.contains(state);
        }
        if (sc.equals("indonesia") || sc.equals("indonesia_all") || sc.equals("malaysia") || sc.equals("malaysia_all")) {
            return ic.contains("indonesia");
        }
        if (sc.contains("sky") || sc.contains("arch") || sc.contains("city")) {
            return ic.contains("sky") || ic.contains("arch") || ic.contains("city") || ic.contains("urban");
        }
        if (sc.contains("nat") || sc.contains("land")) {
            return ic.contains("nat") || ic.contains("land") || ic.contains("mount") || ic.contains("forest") || ic.contains("desert");
        }
        if (sc.contains("space") || sc.contains("cosmo") || sc.contains("astro")) {
            return ic.contains("space") || ic.contains("astro") || ic.contains("cosmo");
        }
        if (sc.contains("ocean") || sc.contains("water") || sc.contains("coast")) {
            return ic.contains("ocean") || ic.contains("underwater") || ic.contains("coast");
        }
        if (sc.contains("minim") || sc.contains("oled")) {
            return ic.contains("minim") || ic.contains("oled") || ic.contains("abstract");
        }
        if (sc.contains("anime")) {
            return ic.contains("anime");
        }
        if (sc.contains("malay")) {
            return ic.contains("malay");
        }
        return ic.contains(sc);
    }

    static int countForCategory(String category) {
        int count = 0;
        for (WallpaperItem item : CURATED_CATALOG) {
            if (matchesCategory(item.category, category)) {
                count++;
            }
        }
        return count;
    }

    static void cleanOutdatedCategoryWallpapers(File d, String category) {
        if (d == null || !d.exists()) return;
        Set<String> validHashes = new HashSet<>();
        for (WallpaperItem item : CURATED_CATALOG) {
            validHashes.add(hashFilename(item.url));
        }
        File[] files = d.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        for (File f : files) {
            String base = f.getName().replace(".jpg", "");
            if (!validHashes.contains(base)) {
                f.delete();
                File txt = new File(d, base + ".txt");
                if (txt.exists()) txt.delete();
                Log.i(TAG, "Removed outdated wallpaper: " + f.getName());
            }
        }
    }

    static List<File> listByCategory(Context ctx, String category) {
        File d = dir(ctx);
        if (d != null && d.exists()) {
            cleanOutdatedCategoryWallpapers(d, category);
        }
        List<File> all = list(ctx);
        if (category == null || category.equalsIgnoreCase("All")) return all;
        List<File> filtered = new ArrayList<>();
        for (File f : all) {
            String[] desc = describe(f);
            if (desc != null && desc.length >= 3) {
                if (matchesCategory(desc[2], category)) {
                    filtered.add(f);
                }
            }
        }
        return filtered;
    }

    static boolean syncCategory(Context ctx, String category) {
        File d = dir(ctx);
        if (d == null) return false;
        if (!d.exists() && !d.mkdirs()) return false;

        cleanOutdatedCategoryWallpapers(d, category);

        List<WallpaperItem> candidates = new ArrayList<>();
        for (WallpaperItem item : CURATED_CATALOG) {
            if (matchesCategory(item.category, category)) {
                candidates.add(item);
            }
        }
        if (candidates.isEmpty()) return false;

        boolean anyDownloaded = false;
        int targetNew = candidates.size();
        int count = 0;

        for (WallpaperItem item : candidates) {
            if (count >= targetNew) break;
            String fileName = hashFilename(item.url) + ".jpg";
            File targetFile = new File(d, fileName);
            if (targetFile.exists() && targetFile.length() > 50_000) {
                continue;
            }

            File tempFile = new File(d, fileName + ".tmp");
            File tempTxt = new File(d, fileName.replace(".jpg", ".txt") + ".tmp");
            try {
                if (downloadUrlToFile(item.url, tempFile)) {
                    String meta = (item.title != null ? item.title : "4K Wallpaper") + "\n"
                            + (item.description != null ? item.description : "") + "\n"
                            + (item.category != null ? item.category : category);
                    try (OutputStream os = new FileOutputStream(tempTxt)) {
                        os.write(meta.getBytes(StandardCharsets.UTF_8));
                    }
                    File finalTxt = new File(d, fileName.replace(".jpg", ".txt"));
                    if (tempFile.renameTo(targetFile)) {
                        tempTxt.renameTo(finalTxt);
                        anyDownloaded = true;
                        count++;
                        Log.i(TAG, "Saved category wallpaper [" + category + "]: " + item.title);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed downloading " + item.url + ": " + e);
            } finally {
                if (tempFile.exists()) tempFile.delete();
                if (tempTxt.exists()) tempTxt.delete();
            }
        }
        return anyDownloaded;
    }

    /** Returns true if sync is needed (empty local pool or refresh interval elapsed). */
    static boolean due(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long last = sp.getLong(PREF_LAST_SYNC, 0);
        return list(ctx).isEmpty() || (System.currentTimeMillis() - last >= REFRESH_INTERVAL_MS);
    }

    /**
     * Reads companion .txt file (same name as picture with .txt extension).
     * Line 1: Title
     * Line 2: Description / Overview
     * Line 3: Category / Source
     */
    static String[] describe(File picture) {
        if (picture == null) return null;
        String name = picture.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return null;
        File text = new File(picture.getParentFile(), name.substring(0, dot) + ".txt");
        if (!text.isFile() || text.length() == 0 || text.length() > 8192) return null;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(text), StandardCharsets.UTF_8))) {
            String title = r.readLine();
            if (title == null || title.trim().isEmpty()) return null;
            String desc = r.readLine();
            String cat = r.readLine();
            return new String[]{
                    title.trim(),
                    desc == null ? "" : desc.trim(),
                    cat == null ? "" : cat.trim()
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Synchronizes wallpapers from an online feed or the built-in curated catalog.
     * Downloads on the caller's thread; call from a background thread.
     * Returns true if one or more fresh pictures were added to the local pool.
     */
    static boolean sync(Context ctx, String feedUrl) {
        File d = dir(ctx);
        if (d == null) return false;
        if (!d.exists() && !d.mkdirs()) return false;

        List<WallpaperItem> items = new ArrayList<>();
        if (feedUrl != null && !feedUrl.trim().isEmpty()) {
            List<WallpaperItem> remote = fetchFeed(feedUrl.trim());
            if (remote != null && !remote.isEmpty()) {
                items.addAll(remote);
                Log.i(TAG, "Loaded " + items.size() + " wallpapers from remote feed: " + feedUrl);
            }
        }

        // If no remote items, use the curated catalog
        if (items.isEmpty()) {
            Collections.addAll(items, CURATED_CATALOG);
        }

        // Shuffle candidate pool so rotation changes over time
        List<WallpaperItem> pool = new ArrayList<>(items);
        Collections.shuffle(pool);

        boolean anyDownloaded = false;
        int targetNew = 5; // Download up to 5 fresh pictures per sync
        int downloadedCount = 0;

        for (WallpaperItem item : pool) {
            if (downloadedCount >= targetNew) break;
            String fileName = hashFilename(item.url) + ".jpg";
            File targetFile = new File(d, fileName);
            if (targetFile.exists() && targetFile.length() > 50_000) {
                continue; // Already cached
            }

            File tempFile = new File(d, fileName + ".tmp");
            File tempTxt = new File(d, fileName.replace(".jpg", ".txt") + ".tmp");
            try {
                if (downloadUrlToFile(item.url, tempFile)) {
                    // Write metadata
                    String meta = (item.title != null ? item.title : "4K Wallpaper") + "\n"
                            + (item.description != null ? item.description : "") + "\n"
                            + (item.category != null ? item.category : "Desktop");
                    try (OutputStream os = new FileOutputStream(tempTxt)) {
                        os.write(meta.getBytes(StandardCharsets.UTF_8));
                    }

                    File finalTxt = new File(d, fileName.replace(".jpg", ".txt"));
                    if (tempFile.renameTo(targetFile)) {
                        tempTxt.renameTo(finalTxt);
                        anyDownloaded = true;
                        downloadedCount++;
                        Log.i(TAG, "Saved 4K wallpaper: " + item.title + " (" + targetFile.length() + " bytes)");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed downloading " + item.url + ": " + e);
            } finally {
                if (tempFile.exists()) tempFile.delete();
                if (tempTxt.exists()) tempTxt.delete();
            }
        }

        // Prune old files if cache exceeds maximum allowed files
        pruneCache(d);

        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                .apply();

        return anyDownloaded;
    }

    private static String hashFilename(String url) {
        return "online_" + Math.abs(url.hashCode());
    }

    private static boolean downloadUrlToFile(String strUrl, File dest) {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            URL url = new URL(strUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 GlassTV/2.4 (https://github.com/aqmangge/glass-tv-home)");
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + code + " for " + strUrl);
                return false;
            }

            in = conn.getInputStream();
            out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    Log.w(TAG, "Image exceeds byte limit, aborting: " + strUrl);
                    return false;
                }
                out.write(buf, 0, read);
            }
            out.flush();
            return total > 10_000;
        } catch (Exception e) {
            Log.w(TAG, "Download error: " + e.getMessage());
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private static List<WallpaperItem> fetchFeed(String feedUrl) {
        List<WallpaperItem> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(feedUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(10_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (GlassTV; Android)");
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return list;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String raw = sb.toString().trim();
            if (raw.startsWith("[")) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    Object o = arr.get(i);
                    if (o instanceof String) {
                        list.add(new WallpaperItem((String) o, "Online Wallpaper", "", "Web Server"));
                    } else if (o instanceof JSONObject) {
                        JSONObject obj = (JSONObject) o;
                        String u = obj.optString("url");
                        if (!u.isEmpty()) {
                            list.add(new WallpaperItem(
                                    u,
                                    obj.optString("title", "Online Wallpaper"),
                                    obj.optString("description", ""),
                                    obj.optString("category", "Web Server")));
                        }
                    }
                }
            } else if (raw.startsWith("{")) {
                JSONObject obj = new JSONObject(raw);
                JSONArray arr = obj.optJSONArray("wallpapers");
                if (arr == null) arr = obj.optJSONArray("images");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        Object o = arr.get(i);
                        if (o instanceof String) {
                            list.add(new WallpaperItem((String) o, "Online Wallpaper", "", "Web Server"));
                        } else if (o instanceof JSONObject) {
                            JSONObject item = (JSONObject) o;
                            String u = item.optString("url");
                            if (!u.isEmpty()) {
                                list.add(new WallpaperItem(
                                        u,
                                        item.optString("title", "Online Wallpaper"),
                                        item.optString("description", ""),
                                        item.optString("category", "Web Server")));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Feed parse error: " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }

    private static void pruneCache(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        if (files == null || files.length <= MAX_CACHE_FILES) return;

        List<File> list = new ArrayList<>();
        Collections.addAll(list, files);
        // Sort oldest first
        Collections.sort(list, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

        int toRemove = list.size() - MAX_CACHE_FILES;
        for (int i = 0; i < toRemove; i++) {
            File f = list.get(i);
            File txt = new File(dir, f.getName().replace(".jpg", ".txt"));
            f.delete();
            if (txt.exists()) txt.delete();
        }
    }
}
