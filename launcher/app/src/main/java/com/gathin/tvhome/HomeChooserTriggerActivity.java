package com.gathin.tvhome;

import android.app.Activity;

/**
 * Disabled dummy HOME activity toggled briefly (DONT_KILL_APP) when Aura TV Home is first opened
 * after sideloading. Toggling a CATEGORY_HOME component forces Android's PackageManager to clear
 * the previous launcher's cached "Always" preference so pressing the remote's HOME button shows
 * the system "Select Home app" chooser dialog.
 */
public class HomeChooserTriggerActivity extends Activity {
}
