package net.kollnig.greasemilkyway;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import android.widget.Switch;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.View;
import android.net.Uri;


import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.ExistingPeriodicWorkPolicy;
import java.util.concurrent.TimeUnit;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;


import androidx.work.ExistingPeriodicWorkPolicy;
import android.os.Build;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import androidx.core.app.NotificationCompat;



import android.content.pm.PackageManager;
import androidx.work.ExistingWorkPolicy;
import android.widget.Button;



import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ServiceConfig config;
    private RecyclerView rulesList;
    private RulesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 🔐 Check for overlay permission (SYSTEM_ALERT_WINDOW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent overlayIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            // startActivityForResult(overlayIntent, 1000);
}


        // 🔔 Check for POST_NOTIFICATIONS permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }


        // Collapsible section behavior
        TextView header = findViewById(R.id.header_behavioral);
        LinearLayout content = findViewById(R.id.content_behavioral);

        header.setOnClickListener(v -> {
            if (content.getVisibility() == View.VISIBLE) {
                content.setVisibility(View.GONE);
                header.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.expand_circle_down_outline, 0);

            } else {
                content.setVisibility(View.VISIBLE);
                header.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.expand_circle_up_outline, 0);
            }
        });


        SharedPreferences prefs = getSharedPreferences("GreasePrefs", MODE_PRIVATE);

        Switch ytSwitch = findViewById(R.id.switch_youtube_scroll);
        Switch igSwitch = findViewById(R.id.switch_ig_nearend);


        // Restore saved states
        ytSwitch.setChecked(prefs.getBoolean("enable_yt_scroll_reminder", true));
        igSwitch.setChecked(prefs.getBoolean("enable_ig_lockout", true));

        // Save changes on toggle
        ytSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("enable_yt_scroll_reminder", isChecked).apply());

        igSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("enable_ig_lockout", isChecked).apply());


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

        scheduleHourlySummaryWorker();


    }
 
    private void scheduleHourlySummaryWorker() {
        Log.d("SummaryWorker", "Scheduling test summary worker...");

        OneTimeWorkRequest testWork =
            new OneTimeWorkRequest.Builder(HourlySummaryWorker.class)
                .setInitialDelay(1, TimeUnit.MINUTES) // ⏱️ set to 1 minute
                .build();

        WorkManager.getInstance(getApplicationContext())
            .enqueueUniqueWork("TestSummaryWorker", ExistingWorkPolicy.REPLACE, testWork);
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


















