package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

/**
 * Ensures Aura TV Home launches cleanly onto the screen after a full TV shutdown / cold boot
 * instead of leaving the TV on a blank screen until the remote Home key is pressed.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "TvHome.Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        if (MainActivity.isForeground()) {
            return;
        }

        if (!isDefaultHome(context) && !HomeKeyService.isAccessibilityActive()) {
            return;
        }

        try {
            Intent home = new Intent(context, MainActivity.class);
            home.setAction(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(home);
            Log.i(TAG, "Started MainActivity on " + action);
        } catch (Exception e) {
            Log.w(TAG, "Failed to start MainActivity on boot: " + e);
        }
    }

    private static boolean isDefaultHome(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null
                    && context.getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }
}
