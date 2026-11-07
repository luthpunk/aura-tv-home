package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class MediaNotificationListener extends NotificationListenerService {
    private static final String TAG = "TvHome.MediaListener";
    private static MediaNotificationListener instance;
    private static final java.util.Map<String, StatusBarNotification> postedNotifications = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        Log.i(TAG, "MediaNotificationListener connected");
        try {
            StatusBarNotification[] sbns = getActiveFullNotifications();
            if (sbns != null) {
                for (StatusBarNotification sbn : sbns) {
                    if (sbn != null && sbn.getPackageName() != null) {
                        postedNotifications.put(sbn.getPackageName(), sbn);
                    }
                }
            }
        } catch (Throwable ignored) {}
        NowPlayingManager.notifyListenerConnected(this);
    }

    @Override
    public void onListenerDisconnected() {
        if (instance == this) instance = null;
        postedNotifications.clear();
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (sbn != null && sbn.getPackageName() != null) {
            postedNotifications.put(sbn.getPackageName(), sbn);
        }
        NowPlayingManager.notifySessionsChanged(this);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        if (sbn != null && sbn.getPackageName() != null) {
            postedNotifications.remove(sbn.getPackageName());
        }
        NowPlayingManager.notifySessionsChanged(this);
    }

    public static StatusBarNotification[] getActiveFullNotifications() {
        if (instance == null) return null;
        StatusBarNotification[] sbns = null;
        try {
            java.lang.reflect.Method m = NotificationListenerService.class.getMethod("getActiveNotifications", int.class);
            sbns = (StatusBarNotification[]) m.invoke(instance, 0); // 0 = TRIM_FULL
        } catch (Throwable t1) {
            try {
                java.lang.reflect.Method m = NotificationListenerService.class.getMethod("getActiveNotifications", String[].class, int.class);
                sbns = (StatusBarNotification[]) m.invoke(instance, (Object) null, 0); // 0 = TRIM_FULL
            } catch (Throwable t2) {
                try {
                    sbns = instance.getActiveNotifications();
                } catch (Throwable t3) {
                    sbns = null;
                }
            }
        }
        if (sbns != null) {
            for (StatusBarNotification sbn : sbns) {
                if (sbn != null && sbn.getPackageName() != null) {
                    postedNotifications.put(sbn.getPackageName(), sbn);
                }
            }
        }
        return sbns;
    }

    public static StatusBarNotification getPostedNotification(String pkg) {
        if (pkg == null) return null;
        return postedNotifications.get(pkg);
    }

    public static java.util.Collection<StatusBarNotification> getAllPostedNotifications() {
        return postedNotifications.values();
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
