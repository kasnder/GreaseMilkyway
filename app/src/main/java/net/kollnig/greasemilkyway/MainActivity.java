package net.kollnig.greasemilkyway;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.MenuItem;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageButton;
import android.graphics.Typeface;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
        
        // Setup navigation bar color to match app background
        setupNavigationBarColor();

        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.app_name);

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
        
        // Setup footer with clickable link
        setupFooter();
        
        // Setup navigation bar padding - reduces available height to push content above nav bar
        setupNavigationBarPadding();
    }

    private void setupFooter() {
        TextView footerText = findViewById(R.id.footer_text);
        
        String fullText = "Made with ❤️ by reddfocus.org";
        SpannableString spannableString = new SpannableString(fullText);
        
        int start = fullText.indexOf("reddfocus.org");
        int end = start + "reddfocus.org".length();
        
        // Make "reddfocus.org" clickable (no special styling)
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://reddfocus.org"));
                startActivity(browserIntent);
            }
            
            @Override
            public void updateDrawState(android.text.TextPaint ds) {
                // Keep default text color, no underline
                ds.setUnderlineText(false);
                ds.setColor(footerText.getCurrentTextColor());
            }
        };
        spannableString.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        footerText.setText(spannableString);
        footerText.setMovementMethod(LinkMovementMethod.getInstance());
        footerText.setHighlightColor(android.graphics.Color.TRANSPARENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload settings to pick up any new custom rules
        loadSettings();
        // Update adapter to grey out items when service disabled
        adapter.refreshServiceState();
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
            // Rules are already reloaded at the start of onResume()
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

    private void setupNavigationBarColor() {
        // Get the background color from theme
        int backgroundColor = getResources().getColor(R.color.background_main, getTheme());
        // Set navigation bar color to match app background
        getWindow().setNavigationBarColor(backgroundColor);
    }

    private void setupNavigationBarPadding() {
        // Apply window insets to root CoordinatorLayout to reduce available height
        // This pushes all content (footer, FAB, RecyclerView) up by nav bar height
        View rootLayout = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            
            // Set padding on root layout: top = status bar, bottom = nav bar
            // This reduces available height, pushing all content up uniformly
            v.setPadding(
                v.getPaddingLeft(),
                statusBarInsets.top,
                v.getPaddingRight(),
                navBarInsets.bottom
            );
            
            return insets;
        });
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