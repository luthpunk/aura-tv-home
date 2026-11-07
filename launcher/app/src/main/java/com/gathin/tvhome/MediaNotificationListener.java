package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class MediaNotificationListener extends NotificationListenerService {
    private static final String TAG = "TvHome.MediaListener";
    private static MediaNotificationListener instance;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        Log.i(TAG, "MediaNotificationListener connected");
        NowPlayingManager.notifyListenerConnected(this);
    }

    @Override
    public void onListenerDisconnected() {
        if (instance == this) instance = null;
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        NowPlayingManager.notifySessionsChanged(this);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        NowPlayingManager.notifySessionsChanged(this);
    }

    public static boolean isConnected() {
        return instance != null;
    }

    public static void requestRebindIfDisconnected(Context context) {
        if (instance == null && context != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    NotificationListenerService.requestRebind(getComponentName(context));
                    Log.i(TAG, "Requested rebind for MediaNotificationListener");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to request rebind: " + e.getMessage());
            }
        }
    }

    public static MediaNotificationListener getInstance() {
        return instance;
    }

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, MediaNotificationListener.class);
    }
}
