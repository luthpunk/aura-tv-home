package com.gathin.tvhome;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

/** Lets the user change HOME through the system UI, without shell or privileged permissions. */
public class HomeSettingsActivity extends Activity {

    private static final int REQ_ROLE_HOME = 901;

    /** Returns true if Aura TV Home is currently the system's default HOME activity. */
    public static boolean isDefaultHomeApp(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager rm = ctx.getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (rm.isRoleHeld(RoleManager.ROLE_HOME)) return true;
                }
            }
            PackageManager pm = ctx.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null
                    && ctx.getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Briefly toggles {@link HomeChooserTriggerActivity} (DONT_KILL_APP) so Android's PackageManager
     * invalidates any previous launcher's "Always" lock, allowing the remote's HOME button to show
     * the system "Select Home app" chooser dialog.
     */
    public static void resetPreferredHomeCache(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            ComponentName trigger = new ComponentName(ctx, HomeChooserTriggerActivity.class);
            pm.setComponentEnabledSetting(trigger,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addCategory(Intent.CATEGORY_DEFAULT);
            pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            pm.setComponentEnabledSetting(trigger,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        resetPreferredHomeCache(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE_HOME);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        openSystemHomePickerAndFinish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE_HOME) {
            if (resultCode == RESULT_OK || isDefaultHomeApp(this)) {
                finish();
                return;
            }
            openSystemHomePickerAndFinish();
        } else {
            finish();
        }
    }

    private void openSystemHomePickerAndFinish() {
        try {
            Intent settings = new Intent(Settings.ACTION_HOME_SETTINGS);
            if (getPackageManager().resolveActivity(settings, 0) != null) {
                startActivity(settings);
                TvToast.show(this, R.string.home_settings_help, true);
                finish();
                return;
            }
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
        try {
            Intent homeChooser = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeChooser);
            TvToast.show(this, R.string.home_settings_help, true);
        } catch (Exception unavailable) {
            TvToast.show(this, R.string.not_available, true);
        } finally {
            finish();
        }
    }
}
