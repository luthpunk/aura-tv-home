package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages OTA in-app update checks, changelog display, and verified APK downloading
 * for Aura TV Home directly on Android TV.
 */
public final class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    private static final String VERSION_URL = "https://raw.githubusercontent.com/luthpunk/aura-tv-home/main/release/version.json";
    private static final String PREFS_NAME = "update_prefs";
    private static final String KEY_LAST_CHECK = "last_check_timestamp";
    public static final String KEY_AUTO_UPDATE = "pref_auto_update";
    public static final String KEY_AUTO_UPDATE_TIME = "pref_auto_update_time";
    public static final String DEFAULT_AUTO_UPDATE_TIME = "03:30 AM";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean isDownloading = new AtomicBoolean(false);

    private AppUpdateManager() {
    }

    public static boolean isAutoUpdateEnabled(Context context) {
        if (context == null) return true;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_AUTO_UPDATE, true); // default ON
    }

    public static void setAutoUpdateEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply();
    }

    public static String getAutoUpdateTime(Context context) {
        if (context == null) return DEFAULT_AUTO_UPDATE_TIME;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_AUTO_UPDATE_TIME, DEFAULT_AUTO_UPDATE_TIME);
    }

    public static void setAutoUpdateTime(Context context, String time) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_AUTO_UPDATE_TIME, time != null ? time : DEFAULT_AUTO_UPDATE_TIME).apply();
    }

    public static void checkAutoUpdateIfDue(Activity activity) {
        if (activity == null || !isAutoUpdateEnabled(activity)) return;
        SharedPreferences sp = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = sp.getLong(KEY_LAST_CHECK, 0);
        // Check once every 12 hours at least
        if (System.currentTimeMillis() - lastCheck >= 12 * 3600 * 1000L) {
            checkForUpdates(activity, false);
        }
    }

    public interface CheckCallback {
        void onResult(boolean hasUpdate, String newVersionName, String changelog, String apkUrl);
    }

    /**
     * Checks for updates. If manualCheck is true, shows feedback toasts even when up-to-date.
     */
    public static void checkForUpdates(Activity activity, boolean manualCheck) {
        if (activity == null || activity.isFinishing() || isDownloading.get()) return;

        if (manualCheck) {
            TvToast.show(activity, "🔄 Memeriksa pembaruan terbaru...");
        }

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(VERSION_URL + "?t=" + System.currentTimeMillis());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    if (manualCheck) {
                        mainHandler.post(() -> TvToast.show(activity, "❌ Gagal memeriksa server pembaruan (HTTP " + responseCode + ")"));
                    }
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }

                JSONObject json = new JSONObject(sb.toString());
                int remoteVersionCode = json.optInt("versionCode", 0);
                String remoteVersionName = json.optString("versionName", "");
                String apkUrl = json.optString("apkUrl", "");
                String changelog = json.optString("changelog", "Peningkatan performa dan stabilitas.");

                PackageManager pm = activity.getPackageManager();
                PackageInfo pInfo = pm.getPackageInfo(activity.getPackageName(), 0);
                int currentVersionCode = pInfo.versionCode;
                String currentVersionName = pInfo.versionName;

                // Save timestamp
                SharedPreferences sp = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                sp.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();

                mainHandler.post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;

                    if (remoteVersionCode > currentVersionCode) {
                        showUpdateDialog(activity, remoteVersionName, currentVersionName, changelog, apkUrl);
                    } else {
                        if (manualCheck) {
                            TvToast.show(activity, "✅ Aura TV Home sudah versi terbaru (v" + currentVersionName + ")!", true);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error checking updates: " + e.getMessage());
                if (manualCheck) {
                    mainHandler.post(() -> TvToast.show(activity, "❌ Gagal memeriksa pembaruan. Pastikan Wi-Fi terhubung."));
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static void showUpdateDialog(Activity activity, String newVersionName, String currentVersionName, String changelog, String apkUrl) {
        if (isDownloading.get()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("🚀 Pembaruan Baru Tersedia (v" + newVersionName + ")");

        String message = "Versi Saat Ini: v" + currentVersionName + "\n"
                + "Versi Terbaru: v" + newVersionName + "\n\n"
                + "📋 Catatan Pembaruan:\n" + changelog + "\n\n"
                + "Apakah Anda ingin mengunduh dan memasangnya sekarang?";
        builder.setMessage(message);

        builder.setPositiveButton("Unduh & Pasang", (dialog, which) -> {
            dialog.dismiss();
            downloadAndInstallApk(activity, apkUrl, newVersionName);
        });

        builder.setNegativeButton("Nanti Saja", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
        MainActivity.styleGlassDialog(activity, dialog);
    }

    private static HttpURLConnection openFollowingRedirects(String targetUrl) throws IOException {
        String currentUrl = targetUrl;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(currentUrl).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "AuraTVHome-OTA");
            conn.setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            conn.setUseCaches(false);
            conn.connect();

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307
                    || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new IOException("Redirect Location kosong (HTTP " + code + ")");
                }
                currentUrl = new URL(new URL(currentUrl), location).toString();
                continue;
            }
            return conn;
        }
        throw new IOException("Terlalu banyak pengalihan URL unduhan");
    }

    @SuppressWarnings("deprecation")
    private static void downloadAndInstallApk(Activity activity, String apkUrl, String versionName) {
        // Check install permission on Android 8.0+ (Oreo, API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                AlertDialog.Builder permDialog = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog);
                permDialog.setTitle("⚠️ Izin Pemasangan Diperlukan");
                permDialog.setMessage("Aktifkan 'Allow from this source' untuk mengizinkan Aura TV Home memasang pembaruan otomatis.");
                permDialog.setPositiveButton("Buka Pengaturan", (d, w) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        TvToast.show(activity, "Buka Pengaturan > Keamanan > Install Unknown Apps", true);
                    }
                });
                permDialog.setNegativeButton("Batal", (d, w) -> d.dismiss());
                AlertDialog pDialog = permDialog.create();
                pDialog.show();
                MainActivity.styleGlassDialog(activity, pDialog);
                return;
            }
        }

        if (!isDownloading.compareAndSet(false, true)) {
            TvToast.show(activity, "⏳ Unduhan pembaruan sedang berjalan...");
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(activity, R.style.Theme_TvHome_GlassDialog);
        progressDialog.setTitle("📥 Mengunduh Pembaruan (v" + versionName + ")");
        progressDialog.setMessage("Sedang mengunduh file pemasangan...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.setCancelable(false);
        progressDialog.show();
        MainActivity.styleGlassDialog(activity, progressDialog);

        executor.execute(() -> {
            // Use internal cacheDir (never external Android/data, which Scoped Storage blocks for PackageInstaller)
            File cacheDir = activity.getCacheDir();
            File tmpFile = new File(cacheDir, "glass-tv-update.apk.tmp");
            File apkFile = new File(cacheDir, "glass-tv-update.apk");
            if (tmpFile.exists()) tmpFile.delete();

            InputStream is = null;
            FileOutputStream fos = null;
            HttpURLConnection conn = null;

            try {
                conn = openFollowingRedirects(apkUrl);
                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Server mengembalikan HTTP " + code);
                }

                int fileLength = conn.getContentLength();
                is = new BufferedInputStream(conn.getInputStream(), 16384);
                fos = new FileOutputStream(tmpFile);

                byte[] buffer = new byte[16384];
                long total = 0;
                int lastProgress = -1;
                int count;

                while ((count = is.read(buffer)) != -1) {
                    total += count;
                    fos.write(buffer, 0, count);
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            mainHandler.post(() -> {
                                if (!activity.isFinishing() && !activity.isDestroyed() && progressDialog.isShowing()) {
                                    progressDialog.setProgress(progress);
                                }
                            });
                        }
                    }
                }
                fos.flush();
                fos.getFD().sync();
                fos.close();
                fos = null;

                if (fileLength > 0 && total != fileLength) {
                    throw new IOException("Unduhan tidak lengkap (" + total + " / " + fileLength + " byte)");
                }
                if (total < 100_000L) {
                    throw new IOException("Ukuran file APK tidak valid (" + total + " byte)");
                }

                if (apkFile.exists()) apkFile.delete();
                if (!tmpFile.renameTo(apkFile)) {
                    throw new IOException("Gagal menyiapkan file APK pembaruan");
                }
                apkFile.setReadable(true, false);

                // Validate that Android's PackageManager can parse the downloaded APK before opening installer
                PackageInfo archiveInfo = activity.getPackageManager()
                        .getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
                if (archiveInfo == null || !activity.getPackageName().equals(archiveInfo.packageName)) {
                    apkFile.delete();
                    throw new IOException("File APK rusak saat diunduh, silakan coba lagi");
                }

                mainHandler.post(() -> {
                    isDownloading.set(false);
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        installApk(activity, apkFile);
                    }
                });

            } catch (Exception e) {
                isDownloading.set(false);
                if (tmpFile.exists()) tmpFile.delete();
                Log.e(TAG, "Download error: " + e.getMessage());
                mainHandler.post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed() && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    TvToast.show(activity, "❌ Gagal mengunduh pembaruan: " + e.getMessage(), true);
                });
            } finally {
                try { if (fos != null) fos.close(); } catch (Exception ignored) {}
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static void installApk(Activity activity, File apkFile) {
        if (!apkFile.exists()) {
            TvToast.show(activity, "File APK pembaruan tidak ditemukan.");
            return;
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setClipData(ClipData.newRawUri("", apkUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Install error: " + e.getMessage());
            TvToast.show(activity, "❌ Gagal memulai pemasangan: " + e.getMessage(), true);
        }
    }
}
