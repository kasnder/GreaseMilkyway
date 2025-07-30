package net.kollnig.greasemilkyway;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages configuration for the LayoutDumpAccessibilityService.
 */
public class ServiceConfig {
    private static final String TAG = "ServiceConfig";
    private static final String PREFS_NAME = "LayoutDumpServicePrefs";
    public static final String KEY_RULE_ENABLED = "rule_enabled_";
    private static final String KEY_CUSTOM_RULES = "custom_rules";
    private static final String KEY_PACKAGE_DISABLED = "package_disabled_";
    private static final String KEY_CLICK_COUNT = "click_count_";
    private static final String DEFAULT_RULES_FILE = "distraction_rules.txt";

    private final SharedPreferences prefs;
    private final FilterRuleParser ruleParser;
    private final Context context;

    public ServiceConfig(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.ruleParser = new FilterRuleParser();
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }

    public void setRuleEnabled(FilterRule rule, boolean enabled) {
        String key = KEY_RULE_ENABLED + rule.hashCode();
        prefs.edit().putBoolean(key, enabled).apply();
    }

    public void setPackageDisabled(String packageName, boolean disabled) {
        String key = KEY_PACKAGE_DISABLED + packageName;
        prefs.edit().putBoolean(key, disabled).apply();
    }

    public boolean isPackageDisabled(String packageName) {
        String key = KEY_PACKAGE_DISABLED + packageName;
        return prefs.getBoolean(key, false);
    }

    /**
     * Get click count for a specific rule
     */
    public int getClickCount(FilterRule rule) {
        String key = KEY_CLICK_COUNT + rule.hashCode();
        return prefs.getInt(key, 0);
    }

    /**
     * Save click count for a specific rule
     */
    public void saveClickCount(FilterRule rule, int count) {
        String key = KEY_CLICK_COUNT + rule.hashCode();
        prefs.edit().putInt(key, count).apply();
    }

    /**
     * Reset all click counters
     */
    public void resetClickCounters() {
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> allPrefs = prefs.getAll();
        for (String key : allPrefs.keySet()) {
            if (key.startsWith(KEY_CLICK_COUNT)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    public List<FilterRule> getRules() {
        List<FilterRule> rules = new ArrayList<>();
        
        // Add default rules from file
        try {
            InputStream is = context.getAssets().open(DEFAULT_RULES_FILE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    rules.addAll(ruleParser.parseRules(new String[]{line}));
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Add custom rules
        String[] customRules = getCustomRules();
        if (customRules != null) {
            rules.addAll(ruleParser.parseRules(customRules));
        }

        // Apply saved enabled states
        for (FilterRule rule : rules) {
            String key = KEY_RULE_ENABLED + rule.hashCode();
            boolean containsFeed = false;
            // Check description
            if (rule.description != null && rule.description.toLowerCase().contains("feed")) {
                containsFeed = true;
            }
            // Check contentDescriptions
            if (!containsFeed && rule.contentDescriptions != null) {
                for (String desc : rule.contentDescriptions) {
                    if (desc != null && desc.toLowerCase().contains("feed")) {
                        containsFeed = true;
                        break;
                    }
                }
            }
            // Default to disabled if contains 'feed', unless explicitly enabled in prefs
            boolean ruleEnabled = prefs.getBoolean(key, !containsFeed); // Default to false if contains 'feed', else true
            // If the package is disabled, force disable all rules for that package
            if (rule.packageName != null && isPackageDisabled(rule.packageName)) {
                rule.enabled = false;
            } else {
                rule.enabled = ruleEnabled;
            }
        }

        return rules;
    }

    public String[] getCustomRules() {
        String rules = prefs.getString(KEY_CUSTOM_RULES, "");
        return rules.isEmpty() ? null : rules.split("\n");
    }

    public void saveCustomRules(String[] rules) {
        prefs.edit().putString(KEY_CUSTOM_RULES, String.join("\n", rules)).apply();
    }
}