package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class HomeKeyService extends AccessibilityService {

    private static final String TAG = "TvHome.HomeKey";
    private static HomeKeyService instance;
    private final Handler homeHandler = new Handler(Looper.getMainLooper());

    private String targetForceStopPackage = null;
    private final Queue<String> forceStopQueue = new LinkedList<>();
    private boolean isProcessingForceStop = false;
    private boolean isWaitingForConfirmDialog = false;

    private boolean isProcessingRestart = false;
    private boolean isWaitingForRestartConfirm = false;
    private long lastRestartClickTime = 0L;
    private int restartScrollAttempts = 0;

    /** Scancodes of remote buttons that should open Settings (config.xml, settings_key_scancodes). */
    private int[] settingsScancodes = new int[0];

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        Log.i(TAG, "HomeKeyService connected");
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isAccessibilityActive() {
        return instance != null;
    }

    private static boolean tryRootCommand(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            boolean finished = p.waitFor(450, TimeUnit.MILLISECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean tryDirectCommand(String... cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            boolean finished = p.waitFor(350, TimeUnit.MILLISECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean sleepScreen() {
        if (instance != null && android.os.Build.VERSION.SDK_INT >= 28) {
            Log.i(TAG, "Executing GLOBAL_ACTION_LOCK_SCREEN to turn off TV display");
            return instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
        return false;
    }

    public static boolean restartTv(Context context) {
        // 1. Rooted / Unlocked STB: direct reboot via su or permissive /system/bin/reboot (verified via exitValue == 0)
        if (tryRootCommand("reboot") || tryDirectCommand("reboot")) {
            return true;
        }
        // 2. System broadcast (works on system-signed STB ROMs; throws SecurityException on non-root TV)
        try {
            Intent intent = new Intent(Intent.ACTION_REBOOT);
            intent.putExtra("nowait", 1);
            intent.putExtra("interval", 1);
            intent.putExtra("window", 0);
            context.sendBroadcast(intent);
            return true;
        } catch (Exception ignored) {}
        // 3. Non-root Android TV with Accessibility enabled
        if (instance != null) {
            Log.i(TAG, "Starting automated TV restart via accessibility service");
            instance.startAutomatedRestart(context);
            return true;
        }
        return false;
    }

    public static boolean turnOffTv(Context context) {
        // 1. Rooted / System-signed STB shutdown first if Accessibility is not active
        if (instance == null) {
            if (tryRootCommand("reboot -p") || tryRootCommand("input keyevent 26") || tryDirectCommand("reboot", "-p")) {
                return true;
            }
            try {
                Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
                intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (Exception ignored) {}
            return false;
        }
        // 2. Non-root Android TV / STB with Accessibility enabled:
        // Execute GLOBAL_ACTION_LOCK_SCREEN first (calls PowerManager.goToSleep() directly in system_server).
        Log.i(TAG, "Attempting TV power off / sleep via accessibility service");
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            boolean locked = instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            if (locked) return true;
        }
        boolean dialog = instance.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        return dialog;
    }

    private void startAutomatedRestart(Context context) {
        isProcessingRestart = true;
        isWaitingForRestartConfirm = false;
        lastRestartClickTime = 0L;
        restartScrollAttempts = 0;
        homeHandler.removeCallbacksAndMessages(null);

        // Step A: Try system power dialog first (works on TVs that display a global power menu)
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);

        // Step B: If the TV firmware ignores GLOBAL_ACTION_POWER_DIALOG (no dialog appears within 400ms),
        // automatically open Device Info / About Settings where every Android TV has "Mulai ulang" / "Restart".
        homeHandler.postDelayed(() -> {
            if (!isProcessingRestart || isWaitingForRestartConfirm) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            String pkg = (root != null && root.getPackageName() != null) ? root.getPackageName().toString() : "";
            if (pkg.isEmpty() || pkg.equals(getPackageName())) {
                Log.i(TAG, "Power dialog not shown by TV firmware; opening Device Info Settings for Restart");
                try {
                    Intent aboutIntent = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
                    aboutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(aboutIntent);
                } catch (Exception e) {
                    try {
                        Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
                        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(settingsIntent);
                    } catch (Exception ignored) {}
                }
            } else {
                handleRestartAccessibilityTree(root);
            }
        }, 400);

        // Safety timeout: clear restart automation state after 5 seconds if not completed
        homeHandler.postDelayed(() -> {
            isProcessingRestart = false;
            isWaitingForRestartConfirm = false;
        }, 5000);
    }

    public static void forceStopApp(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(context.getPackageName())) return;
        
        // 1. Send MEDIA_STOP to release audio/video playback locks
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP));
            }
        } catch (Exception ignored) {}

        // 2. Kill background processes
        try {
            ActivityManager actMgr = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (actMgr != null) {
                actMgr.killBackgroundProcesses(pkg);
            }
        } catch (Exception ignored) {}

        // 3. Automated force-stop via accessibility if available
        if (instance != null) {
            instance.queueForceStop(pkg);
        }
    }

    public static void forceStopAll(Context context, List<String> packages) {
        if (packages == null) return;
        // Stop media and kill background for all
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP));
            }
            ActivityManager actMgr = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (actMgr != null) {
                for (String pkg : packages) {
                    actMgr.killBackgroundProcesses(pkg);
                }
            }
        } catch (Exception ignored) {}

        if (instance != null) {
            for (String pkg : packages) {
                if (!pkg.equals(context.getPackageName()) && !pkg.contains("com.android.tv.settings")) {
                    instance.queueForceStop(pkg);
                }
            }
        }
    }

    private synchronized void queueForceStop(String pkg) {
        forceStopQueue.offer(pkg);
        if (!isProcessingForceStop) {
            processNextForceStop();
        }
    }

    private synchronized void processNextForceStop() {
        if (forceStopQueue.isEmpty()) {
            isProcessingForceStop = false;
            isWaitingForConfirmDialog = false;
            targetForceStopPackage = null;
            return;
        }
        isProcessingForceStop = true;
        isWaitingForConfirmDialog = false;
        targetForceStopPackage = forceStopQueue.poll();
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + targetForceStopPackage));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);

            // Safety timeout: if window doesn't respond or already stopped within 400ms, proceed to next
            homeHandler.removeCallbacksAndMessages(null);
            homeHandler.postDelayed(() -> {
                if (isProcessingForceStop) {
                    isProcessingForceStop = false;
                    isWaitingForConfirmDialog = false;
                    targetForceStopPackage = null;
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    homeHandler.postDelayed(this::processNextForceStop, 80);
                }
            }, 400);
        } catch (Exception e) {
            processNextForceStop();
        }
    }

    private boolean clickNodeOrAncestor(AccessibilityNodeInfo node) {
        if (node == null) return false;
        AccessibilityNodeInfo cur = node;
        for (int i = 0; i < 4; i++) {
            if (cur.isClickable() && cur.isEnabled()) {
                boolean res = cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Clicked ancestor node: " + cur.getClassName() + " -> " + res);
                return res;
            }
            AccessibilityNodeInfo parent = cur.getParent();
            if (parent == null) break;
            cur = parent;
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private boolean clickClickableNodeOrAncestor(AccessibilityNodeInfo node) {
        if (node == null) return false;
        AccessibilityNodeInfo cur = node;
        for (int i = 0; i < 5; i++) {
            if (cur.isClickable() && cur.isEnabled()) {
                boolean res = cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (res) {
                    Log.d(TAG, "Clicked clickable node/ancestor: " + cur.getClassName());
                    return true;
                }
            }
            AccessibilityNodeInfo parent = cur.getParent();
            if (parent == null) break;
            cur = parent;
        }
        return false;
    }

    private boolean scrollFirstScrollable(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isScrollable()) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                return true;
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && scrollFirstScrollable(child)) {
                return true;
            }
        }
        return false;
    }

    private void handleRestartAccessibilityTree(AccessibilityNodeInfo root) {
        if (root == null || !isProcessingRestart) return;
        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
        if (pkg.equals(getPackageName())) return;

        try {
            String[] restartKeywords = {"Mulai ulang", "Restart", "Reboot", "Muat ulang"};
            if (isWaitingForRestartConfirm) {
                if (SystemClock.uptimeMillis() - lastRestartClickTime < 220L) return;
                String[] confirmKeywords = {"Mulai ulang", "Restart", "Reboot", "OK", "Ya"};
                for (String kw : confirmKeywords) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(kw);
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        AccessibilityNodeInfo n = nodes.get(i);
                        CharSequence txt = n.getText();
                        String t = txt != null ? txt.toString().trim() : "";
                        if (t.equalsIgnoreCase(kw) || t.toLowerCase(Locale.ROOT).startsWith(kw.toLowerCase(Locale.ROOT))) {
                            if (clickClickableNodeOrAncestor(n)) {
                                Log.i(TAG, "Confirmed TV Restart button: " + t);
                                isProcessingRestart = false;
                                isWaitingForRestartConfirm = false;
                                homeHandler.removeCallbacksAndMessages(null);
                                return;
                            }
                        }
                    }
                }
                return;
            }

            for (String kw : restartKeywords) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(kw);
                for (AccessibilityNodeInfo n : nodes) {
                    CharSequence txt = n.getText();
                    String t = txt != null ? txt.toString().trim() : "";
                    if (t.equalsIgnoreCase(kw) || t.toLowerCase(Locale.ROOT).contains(kw.toLowerCase(Locale.ROOT))) {
                        if (clickClickableNodeOrAncestor(n)) {
                            Log.i(TAG, "Clicked initial TV Restart item: " + t);
                            isWaitingForRestartConfirm = true;
                            lastRestartClickTime = SystemClock.uptimeMillis();
                            homeHandler.postDelayed(() -> {
                                if (isProcessingRestart && isWaitingForRestartConfirm) {
                                    AccessibilityNodeInfo r = getRootInActiveWindow();
                                    if (r != null) handleRestartAccessibilityTree(r);
                                }
                            }, 320);
                            return;
                        }
                    }
                }
            }

            // If in Settings and Restart is further down the list, scroll down to reveal it
            if (restartScrollAttempts < 5 && scrollFirstScrollable(root)) {
                restartScrollAttempts++;
                homeHandler.postDelayed(() -> {
                    if (isProcessingRestart && !isWaitingForRestartConfirm) {
                        AccessibilityNodeInfo r = getRootInActiveWindow();
                        if (r != null) handleRestartAccessibilityTree(r);
                    }
                }, 250);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error during automated restart accessibility traversal", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (isProcessingRestart) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                handleRestartAccessibilityTree(root);
            }
            return;
        }
        if (!isProcessingForceStop || targetForceStopPackage == null) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            // Step 1: Check for "OK" confirmation button (if dialog is visible)
            List<AccessibilityNodeInfo> okNodes = root.findAccessibilityNodeInfosByText("OK");
            if (okNodes.isEmpty()) {
                okNodes = root.findAccessibilityNodeInfosByViewId("com.android.tv.settings:id/guidedactions_item_title");
            }
            if (okNodes.isEmpty()) {
                okNodes = root.findAccessibilityNodeInfosByViewId("android:id/button1");
            }

            for (AccessibilityNodeInfo ok : okNodes) {
                CharSequence text = ok.getText();
                if (text != null && text.toString().equalsIgnoreCase("OK")) {
                    Log.i(TAG, "Confirming Force Stop OK for: " + targetForceStopPackage);
                    clickNodeOrAncestor(ok);
                    isProcessingForceStop = false;
                    isWaitingForConfirmDialog = false;
                    targetForceStopPackage = null;
                    homeHandler.removeCallbacksAndMessages(null);
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    homeHandler.postDelayed(this::processNextForceStop, 80);
                    return;
                }
            }

            if (isWaitingForConfirmDialog) return;

            // Step 2: Check for "Force stop" / "Paksa henti" button
            List<AccessibilityNodeInfo> forceStopButtons = root.findAccessibilityNodeInfosByText("Force stop");
            if (forceStopButtons.isEmpty()) {
                forceStopButtons = root.findAccessibilityNodeInfosByText("Paksa henti");
            }
            if (forceStopButtons.isEmpty()) {
                forceStopButtons = root.findAccessibilityNodeInfosByViewId("com.android.tv.settings:id/force_stop");
            }
            if (forceStopButtons.isEmpty()) {
                forceStopButtons = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/force_stop_button");
            }

            for (AccessibilityNodeInfo btn : forceStopButtons) {
                CharSequence text = btn.getText();
                String t = text != null ? text.toString().toLowerCase(Locale.ROOT) : "";
                if (t.contains("force stop") || t.contains("paksa henti")) {
                    if (btn.isEnabled()) {
                        Log.i(TAG, "Clicking Force Stop button for: " + targetForceStopPackage);
                        isWaitingForConfirmDialog = true;
                        clickNodeOrAncestor(btn);
                        return;
                    } else {
                        // Already force stopped
                        Log.i(TAG, "Force Stop button already disabled for: " + targetForceStopPackage);
                        isProcessingForceStop = false;
                        isWaitingForConfirmDialog = false;
                        targetForceStopPackage = null;
                        homeHandler.removeCallbacksAndMessages(null);
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        homeHandler.postDelayed(this::processNextForceStop, 80);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error during accessibility force stop", e);
        }
    }

    private boolean isHomeLongPressed = false;
    private final Runnable longPressHomeRunnable = new Runnable() {
        @Override
        public void run() {
            isHomeLongPressed = true;
            Log.i(TAG, "Hold Home button detected -> Opening Settings Popup globally");
            openSettingsPopupGlobal();
        }
    };

    public static void openSettingsPopupGlobal() {
        if (instance == null) return;
        if (MainActivity.isForeground()) {
            MainActivity.openSettingsFromGlobal();
        } else {
            Intent intent = new Intent(instance, MainActivity.class)
                    .putExtra("open_settings", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                instance.startActivity(intent);
            } catch (Exception e) {
                Log.w(TAG, "Failed to launch MainActivity for settings", e);
            }
        }
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }

        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            return true; // Live apps disabled per request
        }

        if (keyCode != KeyEvent.KEYCODE_ALL_APPS && keyCode != KeyEvent.KEYCODE_HOME) return false;

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.isLongPress() || (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0 || event.getRepeatCount() > 0) {
                if (!isHomeLongPressed) {
                    isHomeLongPressed = true;
                    homeHandler.removeCallbacks(longPressHomeRunnable);
                    openSettingsPopupGlobal();
                }
                return true;
            }
            if (event.getRepeatCount() == 0) {
                isHomeLongPressed = false;
                homeHandler.removeCallbacks(longPressHomeRunnable);
                homeHandler.postDelayed(longPressHomeRunnable, 450);
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            homeHandler.removeCallbacks(longPressHomeRunnable);
            if (isHomeLongPressed) {
                isHomeLongPressed = false;
                return true;
            }
            // Single Home press: return to Home
            if (RecentAppsActivity.isForeground()) {
                RecentAppsActivity.dismissFromGlobal();
            } else {
                Intent home = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .setPackage(getPackageName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    startActivity(home);
                } catch (Exception ignored) {}
            }
            return true;
        }
        return true;
    }

    @Override
    public void onInterrupt() {
    }
}
