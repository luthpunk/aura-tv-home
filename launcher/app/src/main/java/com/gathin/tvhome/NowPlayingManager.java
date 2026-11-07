package com.gathin.tvhome;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages active music playback detection for all media players (Spotify, YouTube, Apple Music, VLC, etc.).
 */
public final class NowPlayingManager {
    private static final String TAG = "TvHome.NowPlaying";

    public static boolean isPlayingState(PlaybackState state) {
        if (state == null) return false;
        int s = state.getState();
        return s == PlaybackState.STATE_PLAYING
                || s == PlaybackState.STATE_BUFFERING
                || s == PlaybackState.STATE_CONNECTING
                || s == PlaybackState.STATE_SKIPPING_TO_NEXT
                || s == PlaybackState.STATE_SKIPPING_TO_PREVIOUS
                || s == PlaybackState.STATE_FAST_FORWARDING
                || s == PlaybackState.STATE_REWINDING;
    }

    public static boolean isAllowedMusicApp(String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return false;
        String p = pkg.toLowerCase(java.util.Locale.ROOT).trim();
        if (p.equals("com.gathin.tvhome")
                || p.equals("com.gathin.auratvtools")
                || p.equals("android")
                || p.startsWith("com.android.systemui")
                || p.startsWith("com.google.android.katniss")
                || p.startsWith("com.google.android.gms")
                || p.startsWith("com.google.android.gsf")
                || p.startsWith("com.android.vending")
                || p.startsWith("com.google.android.apps.messaging")
                || p.startsWith("com.google.android.dialer")
                || p.startsWith("com.google.android.inputmethod")
                || p.startsWith("com.android.providers.")
                || p.startsWith("com.android.settings")
                || p.startsWith("com.android.chrome")) {
            return false;
        }
        return true;
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        if (context == null || pkg == null || pkg.trim().isEmpty()) return false;
        try {
            context.getPackageManager().getPackageInfo(pkg.trim(), 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isNotificationAccessGranted(Context context) {
        if (context == null) return false;
        try {
            String pkgName = context.getPackageName();
            String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
            if (flat != null && !flat.isEmpty()) {
                String[] names = flat.split(":");
                for (String name : names) {
                    ComponentName cn = ComponentName.unflattenFromString(name);
                    if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static void openNotificationAccessSettings(Context context) {
        if (context == null) return;
        // Priority 1: Direct to Android TV Apps menu where "Akses aplikasi khusus" is located
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.device.apps.AppsActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // Priority 2: Standard Manage Applications Settings
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // Priority 3: Application settings intent
        try {
            Intent intent = new Intent("android.settings.APPLICATION_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // Priority 4: Try standard notification listener settings
        try {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // Priority 5: Fallback to General Settings
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {}
    }

    public static class MediaInfo {
        public final boolean isPlaying;
        public final String title;
        public final String artist;
        public final String packageName;
        public final String appName;
        public final MediaController controller;
        public final Bitmap albumArt;
        public final Uri albumArtUri;

        public MediaInfo(boolean isPlaying, String title, String artist, String packageName, String appName, MediaController controller, Bitmap albumArt) {
            this(isPlaying, title, artist, packageName, appName, controller, albumArt, null);
        }

        public MediaInfo(boolean isPlaying, String title, String artist, String packageName, String appName, MediaController controller, Bitmap albumArt, Uri albumArtUri) {
            this.isPlaying = isPlaying;
            this.title = title != null ? title : "";
            this.artist = artist != null ? artist : "";
            this.packageName = packageName != null ? packageName : "";
            this.appName = appName != null ? appName : "";
            this.controller = controller;
            this.albumArt = albumArt;
            this.albumArtUri = albumArtUri;
        }

        public boolean hasMusic() {
            if (packageName.isEmpty()) {
                return false;
            }
            if (!isAllowedMusicApp(packageName)) {
                return false;
            }
            if (appContext != null && !isPackageInstalled(appContext, packageName)) {
                return false;
            }
            return isPlaying || (!title.isEmpty());
        }

        public String getDisplayText() {
            if (!hasMusic()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (isPlaying) {
                sb.append("▶ ");
            } else {
                sb.append("⏸ ");
            }
            if (!title.isEmpty()) {
                sb.append(title);
                if (!artist.isEmpty()) {
                    sb.append(" • ").append(artist);
                }
            } else if (!appName.isEmpty()) {
                sb.append(appName);
            }
            return sb.toString();
        }
    }

    public interface Listener {
        void onMediaChanged(MediaInfo info);
    }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static MediaInfo currentInfo = new MediaInfo(false, "", "", "", "", null, null);
    private static MediaInfo lastValidMediaInfo = null;
    private static long lastValidMediaTimestamp = 0;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;
    private static Context appContext;
    private static MediaController trackedController;

    private static final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (appContext != null) updateCurrentMedia(appContext);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            if (appContext != null) updateCurrentMedia(appContext);
        }

        @Override
        public void onSessionDestroyed() {
            trackedController = null;
            if (appContext != null) updateCurrentMedia(appContext);
        }
    };

    private NowPlayingManager() {}

    public static void registerListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            listener.onMediaChanged(currentInfo);
        }
    }

    public static void unregisterListener(Listener listener) {
        listeners.remove(listener);
    }

    public static MediaInfo getCurrentInfo() {
        return currentInfo;
    }

    public static void clearMedia() {
        lastValidMediaInfo = null;
        lastValidMediaTimestamp = 0;
        currentInfo = new MediaInfo(false, "", "", "", "", null, null);
        mainHandler.post(() -> {
            for (Listener l : listeners) {
                l.onMediaChanged(currentInfo);
            }
        });
    }

    public static void notifySessionsChanged(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
        updateCurrentMedia(appContext != null ? appContext : context);
    }

    public static void notifyListenerConnected(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        registerActiveSessionsListener(appContext);
        updateCurrentMedia(appContext);
    }

    public static void init(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        MediaNotificationListener.requestRebindIfDisconnected(appContext);
        registerActiveSessionsListener(appContext);
        updateCurrentMedia(appContext);
    }

    private static void registerActiveSessionsListener(Context context) {
        try {
            MediaSessionManager msm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                ComponentName cn = MediaNotificationListener.getComponentName(context);
                if (sessionsListener == null) {
                    sessionsListener = controllers -> updateCurrentMedia(context);
                }
                try {
                    msm.removeOnActiveSessionsChangedListener(sessionsListener);
                } catch (Exception ignored) {}
                try {
                    msm.addOnActiveSessionsChangedListener(sessionsListener, cn, mainHandler);
                } catch (SecurityException ignored) {
                    Log.w(TAG, "Notification listener access not yet granted");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering active sessions listener: " + e.getMessage());
        }
    }

    public static synchronized void updateCurrentMedia(Context context) {
        if (context == null) return;
        if (appContext == null) appContext = context.getApplicationContext();

        try {
            MediaSessionManager msm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = MediaNotificationListener.getComponentName(context);

            MediaController activeController = null;
            if (msm != null) {
                try {
                    List<MediaController> controllers = msm.getActiveSessions(cn);
                    if (controllers != null && !controllers.isEmpty()) {
                        // Priority 1: Actively playing controller from Spotify or YouTube Music
                        for (MediaController mc : controllers) {
                            if (mc == null || !isAllowedMusicApp(mc.getPackageName())) continue;
                            PlaybackState state = mc.getPlaybackState();
                            if (isPlayingState(state)) {
                                activeController = mc;
                                break;
                            }
                        }
                        // Priority 2: Paused controller from Spotify or YouTube Music
                        if (activeController == null) {
                            for (MediaController mc : controllers) {
                                if (mc == null || !isAllowedMusicApp(mc.getPackageName())) continue;
                                PlaybackState state = mc.getPlaybackState();
                                if (state != null && state.getState() == PlaybackState.STATE_PAUSED) {
                                    activeController = mc;
                                    break;
                                }
                            }
                        }
                    }
                } catch (SecurityException ignored) {
                }
            }

            // Sync controller callback
            if (trackedController != activeController) {
                if (trackedController != null) {
                    try {
                        trackedController.unregisterCallback(controllerCallback);
                    } catch (Exception ignored) {}
                }
                trackedController = activeController;
                if (trackedController != null) {
                    try {
                        trackedController.registerCallback(controllerCallback, mainHandler);
                    } catch (Exception ignored) {}
                }
            }

            String title = "";
            String artist = "";
            String pkg = "";
            Bitmap albumArt = null;
            Uri albumArtUri = null;
            boolean isPlaying = false;

            if (activeController != null) {
                pkg = activeController.getPackageName();
                PlaybackState state = activeController.getPlaybackState();
                isPlaying = isPlayingState(state);

                MediaMetadata metadata = activeController.getMetadata();
                if (metadata != null) {
                    MediaDescription desc = metadata.getDescription();
                    if (desc != null) {
                        if (desc.getTitle() != null && desc.getTitle().length() > 0) {
                            title = desc.getTitle().toString();
                        }
                        if (desc.getSubtitle() != null && desc.getSubtitle().length() > 0) {
                            artist = desc.getSubtitle().toString();
                        }
                        if (desc.getDescription() != null && artist.isEmpty()) {
                            artist = desc.getDescription().toString();
                        }
                        if (albumArt == null && desc.getIconBitmap() != null) {
                            albumArt = desc.getIconBitmap();
                        }
                        if (albumArtUri == null && desc.getIconUri() != null) {
                            albumArtUri = desc.getIconUri();
                        }
                    }

                    if (title.isEmpty()) {
                        CharSequence titleCs = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
                        if (titleCs != null && titleCs.length() > 0) {
                            title = titleCs.toString();
                        }
                    }

                    if (artist.isEmpty()) {
                        CharSequence artistCs = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
                        if (artistCs == null || artistCs.length() == 0) {
                            artistCs = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                        }
                        if (artistCs == null || artistCs.length() == 0) {
                            artistCs = metadata.getText(MediaMetadata.METADATA_KEY_AUTHOR);
                        }
                        if (artistCs != null && artistCs.length() > 0) {
                            artist = artistCs.toString();
                        }
                    }

                    if (albumArt == null) {
                        try {
                            albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                            if (albumArt == null) {
                                albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                            }
                        } catch (Exception ignored) {}
                    }
                    if (albumArtUri == null) {
                        try {
                            String u = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
                            if (u == null || u.isEmpty()) {
                                u = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);
                            }
                            if (u == null || u.isEmpty()) {
                                u = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
                            }
                            if (u != null && !u.isEmpty()) {
                                albumArtUri = Uri.parse(u);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Ekstraksi cover album / thumbnail dari notifikasi media player
            if (MediaNotificationListener.isConnected() || isNotificationAccessGranted(context)) {
                MediaNotificationListener.requestRebindIfDisconnected(context);
                try {
                    StatusBarNotification[] sbns = null;
                    if (MediaNotificationListener.isConnected()) {
                        MediaNotificationListener listener = MediaNotificationListener.getInstance();
                        if (listener != null) {
                            try {
                                java.lang.reflect.Method m = NotificationListenerService.class.getMethod("getActiveNotifications", String[].class, int.class);
                                sbns = (StatusBarNotification[]) m.invoke(listener, (Object) null, 0);
                            } catch (Throwable ignored) {}
                            if (sbns == null || sbns.length == 0) {
                                try {
                                    sbns = listener.getActiveNotifications();
                                } catch (Throwable ignored) {}
                            }
                        }
                    }

                    if (sbns != null) {
                        for (StatusBarNotification sbn : sbns) {
                            if (sbn == null || sbn.getNotification() == null) continue;
                            String sbnPkg = sbn.getPackageName();
                            if (!isAllowedMusicApp(sbnPkg)) continue;
                            if (!isPackageInstalled(context, sbnPkg)) continue;

                            // Jika media player aktif sudah terdeteksi (misal Spotify atau YouTube),
                            // fokuskan pencarian notifikasi pada paket pemutar yang bersangkutan.
                            if (!pkg.isEmpty() && !sbnPkg.equalsIgnoreCase(pkg)) {
                                continue;
                            }

                            Notification n = sbn.getNotification();
                            Bundle extras = n.extras;
                            if (extras == null) continue;

                            // Ambil cover album / video thumbnail dari berbagai field icon notifikasi
                            if (albumArt == null) {
                                Object iconObj = extras.get(Notification.EXTRA_LARGE_ICON);
                                if (iconObj instanceof Bitmap) {
                                    albumArt = (Bitmap) iconObj;
                                } else if (iconObj instanceof Icon) {
                                    try {
                                        Drawable d = ((Icon) iconObj).loadDrawable(context);
                                        if (d instanceof BitmapDrawable) {
                                            albumArt = ((BitmapDrawable) d).getBitmap();
                                        } else if (d != null && d.getIntrinsicWidth() > 0 && d.getIntrinsicHeight() > 0) {
                                            Bitmap b = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                                            Canvas cvs = new Canvas(b);
                                            d.setBounds(0, 0, cvs.getWidth(), cvs.getHeight());
                                            d.draw(cvs);
                                            albumArt = b;
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                            if (albumArt == null) {
                                Object iconBig = extras.get("android.largeIcon.big");
                                if (iconBig instanceof Bitmap) {
                                    albumArt = (Bitmap) iconBig;
                                } else if (iconBig instanceof Icon) {
                                    try {
                                        Drawable d = ((Icon) iconBig).loadDrawable(context);
                                        if (d instanceof BitmapDrawable) {
                                            albumArt = ((BitmapDrawable) d).getBitmap();
                                        } else if (d != null && d.getIntrinsicWidth() > 0 && d.getIntrinsicHeight() > 0) {
                                            Bitmap b = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                                            Canvas cvs = new Canvas(b);
                                            d.setBounds(0, 0, cvs.getWidth(), cvs.getHeight());
                                            d.draw(cvs);
                                            albumArt = b;
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                            if (albumArt == null) {
                                Object picObj = extras.get(Notification.EXTRA_PICTURE);
                                if (picObj instanceof Bitmap) {
                                    albumArt = (Bitmap) picObj;
                                }
                            }

                            // Ekstraksi judul & artis jika belum terisi dari MediaController
                            CharSequence nTitle = extras.getCharSequence(Notification.EXTRA_TITLE);
                            CharSequence nText = extras.getCharSequence(Notification.EXTRA_TEXT);
                            if (nTitle != null && nTitle.length() > 0) {
                                if (title.isEmpty()) {
                                    if (!"Spotify".equalsIgnoreCase(nTitle.toString()) && !"YouTube".equalsIgnoreCase(nTitle.toString())) {
                                        title = nTitle.toString();
                                        if (nText != null && !"Spotify".equalsIgnoreCase(nText.toString()) && !"YouTube".equalsIgnoreCase(nText.toString())) {
                                            artist = nText.toString();
                                        }
                                    } else if (nText != null && !"Spotify".equalsIgnoreCase(nText.toString()) && !"YouTube".equalsIgnoreCase(nText.toString())) {
                                        title = nText.toString();
                                    }
                                    if (pkg.isEmpty()) pkg = sbnPkg;
                                }
                            }

                            if (activeController == null) {
                                Object tokenObj = extras.get(Notification.EXTRA_MEDIA_SESSION);
                                if (tokenObj instanceof MediaSession.Token) {
                                    try {
                                        activeController = new MediaController(context, (MediaSession.Token) tokenObj);
                                        PlaybackState ps = activeController.getPlaybackState();
                                        isPlaying = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
                                    } catch (Exception ignored) {}
                                }
                            }

                            if (albumArt != null) {
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (pkg.isEmpty() && activeController != null) {
                pkg = activeController.getPackageName();
            }

            if (!pkg.isEmpty() && !title.isEmpty() && isAllowedMusicApp(pkg) && isPackageInstalled(context, pkg)) {
                String appName = getAppName(context, pkg);
                currentInfo = new MediaInfo(isPlaying, title, artist, pkg, appName, activeController, albumArt, albumArtUri);
                // Remember this valid playback session for retention when YouTube non-premium pauses upon exiting!
                lastValidMediaInfo = currentInfo;
                lastValidMediaTimestamp = System.currentTimeMillis();
            } else if (lastValidMediaInfo != null
                    && !lastValidMediaInfo.title.isEmpty()
                    && isAllowedMusicApp(lastValidMediaInfo.packageName)
                    && isPackageInstalled(context, lastValidMediaInfo.packageName)
                    && (System.currentTimeMillis() - lastValidMediaTimestamp < 20 * 60 * 1000)) {
                // Retention for YouTube Non-Premium or paused music: keep showing the video/song!
                currentInfo = new MediaInfo(
                        false,
                        lastValidMediaInfo.title,
                        lastValidMediaInfo.artist,
                        lastValidMediaInfo.packageName,
                        lastValidMediaInfo.appName,
                        lastValidMediaInfo.controller,
                        lastValidMediaInfo.albumArt,
                        lastValidMediaInfo.albumArtUri
                );
            } else if (activeController == null && title.isEmpty()) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                boolean isMusic = am != null && am.isMusicActive();
                if (isMusic) {
                    boolean granted = isNotificationAccessGranted(context);
                    currentInfo = new MediaInfo(
                            true,
                            "Musik Diputar",
                            granted ? "Sedang memutar audio" : "Klik aktifkan izin lagu",
                            "",
                            "Media Player",
                            null,
                            null
                    );
                } else {
                    currentInfo = new MediaInfo(false, "", "", "", "", null, null);
                }
            } else {
                currentInfo = new MediaInfo(false, "", "", "", "", null, null);
            }

            mainHandler.post(() -> {
                for (Listener l : listeners) {
                    l.onMediaChanged(currentInfo);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error updating current media: " + e.getMessage());
        }
    }

    public static String getAppName(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return "";
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            if ("com.spotify.tv.android".equals(pkg) || "com.spotify.music".equals(pkg)) return "Spotify";
            if ("com.google.android.youtube.tv".equals(pkg) || "com.google.android.youtube".equals(pkg)) return "YouTube";
            if (pkg.contains("music")) return "YouTube Music";
            if (pkg.contains("youtube")) return "YouTube";
            return pkg;
        }
    }

    public static void togglePlayPause() {
        if (currentInfo != null && currentInfo.controller != null) {
            try {
                PlaybackState state = currentInfo.controller.getPlaybackState();
                boolean isCurrentlyPlaying = isPlayingState(state) || currentInfo.isPlaying;
                if (isCurrentlyPlaying) {
                    currentInfo.controller.getTransportControls().pause();
                } else {
                    currentInfo.controller.getTransportControls().play();
                }
                boolean newPlaying = !isCurrentlyPlaying;
                currentInfo = new MediaInfo(
                        newPlaying,
                        currentInfo.title,
                        currentInfo.artist,
                        currentInfo.packageName,
                        currentInfo.appName,
                        currentInfo.controller,
                        currentInfo.albumArt,
                        currentInfo.albumArtUri
                );
                mainHandler.post(() -> {
                    for (Listener l : listeners) {
                        l.onMediaChanged(currentInfo);
                    }
                });
                return;
            } catch (Exception e) {
                Log.e(TAG, "Error toggling play/pause via controller: " + e.getMessage());
            }
        }
        if (appContext != null) {
            try {
                AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                    am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                }
            } catch (Exception ignored) {}
        }
    }

    public static void pausePlayback() {
        if (currentInfo != null && currentInfo.controller != null) {
            try {
                currentInfo.controller.getTransportControls().pause();
                currentInfo = new MediaInfo(
                        false,
                        currentInfo.title,
                        currentInfo.artist,
                        currentInfo.packageName,
                        currentInfo.appName,
                        currentInfo.controller,
                        currentInfo.albumArt,
                        currentInfo.albumArtUri
                );
                mainHandler.post(() -> {
                    for (Listener l : listeners) {
                        l.onMediaChanged(currentInfo);
                    }
                });
                return;
            } catch (Exception e) {
                Log.e(TAG, "Error pausing via controller: " + e.getMessage());
            }
        }
        if (appContext != null) {
            try {
                AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
                    am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
                }
            } catch (Exception ignored) {}
        }
    }

    public static void skipToNext() {
        if (currentInfo != null && currentInfo.controller != null) {
            try {
                currentInfo.controller.getTransportControls().skipToNext();
                currentInfo = new MediaInfo(
                        true,
                        currentInfo.title,
                        currentInfo.artist,
                        currentInfo.packageName,
                        currentInfo.appName,
                        currentInfo.controller,
                        currentInfo.albumArt,
                        currentInfo.albumArtUri
                );
                mainHandler.post(() -> {
                    for (Listener l : listeners) {
                        l.onMediaChanged(currentInfo);
                    }
                });
                return;
            } catch (Exception ignored) {}
        }
        if (appContext != null) {
            try {
                AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
                    am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT));
                }
            } catch (Exception ignored) {}
        }
    }

    public static void skipToPrevious() {
        if (currentInfo != null && currentInfo.controller != null) {
            try {
                currentInfo.controller.getTransportControls().skipToPrevious();
                currentInfo = new MediaInfo(
                        true,
                        currentInfo.title,
                        currentInfo.artist,
                        currentInfo.packageName,
                        currentInfo.appName,
                        currentInfo.controller,
                        currentInfo.albumArt,
                        currentInfo.albumArtUri
                );
                mainHandler.post(() -> {
                    for (Listener l : listeners) {
                        l.onMediaChanged(currentInfo);
                    }
                });
                return;
            } catch (Exception ignored) {}
        }
        if (appContext != null) {
            try {
                AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
                    am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
                }
            } catch (Exception ignored) {}
        }
    }

    public static void openMediaApp(Context context) {
        if (context == null || currentInfo == null) return;
        if (currentInfo.controller != null) {
            PendingIntent pi = currentInfo.controller.getSessionActivity();
            if (pi != null) {
                try {
                    pi.send();
                    return;
                } catch (Exception ignored) {}
            }
        }
        String pkg = currentInfo.packageName;
        if (pkg == null || pkg.isEmpty() || !isPackageInstalled(context, pkg)) {
            if (isPackageInstalled(context, "com.google.android.youtube.tv")) pkg = "com.google.android.youtube.tv";
            else if (isPackageInstalled(context, "com.google.android.youtube")) pkg = "com.google.android.youtube";
            else if (isPackageInstalled(context, "com.google.android.apps.youtube.music")) pkg = "com.google.android.apps.youtube.music";
            else if (isPackageInstalled(context, "com.spotify.tv.android")) pkg = "com.spotify.tv.android";
            else if (isPackageInstalled(context, "com.spotify.music")) pkg = "com.spotify.music";
            else return;
        }
        try {
            Intent i = context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) {
                i = context.getPackageManager().getLaunchIntentForPackage(pkg);
            }
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        } catch (Exception ignored) {}
    }
}
