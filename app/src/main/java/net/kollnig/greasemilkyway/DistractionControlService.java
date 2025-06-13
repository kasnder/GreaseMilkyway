package net.kollnig.greasemilkyway;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.LayoutInflater;
import android.preference.PreferenceManager;



import android.os.Build;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.SharedPreferences;
import android.content.Context;

import android.widget.Button;
import android.widget.TextView;

import android.provider.Settings;



import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * An accessibility service that helps control distractions by blocking specific content in Android apps
 * using an ad-blocker style filter syntax.
 */
public class DistractionControlService extends AccessibilityService {

    //list of social media apps to regulate usage
    private static final List<String> KNOWN_SOCIAL_APPS = Arrays.asList(
    "com.instagram.android",
    "com.google.android.youtube",
    "com.zhiliaoapp.musically", 
    "com.snapchat.android",
    "com.facebook.katana",
    "com.twitter.android",
    "com.whatsapp"
  
);


    private Map<String, Long> lastLockoutPopupShown = new HashMap<>();
    private static final long LOCKOUT_POPUP_COOLDOWN_MS = 1000;

    //popup tracker
    private boolean isPopupVisible = false;
    private View activePopupView = null;
    


    //skip_count and timer
    private Map<String, Integer> skipCounts = new HashMap<>();
    private Map<String, Long> restrictedUntil = new HashMap<>();
    //time tracker
    private String currentPackage = "";
    private Map<String, Long> appStartTimes = new HashMap<>();



    
    //Infinite-scroll vars
    private static final int SCROLL_THRESHOLD_SECONDS = 5;
    private static final int SCROLL_IDLE_TIMEOUT_MS = 3000;

    private long lastScrollTime = 0;
    private boolean isTrackingScroll = false;
    private boolean reminderShown = false;

    // nearbottom
    private boolean bottomReached = false;
    private static final String TAG = "DistractionControlService";
    private static final int PROCESSING_DELAY_MS = 20;
    private static final int MAX_OVERLAY_COUNT = 100;


    private Handler scrollHandler = new Handler();
    private String getAppNameFromPackage(String packageName) {
            PackageManager pm = getPackageManager();
            try {
                ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                return pm.getApplicationLabel(ai).toString();
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                return packageName; 
            }
        }

    private void exitToHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
    }


    private void showBreakPopup(String sourceApp, boolean isCaughtUp) {
        if (!isMonitoredSocialApp(sourceApp)) {
        Log.d(TAG, "Ignoring non-social app: " + sourceApp);
        return;
    }
    // Load user-defined scroll duration before popup
    SharedPreferences delayPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    int scrollThresholdMins = delayPrefs.getInt("break_reminder_delay", 30); // fallback to 30 mins
    long scrollThresholdMillis = scrollThresholdMins * 60L * 1000L;

    // How long has the user been on this app?
    long startTime = appStartTimes.getOrDefault(sourceApp, System.currentTimeMillis());
    long timeSpentMillis = System.currentTimeMillis() - startTime;

    if (timeSpentMillis < scrollThresholdMillis) {
        Log.d(TAG, "Scroll time not yet reached (" + timeSpentMillis / 1000 + "s < " + scrollThresholdMillis / 1000 + "s), skipping popup.");
        return;
    }



        if (isPopupVisible) return;

        // check user preferences
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("GreasePrefs", Context.MODE_PRIVATE);

        // Check if popups are enabled for this app type
        if (sourceApp.equals("com.google.android.youtube")) {
            boolean ytRemindersEnabled = prefs.getBoolean("enable_yt_scroll_reminder", true);
            if (!ytRemindersEnabled) {
                Log.d("PopupControl", "YouTube reminders disabled - skipping popup");
                return;
            }
        } else if (sourceApp.equals("com.instagram.android")) {
            boolean igLockoutEnabled = prefs.getBoolean("enable_ig_lockout", true);
            if (!igLockoutEnabled) {
                Log.d("PopupControl", "Instagram lockout disabled - skipping popup");
                return;
            }
        }

        // Prevent crash by checking overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(getApplicationContext())) {
            Log.e("OverlayPermission", "Permission not granted. Popup not shown.");
            return;
        }


        isPopupVisible = true;

    LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
    View popupView = inflater.inflate(R.layout.item_infinite_scroll_reminder, null);
    activePopupView = popupView;

    WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    );

    params.gravity = Gravity.CENTER;
    windowManager.addView(popupView, params);

    // Message
    TextView message = popupView.findViewById(R.id.popup_message);
    String appName = getAppNameFromPackage(sourceApp);

    if (isCaughtUp && sourceApp.equals("com.instagram.android")) {
        message.setText("You've seen all new content on " + appName + ". Come back later!");
    } else {
        
        
        long seconds = timeSpentMillis / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        String readableTime = minutes > 0 ?
            minutes + " min " + remainingSeconds + " sec" :
            seconds + " sec";

        message.setText("You've been scrolling on " + appName + " for " + readableTime + ". Would you like to take a break?");
    }

    // Buttons
    Button continueButton = popupView.findViewById(R.id.btn_continue);
    Button exitButton = popupView.findViewById(R.id.btn_break);

    View.OnClickListener dismissListener = v -> {
        try {
            windowManager.removeView(popupView);
        } catch (Exception e) {
            e.printStackTrace();
        }
        isPopupVisible = false;
        activePopupView = null;
        scrollHandler.removeCallbacks(reminderRunnable);
        reminderShown = true;
        isTrackingScroll = false;
    };

    continueButton.setOnClickListener(v -> {
        dismissListener.onClick(v);

        // Lockout logic
        int skips = skipCounts.getOrDefault(sourceApp, 0) + 1;
        skipCounts.put(sourceApp, skips);

        if (skips >= 3) {
            int lockoutMinutes = delayPrefs.getInt("lockout_time", 60);

            long lockoutDuration = lockoutMinutes * 60L * 1000L; 
            long lockoutUntil = System.currentTimeMillis() + lockoutDuration;

            restrictedUntil.put(sourceApp, lockoutUntil);
            skipCounts.put(sourceApp, 0);
            exitToHome();
        }

    });

    exitButton.setOnClickListener(v -> {
        dismissListener.onClick(v);
        exitToHome();
    });
}          
    private boolean isMonitoredSocialApp(String packageName) {
        if (!KNOWN_SOCIAL_APPS.contains(packageName)) return false;

        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    




    private void showLockoutPopup(String packageName, long minutes, long seconds) {
        if (isPopupVisible) return;
        isPopupVisible = true;

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.item_infinite_scroll_reminder, null);
        activePopupView = popupView;

        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.CENTER;
        windowManager.addView(popupView, params);

        TextView message = popupView.findViewById(R.id.popup_message);
        String appName = getAppNameFromPackage(packageName);
        message.setText("You've been locked out of " + appName + ". Try again in " + minutes + " min " + seconds + " sec.");

        // hide buttons
        popupView.findViewById(R.id.btn_continue).setVisibility(View.GONE);
        popupView.findViewById(R.id.btn_break).setVisibility(View.GONE);



        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                windowManager.removeView(popupView);
            } catch (Exception ignored) {}
            isPopupVisible = false;   // ✅ Reset here!
            activePopupView = null;
        }, 3000);

    }


    private Runnable reminderRunnable = new Runnable() {
        @Override
        public void run() {
            if (!reminderShown) {
                showBreakPopup(currentPackage, false);
                reminderShown = true;
                isTrackingScroll = false;
            }
        }
    };

    // Show Restriction PopUp
    private void showRestrictionPopup(long minutes, long seconds) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.item_infinite_scroll_reminder, null);

        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.CENTER;
        windowManager.addView(popupView, params);

        // Set restriction message
        TextView message = popupView.findViewById(R.id.popup_message);
        message.setText("You've reached your limit. Try again in " + minutes + " min " + seconds + " sec");

        // Hide buttons
        popupView.findViewById(R.id.btn_continue).setVisibility(View.GONE);
        popupView.findViewById(R.id.btn_break).setVisibility(View.GONE);

        // Auto-dismiss after 3 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                windowManager.removeView(popupView);
            } catch (Exception ignored) {
            }
        }, 3000);
    }
    
    // Singleton instance
    private static DistractionControlService instance;
    private final List<FilterRule> rules = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final OverlayManager overlayManager = new OverlayManager();
    private final Map<View, Rect> overlayBounds = new HashMap<>();
    private final Map<String, List<BlockedElement>> blockedElements = new HashMap<>();
    private WindowManager windowManager;
    private final Runnable processEvent = () -> {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.w(TAG, "No root window available");
                return;
            }
            try {
                String packageName = root.getPackageName() != null ? root.getPackageName().toString() : "";

                // Check if we have any blocked elements for this package
                List<BlockedElement> elements = blockedElements.get(packageName);
                if (elements != null) {
                    // Check each blocked element to see if it still exists
                    for (int i = elements.size() - 1; i >= 0; i--) {
                        BlockedElement element = elements.get(i);
                        if (!elementStillExists(root, element)) {
                            overlayManager.removeOverlay(element.overlay, windowManager, ui);
                            elements.remove(i);
                        }
                    }
                }

                processRootNode(root);
            } finally {
                root.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing accessibility event", e);
        }
    };
    private ServiceConfig config;
    private LayoutDumper layoutDumper;

    /**
     * Get the current instance of the service.
     *
     * @return The current service instance, or null if the service is not running
     */
    public static DistractionControlService getInstance() {
        return instance;
    }

    /**
     * Update the rules in the service and clear any existing overlays.
     * This should be called whenever rules are modified in the UI.
     */
    public void updateRules() {
        if (instance == null) return;
        rules.clear();
        rules.addAll(config.getRules());
        overlayManager.clearOverlays(windowManager, ui);
        blockedElements.clear();
        Log.i(TAG, "Rules updated, now have " + rules.size() + " rule(s)");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager == null) {
                Log.e(TAG, "Failed to get WindowManager service");
                return;
            }
            config = new ServiceConfig(this);
            rules.clear();
            rules.addAll(config.getRules());
            configureAccessibilityService();
            Log.i(TAG, "Accessibility service initialized with " + rules.size() + " rule(s)");

            layoutDumper = new LayoutDumper();
            layoutDumper.start();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing service", e);
        }
    }

    private void configureAccessibilityService() {
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) {
                Log.e(TAG, "Failed to get service info");
                return;
            }
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            setServiceInfo(info);
        } catch (Exception e) {
            Log.e(TAG, "Error configuring accessibility service", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (instance == null) return;
        
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        if (packageName.equals(getPackageName())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (restrictedUntil.containsKey(packageName)) {
            long until = restrictedUntil.get(packageName);
            if (now < until) {
                long lastShown = lastLockoutPopupShown.getOrDefault(packageName, 0L);
                if (now - lastShown > LOCKOUT_POPUP_COOLDOWN_MS) {
                    long remaining = until - now;
                    long minutes = remaining / 60000;
                    long seconds = (remaining / 1000) % 60;
                    showLockoutPopup(packageName, minutes, seconds);
                    lastLockoutPopupShown.put(packageName, now);
                }
                exitToHome(); // Prevent access
                return;
            }
        }



        //handle app scroll tracking
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                // Track app open time
            if (!appStartTimes.containsKey(packageName)) {
                appStartTimes.put(packageName, System.currentTimeMillis());
            }

            // scroll tracking popup for YouTube
            if (packageName.equals("com.google.android.youtube")) {
                long currentTime = System.currentTimeMillis();

                if (!isTrackingScroll) {
                    isTrackingScroll = true;
                    reminderShown = false;
                    scrollHandler.postDelayed(reminderRunnable, SCROLL_THRESHOLD_SECONDS * 1000);
                }

                if ((currentTime - lastScrollTime) > SCROLL_IDLE_TIMEOUT_MS) {
                    scrollHandler.removeCallbacks(reminderRunnable);
                    isTrackingScroll = false;
                }

                lastScrollTime = currentTime;
            }

        }
        
        //Near End Notification IG
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                int itemCount = event.getItemCount();
                int toIndex = event.getToIndex();

                if (itemCount > 0 && toIndex >= itemCount - 2 && !bottomReached) {
                    bottomReached = true;
                    showBreakPopup(currentPackage, true);
                } else if (toIndex < itemCount - 2) {
                    bottomReached = false;
                }
            
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (packageName.equals(getPackageName())) return;

            // Check for lockscreen
            if (packageName.equals("com.android.systemui")) {
                Log.d(TAG, "Clearing overlays due to lockscreen");
                overlayManager.forceClearOverlays(windowManager);
                blockedElements.clear();
                return;
            }

            // Check for launcher packages
            if (isLauncherPackage(packageName)) {
                Log.d(TAG, "Clearing overlays due to launcher switch");
                overlayManager.forceClearOverlays(windowManager);
                blockedElements.clear();
                return;
            }

            // Update current app
            if (!packageName.equals(currentPackage)) {
                currentPackage = packageName;
                if (restrictedUntil.containsKey(currentPackage) && now < restrictedUntil.get(currentPackage)) {
                    long lastShown = lastLockoutPopupShown.getOrDefault(currentPackage, 0L);
                    if (now - lastShown > LOCKOUT_POPUP_COOLDOWN_MS) {
                        long remaining = restrictedUntil.get(currentPackage) - now;
                        long minutes = remaining / (1000 * 60);
                        long seconds = (remaining / 1000) % 60;

                        showLockoutPopup(currentPackage, minutes, seconds);
                        lastLockoutPopupShown.put(currentPackage, now);
                    }

                    exitToHome();
                    return;
                }

                // Not restricted → track time
                appStartTimes.put(currentPackage, now);
            }
        }


        if (!shouldProcessEvent(event)) return;
        ui.removeCallbacks(processEvent);
        ui.postDelayed(processEvent, PROCESSING_DELAY_MS);
    }

    private boolean isLauncherPackage(String packageName) {
        return packageName.equals("com.android.launcher3") || // AOSP/LineageOS
                packageName.equals("com.google.android.apps.nexuslauncher") || // Pixel
                packageName.equals("com.miui.home") || // Xiaomi
                packageName.equals("com.sec.android.app.launcher") || // Samsung
                packageName.equals("com.oppo.launcher") || // OPPO
                packageName.equals("com.coloros.launcher") || // ColorOS
                packageName.equals("com.huawei.android.launcher") || // Huawei
                packageName.equals("com.oneplus.launcher") || // OnePlus
                packageName.equals("com.nothing.launcher") || // Nothing
                packageName.equals("com.asus.launcher") || // ASUS
                packageName.equals("com.teslacoilsw.launcher"); // Nova
    }

    private boolean shouldProcessEvent(AccessibilityEvent event) {
        if (event == null) return false;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return false;

        int eventType = event.getEventType();
        return (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED)
                && hasMatchingRule(pkg);
    }

    private boolean hasMatchingRule(CharSequence packageName) {
        return rules.stream()
                .filter(rule -> rule.enabled)
                .anyMatch(rule -> rule.matchesPackage(packageName));
    }

    private void processRootNode(AccessibilityNodeInfo root) {
        CharSequence packageName = root.getPackageName();
        if (packageName == null) {
            Log.w(TAG, "Root node has no package name");
            return;
        }

        for (FilterRule rule : rules) {
            if (rule.enabled && rule.matchesPackage(packageName)) {
                applyRule(rule, root);
            }
        }
    }

    private void applyRule(FilterRule rule, AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser()) return;

        if (isTargetView(node, rule)) {
            processTargetView(node, rule);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                applyRule(rule, child);
            } finally {
                child.recycle();
            }
        }
    }

    private boolean isTargetView(AccessibilityNodeInfo node, FilterRule rule) {
        if (rule.targetViewId == null || rule.targetViewId.isEmpty()) {
            CharSequence desc = node.getContentDescription();
            return desc != null && rule.contentDescriptions.contains(desc.toString());
        }

        String viewId = node.getViewIdResourceName();
        return viewId != null && viewId.equals(rule.targetViewId);
    }

    private void processTargetView(AccessibilityNodeInfo node, FilterRule rule) {
        if (rule.targetViewId == null || rule.contentDescriptions == null || rule.contentDescriptions.isEmpty() || rule.targetViewId.isEmpty()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (!bounds.isEmpty()) {
                addOverlay(bounds, rule);
            }
            return;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (subtreeContainsContentDescription(child, rule.contentDescriptions)) {
                    Rect bounds = new Rect();
                    child.getBoundsInScreen(bounds);
                    if (!bounds.isEmpty()) {
                        addOverlay(bounds, rule);
                    }
                }
            } finally {
                child.recycle();
            }
        }
    }

    private boolean subtreeContainsContentDescription(AccessibilityNodeInfo node, Set<String> targets) {
        if (node == null) return false;

        CharSequence desc = node.getContentDescription();
        if (desc != null && targets.contains(desc.toString())) return true;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (subtreeContainsContentDescription(child, targets)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private void addOverlay(Rect area, FilterRule rule) {
        if (overlayManager.getOverlayCount() >= MAX_OVERLAY_COUNT) {
            Log.w(TAG, "Maximum overlay count reached, clearing old overlays");
            overlayManager.clearOverlays(windowManager, ui);
            blockedElements.clear();
        }

        View blocker = new View(this);
        blocker.setBackgroundColor(rule.color);
        blocker.setAlpha(1f);

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!rule.blockTouches) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                area.width(),
                area.height(),
                area.left,
                area.top,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;

        overlayManager.addOverlay(blocker, lp, windowManager, ui);

        List<BlockedElement> elements = blockedElements.computeIfAbsent(rule.packageName, k -> new ArrayList<>());
        elements.add(new BlockedElement(rule.targetViewId, rule.contentDescriptions, blocker, new Rect(area)));
    }

    private boolean elementStillExists(AccessibilityNodeInfo root, BlockedElement element) {
        if (root == null) return false;

        if (element.viewId != null && !element.viewId.isEmpty()) {
            String viewId = root.getViewIdResourceName();
            if (viewId != null && viewId.equals(element.viewId)) {
                Rect bounds = new Rect();
                root.getBoundsInScreen(bounds);
                return bounds.equals(element.bounds);
            }
        }

        if (element.descriptions != null && !element.descriptions.isEmpty()) {
            CharSequence desc = root.getContentDescription();
            if (desc != null && element.descriptions.contains(desc.toString())) {
                Rect bounds = new Rect();
                root.getBoundsInScreen(bounds);
                return bounds.equals(element.bounds);
            }
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) continue;
            try {
                if (elementStillExists(child, element)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        if (layoutDumper != null) {
            layoutDumper.stop();
        }
        overlayManager.forceClearOverlays(windowManager);
        blockedElements.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        if (layoutDumper != null) {
            layoutDumper.stop();
        }
        overlayManager.forceClearOverlays(windowManager);
        blockedElements.clear();
    }

    private static class BlockedElement {
        final String viewId;
        final Set<String> descriptions;
        final View overlay;
        final Rect bounds;

        BlockedElement(String viewId, Set<String> descriptions, View overlay, Rect bounds) {
            this.viewId = viewId;
            this.descriptions = descriptions;
            this.overlay = overlay;
            this.bounds = bounds;
        }
    }
}
