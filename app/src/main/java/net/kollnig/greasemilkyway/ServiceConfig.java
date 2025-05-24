package net.kollnig.greasemilkyway;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages configuration for the LayoutDumpAccessibilityService.
 */
public class ServiceConfig {
    private static final String TAG = "ServiceConfig";
    private static final String PREFS_NAME = "LayoutDumpServicePrefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_RULE_ENABLED = "rule_enabled_";
    private static final String KEY_CUSTOM_RULES = "custom_rules";

    // Default values
    private static final String[] DEFAULT_RULES = {
            "com.google.android.youtube##viewId=com.google.android.youtube:id/watch_list##desc=Shorts|Go to channel##color=FFFFFF##comment=Hide next-up video recommendations",
            "com.whatsapp##viewId=com.whatsapp:id/fab_second##desc=##color=FFFFFF##comment=Hide AI button",
            "com.whatsapp##viewId=com.whatsapp:id/empty_search_carousal##desc=##color=FFFFFF##comment=Hide AI suggestions in search",
            "com.instagram.android##viewId=com.instagram.android:id/layout_container##desc=##color=FFFFFF##comment=Hide search suggestions 1/2",
            "com.instagram.android##viewId=com.instagram.android:id/grid_card_layout_container##desc=##color=FFFFFF##comment=Hide search suggestions 2/2",
            "com.instagram.android##desc=reels tray container##color=FFFFFF##comment=Hide Stories",
            "com.instagram.android##viewId=android:id/list##desc=##color=FFFFFF##comment=Hide Feed",
            "com.instagram.android##viewId=com.instagram.android:id/clips_tab##desc=##color=FFFFFF##comment=Hide Reels button",
            "com.google.android.youtube##desc=Shorts##color=FFFFFF##comment=Hide Shorts button",
            "com.instagram.android##viewId=com.instagram.android:id/root_clips_layout##desc=##color=FFFFFF##comment=Hide Reels layout"
    };

    private final SharedPreferences prefs;
    private final FilterRuleParser ruleParser;

    public ServiceConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.ruleParser = new FilterRuleParser();
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public List<FilterRule> getRules() {
        List<FilterRule> rules = new ArrayList<>();
        
        // Add default rules
        rules.addAll(ruleParser.parseRules(DEFAULT_RULES));
        
        // Add custom rules
        String[] customRules = getCustomRules();
        if (customRules != null) {
            rules.addAll(ruleParser.parseRules(customRules));
        }

        // Apply saved enabled states
        for (FilterRule rule : rules) {
            String key = KEY_RULE_ENABLED + rule.packageName + "_" + rule.targetViewId;
            rule.enabled = prefs.getBoolean(key, true); // Default to enabled if not set
        }

        return rules;
    }

    public void setRuleEnabled(String packageName, String viewId, boolean enabled) {
        String key = KEY_RULE_ENABLED + packageName + "_" + viewId;
        prefs.edit().putBoolean(key, enabled).apply();
    }

    public String[] getCustomRules() {
        String rules = prefs.getString(KEY_CUSTOM_RULES, "");
        return rules.isEmpty() ? null : rules.split("\n");
    }

    public void saveCustomRules(String[] rules) {
        prefs.edit().putString(KEY_CUSTOM_RULES, String.join("\n", rules)).apply();
    }
}