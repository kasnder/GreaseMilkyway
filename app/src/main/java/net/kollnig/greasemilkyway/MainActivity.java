package net.kollnig.greasemilkyway;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ServiceConfig config;
    private RecyclerView rulesList;
    private RulesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.settings_title);

        // Initialize config
        config = new ServiceConfig(this);

        // Initialize views
        rulesList = findViewById(R.id.rules_list);

        // Setup RecyclerView
        rulesList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RulesAdapter(this, config);
        rulesList.setAdapter(adapter);

        // Setup custom rules button
        findViewById(R.id.custom_rules_button).setOnClickListener(v -> {
            Intent intent = new Intent(this, CustomRulesActivity.class);
            startActivity(intent);
        });

        // Load current settings
        loadSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if accessibility service is enabled
        String serviceName = getPackageName() + "/" + DistractionControlService.class.getCanonicalName();
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            return;
        }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null && settingValue.contains(serviceName)) {
                // Reload rules to reflect any changes from CustomRulesActivity
                loadSettings();
            }
        }

        // Notify the adapter to update the service header
        adapter.notifyItemChanged(0);  // Service header is always at position 0
    }

    private void loadSettings() {
        List<FilterRule> rules = config.getRules();
        Log.d("SettingsActivity", "Loading " + rules.size() + " rules");
        for (FilterRule rule : rules) {
            Log.d("SettingsActivity", "Rule for " + rule.packageName + " with description: " + rule.description);
        }
        adapter.setRules(rules);
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" + DistractionControlService.class.getCanonicalName();
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                return settingValue.contains(serviceName);
            }
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 