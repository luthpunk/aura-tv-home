package com.gathin.tvhome;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

/**
 * Lets the user change HOME through the system UI, with an Accessibility fallback for locked OEM TVs.
 * Never enables/disables this app's own components at runtime: doing so restarted the Home activity
 * on several TV firmwares (looked like a boot loop) and interfered with OTA installs.
 */
public class HomeSettingsActivity extends Activity {

    private static final int REQ_ROLE_HOME = 901;

    /** Returns true if Aura TV Home is currently the system's default HOME activity. */
    public static boolean isDefaultHomeApp(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager rm = ctx.getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    return true;
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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        openSystemHomePickerOrAccessibility();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE_HOME) {
            if (resultCode == RESULT_OK || isDefaultHomeApp(this)) {
                TvToast.show(this, "Aura TV Home berhasil dijadikan Launcher Utama", false);
                finish();
                return;
            }
            // Locked OEM firmware (Google TV / Xiaomi / TCL) rejected the ROLE_HOME dialog.
            openSystemHomePickerOrAccessibility();
        } else {
            finish();
        }
    }

    private void openSystemHomePickerOrAccessibility() {
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

        // TVs that lock/remove Home Settings: guide the user to Accessibility (HomeKeyService)
        // so the remote's Home button is intercepted without ADB.
        if (!HomeKeyService.isAccessibilityActive()) {
            try {
                Intent acc = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                acc.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(acc);
                TvToast.show(this, "Aktifkan 'Aura TV Home' di Aksesibilitas agar tombol Home remote selalu masuk ke beranda ini", true);
                finish();
                return;
            } catch (Exception ignored) {
            }
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
