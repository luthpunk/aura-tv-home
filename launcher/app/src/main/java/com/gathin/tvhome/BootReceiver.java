package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

/**
 * Ensures Aura TV Home launches cleanly onto the screen after a full TV shutdown / cold boot
 * instead of leaving the TV on Android's black FallbackHome surface until the remote Home key is pressed.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "TvHome.Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            UserManager um = context.getSystemService(UserManager.class);
            if (um != null && !um.isUserUnlocked()) {
                Log.i(TAG, "Device still locked on " + action + "; waiting for BOOT_COMPLETED");
                return;
            }
        }

        // Only auto-launch on boot if Aura TV Home is the default launcher (or the only valid home app)
        if (!isDefaultOrCandidateHome(context)) {
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

    private static boolean isDefaultOrCandidateHome(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) return true;
            String pkg = ri.activityInfo.packageName;
            // True if Aura TV Home is the default launcher, or if Android's resolver ("android") is current
            return context.getPackageName().equals(pkg)
                    || "android".equals(pkg)
                    || pkg.contains("resolver")
                    || pkg.contains("fallback");
        } catch (Exception e) {
            return true;
        }
    }
}
