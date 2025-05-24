package net.kollnig.greasemilkyway;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    private static final String DEFAULT_RULES_FILE = "distraction_rules.txt";

    private final SharedPreferences prefs;
    private final FilterRuleParser ruleParser;
    private final Context context;

    public ServiceConfig(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.ruleParser = new FilterRuleParser();
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public List<FilterRule> getRules() {
        List<FilterRule> rules = new ArrayList<>();
        
        // Add default rules from file
        try {
            InputStream is = context.getAssets().open(DEFAULT_RULES_FILE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            List<String> defaultRules = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    defaultRules.add(line);
                }
            }
            reader.close();
            rules.addAll(ruleParser.parseRules(defaultRules.toArray(new String[0])));
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