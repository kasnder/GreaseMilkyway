package net.kollnig.greasemilkyway;

import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages overlay views for blocking content.
 */
public class OverlayManager {
    private static final String TAG = "OverlayManager";
    
    private final List<View> overlays = new CopyOnWriteArrayList<>();
    // Clicks per rule (key: FilterRule object)
    private final Map<FilterRule, Integer> clickCountsPerRule = new HashMap<>();
    private final ServiceConfig config;

    public OverlayManager(ServiceConfig config, List<FilterRule> rules) {
        this.config = config;
        this.loadClickCountsForRules(rules);
    }

    public int getOverlayCount() {
        return overlays.size();
    }

    /**
     * Add overlay and set up click tracking per rule.
     * @param overlay The overlay view
     * @param params Layout params
     * @param windowManager Window manager
     * @param ui Handler
     * @param rule The rule object
     */
    public void addOverlay(View overlay, WindowManager.LayoutParams params, WindowManager windowManager, Handler ui, FilterRule rule) {
        ui.post(() -> {
            try {
                // Initialize click counter for this rule if not present
                if (!clickCountsPerRule.containsKey(rule)) {
                    // Load from preferences first, then initialize to 0 if not found
                    int savedCount = config.getClickCount(rule);
                    clickCountsPerRule.put(rule, savedCount);
                }
                // Store rule as tag on the overlay
                overlay.setTag(rule);
                // Add touch listener to track clicks per rule
                overlay.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        Object tag = v.getTag();
                        if (tag instanceof FilterRule) {
                            FilterRule clickedRule = (FilterRule) tag;
                            int currentCount = clickCountsPerRule.getOrDefault(clickedRule, 0);
                            int newCount = currentCount + 1;
                            clickCountsPerRule.put(clickedRule, newCount);
                            // Save to preferences immediately
                            config.saveClickCount(clickedRule, newCount);
                            Log.d(TAG, "Overlay clicked! Rule: " + clickedRule.packageName + ", Total clicks: " + newCount);
                        }
                    }
                    return false; // Allow touch to pass through if needed
                });
                windowManager.addView(overlay, params);
                overlays.add(overlay);
            } catch (Exception e) {
                Log.e(TAG, "Error adding overlay", e);
            }
        });
    }

    public void removeOverlay(View overlay, WindowManager windowManager, Handler ui) {
        ui.post(() -> {
            try {
                if (overlay.getParent() != null) {
                    windowManager.removeView(overlay);
                }
                overlays.remove(overlay);
                // No need to remove click count here; rules may have multiple overlays
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay", e);
            }
        });
    }

    public void clearOverlays(WindowManager windowManager, Handler ui) {
        if (overlays.isEmpty()) return;
        for (View v : new ArrayList<>(overlays)) {
            ui.post(() -> {
                try {
                    windowManager.removeView(v);
                } catch (Exception e) {
                    Log.e(TAG, "Error removing overlay", e);
                }
            });
            overlays.remove(v);
        }
    }

    public void forceClearOverlays(WindowManager windowManager) {
        if (overlays.isEmpty()) return;
        for (View v : new ArrayList<>(overlays)) {
            try {
                if (v.getParent() != null) {
                    windowManager.removeView(v);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay", e);
            }
            overlays.remove(v);
        }
    }

    /**
     * Get click statistics for all rules (key: FilterRule object, value: count)
     */
    public Map<FilterRule, Integer> getClickStatistics() {
        return new HashMap<>(clickCountsPerRule);
    }

    /**
     * Load click counts for a list of rules
     * This should be called when rules are loaded to restore click counts
     */
    public void loadClickCountsForRules(List<FilterRule> rules) {
        for (FilterRule rule : rules) {
            if (!clickCountsPerRule.containsKey(rule)) {
                int savedCount = config.getClickCount(rule);
                if (savedCount > 0) {
                    clickCountsPerRule.put(rule, savedCount);
                    Log.d(TAG, "Loaded click count for rule " + rule.packageName + ": " + savedCount);
                }
            }
        }
    }

    /**
     * Reset click counters for all rules
     */
    public void resetClickCounters() {
        clickCountsPerRule.clear();
        config.resetClickCounters();
    }
} 